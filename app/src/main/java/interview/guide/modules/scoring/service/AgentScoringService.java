package interview.guide.modules.scoring.service;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.transaction.TransactionalExecutor;
import interview.guide.modules.engineer.model.EngineerEntity;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import interview.guide.modules.knowledgebase.service.KnowledgeBaseVectorService;
import interview.guide.modules.scoring.config.ScoringProperties;
import interview.guide.modules.engineer.repository.EngineerRepository;
import interview.guide.modules.engineer.service.EngineerService;
import interview.guide.modules.engineer.model.AuditAction;
import interview.guide.modules.invitation.model.InterviewInvitationEntity;
import interview.guide.modules.invitation.repository.InterviewInvitationRepository;
import interview.guide.modules.scoring.model.AgentScoreReport;
import interview.guide.modules.videointerview.model.CandidateInterviewEntity;
import interview.guide.modules.videointerview.model.CandidateInterviewStatus;
import interview.guide.modules.videointerview.repository.CandidateInterviewRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.stereotype.Service;

/**
 * AI 评分 Agent 服务
 *
 * 工具调用模式：模型自主决定调用信息工具收集证据（评分标准/画像/简历/答卷），
 * 证据齐全后输出结构化评分报告；工具链路异常时降级为单轮内联上下文评分，
 * 保证评分可用性。评分结果持久化在 candidate_interviews.ai_score
 */
@Slf4j
@Service
public class AgentScoringService {

  private static final int MAX_ATTEMPTS = 2;
  private static final int RESUME_MAX_CHARS = 6000;

  private final LlmProviderRegistry llmProviderRegistry;
  private final CandidateInterviewRepository interviewRepository;
  private final InterviewInvitationRepository invitationRepository;
  private final EngineerRepository engineerRepository;
  private final EngineerService engineerService;
  private final TransactionalExecutor transactionalExecutor;
  private final KnowledgeBaseVectorService knowledgeBaseVectorService;
  private final KnowledgeBaseRepository knowledgeBaseRepository;
  private final ScoringProperties scoringProperties;
  private final ScoringProgressEmitter progressEmitter;
  private final PromptTemplate systemPromptTemplate;
  private final PromptTemplate userPromptTemplate;
  private final String standardsText;

  public AgentScoringService(
      LlmProviderRegistry llmProviderRegistry,
      CandidateInterviewRepository interviewRepository,
      InterviewInvitationRepository invitationRepository,
      EngineerRepository engineerRepository,
      EngineerService engineerService,
      TransactionalExecutor transactionalExecutor,
      KnowledgeBaseVectorService knowledgeBaseVectorService,
      KnowledgeBaseRepository knowledgeBaseRepository,
      ScoringProperties scoringProperties,
      ScoringProgressEmitter progressEmitter
  ) {
    this.llmProviderRegistry = llmProviderRegistry;
    this.interviewRepository = interviewRepository;
    this.invitationRepository = invitationRepository;
    this.engineerRepository = engineerRepository;
    this.engineerService = engineerService;
    this.transactionalExecutor = transactionalExecutor;
    this.knowledgeBaseVectorService = knowledgeBaseVectorService;
    this.knowledgeBaseRepository = knowledgeBaseRepository;
    this.scoringProperties = scoringProperties;
    this.progressEmitter = progressEmitter;
    this.systemPromptTemplate = new PromptTemplate(
        loadPrompt("classpath:prompts/agent-scoring-system.st"));
    this.userPromptTemplate = new PromptTemplate(
        loadPrompt("classpath:prompts/agent-scoring-user.st"));
    this.standardsText = loadPrompt("classpath:prompts/scoring-standards.md");
  }

  /**
   * 执行一次评分（由 Redis Stream 消费者调用；LLM 调用在事务外，落库收敛到短事务）
   */
  public void runScoring(Long interviewId) {
    CandidateInterviewEntity interview = interviewRepository.findById(interviewId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_SESSION_NOT_FOUND));
    if (interview.getStatus() != CandidateInterviewStatus.COMPLETED
        || interview.getAnswers() == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "该候选人尚未提交答卷，无法评分");
    }

    InterviewInvitationEntity invitation = invitationRepository
        .findById(interview.getInvitationId())
        .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_NOT_FOUND));
    EngineerEntity engineer = engineerRepository.findById(invitation.getEngineerId())
        .orElseThrow(() -> new BusinessException(ErrorCode.ENGINEER_NOT_FOUND));

    AgentScoreReport report = executeAgent(interviewId, engineer, interview);

    transactionalExecutor.call(() -> {
      CandidateInterviewEntity fresh = interviewRepository.findById(interviewId)
          .orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_SESSION_NOT_FOUND));
      try {
        fresh.setAiScore(new ObjectMapper().writeValueAsString(report));
      } catch (JsonProcessingException e) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "评分报告序列化失败");
      }
      fresh.setAiScoreError(null);
      interviewRepository.save(fresh);
      return null;
    });

    engineerService.recordExternalAudit(engineer.getId(), AuditAction.AI_SCORED, "ai-agent",
        "ai-agent", "AI 评分完成，总分 " + report.overallScore() + "，建议 "
            + report.recommendation());
    log.info("AI 评分完成: interviewId={}, score={}, recommendation={}",
        interviewId, report.overallScore(), report.recommendation());
  }

  /**
   * 工具调用模式：模型自主拉取证据后输出结构化报告；
   * 工具链路不可用（模型不支持/网络等）时降级为单轮内联评分
   */
  private AgentScoreReport executeAgent(Long interviewId, EngineerEntity engineer,
      CandidateInterviewEntity interview) {
    BeanOutputConverter<AgentScoreReport> converter = new BeanOutputConverter<>(AgentScoreReport.class);
    String system = systemPromptTemplate.render(Map.of("format", converter.getFormat()));
    String user = userPromptTemplate.render(Map.of(
        "interviewId", String.valueOf(interviewId),
        "engineerName", safe(engineer.getName()),
        "position", safe(engineer.getPosition())));

    progressEmitter.emit(interviewId, "agent-step",
        Map.of("step", "start", "description", "Agent 开始收集评分证据"));
    ScoringTools tools = new ScoringTools(
        standardsText,
        this::searchScoringStandards,
        buildProfile(engineer),
        truncate(safe(engineer.getResumeText()), RESUME_MAX_CHARS),
        truncate(safe(interview.getAnswers()), RESUME_MAX_CHARS),
        truncate(safe(interview.getTranscript()), RESUME_MAX_CHARS),
        safe(interview.getVisualAnalysis()),
        interviewId,
        progressEmitter);

    Exception lastError = null;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        // 用不带 Advisor 的 plain client：Spring AI 内部工具执行即可，
        // 底座 ToolCallingAdvisor 在关闭会话历史时构造的第二轮消息序会被 GLM 判为非法
        ChatClient chatClient = llmProviderRegistry.getPlainChatClient(null);
        String content = chatClient.prompt()
            .system(system)
            .user(user)
            .tools(tools)
            .call()
            .content();
        return converter.convert(content);
      } catch (Exception e) {
        lastError = e;
        log.warn("Agent 工具调用评分失败（第 {} 次）: {}", attempt, e.getMessage());
      }
    }
    log.warn("工具调用模式降级为单轮内联评分: interviewId={}, lastError={}",
        interviewId, lastError != null ? lastError.getMessage() : "unknown");
    progressEmitter.emit(interviewId, "agent-step",
        Map.of("step", "fallback", "description", "工具链路异常，切换单轮内联评分"));
    return runInlineFallback(user, tools, converter);
  }

  /**
   * 降级：不走工具，把全部证据内联到 user prompt 单轮评分；
   * system 提示词不包含工具调用指令，避免模型输出伪工具调用文本
   */
  private AgentScoreReport runInlineFallback(String user, ScoringTools tools,
      BeanOutputConverter<AgentScoreReport> converter) {
    String inlineSystem = """
        你是车企供应商准入场景下的资深面试评估官，负责对候选人的线上视频面试进行结构化评分。
        所有评估证据已在用户消息中给出，请基于证据评分，禁止编造。

        输出要求：仅输出一个 JSON 对象（不要 Markdown 代码块、不要解释文字），结构如下：
        {format}
        """;
    String inlineSystemRendered = inlineSystem.replace("{format}", converter.getFormat());
    String inlineUser = "面试会话评估任务：\n\n【评分标准】\n" + tools.getScoringStandardsOverview()
        + "\n\n【候选人画像】\n" + tools.getEngineerProfile()
        + "\n\n【简历全文】\n" + tools.getResumeText()
        + "\n\n【面试答卷】\n" + tools.getInterviewAnswers();
    if (user != null && !user.isBlank()) {
      inlineUser = user + "\n\n" + inlineUser;
    }
    Exception lastError = null;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        ChatClient chatClient = llmProviderRegistry.getPlainChatClient(null);
        String content = chatClient.prompt()
            .system(inlineSystemRendered)
            .user(inlineUser)
            .call()
            .content();
        return converter.convert(content);
      } catch (Exception e) {
        lastError = e;
        log.warn("降级评分失败（第 {} 次）: {}", attempt, e.getMessage());
      }
    }
    throw new BusinessException(ErrorCode.AI_SERVICE_ERROR,
        "AI 评分失败: " + (lastError != null ? lastError.getMessage() : "unknown"));
  }

  private String buildProfile(EngineerEntity engineer) {
    return "姓名: " + safe(engineer.getName())
        + "\n应聘岗位: " + safe(engineer.getPosition())
        + "\n工作年限: " + (engineer.getExperienceYears() != null ? engineer.getExperienceYears() : "未填写")
        + "\n学历: " + safe(engineer.getEducation())
        + "\n技能: " + safe(engineer.getSkills());
  }

  /**
   * 评分标准向量检索：按分类定位标准知识库 → pgvector 相似度检索相关片段。
   * 标准库不存在或检索异常时返回 null（工具层回退到静态标准全文）。
   * 该方法在 Agent 工具调用线程执行，不在任何事务内（含 embedding 网络调用）
   */
  private String searchScoringStandards(String query) {
    try {
      var rubricKbs = knowledgeBaseRepository
          .findByCategoryOrderByUploadedAtDesc(scoringProperties.getRubricCategory());
      if (rubricKbs.isEmpty()) {
        log.debug("评分标准知识库（分类 {}）为空，使用静态标准", scoringProperties.getRubricCategory());
        return null;
      }
      List<Long> kbIds = rubricKbs.stream().map(kb -> kb.getId()).toList();
      List<Document> documents =
          knowledgeBaseVectorService.similaritySearch(query, kbIds,
              scoringProperties.getRubricTopK(), scoringProperties.getRubricMinScore());
      if (documents.isEmpty()) {
        return null;
      }
      StringBuilder builder = new StringBuilder();
      for (org.springframework.ai.document.Document doc : documents) {
        Object source = doc.getMetadata().get("file_name");
        builder.append("【标准片段")
            .append(source != null ? " · " + source : "")
            .append("】\n")
            .append(doc.getText())
            .append("\n---\n");
      }
      log.info("评分标准检索完成: query={}, 命中 {} 片段", query, documents.size());
      return builder.toString();
    } catch (Exception e) {
      log.warn("评分标准向量检索失败，回退静态标准: {}", e.getMessage());
      return null;
    }
  }

  private String truncate(String text, int maxChars) {
    if (text == null) {
      return "";
    }
    return text.length() <= maxChars ? text : text.substring(0, maxChars) + "\n...(已截断)";
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  private String loadPrompt(String location) {
    try (var in = new DefaultResourceLoader().getResource(location).getInputStream()) {
      return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("加载提示词模板失败: " + location, e);
    }
  }
}

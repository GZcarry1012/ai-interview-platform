package interview.guide.modules.scoring.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Map;
import java.util.function.Function;

/**
 * 评分 Agent 的信息工具集
 *
 * 每次评分构造一个实例，封装该候选人会话的证据数据；
 * 评分标准支持向量检索（按岗位动态获取相关标准片段），
 * 检索不可用时回退到静态标准全文；
 * 每次工具调用通过 SSE 向复核页推送实时进度
 */
public class ScoringTools {

  private final String standardsOverview;
  private final Function<String, String> standardsSearch;
  private final String profile;
  private final String resumeText;
  private final String answers;
  private final String transcript;
  private final String visualAnalysis;
  private final Long interviewId;
  private final ScoringProgressEmitter progressEmitter;

  public ScoringTools(String standardsOverview, Function<String, String> standardsSearch,
      String profile, String resumeText, String answers,
      String transcript, String visualAnalysis,
      Long interviewId, ScoringProgressEmitter progressEmitter) {
    this.standardsOverview = standardsOverview;
    this.standardsSearch = standardsSearch;
    this.profile = profile;
    this.resumeText = resumeText;
    this.answers = answers;
    this.transcript = transcript;
    this.visualAnalysis = visualAnalysis;
    this.interviewId = interviewId;
    this.progressEmitter = progressEmitter;
  }

  @Tool(description = "按主题检索岗位评分标准的相关片段。可选主题示例：评分维度与权重、"
      + "技术能力评分细则、项目真实性核验、表达与沟通评分。评分前应至少检索一次")
  public String searchScoringStandards(
      @ToolParam(description = "检索主题关键词，如：评分维度与权重") String query) {
    emitToolCall("searchScoringStandards", "检索评分标准：" + query);
    if (standardsSearch == null) {
      return standardsOverview;
    }
    String result = standardsSearch.apply(query);
    return result == null || result.isBlank()
        ? "未检索到相关标准片段，可调用 getScoringStandardsOverview 获取完整标准"
        : result;
  }

  @Tool(description = "获取评分标准的完整文本（当按主题检索不到所需片段时作为兜底）")
  public String getScoringStandardsOverview() {
    emitToolCall("getScoringStandardsOverview", "读取评分标准全文");
    return standardsOverview;
  }

  @Tool(description = "获取候选人基本画像：应聘岗位、工作年限、学历、技能清单")
  public String getEngineerProfile() {
    emitToolCall("getEngineerProfile", "读取候选人画像");
    return profile;
  }

  @Tool(description = "获取候选人简历全文，用于核对答卷所述经历的一致性")
  public String getResumeText() {
    emitToolCall("getResumeText", "读取简历全文");
    return resumeText;
  }

  @Tool(description = "获取候选人面试答卷原文（问题与作答）")
  public String getInterviewAnswers() {
    emitToolCall("getInterviewAnswers", "读取面试答卷");
    return answers;
  }

  @Tool(description = "获取候选人视频语音转写文本（口头陈述原文，用于与答卷和简历交叉验证）。可能不存在")
  public String getTranscript() {
    emitToolCall("getTranscript", "读取语音转写文本");
    return transcript == null || transcript.isBlank()
        ? "本会话无语音转写文本"
        : transcript;
  }

  @Tool(description = "获取视频画面视觉分析结果（在场性/环境/异常信号，用于风险判断）。可能不存在")
  public String getVisualAnalysis() {
    emitToolCall("getVisualAnalysis", "读取视频视觉分析");
    return visualAnalysis == null || visualAnalysis.isBlank()
        ? "本会话无视觉分析结果"
        : visualAnalysis;
  }

  private void emitToolCall(String tool, String description) {
    if (progressEmitter != null && interviewId != null) {
      progressEmitter.emit(interviewId, "agent-step", Map.of(
          "step", "tool_call",
          "tool", tool,
          "description", description));
    }
  }
}

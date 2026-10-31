package interview.guide.modules.scoring.service;

import org.springframework.ai.tool.annotation.Tool;

/**
 * 评分 Agent 的信息工具集
 *
 * 每次评分构造一个实例，封装该候选人会话的证据数据；
 * 模型自主决定调用顺序（先标准后证据），避免一次性塞入超长上下文
 */
public class ScoringTools {

  private final String standards;
  private final String profile;
  private final String resumeText;
  private final String answers;

  public ScoringTools(String standards, String profile, String resumeText, String answers) {
    this.standards = standards;
    this.profile = profile;
    this.resumeText = resumeText;
    this.answers = answers;
  }

  @Tool(description = "获取本岗位的评分标准、维度权重与风险点定义（建议最先调用）")
  public String getScoringStandards() {
    return standards;
  }

  @Tool(description = "获取候选人基本画像：应聘岗位、工作年限、学历、技能清单")
  public String getEngineerProfile() {
    return profile;
  }

  @Tool(description = "获取候选人简历全文，用于核对答卷所述经历的一致性")
  public String getResumeText() {
    return resumeText;
  }

  @Tool(description = "获取候选人面试答卷原文（问题与作答）")
  public String getInterviewAnswers() {
    return answers;
  }
}

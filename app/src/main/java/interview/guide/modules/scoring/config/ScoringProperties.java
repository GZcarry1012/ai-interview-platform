package interview.guide.modules.scoring.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 评分配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.scoring")
public class ScoringProperties {

  /** 评分标准知识库分类（该分类下的知识库将作为 Agent 检索的评分依据） */
  private String rubricCategory = "rubric";

  /** 评分标准检索条数 */
  private int rubricTopK = 4;

  /** 评分标准检索相似度阈值（0-1） */
  private double rubricMinScore = 0.3;
}

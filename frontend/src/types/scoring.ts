/**
 * AI 评分报告类型（与后端 AgentScoreReport 对齐）
 */
export interface DimensionScore {
  name: string;
  score: number;
  comment: string;
}

export interface QuestionFeedback {
  index: number;
  comment: string;
}

export interface AgentScoreReport {
  overallScore: number;
  dimensions: DimensionScore[];
  strengths: string[];
  risks: string[];
  recommendation: string;
  recommendationReason: string;
  summary: string;
  questionFeedback: QuestionFeedback[];
}

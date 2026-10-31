import request from './request';
import type { AgentScoreReport } from '../types/scoring';

export interface AiScoreStatusInfo {
  status: 'NOT_STARTED' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  displayName: string;
}

export interface ReviewListItem {
  id: number;
  engineerId: number;
  engineerName: string;
  position: string;
  status: string;
  durationSeconds: number | null;
  aiScoreStatus: AiScoreStatusInfo['status'];
  aiScoreStatusDisplayName: string;
  aiScoreReady: boolean;
  aiOverallScore: number | null;
  aiRecommendation: string | null;
  humanDecision: 'PENDING' | 'APPROVED' | 'REJECTED';
  humanDecisionDisplayName: string;
  humanComment: string | null;
  humanReviewedByName: string | null;
  humanReviewedAt: string | null;
  submittedAt: string | null;
}

export interface AnswerItem {
  index: number;
  question: string;
  answer: string;
}

export interface ReviewDetail {
  id: number;
  engineerName: string;
  position: string;
  videoStreamUrl: string | null;
  videoFileSize: number | null;
  durationSeconds: number | null;
  answers: AnswerItem[];
  aiScoreStatus: AiScoreStatusInfo['status'];
  aiScore: AgentScoreReport | null;
  aiScoreError: string | null;
  humanDecision: 'PENDING' | 'APPROVED' | 'REJECTED';
  humanComment: string | null;
  humanReviewedByName: string | null;
  humanReviewedAt: string | null;
  submittedAt: string | null;
}

const reviewApi = {
  list(): Promise<ReviewListItem[]> {
    return request.get<ReviewListItem[]>('/api/review/interviews');
  },

  detail(id: number): Promise<ReviewDetail> {
    return request.get<ReviewDetail>(`/api/review/interviews/${id}`);
  },

  triggerAiScore(id: number): Promise<void> {
    return request.post<void>(`/api/review/interviews/${id}/ai-score`);
  },

  humanReview(id: number, decision: 'APPROVED' | 'REJECTED', comment: string): Promise<ReviewDetail> {
    return request.post<ReviewDetail>(`/api/review/interviews/${id}/human-review`, {
      decision,
      comment,
    });
  },

  reportPdfUrl(id: number): string {
    return `/api/review/interviews/${id}/report.pdf`;
  },
};

export default reviewApi;

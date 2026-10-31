/**
 * 工程师准入与面试邀约类型定义
 */
export interface EngineerListItem {
  id: number;
  name: string;
  email: string;
  phone: string | null;
  position: string;
  experienceYears: number | null;
  education: string | null;
  status: 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED';
  statusDisplayName: string;
  resumeFileName: string | null;
  resumeFileSize: number | null;
  reviewComment: string | null;
  reviewedByName: string | null;
  reviewedAt: string | null;
  createdByName: string | null;
  createdAt: string | null;
  invitationSent: boolean;
}

export interface AuditLogItem {
  id: number;
  action: string;
  actionDisplayName: string;
  operatorName: string | null;
  operatorSource: string | null;
  comment: string | null;
  createdAt: string;
}

export interface EngineerDetail extends EngineerListItem {
  skills: string | null;
  resumeFileUrl: string | null;
  auditLogs: AuditLogItem[];
}

export interface EngineerSavePayload {
  name: string;
  phone?: string;
  email: string;
  position: string;
  experienceYears?: number;
  education?: string;
  skills?: string;
}

export interface InvitationDTO {
  id: number;
  engineerId: number;
  engineerName: string;
  invitedEmail: string;
  position: string | null;
  scheduledTime: string;
  status: 'PENDING' | 'CONFIRMED' | 'CANCELLED';
  statusDisplayName: string;
  mailStatus: 'PENDING' | 'SENDING' | 'SENT' | 'FAILED';
  mailStatusDisplayName: string;
  mailError: string | null;
  mailSentAt: string | null;
  confirmedAt: string | null;
  createdAt: string | null;
  invitationUrl: string;
}

/** 候选人视角（公开接口） */
export interface InvitationTokenInfo {
  engineerName: string;
  position: string | null;
  scheduledTime: string;
  status: 'PENDING' | 'CONFIRMED' | 'CANCELLED';
  statusDisplayName: string;
  confirmedAt: string | null;
}

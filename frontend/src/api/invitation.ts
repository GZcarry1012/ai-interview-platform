import request from './request';
import type { InvitationDTO, InvitationTokenInfo } from '../types/engineer';

const invitationApi = {
  /** 发送面试邀约（仅审核通过的工程师） */
  send(engineerId: number, scheduledTime: string, position?: string): Promise<InvitationDTO> {
    return request.post<InvitationDTO>(`/api/invitations/engineer/${engineerId}`, {
      scheduledTime,
      position,
    });
  },

  listByEngineer(engineerId: number): Promise<InvitationDTO[]> {
    return request.get<InvitationDTO[]>(`/api/invitations/engineer/${engineerId}`);
  },

  /** 候选人侧（免登录） */
  getByToken(token: string): Promise<InvitationTokenInfo> {
    return request.get<InvitationTokenInfo>(`/api/invitations/token/${token}`);
  },

  confirmByToken(token: string): Promise<InvitationTokenInfo> {
    return request.post<InvitationTokenInfo>(`/api/invitations/token/${token}/confirm`);
  },
};

export default invitationApi;

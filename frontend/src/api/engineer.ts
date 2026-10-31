import request from './request';
import type { EngineerDetail, EngineerListItem, EngineerSavePayload } from '../types/engineer';

/**
 * multipart 中 JSON part 的标准包装（服务端 @RequestPart 解析）
 */
function jsonPart(payload: unknown): Blob {
  return new Blob([JSON.stringify(payload)], { type: 'application/json' });
}

const engineerApi = {
  list(status?: string): Promise<EngineerListItem[]> {
    const query = status ? `?status=${encodeURIComponent(status)}` : '';
    return request.get<EngineerListItem[]>(`/api/engineers${query}`);
  },

  detail(id: number): Promise<EngineerDetail> {
    return request.get<EngineerDetail>(`/api/engineers/${id}`);
  },

  /** 录入工程师（简历必传） */
  create(payload: EngineerSavePayload, resume: File): Promise<EngineerListItem> {
    const formData = new FormData();
    formData.append('info', jsonPart(payload));
    formData.append('resume', resume);
    return request.upload<EngineerListItem>('/api/engineers', formData);
  },

  /** 驳回后修改并重新提交（简历可选） */
  resubmit(id: number, payload: EngineerSavePayload, resume?: File): Promise<EngineerListItem> {
    const formData = new FormData();
    formData.append('info', jsonPart(payload));
    if (resume) {
      formData.append('resume', resume);
    }
    return request.upload<EngineerListItem>(`/api/engineers/${id}`, formData);
  },

  approve(id: number, comment: string): Promise<EngineerListItem> {
    return request.post<EngineerListItem>(`/api/engineers/${id}/approve`, { comment });
  },

  reject(id: number, comment: string): Promise<EngineerListItem> {
    return request.post<EngineerListItem>(`/api/engineers/${id}/reject`, { comment });
  },
};

export default engineerApi;

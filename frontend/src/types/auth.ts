/**
 * 认证相关类型定义
 */
export interface UserDTO {
  id: number;
  username: string;
  displayName: string;
  email: string | null;
  role: string;
  roleDisplayName: string | null;
  orgType: string | null;
  orgName: string | null;
  status: string;
  createdAt: string | null;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: UserDTO;
}

export interface RegisterPayload {
  username: string;
  password: string;
  displayName: string;
  email?: string;
}

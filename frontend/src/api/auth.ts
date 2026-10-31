import request from './request';
import { clearTokens, getRefreshToken, saveTokens } from './tokenStorage';
import type { LoginResponse, RegisterPayload, UserDTO } from '../types/auth';

const authApi = {
  async login(username: string, password: string): Promise<LoginResponse> {
    const data = await request.post<LoginResponse>('/api/auth/login', { username, password });
    saveTokens(data.accessToken, data.refreshToken);
    return data;
  },

  async register(payload: RegisterPayload): Promise<UserDTO> {
    return request.post<UserDTO>('/api/auth/register', payload);
  },

  async me(): Promise<UserDTO> {
    return request.get<UserDTO>('/api/auth/me');
  },

  async refresh(): Promise<LoginResponse | null> {
    const refreshToken = getRefreshToken();
    if (!refreshToken) {
      return null;
    }
    try {
      const data = await request.post<LoginResponse>('/api/auth/refresh', { refreshToken });
      saveTokens(data.accessToken, data.refreshToken);
      return data;
    } catch {
      clearTokens();
      return null;
    }
  },

  async logout(): Promise<void> {
    try {
      await request.post('/api/auth/logout');
    } finally {
      clearTokens();
    }
  },
};

export default authApi;

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import authApi from '../api/auth';
import { getAccessToken } from '../api/tokenStorage';
import type { RegisterPayload, UserDTO } from '../types/auth';

interface AuthContextValue {
  user: UserDTO | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<UserDTO>;
  register: (payload: RegisterPayload) => Promise<UserDTO>;
  logout: () => Promise<void>;
  hasAnyRole: (...roles: string[]) => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserDTO | null>(null);
  const [loading, setLoading] = useState(true);

  // 初始化：本地有令牌则拉取当前用户，失败则静默清理
  useEffect(() => {
    if (!getAccessToken()) {
      setLoading(false);
      return;
    }
    authApi.me()
      .then(setUser)
      .catch(() => {
        // 令牌无效或过期：尝试刷新一次
        return authApi.refresh().then((data) => (data ? authApi.me().then(setUser) : null));
      })
      .catch(() => undefined)
      .finally(() => setLoading(false));
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const data = await authApi.login(username, password);
    setUser(data.user);
    return data.user;
  }, []);

  const register = useCallback(async (payload: RegisterPayload) => {
    return authApi.register(payload);
  }, []);

  const logout = useCallback(async () => {
    await authApi.logout();
    setUser(null);
  }, []);

  const hasAnyRole = useCallback((...roles: string[]) => {
    return user != null && roles.includes(user.role);
  }, [user]);

  const value = useMemo(
    () => ({ user, loading, login, register, logout, hasAnyRole }),
    [user, loading, login, register, logout, hasAnyRole],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth 必须在 AuthProvider 内使用');
  }
  return context;
}

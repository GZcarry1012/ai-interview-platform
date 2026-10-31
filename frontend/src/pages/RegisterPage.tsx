import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Sparkles, Loader2, UserPlus } from 'lucide-react';
import { useAuth } from '../hooks/useAuth';
import { getErrorMessage } from '../api/request';

export default function RegisterPage() {
  const { register, login } = useAuth();
  const navigate = useNavigate();

  const [username, setUsername] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (loading) {
      return;
    }
    if (!username.trim() || !displayName.trim() || !password) {
      setError('请填写完整信息');
      return;
    }
    if (password.length < 6) {
      setError('密码长度至少 6 位');
      return;
    }
    if (password !== confirmPassword) {
      setError('两次输入的密码不一致');
      return;
    }

    setError('');
    setLoading(true);
    try {
      // 注册成功后自动登录进入平台
      await register({
        username: username.trim(),
        password,
        displayName: displayName.trim(),
        email: email.trim() || undefined,
      });
      await login(username.trim(), password);
      navigate('/history', { replace: true });
    } catch (err) {
      setError(getErrorMessage(err));
      setLoading(false);
    }
  };

  const inputClass =
    'w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-800 text-slate-900 dark:text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent transition';

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-50 to-indigo-50 dark:from-slate-900 dark:to-slate-800 px-4 py-10">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="w-14 h-14 mx-auto bg-gradient-to-br from-primary-500 to-primary-600 rounded-2xl flex items-center justify-center text-white shadow-lg shadow-primary-500/30 mb-4">
            <Sparkles className="w-7 h-7" />
          </div>
          <h1 className="text-2xl font-bold text-slate-900 dark:text-white tracking-tight">创建账号</h1>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">注册求职者账号，开启智能面试练习</p>
        </div>

        <form
          onSubmit={handleSubmit}
          className="bg-white dark:bg-slate-900 rounded-2xl shadow-xl shadow-slate-200/50 dark:shadow-none border border-slate-100 dark:border-slate-700 p-8 space-y-4"
        >
          <div>
            <label htmlFor="reg-username" className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              用户名 <span className="text-slate-400">（字母/数字/下划线/中划线）</span>
            </label>
            <input
              id="reg-username"
              type="text"
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="如 engineer01"
              className={inputClass}
            />
          </div>

          <div>
            <label htmlFor="reg-display" className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              显示名
            </label>
            <input
              id="reg-display"
              type="text"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              placeholder="如 张工"
              className={inputClass}
            />
          </div>

          <div>
            <label htmlFor="reg-email" className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              邮箱 <span className="text-slate-400">（选填）</span>
            </label>
            <input
              id="reg-email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="name@example.com"
              className={inputClass}
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label htmlFor="reg-password" className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                密码
              </label>
              <input
                id="reg-password"
                type="password"
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="至少 6 位"
                className={inputClass}
              />
            </div>
            <div>
              <label htmlFor="reg-confirm" className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                确认密码
              </label>
              <input
                id="reg-confirm"
                type="password"
                autoComplete="new-password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                placeholder="再输入一次"
                className={inputClass}
              />
            </div>
          </div>

          {error && (
            <div className="px-4 py-3 rounded-xl bg-red-50 dark:bg-red-900/30 text-sm text-red-600 dark:text-red-400">
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full flex items-center justify-center gap-2 py-2.5 rounded-xl bg-primary-500 hover:bg-primary-600 disabled:bg-slate-300 dark:disabled:bg-slate-700 disabled:cursor-not-allowed text-white font-medium transition-colors"
          >
            {loading ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <UserPlus className="w-4 h-4" />
            )}
            {loading ? '注册中...' : '注册并登录'}
          </button>

          <p className="text-center text-sm text-slate-500 dark:text-slate-400">
            已有账号？
            <Link to="/login" className="text-primary-600 dark:text-primary-400 hover:underline font-medium">
              直接登录
            </Link>
          </p>
        </form>
      </div>
    </div>
  );
}

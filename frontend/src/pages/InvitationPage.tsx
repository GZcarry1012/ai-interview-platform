import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { CalendarCheck2, CheckCircle2, Loader2, Video, XCircle } from 'lucide-react';
import invitationApi from '../api/invitation';
import { getErrorMessage } from '../api/request';
import type { InvitationTokenInfo } from '../types/engineer';

/**
 * 候选人邀约确认页（公开，凭邮件中的 token 访问，无需登录）
 */
export default function InvitationPage() {
  const { token } = useParams<{ token: string }>();
  const [info, setInfo] = useState<InvitationTokenInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [confirming, setConfirming] = useState(false);

  const load = useCallback(async () => {
    if (!token) {
      return;
    }
    setLoading(true);
    setError('');
    try {
      setInfo(await invitationApi.getByToken(token));
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    load();
  }, [load]);

  const handleConfirm = async () => {
    if (!token || confirming) {
      return;
    }
    setConfirming(true);
    setError('');
    try {
      setInfo(await invitationApi.confirmByToken(token));
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setConfirming(false);
    }
  };

  const formatTime = (value: string | null) =>
    value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—';

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-50 to-indigo-50 dark:from-slate-900 dark:to-slate-800 px-4">
      <div className="w-full max-w-md">
        <div className="bg-white dark:bg-slate-900 rounded-2xl shadow-xl shadow-slate-200/50 dark:shadow-none border border-slate-100 dark:border-slate-700 p-8">
          {loading ? (
            <div className="flex justify-center py-12">
              <Loader2 className="w-8 h-8 text-primary-500 animate-spin" />
            </div>
          ) : error ? (
            <div className="text-center py-6">
              <XCircle className="w-12 h-12 mx-auto text-red-400 mb-3" />
              <p className="text-slate-700 dark:text-slate-200 font-medium mb-1">无法打开邀约</p>
              <p className="text-sm text-slate-500 dark:text-slate-400">{error}</p>
            </div>
          ) : info ? (
            <>
              <div className="text-center mb-6">
                <div className="w-14 h-14 mx-auto bg-gradient-to-br from-primary-500 to-primary-600 rounded-2xl flex items-center justify-center text-white shadow-lg shadow-primary-500/30 mb-4">
                  <CalendarCheck2 className="w-7 h-7" />
                </div>
                <h1 className="text-xl font-bold text-slate-900 dark:text-white">面试邀约确认</h1>
                <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
                  {info.engineerName}，您已通过简历初筛
                </p>
              </div>

              <div className="space-y-3 text-sm bg-slate-50 dark:bg-slate-800/60 rounded-xl p-4">
                <div className="flex justify-between">
                  <span className="text-slate-500 dark:text-slate-400">应聘岗位</span>
                  <span className="text-slate-800 dark:text-white font-medium">{info.position ?? '—'}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500 dark:text-slate-400">预约时间</span>
                  <span className="text-slate-800 dark:text-white font-medium">{formatTime(info.scheduledTime)}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500 dark:text-slate-400">面试形式</span>
                  <span className="text-slate-800 dark:text-white font-medium">线上视频面试</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500 dark:text-slate-400">当前状态</span>
                  <span className={`font-medium ${info.status === 'CONFIRMED' ? 'text-emerald-600 dark:text-emerald-400' : 'text-amber-600 dark:text-amber-400'}`}>
                    {info.statusDisplayName}
                  </span>
                </div>
              </div>

              {info.status === 'PENDING' && (
                <button
                  onClick={handleConfirm}
                  disabled={confirming}
                  className="mt-6 w-full flex items-center justify-center gap-2 py-2.5 rounded-xl bg-primary-500 hover:bg-primary-600 disabled:opacity-50 text-white font-medium transition-colors"
                >
                  {confirming ? <Loader2 className="w-4 h-4 animate-spin" /> : <CheckCircle2 className="w-4 h-4" />}
                  {confirming ? '提交中...' : '确认参加面试'}
                </button>
              )}
              {info.status === 'CONFIRMED' && (
                <div className="mt-6 space-y-3">
                  <div className="px-4 py-3 rounded-xl bg-emerald-50 dark:bg-emerald-900/30 text-sm text-emerald-600 dark:text-emerald-300 text-center">
                    已确认参加，面试前请准备好摄像头与麦克风
                  </div>
                  <Link
                    to={`/interview-room/${token}`}
                    className="w-full flex items-center justify-center gap-2 py-2.5 rounded-xl bg-primary-500 hover:bg-primary-600 text-white font-medium transition-colors"
                  >
                    <Video className="w-4 h-4" />
                    进入面试（设备检测与录制）
                  </Link>
                </div>
              )}
            </>
          ) : null}

          {error && info && (
            <div className="mt-4 px-4 py-3 rounded-xl bg-red-50 dark:bg-red-900/30 text-sm text-red-600 dark:text-red-300">
              {error}
            </div>
          )}
        </div>

        <p className="text-center text-xs text-slate-400 mt-4">
          <Link to="/login" className="hover:underline">面试平台首页</Link>
          · 遇到问题请联系您的对接人
        </p>
      </div>
    </div>
  );
}

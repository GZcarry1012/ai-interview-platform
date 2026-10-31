import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  Bot,
  CheckCircle2,
  Download,
  Loader2,
  RefreshCw,
  ThumbsDown,
  ThumbsUp,
  XCircle,
} from 'lucide-react';
import reviewApi from '../api/review';
import { getErrorMessage } from '../api/request';
import type { ReviewDetail } from '../api/review';
import type { DimensionScore } from '../types/scoring';

export default function ReviewDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [detail, setDetail] = useState<ReviewDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');
  const [busy, setBusy] = useState(false);
  const [comment, setComment] = useState('');

  const load = useCallback(async () => {
    if (!id) {
      return;
    }
    try {
      setDetail(await reviewApi.detail(Number(id)));
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  const handleTrigger = async () => {
    if (!id || busy) {
      return;
    }
    setBusy(true);
    setActionError('');
    try {
      await reviewApi.triggerAiScore(Number(id));
      // 触发后开始轮询评分状态
      const timer = setInterval(async () => {
        try {
          const d = await reviewApi.detail(Number(id));
          setDetail(d);
          if (d.aiScoreStatus === 'COMPLETED' || d.aiScoreStatus === 'FAILED') {
            clearInterval(timer);
          }
        } catch {
          clearInterval(timer);
        }
      }, 4000);
    } catch (err) {
      setActionError(getErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const handleHumanReview = async (decision: 'APPROVED' | 'REJECTED') => {
    if (!id || busy) {
      return;
    }
    setBusy(true);
    setActionError('');
    try {
      setDetail(await reviewApi.humanReview(Number(id), decision, comment));
    } catch (err) {
      setActionError(getErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center py-20">
        <Loader2 className="w-6 h-6 text-primary-500 animate-spin" />
      </div>
    );
  }
  if (error || !detail) {
    return (
      <div className="max-w-3xl mx-auto text-center py-16">
        <XCircle className="w-12 h-12 mx-auto text-red-400 mb-3" />
        <p className="text-slate-600 dark:text-slate-300">{error || '记录不存在'}</p>
        <Link to="/review" className="text-sm text-primary-600 hover:underline mt-3 inline-block">
          返回复核列表
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-900 dark:text-white">
            复核详情 · {detail.engineerName}
          </h1>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
            {detail.position}
            {detail.durationSeconds != null && ` · 面试时长 ${Math.round(detail.durationSeconds / 60)} 分钟`}
          </p>
        </div>
        <button
          onClick={() => navigate('/review')}
          className="px-3.5 py-2 rounded-xl text-sm border border-slate-200 dark:border-slate-600 text-slate-600 dark:text-slate-300"
        >
          返回列表
        </button>
      </div>

      {actionError && (
        <div className="px-4 py-3 rounded-xl bg-red-50 dark:bg-red-900/30 text-sm text-red-600 dark:text-red-300">
          {actionError}
        </div>
      )}

      {/* 视频回放 */}
      <div className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-700 p-5 space-y-3">
        <h2 className="font-semibold text-slate-800 dark:text-white">面试视频回放</h2>
        {detail.videoStreamUrl ? (
          <video
            src={detail.videoStreamUrl}
            controls
            className="w-full rounded-xl bg-black aspect-video"
          />
        ) : (
          <p className="text-sm text-slate-400">视频未归档</p>
        )}
      </div>

      {/* AI 评分 */}
      <div className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-700 p-5 space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="font-semibold text-slate-800 dark:text-white flex items-center gap-2">
            <Bot className="w-5 h-5 text-primary-500" /> AI 评分报告
          </h2>
          {detail.aiScoreStatus !== 'COMPLETED' && detail.aiScoreStatus !== 'RUNNING' && (
            <button
              onClick={handleTrigger}
              disabled={busy}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs bg-primary-500 hover:bg-primary-600 disabled:opacity-50 text-white"
            >
              <RefreshCw className="w-3.5 h-3.5" />
              触发 AI 评分
            </button>
          )}
          {detail.aiScoreStatus === 'RUNNING' && (
            <span className="flex items-center gap-1.5 text-xs text-amber-600 dark:text-amber-400">
              <Loader2 className="w-3.5 h-3.5 animate-spin" /> 评分进行中...
            </span>
          )}
        </div>

        {detail.aiScoreStatus === 'FAILED' && (
          <p className="text-sm text-red-500">评分失败：{detail.aiScoreError}</p>
        )}

        {detail.aiScore ? (
          <div className="space-y-4">
            <div className="flex items-center gap-4">
              <div className="w-20 h-20 rounded-2xl bg-gradient-to-br from-primary-500 to-primary-600 flex items-center justify-center text-white text-2xl font-bold">
                {detail.aiScore.overallScore}
              </div>
              <div>
                <p className="text-sm font-medium text-slate-700 dark:text-slate-200">
                  建议：{detail.aiScore.recommendation}
                </p>
                <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                  {detail.aiScore.recommendationReason}
                </p>
              </div>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
              {detail.aiScore.dimensions.map((d: DimensionScore) => (
                <div key={d.name} className="px-3 py-2 rounded-xl bg-slate-50 dark:bg-slate-800/60 text-sm">
                  <p className="font-medium text-slate-700 dark:text-slate-200">
                    {d.name} · {d.score} 分
                  </p>
                  <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">{d.comment}</p>
                </div>
              ))}
            </div>
            {detail.aiScore.risks.length > 0 && (
              <div className="px-3 py-2 rounded-xl bg-red-50 dark:bg-red-900/20 text-sm text-red-600 dark:text-red-300">
                风险点：{detail.aiScore.risks.join('；')}
              </div>
            )}
            <p className="text-sm text-slate-600 dark:text-slate-300">
              评语：{detail.aiScore.summary}
            </p>
          </div>
        ) : (
          detail.aiScoreStatus !== 'RUNNING' && (
            <p className="text-sm text-slate-400">尚未生成评分报告</p>
          )
        )}
      </div>

      {/* 答卷 */}
      <div className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-700 p-5 space-y-3">
        <h2 className="font-semibold text-slate-800 dark:text-white">答卷</h2>
        {detail.answers.length === 0 ? (
          <p className="text-sm text-slate-400">无答卷内容</p>
        ) : (
          detail.answers.map((a) => (
            <div key={a.index}>
              <p className="text-sm font-medium text-slate-700 dark:text-slate-200">
                {a.index + 1}. {a.question}
              </p>
              <p className="text-sm text-slate-600 dark:text-slate-300 whitespace-pre-wrap mt-0.5">
                {a.answer || '（未作答）'}
              </p>
            </div>
          ))
        )}
      </div>

      {/* 人工复核 */}
      <div className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-700 p-5 space-y-4">
        <h2 className="font-semibold text-slate-800 dark:text-white">人工复核</h2>
        {detail.humanDecision !== 'PENDING' ? (
          <div
            className={`px-4 py-3 rounded-xl text-sm ${
              detail.humanDecision === 'APPROVED'
                ? 'bg-emerald-50 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-300'
                : 'bg-red-50 dark:bg-red-900/30 text-red-600 dark:text-red-300'
            }`}
          >
            {detail.humanDecision === 'APPROVED' ? (
              <CheckCircle2 className="w-4 h-4 inline mr-1.5 -mt-0.5" />
            ) : (
              <XCircle className="w-4 h-4 inline mr-1.5 -mt-0.5" />
            )}
            {detail.humanDecision === "APPROVED" ? "复核通过" : "复核不通过"}
            {detail.humanComment ? `：${detail.humanComment}` : ''}
            {detail.humanReviewedByName && (
              <span className="text-xs opacity-70 ml-2">
                （{detail.humanReviewedByName} ·{' '}
                {detail.humanReviewedAt?.replace('T', ' ').slice(0, 19)}）
              </span>
            )}
          </div>
        ) : (
          <>
            <textarea
              rows={2}
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              placeholder="复核意见（可选）"
              className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-800 text-slate-900 dark:text-white placeholder-slate-400 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent transition"
            />
            <div className="flex justify-end gap-2">
              <button
                onClick={() => handleHumanReview('REJECTED')}
                disabled={busy}
                className="flex items-center gap-1.5 px-4 py-2 rounded-xl text-sm bg-red-500 hover:bg-red-600 disabled:opacity-50 text-white font-medium"
              >
                <ThumbsDown className="w-4 h-4" />
                不通过
              </button>
              <button
                onClick={() => handleHumanReview('APPROVED')}
                disabled={busy}
                className="flex items-center gap-1.5 px-4 py-2 rounded-xl text-sm bg-emerald-500 hover:bg-emerald-600 disabled:opacity-50 text-white font-medium"
              >
                <ThumbsUp className="w-4 h-4" />
                复核通过
              </button>
            </div>
          </>
        )}
        <a
          href={reviewApi.reportPdfUrl(detail.id)}
          className="inline-flex items-center gap-1.5 text-sm text-primary-600 dark:text-primary-400 hover:underline"
        >
          <Download className="w-4 h-4" />
          下载 PDF 归档报告
        </a>
      </div>
    </div>
  );
}

import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Bot, FileSearch, Loader2 } from 'lucide-react';
import reviewApi from '../api/review';
import { getErrorMessage } from '../api/request';
import type { ReviewListItem } from '../api/review';

const aiBadgeClass: Record<string, string> = {
  NOT_STARTED: 'bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400',
  RUNNING: 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300',
  COMPLETED: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300',
  FAILED: 'bg-red-100 text-red-600 dark:bg-red-900/40 dark:text-red-300',
};

const humanBadgeClass: Record<string, string> = {
  PENDING: 'bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400',
  APPROVED: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300',
  REJECTED: 'bg-red-100 text-red-600 dark:bg-red-900/40 dark:text-red-300',
};

export default function ReviewWorkbenchPage() {
  const [items, setItems] = useState<ReviewListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    reviewApi.list()
      .then(setItems)
      .catch((err) => setError(getErrorMessage(err)))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="max-w-5xl mx-auto">
      <div className="mb-6">
        <h1 className="text-xl font-bold text-slate-900 dark:text-white">人工复核工作台</h1>
        <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
          查看候选人视频与答卷，结合 AI 评分进行人工定夺
        </p>
      </div>

      {error && (
        <div className="mb-4 px-4 py-3 rounded-xl bg-red-50 dark:bg-red-900/30 text-sm text-red-600 dark:text-red-300">
          {error}
        </div>
      )}

      {loading ? (
        <div className="flex justify-center py-16">
          <Loader2 className="w-6 h-6 text-primary-500 animate-spin" />
        </div>
      ) : items.length === 0 ? (
        <div className="py-16 text-center text-sm text-slate-400">
          暂无待复核的面试记录——候选人提交答卷后会出现在这里
        </div>
      ) : (
        <div className="space-y-3">
          {items.map((item) => (
            <div
              key={item.id}
              className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-700 p-5 flex flex-wrap items-center gap-4"
            >
              <div className="flex-1 min-w-[200px]">
                <p className="font-medium text-slate-800 dark:text-white">
                  {item.engineerName}
                  <span className="text-xs text-slate-400 ml-2">#{item.id}</span>
                </p>
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  {item.position}
                  {item.durationSeconds != null && ` · 面试 ${Math.round(item.durationSeconds / 60)} 分钟`}
                </p>
              </div>

              <div className="flex items-center gap-2">
                <span className={`px-2.5 py-1 rounded-lg text-xs font-medium ${aiBadgeClass[item.aiScoreStatus]}`}>
                  AI：{item.aiScoreStatusDisplayName}
                  {item.aiOverallScore != null && ` ${item.aiOverallScore} 分`}
                </span>
                <span className={`px-2.5 py-1 rounded-lg text-xs font-medium ${humanBadgeClass[item.humanDecision]}`}>
                  人工：{item.humanDecisionDisplayName}
                </span>
              </div>

              <div className="flex items-center gap-2">
                {item.aiScoreReady && (
                  <span className="px-2 py-1 rounded-lg text-xs bg-indigo-50 dark:bg-indigo-900/30 text-indigo-500">
                    建议 {item.aiRecommendation}
                  </span>
                )}
                <Link
                  to={`/review/${item.id}`}
                  className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-sm bg-primary-500 hover:bg-primary-600 text-white font-medium transition-colors"
                >
                  <FileSearch className="w-4 h-4" />
                  复核详情
                </Link>
              </div>
            </div>
          ))}
        </div>
      )}

      <p className="text-xs text-slate-400 mt-6 flex items-center gap-1.5">
        <Bot className="w-3.5 h-3.5" />
        AI 评分由 Agent 自动收集答卷/简历/评分标准后生成，仅供复核参考
      </p>
    </div>
  );
}

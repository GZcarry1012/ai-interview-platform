import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  BadgeCheck,
  CheckCircle2,
  FileText,
  Loader2,
  Plus,
  SendHorizonal,
  XCircle,
} from 'lucide-react';
import engineerApi from '../api/engineer';
import invitationApi from '../api/invitation';
import { getErrorMessage } from '../api/request';
import { useAuth } from '../hooks/useAuth';
import type {
  EngineerDetail,
  EngineerListItem,
  EngineerSavePayload,
} from '../types/engineer';

type StatusFilter = '' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED';

const STATUS_TABS: { value: StatusFilter; label: string }[] = [
  { value: '', label: '全部' },
  { value: 'PENDING_REVIEW', label: '待审核' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
];

const statusBadgeClass: Record<string, string> = {
  PENDING_REVIEW: 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300',
  APPROVED: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300',
  REJECTED: 'bg-red-100 text-red-600 dark:bg-red-900/40 dark:text-red-300',
};

const emptyForm: EngineerSavePayload = {
  name: '',
  phone: '',
  email: '',
  position: '',
  experienceYears: undefined,
  education: '',
  skills: '',
};

export default function EngineersPage() {
  const { hasAnyRole } = useAuth();
  const isPartnerAdmin = hasAnyRole('PARTNER_ADMIN');
  const isReviewer = hasAnyRole('REVIEWER', 'ADMIN');

  const [status, setStatus] = useState<StatusFilter>('');
  const [items, setItems] = useState<EngineerListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');

  // 弹窗状态
  const [formOpen, setFormOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<EngineerSavePayload>(emptyForm);
  const [resumeFile, setResumeFile] = useState<File | null>(null);
  const [formSaving, setFormSaving] = useState(false);
  const [reviewTarget, setReviewTarget] = useState<EngineerListItem | null>(null);
  const [reviewComment, setReviewComment] = useState('');
  const [reviewKind, setReviewKind] = useState<'approve' | 'reject'>('approve');
  const [inviteTarget, setInviteTarget] = useState<EngineerListItem | null>(null);
  const [inviteTime, setInviteTime] = useState('');
  const [detailOpen, setDetailOpen] = useState(false);
  const [detail, setDetail] = useState<EngineerDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const reload = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setItems(await engineerApi.list(status || undefined));
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [status]);

  useEffect(() => {
    reload();
  }, [reload]);

  const openCreate = () => {
    setEditingId(null);
    setForm(emptyForm);
    setResumeFile(null);
    setActionError('');
    setFormOpen(true);
  };

  const openResubmit = (item: EngineerListItem) => {
    setEditingId(item.id);
    setForm({
      name: item.name,
      phone: item.phone ?? '',
      email: item.email,
      position: item.position,
      experienceYears: item.experienceYears ?? undefined,
      education: item.education ?? '',
      skills: '',
    });
    setResumeFile(null);
    setActionError('');
    setFormOpen(true);
  };

  const handleFormSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (formSaving) {
      return;
    }
    if (!form.name.trim() || !form.email.trim() || !form.position.trim()) {
      setActionError('姓名、邮箱、岗位为必填项');
      return;
    }
    if (editingId == null && !resumeFile) {
      setActionError('请上传工程师简历附件');
      return;
    }
    setActionError('');
    setFormSaving(true);
    try {
      if (editingId == null) {
        await engineerApi.create(form, resumeFile!);
      } else {
        await engineerApi.resubmit(editingId, form, resumeFile ?? undefined);
      }
      setFormOpen(false);
      await reload();
    } catch (err) {
      setActionError(getErrorMessage(err));
    } finally {
      setFormSaving(false);
    }
  };

  const openReview = (item: EngineerListItem, kind: 'approve' | 'reject') => {
    setReviewTarget(item);
    setReviewKind(kind);
    setReviewComment('');
    setActionError('');
  };

  const handleReview = async () => {
    if (!reviewTarget || formSaving) {
      return;
    }
    setFormSaving(true);
    setActionError('');
    try {
      if (reviewKind === 'approve') {
        await engineerApi.approve(reviewTarget.id, reviewComment);
      } else {
        await engineerApi.reject(reviewTarget.id, reviewComment);
      }
      setReviewTarget(null);
      await reload();
    } catch (err) {
      setActionError(getErrorMessage(err));
    } finally {
      setFormSaving(false);
    }
  };

  const handleInvite = async () => {
    if (!inviteTarget || !inviteTime || formSaving) {
      return;
    }
    setFormSaving(true);
    setActionError('');
    try {
      await invitationApi.send(inviteTarget.id, new Date(inviteTime).toISOString());
      setInviteTarget(null);
      await reload();
    } catch (err) {
      setActionError(getErrorMessage(err));
    } finally {
      setFormSaving(false);
    }
  };

  const openDetail = async (item: EngineerListItem) => {
    setDetailOpen(true);
    setDetailLoading(true);
    setDetail(null);
    try {
      setDetail(await engineerApi.detail(item.id));
    } catch (err) {
      setActionError(getErrorMessage(err));
      setDetailOpen(false);
    } finally {
      setDetailLoading(false);
    }
  };

  const modalShell = useMemo(() => (
    'fixed inset-0 z-[60] flex items-center justify-center bg-black/40 px-4'
  ), []);

  return (
    <div className="max-w-6xl mx-auto">
      <div className="flex flex-wrap items-center justify-between gap-3 mb-6">
        <div>
          <h1 className="text-xl font-bold text-slate-900 dark:text-white">工程师准入管理</h1>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
            供应商工程师资料录入、简历审核与面试邀约
          </p>
        </div>
        {isPartnerAdmin && (
          <button
            onClick={openCreate}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-primary-500 hover:bg-primary-600 text-white text-sm font-medium transition-colors"
          >
            <Plus className="w-4 h-4" />
            录入工程师
          </button>
        )}
      </div>

      {/* 状态筛选 */}
      <div className="flex gap-2 mb-4">
        {STATUS_TABS.map((tab) => (
          <button
            key={tab.value}
            onClick={() => setStatus(tab.value)}
            className={`px-3.5 py-1.5 rounded-lg text-sm font-medium transition-colors ${
              status === tab.value
                ? 'bg-primary-500 text-white'
                : 'bg-white dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-700 border border-slate-200 dark:border-slate-600'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {error && (
        <div className="mb-4 px-4 py-3 rounded-xl bg-red-50 dark:bg-red-900/30 text-sm text-red-600 dark:text-red-300">
          {error}
        </div>
      )}
      {actionError && !formOpen && !reviewTarget && !inviteTarget && (
        <div className="mb-4 px-4 py-3 rounded-xl bg-red-50 dark:bg-red-900/30 text-sm text-red-600 dark:text-red-300">
          {actionError}
        </div>
      )}

      {/* 列表 */}
      <div className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-700 overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 text-primary-500 animate-spin" />
          </div>
        ) : items.length === 0 ? (
          <div className="py-16 text-center text-sm text-slate-400">暂无工程师记录</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 dark:bg-slate-800/60 text-slate-500 dark:text-slate-400">
                <tr>
                  <th className="text-left px-4 py-3 font-medium">工程师</th>
                  <th className="text-left px-4 py-3 font-medium">岗位 / 经验</th>
                  <th className="text-left px-4 py-3 font-medium">状态</th>
                  <th className="text-left px-4 py-3 font-medium">审核意见</th>
                  <th className="text-left px-4 py-3 font-medium">操作</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-700">
                {items.map((item) => (
                  <tr key={item.id} className="hover:bg-slate-50/60 dark:hover:bg-slate-800/40">
                    <td className="px-4 py-3">
                      <p className="font-medium text-slate-800 dark:text-white">{item.name}</p>
                      <p className="text-xs text-slate-400">{item.email}</p>
                    </td>
                    <td className="px-4 py-3">
                      <p className="text-slate-700 dark:text-slate-300">{item.position}</p>
                      <p className="text-xs text-slate-400">
                        {item.experienceYears != null ? `${item.experienceYears} 年` : '—'}
                        {item.education ? ` · ${item.education}` : ''}
                      </p>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`inline-block px-2.5 py-1 rounded-lg text-xs font-medium ${statusBadgeClass[item.status] ?? ''}`}>
                        {item.statusDisplayName}
                      </span>
                      {item.invitationSent && (
                        <span className="ml-1 inline-block px-2 py-1 rounded-lg text-xs bg-indigo-100 text-indigo-600 dark:bg-indigo-900/40 dark:text-indigo-300">
                          已邀约
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 max-w-[200px]">
                      {item.reviewComment ? (
                        <p className="text-xs text-slate-500 dark:text-slate-400 truncate" title={item.reviewComment}>
                          {item.reviewedByName ? `${item.reviewedByName}：` : ''}{item.reviewComment}
                        </p>
                      ) : (
                        <span className="text-xs text-slate-300">—</span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-1.5">
                        <button
                          onClick={() => openDetail(item)}
                          className="px-2.5 py-1.5 rounded-lg text-xs border border-slate-200 dark:border-slate-600 text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
                        >
                          详情
                        </button>
                        {isReviewer && item.status === 'PENDING_REVIEW' && (
                          <>
                            <button
                              onClick={() => openReview(item, 'approve')}
                              className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-xs bg-emerald-500 hover:bg-emerald-600 text-white transition-colors"
                            >
                              <CheckCircle2 className="w-3.5 h-3.5" />
                              通过
                            </button>
                            <button
                              onClick={() => openReview(item, 'reject')}
                              className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-xs bg-red-500 hover:bg-red-600 text-white transition-colors"
                            >
                              <XCircle className="w-3.5 h-3.5" />
                              驳回
                            </button>
                          </>
                        )}
                        {isPartnerAdmin && item.status === 'REJECTED' && (
                          <button
                            onClick={() => openResubmit(item)}
                            className="px-2.5 py-1.5 rounded-lg text-xs bg-primary-500 hover:bg-primary-600 text-white transition-colors"
                          >
                            重新提交
                          </button>
                        )}
                        {item.status === 'APPROVED' && !item.invitationSent && (
                          <button
                            onClick={() => {
                              setInviteTarget(item);
                              setInviteTime('');
                              setActionError('');
                            }}
                            className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-xs bg-indigo-500 hover:bg-indigo-600 text-white transition-colors"
                          >
                            <SendHorizonal className="w-3.5 h-3.5" />
                            发送邀约
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* 录入 / 重新提交弹窗 */}
      {formOpen && (
        <div className={modalShell} onClick={(e) => e.target === e.currentTarget && setFormOpen(false)}>
          <form
            onSubmit={handleFormSubmit}
            className="bg-white dark:bg-slate-900 rounded-2xl w-full max-w-lg p-6 space-y-3.5 max-h-[90vh] overflow-y-auto"
          >
            <h2 className="text-lg font-bold text-slate-900 dark:text-white">
              {editingId == null ? '录入工程师' : '修改并重新提交'}
            </h2>
            <div className="grid grid-cols-2 gap-3">
              <input required placeholder="姓名 *" value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                className={inputClass} />
              <input placeholder="手机号" value={form.phone ?? ''}
                onChange={(e) => setForm({ ...form, phone: e.target.value })}
                className={inputClass} />
              <input required type="email" placeholder="邮箱 *" value={form.email}
                onChange={(e) => setForm({ ...form, email: e.target.value })}
                className={inputClass} />
              <input required placeholder="应聘岗位 *" value={form.position}
                onChange={(e) => setForm({ ...form, position: e.target.value })}
                className={inputClass} />
              <input type="number" min={0} placeholder="工作年限" value={form.experienceYears ?? ''}
                onChange={(e) => setForm({ ...form, experienceYears: e.target.value ? Number(e.target.value) : undefined })}
                className={inputClass} />
              <input placeholder="学历" value={form.education ?? ''}
                onChange={(e) => setForm({ ...form, education: e.target.value })}
                className={inputClass} />
            </div>
            <textarea placeholder="技能清单（供 AI 评估参考）" rows={2} value={form.skills ?? ''}
              onChange={(e) => setForm({ ...form, skills: e.target.value })}
              className={inputClass} />
            <label className="block">
              <span className="text-sm text-slate-600 dark:text-slate-300">
                简历附件 {editingId == null ? '*' : '（可选，替换旧简历）'}
              </span>
              <input type="file" accept=".pdf,.doc,.docx,.txt"
                onChange={(e) => setResumeFile(e.target.files?.[0] ?? null)}
                className="mt-1 block w-full text-sm text-slate-500 dark:text-slate-400 file:mr-3 file:py-2 file:px-3 file:rounded-lg file:border-0 file:bg-primary-50 dark:file:bg-slate-800 file:text-primary-600 dark:file:text-primary-300 file:text-sm file:font-medium" />
            </label>
            {actionError && (
              <div className="px-3 py-2 rounded-lg bg-red-50 dark:bg-red-900/30 text-sm text-red-600 dark:text-red-300">
                {actionError}
              </div>
            )}
            <div className="flex justify-end gap-2 pt-1">
              <button type="button" onClick={() => setFormOpen(false)}
                className="px-4 py-2 rounded-xl text-sm border border-slate-200 dark:border-slate-600 text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-700">
                取消
              </button>
              <button type="submit" disabled={formSaving}
                className="flex items-center gap-1.5 px-4 py-2 rounded-xl text-sm bg-primary-500 hover:bg-primary-600 disabled:opacity-50 text-white font-medium">
                {formSaving && <Loader2 className="w-4 h-4 animate-spin" />}
                {editingId == null ? '提交审核' : '重新提交'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* 审核弹窗 */}
      {reviewTarget && (
        <div className={modalShell} onClick={(e) => e.target === e.currentTarget && setReviewTarget(null)}>
          <div className="bg-white dark:bg-slate-900 rounded-2xl w-full max-w-md p-6 space-y-4">
            <h2 className="text-lg font-bold text-slate-900 dark:text-white">
              {reviewKind === 'approve' ? '通过审核' : '驳回资料'} - {reviewTarget.name}
            </h2>
            <p className="text-sm text-slate-500 dark:text-slate-400">
              应聘岗位：{reviewTarget.position} · {reviewTarget.resumeFileName}
            </p>
            <textarea
              rows={3}
              value={reviewComment}
              onChange={(e) => setReviewComment(e.target.value)}
              placeholder={reviewKind === 'approve' ? '审核意见（可选）' : '驳回原因（建议填写，便于供应商修改）'}
              className={inputClass}
            />
            {actionError && (
              <div className="px-3 py-2 rounded-lg bg-red-50 dark:bg-red-900/30 text-sm text-red-600 dark:text-red-300">
                {actionError}
              </div>
            )}
            <div className="flex justify-end gap-2">
              <button onClick={() => setReviewTarget(null)}
                className="px-4 py-2 rounded-xl text-sm border border-slate-200 dark:border-slate-600 text-slate-600 dark:text-slate-300">
                取消
              </button>
              <button onClick={handleReview} disabled={formSaving}
                className={`flex items-center gap-1.5 px-4 py-2 rounded-xl text-sm text-white font-medium disabled:opacity-50 ${
                  reviewKind === 'approve' ? 'bg-emerald-500 hover:bg-emerald-600' : 'bg-red-500 hover:bg-red-600'
                }`}>
                {formSaving && <Loader2 className="w-4 h-4 animate-spin" />}
                确认{reviewKind === 'approve' ? '通过' : '驳回'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 发送邀约弹窗 */}
      {inviteTarget && (
        <div className={modalShell} onClick={(e) => e.target === e.currentTarget && setInviteTarget(null)}>
          <div className="bg-white dark:bg-slate-900 rounded-2xl w-full max-w-md p-6 space-y-4">
            <h2 className="text-lg font-bold text-slate-900 dark:text-white">发送面试邀约</h2>
            <p className="text-sm text-slate-500 dark:text-slate-400">
              将向 {inviteTarget.email} 发送邀约邮件，候选人凭链接确认参加
            </p>
            <label className="block">
              <span className="text-sm text-slate-600 dark:text-slate-300">预约面试时间 *</span>
              <input type="datetime-local" value={inviteTime}
                onChange={(e) => setInviteTime(e.target.value)}
                className={`${inputClass} mt-1`} />
            </label>
            {actionError && (
              <div className="px-3 py-2 rounded-lg bg-red-50 dark:bg-red-900/30 text-sm text-red-600 dark:text-red-300">
                {actionError}
              </div>
            )}
            <div className="flex justify-end gap-2">
              <button onClick={() => setInviteTarget(null)}
                className="px-4 py-2 rounded-xl text-sm border border-slate-200 dark:border-slate-600 text-slate-600 dark:text-slate-300">
                取消
              </button>
              <button onClick={handleInvite} disabled={formSaving || !inviteTime}
                className="flex items-center gap-1.5 px-4 py-2 rounded-xl text-sm bg-indigo-500 hover:bg-indigo-600 disabled:opacity-50 text-white font-medium">
                {formSaving ? <Loader2 className="w-4 h-4 animate-spin" /> : <SendHorizonal className="w-4 h-4" />}
                发送邀约
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 详情弹窗（含审计时间线） */}
      {detailOpen && (
        <div className={modalShell} onClick={(e) => e.target === e.currentTarget && setDetailOpen(false)}>
          <div className="bg-white dark:bg-slate-900 rounded-2xl w-full max-w-lg p-6 max-h-[85vh] overflow-y-auto space-y-4">
            {detailLoading || !detail ? (
              <div className="flex justify-center py-10">
                <Loader2 className="w-6 h-6 text-primary-500 animate-spin" />
              </div>
            ) : (
              <>
                <div className="flex items-center justify-between">
                  <h2 className="text-lg font-bold text-slate-900 dark:text-white">{detail.name}</h2>
                  <span className={`px-2.5 py-1 rounded-lg text-xs font-medium ${statusBadgeClass[detail.status] ?? ''}`}>
                    {detail.statusDisplayName}
                  </span>
                </div>
                <div className="grid grid-cols-2 gap-2 text-sm text-slate-600 dark:text-slate-300">
                  <p>岗位：{detail.position}</p>
                  <p>经验：{detail.experienceYears ?? '—'} 年</p>
                  <p>学历：{detail.education ?? '—'}</p>
                  <p>邮箱：{detail.email}</p>
                </div>
                {detail.skills && (
                  <p className="text-sm text-slate-500 dark:text-slate-400">技能：{detail.skills}</p>
                )}
                {detail.resumeFileName && (
                  <a href={detail.resumeFileUrl ?? '#'} target="_blank" rel="noreferrer"
                    className="flex items-center gap-1.5 text-sm text-primary-600 dark:text-primary-400 hover:underline">
                    <FileText className="w-4 h-4" />
                    {detail.resumeFileName}
                  </a>
                )}
                <div>
                  <p className="text-sm font-medium text-slate-700 dark:text-slate-200 mb-2 flex items-center gap-1.5">
                    <BadgeCheck className="w-4 h-4 text-primary-500" />
                    流程时间线
                  </p>
                  <div className="space-y-2 border-l-2 border-slate-100 dark:border-slate-700 pl-4">
                    {detail.auditLogs.map((log) => (
                      <div key={log.id} className="text-sm">
                        <p className="text-slate-700 dark:text-slate-300">
                          <span className="font-medium">{log.actionDisplayName}</span>
                          <span className="text-xs text-slate-400 ml-2">
                            {log.operatorName ?? log.operatorSource ?? '—'}
                          </span>
                        </p>
                        {log.comment && (
                          <p className="text-xs text-slate-500 dark:text-slate-400">{log.comment}</p>
                        )}
                        <p className="text-xs text-slate-400">{log.createdAt?.replace('T', ' ').slice(0, 19)}</p>
                      </div>
                    ))}
                  </div>
                </div>
                <div className="flex justify-end">
                  <button onClick={() => setDetailOpen(false)}
                    className="px-4 py-2 rounded-xl text-sm border border-slate-200 dark:border-slate-600 text-slate-600 dark:text-slate-300">
                    关闭
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

const inputClass =
  'w-full px-3.5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-800 text-slate-900 dark:text-white placeholder-slate-400 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent transition';

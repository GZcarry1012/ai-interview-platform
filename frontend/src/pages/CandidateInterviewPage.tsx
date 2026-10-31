import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  Camera,
  CheckCircle2,
  Loader2,
  Mic,
  Square,
  Upload,
  Video,
  XCircle,
} from 'lucide-react';
import invitationApi from '../api/invitation';
import request, { getErrorMessage } from '../api/request';
import type { InvitationTokenInfo } from '../types/engineer';

interface SessionInfo {
  status: string;
  statusDisplayName: string;
  questions: string[];
  videoUploaded: boolean;
  answersSubmitted: boolean;
}

type Step = 'check' | 'interview' | 'uploading' | 'done';

const CHUNK_SIZE = 5 * 1024 * 1024; // 5MB，与后端协议一致

/**
 * 候选人视频面试页（公开，凭邀约 token）
 * 流程：设备预检 → 录制作答（MediaRecorder）→ 切片上传（秒传/断点续传）→ 提交答卷
 */
export default function CandidateInterviewPage() {
  const { token } = useParams<{ token: string }>();

  const [invitation, setInvitation] = useState<InvitationTokenInfo | null>(null);
  const [session, setSession] = useState<SessionInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [step, setStep] = useState<Step>('check');

  // 设备预检
  const videoRef = useRef<HTMLVideoElement>(null);
  const [stream, setStream] = useState<MediaStream | null>(null);
  const [micLevel, setMicLevel] = useState(0);
  const [deviceError, setDeviceError] = useState('');
  const [devicesReady, setDevicesReady] = useState(false);

  // 录制与作答
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const [recording, setRecording] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [answers, setAnswers] = useState<Record<number, string>>({});

  // 上传状态
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploadMessage, setUploadMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!token) {
      return;
    }
    Promise.all([
      invitationApi.getByToken(token),
      request.getInstance().get(`/api/candidate/${token}/session`).then((r) => r.data as SessionInfo),
    ])
      .then(([inv, sess]) => {
        setInvitation(inv);
        setSession(sess);
      })
      .catch((err) => setError(getErrorMessage(err)))
      .finally(() => setLoading(false));
  }, [token]);

  // —— 设备预检 ——
  const startDeviceCheck = async () => {
    setDeviceError('');
    try {
      const mediaStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
      setStream(mediaStream);
      if (videoRef.current) {
        videoRef.current.srcObject = mediaStream;
      }
      // 麦克风电平表
      const audioCtx = new AudioContext();
      const analyser = audioCtx.createAnalyser();
      audioCtx.createMediaStreamSource(mediaStream).connect(analyser);
      const data = new Uint8Array(analyser.frequencyBinCount);
      setInterval(() => {
        analyser.getByteFrequencyData(data);
        const avg = data.reduce((a, b) => a + b, 0) / data.length;
        setMicLevel(Math.min(100, Math.round((avg / 128) * 100)));
      }, 200);
      setDevicesReady(true);
    } catch (err) {
      setDeviceError(
        '无法访问摄像头/麦克风：请在浏览器地址栏允许设备权限，或检查设备是否被占用。'
      );
    }
  };

  useEffect(() => {
    return () => {
      stream?.getTracks().forEach((t) => t.stop());
    };
  }, [stream]);

  // 步骤切换后 video 元素会重建，需要重新挂载媒体流
  useEffect(() => {
    if (videoRef.current && stream) {
      videoRef.current.srcObject = stream;
    }
  }, [step, stream]);

  // —— 录制 ——
  const startRecording = () => {
    if (!stream) {
      return;
    }
    chunksRef.current = [];
    const mimeType = MediaRecorder.isTypeSupported('video/webm;codecs=vp8,opus')
      ? 'video/webm;codecs=vp8,opus'
      : 'video/webm';
    const recorder = new MediaRecorder(stream, { mimeType });
    recorder.ondataavailable = (e) => {
      if (e.data.size > 0) {
        chunksRef.current.push(e.data);
      }
    };
    recorder.start(1000);
    recorderRef.current = recorder;
    setRecording(true);
    setElapsed(0);
  };

  const stopRecording = (): Promise<Blob> =>
    new Promise((resolve) => {
      const recorder = recorderRef.current;
      if (!recorder) {
        resolve(new Blob());
        return;
      }
      recorder.onstop = () => {
        resolve(new Blob(chunksRef.current, { type: 'video/webm' }));
      };
      recorder.stop();
      setRecording(false);
    });

  useEffect(() => {
    if (!recording) {
      return;
    }
    const timer = setInterval(() => setElapsed((s) => s + 1), 1000);
    return () => clearInterval(timer);
  }, [recording]);

  const formatElapsed = (s: number) =>
    `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;

  // —— 提交：上传视频 + 答卷 ——
  const sha256Hex = async (blob: Blob): Promise<string> => {
    const buffer = await blob.arrayBuffer();
    const digest = await crypto.subtle.digest('SHA-256', buffer);
    return Array.from(new Uint8Array(digest))
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('');
  };

  const handleSubmit = async () => {
    if (!token || submitting) {
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      setUploadMessage('结束录制...');
      const blob = await stopRecording();

      setUploadMessage('计算文件指纹（SHA-256）...');
      const fileHash = await sha256Hex(blob);
      const totalChunks = Math.max(1, Math.ceil(blob.size / CHUNK_SIZE));

      setUploadMessage('初始化上传（秒传/断点续传校验）...');
      const init = await request.getInstance().post(`/api/candidate/${token}/upload/init`, {
        fileName: `interview-${Date.now()}.webm`,
        fileSize: blob.size,
        totalChunks,
        chunkSize: CHUNK_SIZE,
        fileHash,
      });
      const { uploadId, instantUpload, uploadedChunks } = init.data;

      if (!instantUpload) {
        const done = new Set(uploadedChunks);
        for (let i = 0; i < totalChunks; i++) {
          if (done.has(i)) {
            setUploadProgress(Math.round(((i + 1) / totalChunks) * 100));
            continue;
          }
          const chunk = blob.slice(i * CHUNK_SIZE, (i + 1) * CHUNK_SIZE);
          // 单分片最多重试 3 次（失败重试，成功的分片幂等跳过）
          let attempt = 0;
          for (;;) {
            try {
              await request.getInstance().post(
                `/api/candidate/${token}/upload/${uploadId}/${i}`,
                chunk,
                { headers: { 'Content-Type': 'application/octet-stream' }, timeout: 120000 },
              );
              break;
            } catch (chunkErr) {
              attempt++;
              if (attempt >= 3) {
                throw chunkErr;
              }
              await new Promise((r) => setTimeout(r, 1000 * attempt));
            }
          }
          setUploadProgress(Math.round(((i + 1) / totalChunks) * 100));
        }
        setUploadMessage('合并分片并归档...');
        await request.getInstance().post(`/api/candidate/${token}/upload/${uploadId}/complete`, {
          durationSeconds: elapsed,
        });
      } else {
        setUploadProgress(100);
      }

      setUploadMessage('提交答卷...');
      const answersPayload = (session?.questions ?? []).map((question, index) => ({
        index,
        question,
        answer: answers[index] ?? '',
      }));
      const finalSession = await request.getInstance()
        .post(`/api/candidate/${token}/answers`, { answers: answersPayload })
        .then((r) => r.data);
      setSession(finalSession);
      setUploadMessage('');
      setStep('done');
    } catch (err) {
      setError(getErrorMessage(err));
      setStep('interview');
    } finally {
      setSubmitting(false);
    }
  };

  const allAnswered = (session?.questions ?? []).every((_, i) => (answers[i] ?? '').trim() !== '');

  // —— 渲染 ——
  if (loading) {
    return <Centered><Loader2 className="w-8 h-8 text-primary-500 animate-spin" /></Centered>;
  }
  if (error && !session) {
    return (
      <Centered>
        <XCircle className="w-12 h-12 mx-auto text-red-400 mb-3" />
        <p className="text-slate-600 dark:text-slate-300">{error}</p>
        <Link to="/" className="text-sm text-primary-600 hover:underline mt-3 inline-block">返回首页</Link>
      </Centered>
    );
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 to-indigo-50 dark:from-slate-900 dark:to-slate-800 py-8 px-4">
      <div className="max-w-3xl mx-auto space-y-5">
        <div className="text-center">
          <h1 className="text-xl font-bold text-slate-900 dark:text-white">线上视频面试</h1>
          {invitation && (
            <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
              {invitation.engineerName} · {invitation.position} ·{' '}
              {new Date(invitation.scheduledTime).toLocaleString('zh-CN', { hour12: false })}
            </p>
          )}
        </div>

        {error && (
          <div className="px-4 py-3 rounded-xl bg-red-50 dark:bg-red-900/30 text-sm text-red-600 dark:text-red-300">
            {error}
          </div>
        )}

        {step === 'check' && (
          <div className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-700 p-6 space-y-4">
            <h2 className="font-semibold text-slate-800 dark:text-white flex items-center gap-2">
              <Camera className="w-5 h-5 text-primary-500" /> 设备预检
            </h2>
            <div className="relative rounded-xl overflow-hidden bg-slate-900 aspect-video">
              <video ref={videoRef} autoPlay muted playsInline className="w-full h-full object-cover" />
              {!devicesReady && (
                <div className="absolute inset-0 flex items-center justify-center text-slate-400 text-sm">
                  点击下方按钮开启摄像头与麦克风
                </div>
              )}
            </div>
            <div className="flex items-center gap-3">
              <Mic className="w-4 h-4 text-slate-400" />
              <div className="flex-1 h-2.5 rounded-full bg-slate-100 dark:bg-slate-700 overflow-hidden">
                <div
                  className="h-full bg-emerald-500 transition-all duration-150"
                  style={{ width: `${micLevel}%` }}
                />
              </div>
              <span className="text-xs text-slate-400 w-8 text-right">{micLevel}%</span>
            </div>
            {deviceError && (
              <div className="px-4 py-3 rounded-xl bg-red-50 dark:bg-red-900/30 text-sm text-red-600 dark:text-red-300">
                {deviceError}
              </div>
            )}
            {!devicesReady ? (
              <button
                onClick={startDeviceCheck}
                className="w-full flex items-center justify-center gap-2 py-2.5 rounded-xl bg-primary-500 hover:bg-primary-600 text-white font-medium"
              >
                <Video className="w-4 h-4" /> 开启设备检测
              </button>
            ) : (
              <button
                onClick={() => setStep('interview')}
                className="w-full flex items-center justify-center gap-2 py-2.5 rounded-xl bg-emerald-500 hover:bg-emerald-600 text-white font-medium"
              >
                <CheckCircle2 className="w-4 h-4" /> 设备正常，进入面试
              </button>
            )}
          </div>
        )}

        {step === 'interview' && (
          <div className="space-y-4">
            <div className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-700 p-5 space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="font-semibold text-slate-800 dark:text-white flex items-center gap-2">
                  <Video className="w-5 h-5 text-primary-500" /> 面试进行中
                </h2>
                <span className={`text-sm font-mono px-3 py-1 rounded-lg ${recording ? 'bg-red-50 dark:bg-red-900/30 text-red-500' : 'bg-slate-100 dark:bg-slate-800 text-slate-400'}`}>
                  {recording ? `● REC ${formatElapsed(elapsed)}` : formatElapsed(elapsed)}
                </span>
              </div>
              <div className="relative rounded-xl overflow-hidden bg-slate-900 aspect-video">
                <video ref={videoRef} autoPlay muted playsInline className="w-full h-full object-cover" />
              </div>
              {!recording ? (
                <button
                  onClick={startRecording}
                  className="w-full flex items-center justify-center gap-2 py-2.5 rounded-xl bg-primary-500 hover:bg-primary-600 text-white font-medium"
                >
                  <Video className="w-4 h-4" /> 开始录制并作答
                </button>
              ) : (
                <p className="text-xs text-slate-400 text-center">
                  录制中——请逐题作答，完成后点击下方提交
                </p>
              )}
            </div>

            {recording && (
              <div className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-700 p-5 space-y-4">
                <h3 className="font-semibold text-slate-800 dark:text-white text-sm">答卷</h3>
                {(session?.questions ?? []).map((question, i) => (
                  <div key={i}>
                    <p className="text-sm text-slate-700 dark:text-slate-200 mb-1.5">
                      {i + 1}. {question}
                    </p>
                    <textarea
                      rows={2}
                      value={answers[i] ?? ''}
                      onChange={(e) => setAnswers({ ...answers, [i]: e.target.value })}
                      placeholder="在此作答..."
                      className={inputClass}
                    />
                  </div>
                ))}
                <button
                  onClick={handleSubmit}
                  disabled={submitting || !allAnswered}
                  className="w-full flex items-center justify-center gap-2 py-2.5 rounded-xl bg-red-500 hover:bg-red-600 disabled:opacity-50 text-white font-medium"
                  title={allAnswered ? '' : '请完成所有题目后提交'}
                >
                  {submitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Square className="w-4 h-4" />}
                  结束面试并提交
                </button>
                {!allAnswered && (
                  <p className="text-xs text-slate-400 text-center">请完成所有题目后提交</p>
                )}
              </div>
            )}

            {submitting && (
              <div className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-700 p-5 space-y-3">
                <p className="text-sm text-slate-600 dark:text-slate-300 flex items-center gap-2">
                  <Upload className="w-4 h-4 text-primary-500" /> {uploadMessage}
                </p>
                <div className="h-2.5 rounded-full bg-slate-100 dark:bg-slate-700 overflow-hidden">
                  <div className="h-full bg-primary-500 transition-all" style={{ width: `${uploadProgress}%` }} />
                </div>
                <p className="text-xs text-slate-400 text-right">{uploadProgress}%</p>
              </div>
            )}
          </div>
        )}

        {step === 'done' && (
          <div className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-700 p-8 text-center space-y-3">
            <CheckCircle2 className="w-14 h-14 mx-auto text-emerald-500" />
            <h2 className="text-lg font-bold text-slate-900 dark:text-white">面试已完成</h2>
            <p className="text-sm text-slate-500 dark:text-slate-400">
              视频与答卷已归档，业务审核人将结合 AI 评分进行复核，请留意后续通知。
            </p>
            <Link to="/" className="text-sm text-primary-600 hover:underline inline-block">
              返回首页
            </Link>
          </div>
        )}
      </div>
    </div>
  );
}

function Centered({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-50 to-indigo-50 dark:from-slate-900 dark:to-slate-800 px-4">
      <div className="text-center">{children}</div>
    </div>
  );
}

const inputClass =
  'w-full px-3.5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-800 text-slate-900 dark:text-white placeholder-slate-400 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent transition';

import { App as AntdApp, Modal, Progress } from 'antd';
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type DragEvent as ReactDragEvent,
  type ReactNode,
} from 'react';
import { Client } from '@stomp/stompjs';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import {
  analyzeVideo,
  AUTH_REQUIRED_EVENT,
  clearAuth,
  deleteVideo,
  getCurrentUserApi,
  getStoredToken,
  getStoredUser,
  getVideo,
  importVideoByUrl,
  listVideos,
  retryImport,
  uploadVideoChunked,
  type UserProfile,
  type VideoInfo,
  type VideoStatus,
} from './api';
import Auth from './Auth';

const PAGE_SIZE = 10;

type Page = 'home' | 'workbench';
type DetailTab = 'transcript' | 'summary' | 'video';
type Lang = 'zh' | 'en';
type Source = 'file' | 'url';
type StatusTone = 'ok' | 'warn' | 'err' | 'neutral' | 'accent';
type ActionTone = 'neutral' | 'accent' | 'err';

const TRANSLATIONS = {
  zh: {
    nav_workbench: '工作台',
    nav_home: '返回首页',
    lang_toggle: 'EN · 中',
    hero_rotate_decode: '解码',
    hero_rotate_transcribe: '转写',
    hero_rotate_analyze: '分析',
    hero_title_prefix: '解码你的',
    hero_title_word: '视频',
    hero_sub: '上传本地文件或粘贴链接 — 剩下的交给我们。',
    col_local_title: '本地文件',
    col_local_sub: '拖拽 · 点击 · 选择',
    col_link_title: '在线链接',
    col_link_sub: '粘贴 · 抓取 · 解析',
    drop_prompt_prefix: '拖入视频,或',
    drop_browse: '选择文件',
    url_placeholder: '粘贴视频链接…',
    panel_source_label: '来源',
    panel_source_file_empty: '本地文件 (未选择)',
    panel_source_url_empty: '在线链接 (空)',
    home_submit: '开始分析',
    home_submitting: '分析中…',
    msg_submitted_file: '视频已提交分析',
    msg_submitted_url: '链接已提交分析',
    msg_need_input: '请上传视频文件或粘贴链接',
    msg_load_failed: '加载视频列表失败',
    msg_submit_failed: '提交失败',
    msg_analyze_failed: '启动分析失败',
    wb_title: '工作台',
    wb_tasks_suffix: '项任务',
    wb_refresh: '刷新',
    wb_new_task: '新任务',
    wb_empty_title_prefix: '还没有视频',
    wb_empty_title_word: '任务',
    wb_empty_sub: '在首页拖入文件或粘贴链接,所有分析任务都会出现在这里。',
    wb_start_first: '开始第一个任务',
    source_local: '本地文件',
    source_web: '在线链接',
    status_uploading: '上传中',
    status_importing: '导入中',
    status_import_failed: '导入失败',
    status_pending: '待处理',
    status_processing: '分析中',
    status_completed: '已就绪',
    status_failed: '失败',
    action_audio: '音频',
    action_transcript: '字幕',
    action_summary: '总结',
    action_reanalyze: '重新分析',
    action_retry: '重试',
    action_delete: '删除',
    action_cancel: '取消',
    stage_queue: '已入队',
    stage_probe: '探测媒体',
    stage_decode: '解码音频',
    stage_diarize: '分离说话人',
    stage_transcribe: '转写文字',
    stage_compose: '生成总结',
    msg_delete_confirm_title: '确认删除?',
    msg_delete_confirm_content: '此操作不可撤销,视频文件和分析结果都将被删除。',
    msg_cancel_confirm_title: '确认取消分析?',
    msg_cancel_confirm_content: '当前分析任务及其视频记录将被移除。',
    msg_logout_confirm_title: '退出登录?',
    msg_logout_confirm_content: '退出后需要重新登录才能回到工作台。',
    msg_logout_ok: '退出',
    msg_delete_ok: '删除',
    msg_delete_cancel: '取消',
    msg_delete_success: '已删除',
    msg_delete_failed: '删除失败',
    msg_retry_submitted: '已重新提交导入',
    msg_retry_failed: '重新导入失败',
    account_label: '账户',
    modal_tab_video: '视频',
    modal_video_unavailable: '视频暂不可用',
    modal_tab_transcript: '字幕',
    modal_tab_summary: 'AI 总结',
    modal_transcript_processing: '正在提取文字,请稍候…',
    modal_transcript_empty: '分析完成后文字内容将显示在这里',
    modal_summary_processing: 'AI 正在生成总结…',
    modal_summary_empty: '分析完成后 AI 总结将显示在这里',
    pagination_prev: '上一页',
    pagination_next: '下一页',
    footer: '© 2026 VidInsight',
  },
  en: {
    nav_workbench: 'Workbench',
    nav_home: 'Home',
    lang_toggle: 'EN · 中',
    hero_rotate_decode: 'decode',
    hero_rotate_transcribe: 'transcribe',
    hero_rotate_analyze: 'analyze',
    hero_title_prefix: 'Decode your',
    hero_title_word: 'video',
    hero_sub: "Drop a local file or paste a URL — we'll handle the rest.",
    col_local_title: 'Local file',
    col_local_sub: 'drop · click · paste',
    col_link_title: 'Web link',
    col_link_sub: 'paste · fetch · stream',
    drop_prompt_prefix: 'Drop a video, or',
    drop_browse: 'browse',
    url_placeholder: 'Paste video URL…',
    panel_source_label: 'Source',
    panel_source_file_empty: 'local file (none selected)',
    panel_source_url_empty: 'web link (empty)',
    home_submit: 'Start analysis',
    home_submitting: 'Analyzing…',
    msg_submitted_file: 'Video submitted for analysis',
    msg_submitted_url: 'Link submitted for analysis',
    msg_need_input: 'Please upload a video file or paste a link',
    msg_load_failed: 'Failed to load videos',
    msg_submit_failed: 'Submit failed',
    msg_analyze_failed: 'Failed to start analysis',
    wb_title: 'Workbench',
    wb_tasks_suffix: 'tasks',
    wb_refresh: 'Refresh',
    wb_new_task: 'New task',
    wb_empty_title_prefix: 'No video',
    wb_empty_title_word: 'tasks',
    wb_empty_sub:
      'Drop a file or paste a URL on the home page — every analysis run will show up here.',
    wb_start_first: 'Start your first task',
    source_local: 'local file',
    source_web: 'web link',
    status_uploading: 'uploading',
    status_importing: 'importing',
    status_import_failed: 'import failed',
    status_pending: 'pending',
    status_processing: 'analyzing',
    status_completed: 'ready',
    status_failed: 'failed',
    action_audio: 'Audio',
    action_transcript: 'Transcript',
    action_summary: 'Summary',
    action_reanalyze: 'Re-analyze',
    action_retry: 'Retry',
    action_delete: 'Delete',
    action_cancel: 'Cancel',
    stage_queue: 'queued',
    stage_probe: 'probing media',
    stage_decode: 'decoding audio',
    stage_diarize: 'diarizing',
    stage_transcribe: 'transcribing',
    stage_compose: 'composing',
    msg_delete_confirm_title: 'Delete this video?',
    msg_delete_confirm_content:
      'This cannot be undone. The video file and analysis results will be removed.',
    msg_cancel_confirm_title: 'Cancel this analysis?',
    msg_cancel_confirm_content:
      'The running analysis task and its video record will be removed.',
    msg_logout_confirm_title: 'Log out?',
    msg_logout_confirm_content:
      "You'll need to sign in again to return to your workbench.",
    msg_logout_ok: 'Log out',
    msg_delete_ok: 'Delete',
    msg_delete_cancel: 'Cancel',
    msg_delete_success: 'Deleted',
    msg_delete_failed: 'Delete failed',
    msg_retry_submitted: 'Retry submitted',
    msg_retry_failed: 'Retry failed',
    account_label: 'Account',
    modal_tab_video: 'Video',
    modal_video_unavailable: 'Video not available',
    modal_tab_transcript: 'Transcript',
    modal_tab_summary: 'AI Summary',
    modal_transcript_processing: 'Extracting transcript, please wait…',
    modal_transcript_empty: 'Transcript will appear here after analysis',
    modal_summary_processing: 'AI is generating the summary…',
    modal_summary_empty: 'AI summary will appear here after analysis',
    pagination_prev: 'Prev',
    pagination_next: 'Next',
    footer: '© 2026 VidInsight',
  },
} as const;

type I18nKey = keyof typeof TRANSLATIONS['zh'];

function getInitialLang(): Lang {
  try {
    const stored = window.localStorage.getItem('vi-lang');
    if (stored === 'zh' || stored === 'en') return stored;
  } catch {
    /* ignore */
  }
  return 'zh';
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return '';
  try {
    const d = new Date(dateStr);
    return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(
      d.getMinutes(),
    ).padStart(2, '0')}`;
  } catch {
    return '';
  }
}

function getPageFromPath(): Page {
  return window.location.pathname === '/workbench' ? 'workbench' : 'home';
}

function isLocalSource(sourceUrl: string | undefined): boolean {
  if (!sourceUrl) return true;
  return sourceUrl.startsWith('/uploads/');
}

/* ── Brand logo mark (two-triangle V) ───────────────────────── */
function LogoMark({ size = 24 }: { size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
      <defs>
        <linearGradient id="vi-logo-grad" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="oklch(0.82 0.13 70)" />
          <stop offset="100%" stopColor="oklch(0.60 0.13 50)" />
        </linearGradient>
      </defs>
      <path d="M3 4 L12 20 L12 12 Z" fill="url(#vi-logo-grad)" />
      <path
        d="M21 4 L12 20 L12 12 Z"
        fill="none"
        stroke="oklch(0.78 0.13 70)"
        strokeWidth="1.5"
        strokeLinejoin="round"
      />
    </svg>
  );
}

/* ── Inline icons (16px viewBox, currentColor stroke) ──────── */
type IconProps = { size?: number; stroke?: number };

const baseIconProps = (size: number, stroke: number) => ({
  width: size,
  height: size,
  viewBox: '0 0 16 16',
  fill: 'none',
  stroke: 'currentColor' as const,
  strokeWidth: stroke,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  'aria-hidden': true,
});

function IconUpload({ size = 16, stroke = 1.25 }: IconProps) {
  return (
    <svg {...baseIconProps(size, stroke)}>
      <path d="M8 10.5V2.5M8 2.5L5 5.5M8 2.5L11 5.5M2.5 10.5V12.5A1 1 0 0 0 3.5 13.5H12.5A1 1 0 0 0 13.5 12.5V10.5" />
    </svg>
  );
}

function IconLink({ size = 16, stroke = 1.25 }: IconProps) {
  return (
    <svg {...baseIconProps(size, stroke)}>
      <path d="M9.5 6.5L13 3M13 3L10.5 3M13 3V5.5M6.5 9.5L3 13M3 13H5.5M3 13V10.5" />
    </svg>
  );
}

function IconArrow({ size = 16, stroke = 1.25 }: IconProps) {
  return (
    <svg {...baseIconProps(size, stroke)}>
      <path d="M3.5 8H12.5M12.5 8L9 4.5M12.5 8L9 11.5" />
    </svg>
  );
}

function IconArrowLeft({ size = 16, stroke = 1.25 }: IconProps) {
  return (
    <svg {...baseIconProps(size, stroke)}>
      <path d="M12.5 8H3.5M3.5 8L7 4.5M3.5 8L7 11.5" />
    </svg>
  );
}

function IconPlay({ size = 16, stroke = 1.25 }: IconProps) {
  return (
    <svg {...baseIconProps(size, stroke)}>
      <path d="M5 3.5L12 8L5 12.5V3.5Z" />
    </svg>
  );
}

function IconRefresh({ size = 16, stroke = 1.25 }: IconProps) {
  return (
    <svg {...baseIconProps(size, stroke)}>
      <path d="M2.5 8A5.5 5.5 0 0 1 12.5 5M13.5 8A5.5 5.5 0 0 1 3.5 11M11 3V5.5H8.5M5 13V10.5H7.5" />
    </svg>
  );
}

function IconPlus({ size = 16, stroke = 1.25 }: IconProps) {
  return (
    <svg {...baseIconProps(size, stroke)}>
      <path d="M8 3V13M3 8H13" />
    </svg>
  );
}

function IconAudio({ size = 16, stroke = 1.25 }: IconProps) {
  return (
    <svg {...baseIconProps(size, stroke)}>
      <path d="M9.5 3V11.5A2 2 0 1 1 7.5 9.5A2 2 0 0 1 9.5 11.5" />
    </svg>
  );
}

function IconDoc({ size = 16, stroke = 1.25 }: IconProps) {
  return (
    <svg {...baseIconProps(size, stroke)}>
      <path d="M4 2.5H9L12 5.5V13.5A1 1 0 0 1 11 14.5H4A1 1 0 0 1 3 13.5V3.5A1 1 0 0 1 4 2.5ZM9 2.5V5.5H12M5 8H10M5 10.5H10M5 6H7" />
    </svg>
  );
}

function IconSpark({ size = 14, stroke = 1.25 }: IconProps) {
  return (
    <svg {...baseIconProps(size, stroke)}>
      <path d="M7 1.5L8.3 5.7L12.5 7L8.3 8.3L7 12.5L5.7 8.3L1.5 7L5.7 5.7L7 1.5Z" />
    </svg>
  );
}

function IconTrash({ size = 16, stroke = 1.25 }: IconProps) {
  return (
    <svg {...baseIconProps(size, stroke)}>
      <path d="M3 4.5H13M5.5 4.5V3.5A1 1 0 0 1 6.5 2.5H9.5A1 1 0 0 1 10.5 3.5V4.5M4 4.5L4.5 13A1 1 0 0 0 5.5 14H10.5A1 1 0 0 0 11.5 13L12 4.5M6.5 7V11.5M9.5 7V11.5" />
    </svg>
  );
}

function IconEmpty({ size = 48, stroke = 1 }: IconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 48 48"
      fill="none"
      stroke="currentColor"
      strokeWidth={stroke}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
    >
      <path d="M12 18L36 18A2 2 0 0 1 38 20L38 36A2 2 0 0 1 36 38L12 38A2 2 0 0 1 10 36L10 20A2 2 0 0 1 12 18ZM21 25L27 28.5L21 32L21 25Z" />
    </svg>
  );
}

const FILE_FORMATS = ['mp4', 'mov', 'avi', 'mkv', 'webm'];
const LINK_SOURCES = ['bilibili', 'youtube', 'douyin', 'vimeo', 'm3u8'];

const STAGES: { key: I18nKey; pct: number }[] = [
  { key: 'stage_queue', pct: 4 },
  { key: 'stage_probe', pct: 14 },
  { key: 'stage_decode', pct: 32 },
  { key: 'stage_diarize', pct: 52 },
  { key: 'stage_transcribe', pct: 78 },
  { key: 'stage_compose', pct: 94 },
];

const STEP_STAGE: Record<string, { key: I18nKey; pct: number }> = {
  EXTRACTING:  { key: 'stage_decode',     pct: 20 },
  TRANSCRIBING: { key: 'stage_transcribe', pct: 55 },
  SUMMARIZING:  { key: 'stage_compose',    pct: 85 },
};

/* ── Status → pill meta ─────────────────────────────────── */
function getStatusMeta(
  status: VideoStatus,
  t: (k: I18nKey) => string,
): { tone: StatusTone; label: string } {
  switch (status) {
    case 'COMPLETED':
      return { tone: 'ok', label: t('status_completed') };
    case 'PROCESSING':
      return { tone: 'warn', label: t('status_processing') };
    case 'UPLOADING':
      return { tone: 'warn', label: t('status_uploading') };
    case 'IMPORTING':
      return { tone: 'warn', label: t('status_importing') };
    case 'PENDING':
      return { tone: 'neutral', label: t('status_pending') };
    case 'FAILED':
      return { tone: 'err', label: t('status_failed') };
    case 'IMPORT_FAILED':
      return { tone: 'err', label: t('status_import_failed') };
    default:
      return { tone: 'neutral', label: status };
  }
}

function StatusPill({
  status,
  t,
}: {
  status: VideoStatus;
  t: (k: I18nKey) => string;
}) {
  const meta = getStatusMeta(status, t);
  const isProcessing = status === 'PROCESSING';
  return (
    <span className={`vi-status-pill tone-${meta.tone}`}>
      {isProcessing ? <span className="vi-dot-pulse" /> : <span className="dot" />}
      {meta.label}
    </span>
  );
}

/* ── Action button (icon + label) ────────────────────────── */
type ActionBtnProps = {
  icon: ReactNode;
  label: string;
  enabled: boolean;
  tone?: ActionTone;
  href?: string;
  onClick?: (event: React.MouseEvent) => void;
};

function ActionBtn({
  icon,
  label,
  enabled,
  tone = 'neutral',
  href,
  onClick,
}: ActionBtnProps) {
  const className = `vi-action tone-${tone}`;
  if (href && enabled) {
    return (
      <a
        className={className}
        href={href}
        target="_blank"
        rel="noopener noreferrer"
        onClick={(e) => {
          e.stopPropagation();
          onClick?.(e);
        }}
      >
        <span style={{ display: 'inline-flex' }}>{icon}</span>
        <span className="vi-action-label">{label}</span>
      </a>
    );
  }
  return (
    <button
      type="button"
      className={className}
      disabled={!enabled}
      onClick={(e) => {
        e.stopPropagation();
        if (enabled) onClick?.(e);
      }}
    >
      <span style={{ display: 'inline-flex' }}>{icon}</span>
      <span className="vi-action-label">{label}</span>
    </button>
  );
}

interface AppInnerProps {
  user: UserProfile;
  onLogout: () => void;
}

function AppInner({ user, onLogout }: AppInnerProps) {
  const { message } = AntdApp.useApp();
  const [page, setPage] = useState<Page>(() => getPageFromPath());
  const [file, setFile] = useState<File | null>(null);
  const [sourceUrl, setSourceUrl] = useState('');
  const [source, setSource] = useState<Source>('file');
  const [drag, setDrag] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [uploadProgress, setUploadProgress] = useState<number | null>(null);
  const [videos, setVideos] = useState<VideoInfo[]>([]);
  const [activeVideo, setActiveVideo] = useState<VideoInfo | null>(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [totalVideos, setTotalVideos] = useState(0);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailTab, setDetailTab] = useState<DetailTab>('transcript');
  const [refreshing, setRefreshing] = useState(false);
  const [refreshSpin, setRefreshSpin] = useState(false);
  const [lang, setLang] = useState<Lang>(getInitialLang);
  const [processingSteps, setProcessingSteps] = useState<Record<number, string>>({});

  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const cursorGlowRef = useRef<HTMLDivElement | null>(null);
  const panelRef = useRef<HTMLDivElement | null>(null);

  const t = useCallback((key: I18nKey) => TRANSLATIONS[lang][key], [lang]);

  /* Rotating hero word */
  const rotateWords = useMemo(
    () => [
      t('hero_rotate_decode'),
      t('hero_rotate_transcribe'),
      t('hero_rotate_analyze'),
    ],
    [t],
  );
  const [rotateIdx, setRotateIdx] = useState(0);
  useEffect(() => {
    const id = window.setInterval(() => {
      setRotateIdx((i) => (i + 1) % rotateWords.length);
    }, 2200);
    return () => window.clearInterval(id);
  }, [rotateWords.length]);

  useEffect(() => {
    try {
      window.localStorage.setItem('vi-lang', lang);
    } catch {
      /* ignore */
    }
    document.documentElement.lang = lang === 'zh' ? 'zh-CN' : 'en';
  }, [lang]);

  const toggleLang = useCallback(() => {
    setLang((curr) => (curr === 'zh' ? 'en' : 'zh'));
  }, []);

  const navigateTo = useCallback((nextPage: Page) => {
    const nextPath = nextPage === 'workbench' ? '/workbench' : '/';
    if (window.location.pathname !== nextPath) {
      window.history.pushState(null, '', nextPath);
    }
    setPage(nextPage);
  }, []);

  const loadVideos = useCallback(
    async (targetPage: number, silent = false) => {
      if (!silent) setRefreshing(true);
      try {
        const data = await listVideos(targetPage, PAGE_SIZE);
        setVideos(data.records);
        setCurrentPage(data.page);
        setTotalPages(Math.ceil(data.total / PAGE_SIZE) || 1);
        setTotalVideos(data.total);
        setActiveVideo((current) => {
          if (!current) return data.records[0] ?? null;
          return data.records.find((v) => v.id === current.id) ?? data.records[0] ?? null;
        });
      } catch (error) {
        message.error(error instanceof Error ? error.message : t('msg_load_failed'));
      } finally {
        if (!silent) setRefreshing(false);
      }
    },
    [message, t],
  );

  useEffect(() => {
    const handlePopState = () => setPage(getPageFromPath());
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  useEffect(() => {
    if (page === 'workbench') void loadVideos(1);
  }, [loadVideos, page]);

  /* Global pointer tracker — drives the cursor spotlight and hero
     parallax via CSS custom properties on :root. rAF-throttled. */
  useEffect(() => {
    let frame = 0;
    let mx = 0;
    let my = 0;
    let px = 0;
    let py = 0;
    const onMove = (e: PointerEvent) => {
      mx = e.clientX;
      my = e.clientY;
      px = (e.clientX / window.innerWidth - 0.5) * 2;
      py = (e.clientY / window.innerHeight - 0.5) * 2;
      if (frame) return;
      frame = requestAnimationFrame(() => {
        const root = document.documentElement;
        root.style.setProperty('--mx', `${mx}px`);
        root.style.setProperty('--my', `${my}px`);
        root.style.setProperty('--px', px.toFixed(3));
        root.style.setProperty('--py', py.toFixed(3));
        cursorGlowRef.current?.classList.add('is-armed');
        frame = 0;
      });
    };
    window.addEventListener('pointermove', onMove, { passive: true });
    return () => {
      window.removeEventListener('pointermove', onMove);
      if (frame) cancelAnimationFrame(frame);
    };
  }, []);

  /* Per-panel pointer tracker — drives the upload card's --cmx / --cmy. */
  useEffect(() => {
    const el = panelRef.current;
    if (!el) return;
    let frame = 0;
    let nx = 0;
    let ny = 0;
    const onMove = (e: PointerEvent) => {
      const rect = el.getBoundingClientRect();
      nx = e.clientX - rect.left;
      ny = e.clientY - rect.top;
      if (frame) return;
      frame = requestAnimationFrame(() => {
        el.style.setProperty('--cmx', `${nx}px`);
        el.style.setProperty('--cmy', `${ny}px`);
        frame = 0;
      });
    };
    el.addEventListener('pointermove', onMove);
    return () => {
      el.removeEventListener('pointermove', onMove);
      if (frame) cancelAnimationFrame(frame);
    };
  }, [page]);


  useEffect(() => {
    const token = getStoredToken();
    if (!token) return;

    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const client = new Client({
      brokerURL: `${protocol}://${window.location.host}/ws/websocket`,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
    });

    client.onConnect = () => {
      client.subscribe('/user/queue/video-status', (frame) => {
        const push: { videoId: number; videoStatus: VideoStatus; audioUrl: string | null; step?: string } =
          JSON.parse(frame.body);

        if (push.videoStatus === 'PENDING') {
          setTimeout(() => void handleStartAnalysis({ id: push.videoId } as VideoInfo), 0);
          return;
        }

        if (push.videoStatus === 'PROCESSING' && push.step) {
          setProcessingSteps((prev) => ({ ...prev, [push.videoId]: push.step! }));
          return;
        }

        const TERMINAL = new Set(['COMPLETED', 'FAILED', 'IMPORT_FAILED']);
        if (TERMINAL.has(push.videoStatus)) {
          setProcessingSteps((prev) => { const n = { ...prev }; delete n[push.videoId]; return n; });
          void getVideo(push.videoId).then((full) => {
            setVideos((curr) => curr.map((v) => (v.id === full.id ? full : v)));
            setActiveVideo((curr) => (curr?.id === full.id ? full : curr));
          });
          return;
        }

        setVideos((curr) =>
          curr.map((v) =>
            v.id === push.videoId
              ? { ...v, videoStatus: push.videoStatus, audioUrl: push.audioUrl ?? v.audioUrl }
              : v,
          ),
        );
        setActiveVideo((curr) =>
          curr?.id === push.videoId
            ? { ...curr, videoStatus: push.videoStatus, audioUrl: push.audioUrl ?? curr.audioUrl }
            : curr,
        );
      });
    };

    client.activate();
    return () => { void client.deactivate(); };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user.id]);

  const handleRefresh = () => {
    if (refreshing) return;
    setRefreshSpin(true);
    void loadVideos(currentPage).finally(() => {
      window.setTimeout(() => setRefreshSpin(false), 700);
    });
  };

  const handleStartAnalysis = async (video: VideoInfo) => {
    try {
      const analyzing = await analyzeVideo(video.id);
      setActiveVideo(analyzing);
      setVideos((curr) => curr.map((v) => (v.id === analyzing.id ? analyzing : v)));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('msg_analyze_failed'));
    }
  };

  const handleDelete = (video: VideoInfo) => {
    Modal.confirm({
      title: t('msg_delete_confirm_title'),
      content: t('msg_delete_confirm_content'),
      okText: t('msg_delete_ok'),
      cancelText: t('msg_delete_cancel'),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteVideo(video.id);
          message.success(t('msg_delete_success'));
          await loadVideos(currentPage, true);
        } catch (error) {
          message.error(error instanceof Error ? error.message : t('msg_delete_failed'));
        }
      },
    });
  };

  const handleCancel = (video: VideoInfo) => {
    Modal.confirm({
      title: t('msg_cancel_confirm_title'),
      content: t('msg_cancel_confirm_content'),
      okText: t('action_cancel'),
      cancelText: t('msg_delete_cancel'),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteVideo(video.id);
          message.success(t('msg_delete_success'));
          await loadVideos(currentPage, true);
        } catch (error) {
          message.error(error instanceof Error ? error.message : t('msg_delete_failed'));
        }
      },
    });
  };

  const handleRetryImport = async (video: VideoInfo) => {
    try {
      const importing = await retryImport(video.id);
      setActiveVideo(importing);
      setVideos((curr) => curr.map((v) => (v.id === importing.id ? importing : v)));
      message.success(t('msg_retry_submitted'));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('msg_retry_failed'));
    }
  };

  const handleUpload = async () => {
    if (!file) return;
    setSubmitting(true);
    setUploadProgress(0);
    try {
      const created = await uploadVideoChunked(file, file.name, setUploadProgress);
      const analyzing = await analyzeVideo(created.id);
      setActiveVideo(analyzing);
      await loadVideos(1, true);
      message.success(t('msg_submitted_file'));
      setFile(null);
      navigateTo('workbench');
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('msg_submit_failed'));
    } finally {
      setSubmitting(false);
      setUploadProgress(null);
    }
  };

  const handleUrlSubmit = async () => {
    if (!sourceUrl.trim()) return;
    setSubmitting(true);
    try {
      const importing = await importVideoByUrl('Video link', sourceUrl.trim());
      setActiveVideo(importing);
      await loadVideos(1, true);
      message.success(t('msg_submitted_url'));
      setSourceUrl('');
      navigateTo('workbench');
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('msg_submit_failed'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleHomeSubmit = () => {
    if (submitting) return;
    if (source === 'file' && file) {
      void handleUpload();
      return;
    }
    if (source === 'url' && sourceUrl.trim()) {
      void handleUrlSubmit();
      return;
    }
    if (file) {
      void handleUpload();
      return;
    }
    if (sourceUrl.trim()) {
      void handleUrlSubmit();
      return;
    }
    message.warning(t('msg_need_input'));
  };

  const pickFile = (f?: File | null) => {
    if (!f) return;
    setFile(f);
    setSource('file');
  };

  const openDetail = (video: VideoInfo, tab: DetailTab) => {
    setActiveVideo(video);
    setDetailTab(tab);
    setDetailOpen(true);
  };

  const canSubmit =
    !submitting && ((source === 'file' && !!file) || (source === 'url' && !!sourceUrl.trim()));

  const panelSourceLabel = (() => {
    if (source === 'file') {
      return file ? file.name : t('panel_source_file_empty');
    }
    return sourceUrl ? sourceUrl : t('panel_source_url_empty');
  })();

  const fileSizeMb = file ? (file.size / 1024 / 1024).toFixed(1) : '0';

  /* ── Header (varies by current page) ──────────────────── */
  const Header = (
    <header className="vi-header">
      <div className="vi-header-inner">
        <button
          className="logo-link"
          onClick={() => navigateTo('home')}
          aria-label="VidInsight"
        >
          <span className="vi-logo-mark logo-mark">
            <LogoMark />
          </span>
          <span className="vi-logo-text">
            VidInsight <span className="dim">AI</span>
          </span>
        </button>

        <div className="vi-header-spacer" />

        <button
          className="vi-lang-btn btn-lift"
          onClick={toggleLang}
          title={lang === 'zh' ? 'Switch to English' : '切换到中文'}
        >
          {t('lang_toggle')}
        </button>

        {page === 'workbench' ? (
          <button
            className="vi-nav-btn btn-lift is-back"
            onClick={() => navigateTo('home')}
          >
            <span className="arrow-slide">
              <IconArrowLeft />
            </span>
            {t('nav_home')}
          </button>
        ) : (
          <button
            className="vi-nav-btn btn-lift"
            onClick={() => navigateTo('workbench')}
          >
            {t('nav_workbench')}
            <span className="arrow-slide">
              <IconArrow />
            </span>
          </button>
        )}

        <button
          className="vi-account-btn btn-lift"
          title={user.displayName || user.email}
          aria-label={t('msg_logout_ok')}
          onClick={() => {
            Modal.confirm({
              title: t('msg_logout_confirm_title'),
              content: t('msg_logout_confirm_content'),
              okText: t('msg_logout_ok'),
              cancelText: t('msg_delete_cancel'),
              okButtonProps: { danger: true },
              onOk: () => {
                onLogout();
              },
            });
          }}
        >
          {(user.displayName || user.email).slice(0, 2).toUpperCase()}
        </button>
      </div>
    </header>
  );

  const Footer = (
    <footer className="vi-footer">
      <div className="vi-footer-inner">{t('footer')}</div>
    </footer>
  );

  /* ── HOME ─────────────────────────────── */
  if (page === 'home') {
    const onDragOver = (e: ReactDragEvent<HTMLDivElement>) => {
      e.preventDefault();
      setDrag(true);
      setSource('file');
    };
    const onDragLeave = () => setDrag(false);
    const onDrop = (e: ReactDragEvent<HTMLDivElement>) => {
      e.preventDefault();
      setDrag(false);
      pickFile(e.dataTransfer.files?.[0]);
    };

    return (
      <>
        <div ref={cursorGlowRef} className="cursor-glow" aria-hidden="true" />
        {Header}
        <main className="vi-main">
          <section className="vi-hero">
            <div className="vi-eyebrow anim-up">
              <span className="vi-eyebrow-dot anim-pulse" />
              <span className="vi-eyebrow-words">
                {rotateWords.map((w, i) => (
                  <span key={w} style={{ display: 'inline-flex', gap: 6 }}>
                    {i > 0 && <span className="sep">·</span>}
                    {i === rotateIdx ? (
                      <span className="word-rotate" key={`a-${rotateIdx}`}>
                        {w}
                      </span>
                    ) : (
                      <span className="dim">{w}</span>
                    )}
                  </span>
                ))}
              </span>
            </div>

            <h1 className="vi-hero-title anim-up stagger-1">
              {t('hero_title_prefix')}{' '}
              <span className="serif serif-accent">{t('hero_title_word')}</span>
              <span className="cursor anim-blink">.</span>
            </h1>

            <p className="vi-hero-sub anim-up stagger-2">{t('hero_sub')}</p>
          </section>

          <div
            ref={panelRef}
            className="vi-panel anim-up stagger-3 anim-card-glow glow-host"
          >
            <div className="glow-spot" />
            <div className="glow-border" />
            <div className="vi-panel-grid">
              {/* LOCAL FILE */}
              <div
                className={`vi-col src-col ${source === 'file' ? 'is-active' : ''} ${
                  drag ? 'is-drag' : ''
                }`}
                role="button"
                tabIndex={0}
                onClick={() => {
                  setSource('file');
                  fileInputRef.current?.click();
                }}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    setSource('file');
                    fileInputRef.current?.click();
                  }
                }}
                onDragOver={onDragOver}
                onDragLeave={onDragLeave}
                onDrop={onDrop}
              >
                <input
                  ref={fileInputRef}
                  type="file"
                  accept="video/*,audio/*"
                  hidden
                  onChange={(e) => pickFile(e.target.files?.[0])}
                />

                <div className="vi-col-head">
                  <span className={`vi-col-icon ${source === 'file' ? 'icon-bob' : ''}`}>
                    <IconUpload />
                  </span>
                  <div className="vi-col-title">
                    <div className="vi-col-title-main">{t('col_local_title')}</div>
                    <div className="vi-col-title-sub">{t('col_local_sub')}</div>
                  </div>
                  {source === 'file' && <span className="vi-col-active-dot" />}
                </div>

                <div className={`dropzone vi-drop ${drag ? 'is-drag' : ''}`}>
                  {file ? (
                    <>
                      <div className="vi-file-current">
                        {file.name}
                        <button
                          className="vi-file-remove-inline"
                          onClick={(e) => {
                            e.stopPropagation();
                            setFile(null);
                          }}
                          aria-label="Remove file"
                        >
                          ×
                        </button>
                      </div>
                      <div className="vi-file-meta">{fileSizeMb} MB · ready</div>
                    </>
                  ) : (
                    <>
                      <div className="vi-drop-prompt">
                        {t('drop_prompt_prefix')}{' '}
                        <span className="browse">{t('drop_browse')}</span>
                      </div>
                      <div className="vi-format-row">
                        {FILE_FORMATS.map((f, i) => (
                          <span key={f} style={{ display: 'inline-flex', gap: 8 }}>
                            {i > 0 && <span className="sep">·</span>}
                            <span>{f}</span>
                          </span>
                        ))}
                      </div>
                    </>
                  )}
                </div>
              </div>

              {/* WEB LINK */}
              <div
                className={`vi-col src-col ${source === 'url' ? 'is-active' : ''}`}
                onClick={() => setSource('url')}
              >
                <div className="vi-col-head">
                  <span className={`vi-col-icon ${source === 'url' ? 'icon-bob' : ''}`}>
                    <IconLink />
                  </span>
                  <div className="vi-col-title">
                    <div className="vi-col-title-main">{t('col_link_title')}</div>
                    <div className="vi-col-title-sub">{t('col_link_sub')}</div>
                  </div>
                  {source === 'url' && <span className="vi-col-active-dot" />}
                </div>

                <div
                  style={{
                    flex: 1,
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'center',
                    gap: 14,
                  }}
                >
                  <div className="vi-url-wrap">
                    <input
                      className="vi-url-input"
                      value={sourceUrl}
                      onChange={(e) => {
                        setSourceUrl(e.target.value);
                        setSource('url');
                      }}
                      onFocus={() => setSource('url')}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') handleHomeSubmit();
                      }}
                      placeholder={t('url_placeholder')}
                    />
                    <button
                      className="vi-url-arrow"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleHomeSubmit();
                      }}
                      disabled={!sourceUrl.trim() || submitting}
                      aria-label="Fetch URL"
                    >
                      <IconArrow />
                    </button>
                  </div>
                  <div className="vi-format-row">
                    {LINK_SOURCES.map((s, i) => (
                      <span key={s} style={{ display: 'inline-flex', gap: 8 }}>
                        {i > 0 && <span className="sep">·</span>}
                        <span>{s}</span>
                      </span>
                    ))}
                  </div>
                </div>
              </div>
            </div>

            <div className="vi-panel-foot">
              <div className="vi-panel-source">
                {t('panel_source_label')}: <span className="value">{panelSourceLabel}</span>
              </div>
              <button
                className="vi-submit-btn btn-lift btn-primary"
                disabled={!canSubmit}
                onClick={handleHomeSubmit}
              >
                {submitting ? t('home_submitting') : t('home_submit')}
                {!submitting && (
                  <span className="arrow-slide">
                    <IconArrow />
                  </span>
                )}
              </button>
            </div>
          </div>

          {uploadProgress !== null && (
            <div className="vi-progress-wrap">
              <Progress
                percent={uploadProgress}
                status={uploadProgress < 100 ? 'active' : 'success'}
                size="small"
                showInfo={false}
              />
            </div>
          )}
        </main>
        {Footer}
      </>
    );
  }

  /* ── WORKBENCH ────────────────────────── */
  return (
    <>
      {Header}
      <main className="vi-main-wb">
        <div className="vi-wb-titlebar anim-up">
          <h1 className="vi-wb-h1">{t('wb_title')}</h1>
          <span className="vi-tasks-pill">
            <span className="num">{totalVideos}</span>&nbsp;&nbsp;{t('wb_tasks_suffix')}
          </span>

          <div className="vi-wb-spacer" />

          <button
            className="vi-refresh-btn btn-lift"
            onClick={handleRefresh}
            disabled={refreshing}
          >
            <span className={`spin ${refreshSpin ? 'is-spinning' : ''}`}>
              <IconRefresh />
            </span>
            {t('wb_refresh')}
          </button>

          <button
            className="vi-newtask-btn btn-lift btn-primary"
            onClick={() => navigateTo('home')}
          >
            <span style={{ display: 'inline-flex' }}>
              <IconPlus />
            </span>
            {t('wb_new_task')}
          </button>
        </div>

        {videos.length === 0 ? (
          <div className="vi-empty anim-up stagger-2">
            <div className="vi-empty-icon">
              <IconEmpty />
            </div>
            <div className="vi-empty-title">
              {t('wb_empty_title_prefix')}{' '}
              <span className="serif serif-accent">{t('wb_empty_title_word')}</span>
              <span>.</span>
            </div>
            <p className="vi-empty-sub">{t('wb_empty_sub')}</p>
            <button
              className="vi-cta btn-lift btn-primary"
              onClick={() => navigateTo('home')}
            >
              {t('wb_start_first')}
              <span className="arrow-slide">
                <IconArrow />
              </span>
            </button>
          </div>
        ) : (
          <div className="vi-task-grid">
            {videos.map((video, idx) => {
              const hasAudio = !!video.audioUrl;
              const hasTranscript = !!video.transcript;
              const hasSummary = !!video.summary;
              const isImportFailed = video.videoStatus === 'IMPORT_FAILED';
              const isProcessing = video.videoStatus === 'PROCESSING';
              const canReanalyze =
                video.videoStatus === 'PENDING' ||
                video.videoStatus === 'FAILED' ||
                video.videoStatus === 'COMPLETED';
              const sourceLabel = isLocalSource(video.sourceUrl)
                ? t('source_local')
                : t('source_web');
              const staggerIdx = Math.min(idx + 2, 6);
              const stepKey = processingSteps[video.id];
              const stage = (stepKey && STEP_STAGE[stepKey]) ?? STAGES[0];

              return (
                <article
                  key={video.id}
                  className={`vi-task-card anim-up stagger-${staggerIdx} ${
                    isProcessing ? 'is-processing' : ''
                  }`}
                >
                  <div className="vi-task-head">
                    <div
                      className={`vi-task-thumb ${isProcessing ? 'thumb-processing' : ''}`}
                    >
                      <IconPlay />
                    </div>
                    <div className="vi-task-info">
                      <div className="vi-task-title">{video.title}</div>
                      <div className="vi-task-meta num">
                        {formatDate(video.createdAt)} · {sourceLabel}
                      </div>
                    </div>
                    <StatusPill status={video.videoStatus} t={t} />
                  </div>

                  {isProcessing && (
                    <>
                      <div className="vi-progress-track" />
                      <div className="vi-stage-strip">
                        <span className="vi-stage-text">
                          <span className="idx">
                            {String(Object.keys(STEP_STAGE).indexOf(stepKey ?? '') + 1 || 1).padStart(2, '0')}/
                            {Object.keys(STEP_STAGE).length}
                          </span>
                          &nbsp;&nbsp;{t(stage.key)}
                          <span className="ellipsis">…</span>
                        </span>
                        <span className="vi-stage-pct">{stage.pct}%</span>
                      </div>
                    </>
                  )}

                  <div className="vi-action-row">
                    {isProcessing ? (
                      <ActionBtn
                        icon={<IconTrash />}
                        label={t('action_cancel')}
                        enabled
                        tone="err"
                        onClick={() => handleCancel(video)}
                      />
                    ) : (
                      <>
                        <ActionBtn
                          icon={<IconAudio />}
                          label={t('action_audio')}
                          enabled={hasAudio}
                          href={hasAudio ? video.audioUrl : undefined}
                        />
                        <ActionBtn
                          icon={<IconDoc />}
                          label={t('action_transcript')}
                          enabled={hasTranscript}
                          onClick={() => openDetail(video, 'transcript')}
                        />
                        <ActionBtn
                          icon={<IconSpark />}
                          label={t('action_summary')}
                          enabled={hasSummary}
                          tone="accent"
                          onClick={() => openDetail(video, 'summary')}
                        />
                        {isImportFailed ? (
                          <ActionBtn
                            icon={<IconRefresh />}
                            label={t('action_retry')}
                            enabled
                            onClick={() => void handleRetryImport(video)}
                          />
                        ) : (
                          <ActionBtn
                            icon={<IconPlay />}
                            label={t('action_reanalyze')}
                            enabled={canReanalyze}
                            tone="accent"
                            onClick={() => void handleStartAnalysis(video)}
                          />
                        )}
                        <ActionBtn
                          icon={<IconTrash />}
                          label={t('action_delete')}
                          enabled
                          tone="err"
                          onClick={() => handleDelete(video)}
                        />
                      </>
                    )}
                  </div>
                </article>
              );
            })}
          </div>
        )}

        {totalPages > 1 && (
          <div className="vi-pagination">
            <button
              className="vi-refresh-btn btn-lift is-back"
              disabled={currentPage <= 1}
              onClick={() => void loadVideos(currentPage - 1)}
            >
              <span className="arrow-slide">
                <IconArrowLeft />
              </span>
              {t('pagination_prev')}
            </button>
            <span className="vi-page-info num">
              {currentPage} / {totalPages}
            </span>
            <button
              className="vi-refresh-btn btn-lift"
              disabled={currentPage >= totalPages}
              onClick={() => void loadVideos(currentPage + 1)}
            >
              {t('pagination_next')}
              <span className="arrow-slide">
                <IconArrow />
              </span>
            </button>
          </div>
        )}
      </main>
      {Footer}

      {/* Detail modal */}
      {detailOpen && activeVideo && (
        <div className="vi-overlay" onClick={() => setDetailOpen(false)}>
          <div className="vi-modal" onClick={(e) => e.stopPropagation()}>
            <div className="vi-modal-head">
              <StatusPill status={activeVideo.videoStatus} t={t} />
              <span className="vi-modal-title">{activeVideo.title}</span>
              <button className="vi-modal-x" onClick={() => setDetailOpen(false)}>
                ×
              </button>
            </div>

            <div className="vi-modal-tabs">
              <button
                className={`vi-modal-tab ${detailTab === 'video' ? 'vi-tab-active' : ''}`}
                onClick={() => setDetailTab('video')}
              >
                <IconPlay /> {t('modal_tab_video')}
              </button>
              <button
                className={`vi-modal-tab ${detailTab === 'transcript' ? 'vi-tab-active' : ''}`}
                onClick={() => setDetailTab('transcript')}
              >
                <IconDoc /> {t('modal_tab_transcript')}
              </button>
              <button
                className={`vi-modal-tab ${detailTab === 'summary' ? 'vi-tab-active' : ''}`}
                onClick={() => setDetailTab('summary')}
              >
                <IconSpark size={14} /> {t('modal_tab_summary')}
              </button>
            </div>

            <div className="vi-modal-body">
              {detailTab === 'video' ? (
                activeVideo.sourceUrl && activeVideo.sourceUrl.startsWith('/uploads/') ? (
                  <video
                    className="vi-modal-video"
                    src={activeVideo.sourceUrl}
                    controls
                    preload="metadata"
                  />
                ) : (
                  <div className="vi-modal-empty">{t('modal_video_unavailable')}</div>
                )
              ) : detailTab === 'transcript' ? (
                activeVideo.transcript ? (
                  <p className="vi-modal-text">{activeVideo.transcript}</p>
                ) : (
                  <div className="vi-modal-empty">
                    {activeVideo.videoStatus === 'PROCESSING' ? (
                      <>
                        <span className="vi-spin">⟳</span>{' '}
                        {t('modal_transcript_processing')}
                      </>
                    ) : (
                      t('modal_transcript_empty')
                    )}
                  </div>
                )
              ) : activeVideo.summary ? (
                <div className="vi-modal-markdown">
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>
                    {activeVideo.summary}
                  </ReactMarkdown>
                </div>
              ) : (
                <div className="vi-modal-empty">
                  {activeVideo.videoStatus === 'PROCESSING' ? (
                    <>
                      <span className="vi-spin">⟳</span> {t('modal_summary_processing')}
                    </>
                  ) : (
                    t('modal_summary_empty')
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </>
  );
}

type AuthState =
  | { kind: 'checking' }
  | { kind: 'unauthenticated' }
  | { kind: 'authenticated'; user: UserProfile };

/**
 * Auth gate:启动时先验证 token 是否还有效再渲染主应用。
 * - 没 token → 直接进登录
 * - 有 token → 调 /me 验证;通过就进主应用,失败就清掉跳登录
 * - 运行期间任何 API 返回 HTTP 401 → 通过全局事件回到登录页
 */
function App() {
  const [authState, setAuthState] = useState<AuthState>(() =>
    getStoredToken() ? { kind: 'checking' } : { kind: 'unauthenticated' },
  );

  useEffect(() => {
    if (authState.kind !== 'checking') return;
    // 先用 localStorage 里的 user 立刻渲染 UI(避免短暂白屏),后台再调 /me 校验。
    const cached = getStoredUser();
    let cancelled = false;
    getCurrentUserApi()
      .then((user) => {
        if (!cancelled) setAuthState({ kind: 'authenticated', user });
      })
      .catch(() => {
        if (!cancelled) {
          clearAuth();
          setAuthState({ kind: 'unauthenticated' });
        }
      });
    // 如果之前 localStorage 有 user,立即用上(乐观渲染),后续 /me 失败再回退。
    if (cached) {
      setAuthState({ kind: 'authenticated', user: cached });
    }
    return () => {
      cancelled = true;
    };
  }, [authState.kind]);

  useEffect(() => {
    const handler = () => setAuthState({ kind: 'unauthenticated' });
    window.addEventListener(AUTH_REQUIRED_EVENT, handler);
    return () => window.removeEventListener(AUTH_REQUIRED_EVENT, handler);
  }, []);

  const handleLogout = useCallback(() => {
    clearAuth();
    setAuthState({ kind: 'unauthenticated' });
  }, []);

  if (authState.kind === 'checking') {
    return (
      <div
        style={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#999',
        }}
      >
        Loading…
      </div>
    );
  }

  if (authState.kind === 'unauthenticated') {
    return (
      <Auth
        onAuthenticated={(user) => setAuthState({ kind: 'authenticated', user })}
      />
    );
  }

  return <AppInner user={authState.user} onLogout={handleLogout} />;
}

export default App;

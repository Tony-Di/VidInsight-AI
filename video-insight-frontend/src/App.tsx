import { App as AntdApp, Modal, Progress, Upload } from 'antd';
import type { UploadProps } from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  analyzeVideo,
  deleteVideo,
  getVideo,
  importVideoByUrl,
  listVideos,
  uploadVideoChunked,
  type VideoInfo,
  type VideoStatus,
} from './api';

const PAGE_SIZE = 10;

const { Dragger } = Upload;
type Page = 'home' | 'workbench';
type DetailTab = 'transcript' | 'summary';
type Lang = 'zh' | 'en';

const STATUS_LABEL: Record<VideoStatus, string> = {
  UPLOADING: 'UPLOAD',
  IMPORTING: 'IMPORT',
  IMPORT_FAILED: 'IMPORT FAILED',
  PENDING: 'PENDING',
  PROCESSING: 'PROC',
  COMPLETED: 'READY',
  FAILED: 'FAILED',
};

const POLLING_STATUSES = new Set<VideoStatus>(['IMPORTING', 'PROCESSING']);

const TRANSLATIONS = {
  zh: {
    nav_workbench: '工作台',
    lang_toggle: 'EN',
    home_local_file: 'LOCAL FILE',
    home_local_hint: '点击 / 拖拽本地文件',
    home_upload_text: '选择或拖拽视频文件',
    home_web_link: 'WEB LINK',
    home_web_hint: 'B站 / YouTube / 抖音',
    home_url_placeholder: '粘贴视频链接...',
    home_link_warning: '⚠ 在线视频需后端 yt-dlp 适配器支持',
    home_submit: 'START ANALYSIS',
    home_submitting: '分析中...',
    msg_submitted_file: '视频已提交分析',
    msg_submitted_url: '链接已提交分析',
    msg_need_input: '请上传视频文件或粘贴链接',
    msg_load_failed: '加载视频列表失败',
    msg_submit_failed: '提交失败',
    msg_analyze_failed: '启动分析失败',
    wb_title: '工作台',
    wb_tasks_suffix: 'TASKS',
    wb_refresh: '↻ 刷新',
    wb_refreshing: '刷新中...',
    wb_new_task: '+ 新任务',
    wb_empty: '暂无视频任务',
    wb_start_first: '开始第一个任务',
    card_download_audio: '下载音频',
    card_transcript: '提取文字',
    card_summary: 'AI智能总结',
    card_reanalyze: '重新分析',
    card_delete: '删除',
    msg_delete_confirm_title: '确认删除？',
    msg_delete_confirm_content: '此操作不可撤销，视频文件和分析结果都将被删除。',
    msg_delete_ok: '删除',
    msg_delete_cancel: '取消',
    msg_delete_success: '已删除',
    msg_delete_failed: '删除失败',
    modal_tab_transcript: '≡ 文字提取',
    modal_tab_summary: '◈ AI智能总结',
    modal_transcript_processing: '正在提取文字，请稍候...',
    modal_transcript_empty: '分析完成后文字内容将显示在这里',
    modal_summary_processing: 'AI 正在生成总结...',
    modal_summary_empty: '分析完成后 AI 总结将显示在这里',
  },
  en: {
    nav_workbench: 'Workbench',
    lang_toggle: '中',
    home_local_file: 'LOCAL FILE',
    home_local_hint: 'Click or drop a local file',
    home_upload_text: 'Choose or drag a video file',
    home_web_link: 'WEB LINK',
    home_web_hint: 'Bilibili / YouTube / Douyin',
    home_url_placeholder: 'Paste video URL...',
    home_link_warning: '⚠ Online videos require a yt-dlp adapter on the backend',
    home_submit: 'START ANALYSIS',
    home_submitting: 'Analyzing...',
    msg_submitted_file: 'Video submitted for analysis',
    msg_submitted_url: 'Link submitted for analysis',
    msg_need_input: 'Please upload a video file or paste a link',
    msg_load_failed: 'Failed to load videos',
    msg_submit_failed: 'Submit failed',
    msg_analyze_failed: 'Failed to start analysis',
    wb_title: 'Workbench',
    wb_tasks_suffix: 'TASKS',
    wb_refresh: '↻ Refresh',
    wb_refreshing: 'Refreshing...',
    wb_new_task: '+ New Task',
    wb_empty: 'No video tasks yet',
    wb_start_first: 'Start your first task',
    card_download_audio: 'Audio',
    card_transcript: 'Transcript',
    card_summary: 'AI Summary',
    card_reanalyze: 'Re-analyze',
    card_delete: 'Delete',
    msg_delete_confirm_title: 'Delete this video?',
    msg_delete_confirm_content: 'This cannot be undone. The video file and analysis results will be removed.',
    msg_delete_ok: 'Delete',
    msg_delete_cancel: 'Cancel',
    msg_delete_success: 'Deleted',
    msg_delete_failed: 'Delete failed',
    modal_tab_transcript: '≡ Transcript',
    modal_tab_summary: '◈ AI Summary',
    modal_transcript_processing: 'Extracting transcript, please wait...',
    modal_transcript_empty: 'Transcript will appear here after analysis',
    modal_summary_processing: 'AI is generating the summary...',
    modal_summary_empty: 'AI summary will appear here after analysis',
  },
} as const;

type I18nKey = keyof typeof TRANSLATIONS['zh'];

function getInitialLang(): Lang {
  try {
    const stored = window.localStorage.getItem('vi-lang');
    if (stored === 'zh' || stored === 'en') return stored;
  } catch { /* ignore */ }
  return 'zh';
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return '';
  try {
    const d = new Date(dateStr);
    return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  } catch {
    return '';
  }
}

function getPageFromPath(): Page {
  return window.location.pathname === '/workbench' ? 'workbench' : 'home';
}

function IconUpload() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/>
    </svg>
  );
}

function IconGlobe() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/>
      <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>
    </svg>
  );
}

function IconArrow() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
      <line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/>
    </svg>
  );
}

function IconVideo() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
      <rect x="2" y="2" width="20" height="20" rx="2"/><polygon points="10 8 16 12 10 16 10 8"/>
    </svg>
  );
}

function App() {
  const { message } = AntdApp.useApp();
  const [page, setPage] = useState<Page>(() => getPageFromPath());
  const [file, setFile] = useState<File | null>(null);
  const [fileTitle, setFileTitle] = useState('');
  const [sourceUrl, setSourceUrl] = useState('');
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
  const [lang, setLang] = useState<Lang>(getInitialLang);
  const pollTimerRef = useRef<number | null>(null);

  const t = useCallback((key: I18nKey) => TRANSLATIONS[lang][key], [lang]);

  useEffect(() => {
    try { window.localStorage.setItem('vi-lang', lang); } catch { /* ignore */ }
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

  const loadVideos = useCallback(async (targetPage: number, silent = false) => {
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
  }, [message, t]);

  useEffect(() => {
    const handlePopState = () => setPage(getPageFromPath());
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  useEffect(() => {
    if (page === 'workbench') void loadVideos(1);
  }, [loadVideos, page]);

  useEffect(() => {
    if (pollTimerRef.current) {
      window.clearInterval(pollTimerRef.current);
      pollTimerRef.current = null;
    }
    if (!activeVideo || !POLLING_STATUSES.has(activeVideo.videoStatus)) return;

    pollTimerRef.current = window.setInterval(async () => {
      try {
        const latest = await getVideo(activeVideo.id);
        if (activeVideo.videoStatus === 'IMPORTING' && latest.videoStatus === 'PENDING') {
          const analyzing = await analyzeVideo(latest.id);
          setActiveVideo(analyzing);
          setVideos((curr) => curr.map((v) => (v.id === analyzing.id ? analyzing : v)));
          return;
        }

        setActiveVideo(latest);
        setVideos((curr) => curr.map((v) => (v.id === latest.id ? latest : v)));
        if (!POLLING_STATUSES.has(latest.videoStatus) && pollTimerRef.current) {
          window.clearInterval(pollTimerRef.current);
          pollTimerRef.current = null;
        }
      } catch {
        if (pollTimerRef.current) {
          window.clearInterval(pollTimerRef.current);
          pollTimerRef.current = null;
        }
      }
    }, 2400);

    return () => {
      if (pollTimerRef.current) window.clearInterval(pollTimerRef.current);
    };
  }, [activeVideo]);

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

  const handleUpload = async () => {
    if (!file) return;
    setSubmitting(true);
    setUploadProgress(0);
    try {
      const created = await uploadVideoChunked(file, fileTitle || file.name, setUploadProgress);
      const analyzing = await analyzeVideo(created.id);
      setActiveVideo(analyzing);
      await loadVideos(1, true);
      message.success(t('msg_submitted_file'));
      setFile(null);
      setFileTitle('');
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
    if (file) { void handleUpload(); return; }
    if (sourceUrl.trim()) { void handleUrlSubmit(); return; }
    message.warning(t('msg_need_input'));
  };

  const uploadProps: UploadProps = {
    accept: '.mp4,.mov,.avi,.mkv,.webm',
    multiple: false,
    maxCount: 1,
    showUploadList: false,
    beforeUpload: (selectedFile) => {
      setFile(selectedFile);
      if (!fileTitle) setFileTitle(selectedFile.name);
      return false;
    },
  };

  const openDetail = (video: VideoInfo, tab: DetailTab) => {
    setActiveVideo(video);
    setDetailTab(tab);
    setDetailOpen(true);
  };

  const NavBar = (
    <nav className="vi-nav">
      <button className="vi-brand" onClick={() => navigateTo('home')}>
        <span className="vi-brand-mark">V</span>
        <span>VidInsight AI</span>
      </button>
      <div className="vi-nav-links">
        <button
          className="vi-nav-btn vi-lang-btn"
          onClick={toggleLang}
          aria-label="Switch language"
          title={lang === 'zh' ? 'Switch to English' : '切换到中文'}
        >
          {t('lang_toggle')}
        </button>
        <button
          className={`vi-nav-btn ${page === 'workbench' ? 'vi-nav-active' : ''}`}
          onClick={() => navigateTo('workbench')}
        >
          {t('nav_workbench')}
        </button>
      </div>
    </nav>
  );

  /* ── HOME ─────────────────────────────── */
  if (page === 'home') {
    const canSubmit = !submitting && (!!file || !!sourceUrl.trim());
    return (
      <main className="vi-home">
        <div className="vi-home-bg" />
        {NavBar}
        <section className="vi-hero">
          <h1 className="vi-hero-title">DECODE YOUR VIDEO</h1>

          <div className="vi-split-panel">
            {/* LOCAL FILE half */}
            <div className="vi-half">
              <div className="vi-half-icon"><IconUpload /></div>
              <h2>{t('home_local_file')}</h2>
              <p className="vi-half-hint">{t('home_local_hint')}</p>
              <Dragger {...uploadProps} className="vi-dragger">
                <p className="ant-upload-drag-icon">
                  <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                    <polyline points="14 2 14 8 20 8"/>
                  </svg>
                </p>
                <p className="ant-upload-text">{t('home_upload_text')}</p>
                <p className="ant-upload-hint">mp4 · mov · avi · mkv · webm</p>
              </Dragger>
              {file && (
                <div className="vi-file-tag">
                  <span>✓</span>
                  <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {file.name}
                  </span>
                  <button
                    className="vi-file-remove"
                    onClick={(e) => { e.stopPropagation(); setFile(null); setFileTitle(''); }}
                  >
                    ×
                  </button>
                </div>
              )}
            </div>

            {/* WEB LINK half */}
            <div className="vi-half">
              <div className="vi-half-icon"><IconGlobe /></div>
              <h2>{t('home_web_link')}</h2>
              <p className="vi-half-hint">{t('home_web_hint')}</p>
              <div className="vi-url-wrap">
                <input
                  className="vi-url-input"
                  placeholder={t('home_url_placeholder')}
                  value={sourceUrl}
                  onChange={(e) => setSourceUrl(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter') handleHomeSubmit(); }}
                />
                <button className="vi-url-arrow" onClick={handleHomeSubmit}>›</button>
              </div>
              <p className="vi-link-warning">{t('home_link_warning')}</p>
            </div>
          </div>

          <button className="vi-submit-btn" disabled={!canSubmit} onClick={handleHomeSubmit}>
            {submitting ? t('home_submitting') : t('home_submit')}
            {!submitting && <IconArrow />}
          </button>

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
        </section>
      </main>
    );
  }

  /* ── WORKBENCH ────────────────────────── */
  return (
    <main className="vi-wb-shell">
      {NavBar}
      <div className="vi-wb-content">
        <div className="vi-wb-header">
          <span className="vi-wb-title">{t('wb_title')}</span>
          <span className="vi-task-badge">{totalVideos} {t('wb_tasks_suffix')}</span>
          <div className="vi-wb-actions">
            <button
              className="vi-icon-btn"
              onClick={() => void loadVideos(currentPage)}
              disabled={refreshing}
            >
              {refreshing ? t('wb_refreshing') : t('wb_refresh')}
            </button>
            <button
              className="vi-icon-btn vi-btn-accent"
              onClick={() => navigateTo('home')}
            >
              {t('wb_new_task')}
            </button>
          </div>
        </div>

        <div className="vi-task-grid">
          {videos.length === 0 ? (
            <div className="vi-empty">
              <div className="vi-empty-icon">◻</div>
              <p>{t('wb_empty')}</p>
              <button
                className="vi-submit-btn"
                style={{ marginTop: 20, fontSize: 12 }}
                onClick={() => navigateTo('home')}
              >
                {t('wb_start_first')} <IconArrow />
              </button>
            </div>
          ) : (
            videos.map((video) => (
              <div
                key={video.id}
                className={`vi-task-card ${activeVideo?.id === video.id ? 'vi-card-active' : ''}`}
                onClick={() => setActiveVideo(video)}
              >
                <div className="vi-card-head">
                  <div className="vi-card-thumb"><IconVideo /></div>
                  <div className="vi-card-info">
                    <div className="vi-card-name">{video.title}</div>
                    <div className="vi-card-time">{formatDate(video.createdAt)}</div>
                  </div>
                  <span className={`vi-card-status vi-status-${video.videoStatus}`}>
                    {STATUS_LABEL[video.videoStatus]}
                  </span>
                </div>

                <div className="vi-card-actions">
                  {video.audioUrl ? (
                    <a
                      className="vi-card-action"
                      href={video.audioUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      onClick={(e) => e.stopPropagation()}
                    >
                      <span className="vi-action-icon">♪</span>
                      <span>{t('card_download_audio')}</span>
                    </a>
                  ) : (
                    <button className="vi-card-action" disabled>
                      <span className="vi-action-icon">♪</span>
                      <span>{t('card_download_audio')}</span>
                    </button>
                  )}

                  <button
                    className="vi-card-action"
                    disabled={!video.transcript}
                    onClick={(e) => {
                      e.stopPropagation();
                      openDetail(video, 'transcript');
                    }}
                  >
                    <span className="vi-action-icon">≡</span>
                    <span>{t('card_transcript')}</span>
                  </button>

                  <button
                    className={`vi-card-action ${video.summary ? 'vi-action-purple' : ''}`}
                    disabled={!video.summary}
                    onClick={(e) => {
                      e.stopPropagation();
                      openDetail(video, 'summary');
                    }}
                  >
                    <span className="vi-action-icon">◈</span>
                    <span>{t('card_summary')}</span>
                  </button>

                  {(video.videoStatus === 'PENDING' || video.videoStatus === 'FAILED') && (
                    <button
                      className="vi-card-action"
                      onClick={(e) => {
                        e.stopPropagation();
                        void handleStartAnalysis(video);
                      }}
                    >
                      <span className="vi-action-icon">▷</span>
                      <span>{t('card_reanalyze')}</span>
                    </button>
                  )}

                  <button
                    className="vi-card-action vi-action-danger"
                    onClick={(e) => {
                      e.stopPropagation();
                      handleDelete(video);
                    }}
                  >
                    <span className="vi-action-icon">×</span>
                    <span>{t('card_delete')}</span>
                  </button>
                </div>
              </div>
            ))
          )}
        </div>

        {totalPages > 1 && (
          <div className="vi-pagination">
            <button
              className="vi-icon-btn"
              disabled={currentPage <= 1}
              onClick={() => void loadVideos(currentPage - 1)}
            >
              ← PREV
            </button>
            <span className="vi-page-info">{currentPage} / {totalPages}</span>
            <button
              className="vi-icon-btn"
              disabled={currentPage >= totalPages}
              onClick={() => void loadVideos(currentPage + 1)}
            >
              NEXT →
            </button>
          </div>
        )}
      </div>

      {/* Detail modal */}
      {detailOpen && activeVideo && (
        <div className="vi-overlay" onClick={() => setDetailOpen(false)}>
          <div className="vi-modal" onClick={(e) => e.stopPropagation()}>
            <div className="vi-modal-head">
              <span className={`vi-card-status vi-status-${activeVideo.videoStatus}`} style={{ flexShrink: 0 }}>
                {STATUS_LABEL[activeVideo.videoStatus]}
              </span>
              <span className="vi-modal-title">{activeVideo.title}</span>
              <button className="vi-modal-x" onClick={() => setDetailOpen(false)}>×</button>
            </div>

            <div className="vi-modal-tabs">
              <button
                className={`vi-modal-tab ${detailTab === 'transcript' ? 'vi-tab-active' : ''}`}
                onClick={() => setDetailTab('transcript')}
              >
                {t('modal_tab_transcript')}
              </button>
              <button
                className={`vi-modal-tab ${detailTab === 'summary' ? 'vi-tab-active' : ''}`}
                onClick={() => setDetailTab('summary')}
              >
                {t('modal_tab_summary')}
              </button>
            </div>

            <div className="vi-modal-body">
              {detailTab === 'transcript' ? (
                activeVideo.transcript ? (
                  <p className="vi-modal-text">{activeVideo.transcript}</p>
                ) : (
                  <div className="vi-modal-empty">
                    {activeVideo.videoStatus === 'PROCESSING'
                      ? <><span className="vi-spin">⟳</span> {t('modal_transcript_processing')}</>
                      : t('modal_transcript_empty')}
                  </div>
                )
              ) : (
                activeVideo.summary ? (
                  <p className="vi-modal-text">{activeVideo.summary}</p>
                ) : (
                  <div className="vi-modal-empty">
                    {activeVideo.videoStatus === 'PROCESSING'
                      ? <><span className="vi-spin">⟳</span> {t('modal_summary_processing')}</>
                      : t('modal_summary_empty')}
                  </div>
                )
              )}
            </div>
          </div>
        </div>
      )}
    </main>
  );
}

export default App;

import {
  ApiOutlined,
  ArrowRightOutlined,
  CheckCircleFilled,
  CloudUploadOutlined,
  FileTextOutlined,
  LinkOutlined,
  LoadingOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import {
  Alert,
  App as AntdApp,
  Button,
  Col,
  Empty,
  Form,
  Input,
  List,
  Row,
  Segmented,
  Space,
  Statistic,
  Tag,
  Typography,
  Upload,
} from 'antd';
import type { UploadProps } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  analyzeVideo,
  createVideoByUrl,
  getVideo,
  listVideos,
  uploadVideo,
  type VideoInfo,
  type VideoStatus,
} from './api';

const { Dragger } = Upload;
const { Paragraph, Text, Title } = Typography;
type Page = 'home' | 'workbench';

const statusMeta: Record<VideoStatus, { color: string; label: string }> = {
  PENDING: { color: 'default', label: 'Pending' },
  PROCESSING: { color: 'processing', label: 'Processing' },
  COMPLETED: { color: 'success', label: 'Completed' },
  FAILED: { color: 'error', label: 'Failed' },
};

function getStatusIcon(status: VideoStatus) {
  if (status === 'PROCESSING') return <LoadingOutlined />;
  if (status === 'COMPLETED') return <CheckCircleFilled />;
  if (status === 'FAILED') return <ApiOutlined />;
  return <PlayCircleOutlined />;
}

function getPageFromPath(): Page {
  return window.location.pathname === '/workbench' ? 'workbench' : 'home';
}

function App() {
  const { message } = AntdApp.useApp();
  const [page, setPage] = useState<Page>(() => getPageFromPath());
  const [mode, setMode] = useState<'upload' | 'link'>('upload');
  const [file, setFile] = useState<File | null>(null);
  const [fileTitle, setFileTitle] = useState('');
  const [linkTitle, setLinkTitle] = useState('');
  const [sourceUrl, setSourceUrl] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [videos, setVideos] = useState<VideoInfo[]>([]);
  const [activeVideo, setActiveVideo] = useState<VideoInfo | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const pollTimerRef = useRef<number | null>(null);

  const completedCount = useMemo(
    () => videos.filter((video) => video.videoStatus === 'COMPLETED').length,
    [videos],
  );

  const navigateTo = useCallback((nextPage: Page) => {
    const nextPath = nextPage === 'workbench' ? '/workbench' : '/';
    if (window.location.pathname !== nextPath) {
      window.history.pushState(null, '', nextPath);
    }
    setPage(nextPage);
  }, []);

  const loadVideos = useCallback(async (silent = false) => {
    if (!silent) setRefreshing(true);
    try {
      const data = await listVideos();
      setVideos(data);
      setActiveVideo((current) => {
        if (!current) return data[0] ?? null;
        return data.find((video) => video.id === current.id) ?? data[0] ?? null;
      });
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Failed to load videos');
    } finally {
      if (!silent) setRefreshing(false);
    }
  }, [message]);

  useEffect(() => {
    const handlePopState = () => {
      setPage(getPageFromPath());
    };

    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  useEffect(() => {
    if (page === 'workbench') {
      void loadVideos();
    }
  }, [loadVideos, page]);

  useEffect(() => {
    if (pollTimerRef.current) {
      window.clearInterval(pollTimerRef.current);
      pollTimerRef.current = null;
    }

    if (!activeVideo || activeVideo.videoStatus !== 'PROCESSING') return;

    pollTimerRef.current = window.setInterval(async () => {
      try {
        const latest = await getVideo(activeVideo.id);
        setActiveVideo(latest);
        setVideos((current) =>
          current.map((video) => (video.id === latest.id ? latest : video)),
        );
        if (latest.videoStatus !== 'PROCESSING' && pollTimerRef.current) {
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
      if (pollTimerRef.current) {
        window.clearInterval(pollTimerRef.current);
      }
    };
  }, [activeVideo]);

  const handleStartAnalysis = async (video: VideoInfo) => {
    const analyzing = await analyzeVideo(video.id);
    setActiveVideo(analyzing);
    setVideos((current) =>
      current.map((item) => (item.id === analyzing.id ? analyzing : item)),
    );
  };

  const handleUpload = async () => {
    if (!file) {
      message.warning('Choose a local video first');
      return;
    }

    setSubmitting(true);
    try {
      const created = await uploadVideo(file, fileTitle || file.name);
      const analyzing = await analyzeVideo(created.id);
      setActiveVideo(analyzing);
      await loadVideos(true);
      message.success('Video submitted for analysis');
      setFile(null);
      setFileTitle('');
      navigateTo('workbench');
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Submit failed');
    } finally {
      setSubmitting(false);
    }
  };

  const handleUrlSubmit = async (values: { title: string; sourceUrl: string }) => {
    setSubmitting(true);
    try {
      const created = await createVideoByUrl(values.title, values.sourceUrl);
      const analyzing = await analyzeVideo(created.id);
      setActiveVideo(analyzing);
      await loadVideos(true);
      message.success('Link submitted for analysis');
      setLinkTitle('');
      setSourceUrl('');
      navigateTo('workbench');
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Submit failed');
    } finally {
      setSubmitting(false);
    }
  };

  const uploadProps: UploadProps = {
    accept: '.mp4,.mov,.avi,.mkv,.webm',
    multiple: false,
    maxCount: 1,
    showUploadList: file ? { showRemoveIcon: true } : false,
    beforeUpload: (selectedFile) => {
      setFile(selectedFile);
      if (!fileTitle) setFileTitle(selectedFile.name);
      return false;
    },
    onRemove: () => {
      setFile(null);
    },
  };

  const handleHomeSubmit = () => {
    if (mode === 'upload') {
      void handleUpload();
      return;
    }

    if (!sourceUrl.trim()) {
      message.warning('Enter a video link first');
      return;
    }

    void handleUrlSubmit({
      title: linkTitle.trim() || 'Video link',
      sourceUrl: sourceUrl.trim(),
    });
  };

  if (page === 'home') {
    return (
      <main className="landing-page">
        <header className="landing-nav">
          <div className="landing-brand">
            <span className="brand-dot" />
            <span>VidInsight AI</span>
          </div>
          <Button type="text" onClick={() => navigateTo('workbench')}>
            View Results
          </Button>
        </header>

        <section className="home-stage">
          <div className="home-copy">
            <Text className="section-kicker">Video Understanding</Text>
            <Title>Analyze video in one step.</Title>
            <Paragraph>
              Upload a local file or paste a video link, then let the backend handle audio extraction,
              speech recognition, and summary generation.
            </Paragraph>
          </div>

          <div className="start-console">
            <Segmented
              block
              value={mode}
              onChange={(value) => setMode(value as 'upload' | 'link')}
              options={[
                {
                  value: 'upload',
                  label: (
                    <Space size={6}>
                      <CloudUploadOutlined />
                      Local File
                    </Space>
                  ),
                },
                {
                  value: 'link',
                  label: (
                    <Space size={6}>
                      <LinkOutlined />
                      Web Link
                    </Space>
                  ),
                },
              ]}
            />

            {mode === 'upload' ? (
              <div className="home-input-stack">
                <Input
                  size="large"
                  placeholder="Video title, default uses file name"
                  value={fileTitle}
                  onChange={(event) => setFileTitle(event.target.value)}
                />
                <Dragger {...uploadProps} className="home-upload">
                  <p className="ant-upload-drag-icon">
                    <CloudUploadOutlined />
                  </p>
                  <p className="ant-upload-text">Choose a local video</p>
                  <p className="ant-upload-hint">mp4, mov, avi, mkv, webm</p>
                </Dragger>
              </div>
            ) : (
              <div className="home-input-stack">
                <Input
                  size="large"
                  placeholder="Video title"
                  value={linkTitle}
                  onChange={(event) => setLinkTitle(event.target.value)}
                />
                <Input
                  size="large"
                  placeholder="Paste video link"
                  prefix={<LinkOutlined />}
                  value={sourceUrl}
                  onChange={(event) => setSourceUrl(event.target.value)}
                />
                <Alert
                  type="warning"
                  showIcon
                  message="Online video analysis needs the backend download adapter before it can fully run."
                />
              </div>
            )}

            <Button
              type="primary"
              size="large"
              icon={<PlayCircleOutlined />}
              loading={submitting}
              disabled={mode === 'upload' ? !file : !sourceUrl.trim()}
              onClick={handleHomeSubmit}
              block
            >
              Start Analysis
            </Button>
          </div>
        </section>
      </main>
    );
  }

  return (
    <main className="page-shell">
      <section className="workbench-header">
        <div>
          <Button type="text" onClick={() => navigateTo('home')}>
            VidInsight AI
          </Button>
          <Title>Video Analysis Workbench</Title>
          <Paragraph>
            Upload a local video or submit a link. The backend extracts audio, runs ASR, and generates a summary.
          </Paragraph>
        </div>
        <Row gutter={12} className="stats-row compact">
          <Col span={8}>
            <Statistic title="Total" value={videos.length} />
          </Col>
          <Col span={8}>
            <Statistic title="Done" value={completedCount} />
          </Col>
          <Col span={8}>
            <Statistic title="Queue" value={videos.length - completedCount} />
          </Col>
        </Row>
      </section>

      <section className="submit-grid">
        <div className="action-panel">
          <div className="panel-head">
            <div>
              <Text className="section-kicker">Start</Text>
              <Title level={2}>Submit Analysis Task</Title>
            </div>
            <Segmented
              value={mode}
              onChange={(value) => setMode(value as 'upload' | 'link')}
              options={[
                {
                  value: 'upload',
                  label: (
                    <Space size={6}>
                      <CloudUploadOutlined />
                      Local File
                    </Space>
                  ),
                },
                {
                  value: 'link',
                  label: (
                    <Space size={6}>
                      <LinkOutlined />
                      Video Link
                    </Space>
                  ),
                },
              ]}
            />
          </div>

          {mode === 'upload' ? (
            <div className="flow-card">
              <Input
                placeholder="Video title, default uses file name"
                value={fileTitle}
                onChange={(event) => setFileTitle(event.target.value)}
              />
              <Dragger {...uploadProps} className="upload-drop">
                <p className="ant-upload-drag-icon">
                  <CloudUploadOutlined />
                </p>
                <p className="ant-upload-text">Drop video here, or click to choose a file</p>
                <p className="ant-upload-hint">Supports mp4, mov, avi, mkv, webm</p>
              </Dragger>
              <Button
                type="primary"
                icon={<ArrowRightOutlined />}
                loading={submitting}
                disabled={!file}
                onClick={handleUpload}
                block
              >
                Upload and Start Analysis
              </Button>
            </div>
          ) : (
            <div className="flow-card">
              <Alert
                type="warning"
                showIcon
                message="The backend can create URL tasks, but real online video downloading still needs a yt-dlp adapter."
              />
              <Form layout="vertical" onFinish={handleUrlSubmit}>
                <Form.Item
                  name="title"
                  label="Title"
                  rules={[{ required: true, message: 'Enter a video title' }]}
                >
                  <Input placeholder="Product demo video" />
                </Form.Item>
                <Form.Item
                  name="sourceUrl"
                  label="Video link"
                  rules={[{ required: true, message: 'Enter a video link' }]}
                >
                  <Input placeholder="https://..." prefix={<LinkOutlined />} />
                </Form.Item>
                <Button
                  type="primary"
                  htmlType="submit"
                  icon={<ArrowRightOutlined />}
                  loading={submitting}
                  block
                >
                  Submit Link and Start Analysis
                </Button>
              </Form>
            </div>
          )}
        </div>
      </section>

      <section className="workspace-grid">
        <div className="list-panel">
          <div className="section-title">
            <div>
              <Text className="section-kicker">Library</Text>
              <Title level={3}>Recent Videos</Title>
            </div>
            <Button icon={<ReloadOutlined />} loading={refreshing} onClick={() => void loadVideos()}>
              Refresh
            </Button>
          </div>
          {videos.length ? (
            <List
              dataSource={videos}
              renderItem={(video) => (
                <List.Item
                  className={video.id === activeVideo?.id ? 'video-row active' : 'video-row'}
                  onClick={() => setActiveVideo(video)}
                >
                  <List.Item.Meta
                    avatar={getStatusIcon(video.videoStatus)}
                    title={
                      <Space>
                        <span>{video.title}</span>
                        <Tag color={statusMeta[video.videoStatus].color}>
                          {statusMeta[video.videoStatus].label}
                        </Tag>
                      </Space>
                    }
                    description={video.sourceUrl}
                  />
                </List.Item>
              )}
            />
          ) : (
            <Empty description="No video records yet" />
          )}
        </div>

        <div className="result-panel">
          <div className="section-title">
            <div>
              <Text className="section-kicker">Result</Text>
              <Title level={3}>Analysis Result</Title>
            </div>
            {activeVideo && (
              <Tag color={statusMeta[activeVideo.videoStatus].color}>
                {statusMeta[activeVideo.videoStatus].label}
              </Tag>
            )}
          </div>

          {activeVideo ? (
            <Space direction="vertical" size={18} className="result-stack">
              <div className="detail-head">
                <div>
                  <Title level={4}>{activeVideo.title}</Title>
                  <Text type="secondary">{activeVideo.sourceUrl}</Text>
                </div>
                {(activeVideo.videoStatus === 'PENDING' || activeVideo.videoStatus === 'FAILED') && (
                  <Button icon={<PlayCircleOutlined />} onClick={() => void handleStartAnalysis(activeVideo)}>
                    Re-run
                  </Button>
                )}
              </div>

              <div className="result-block">
                <Space>
                  <FileTextOutlined />
                  <Text strong>Transcript</Text>
                </Space>
                <Paragraph>
                  {activeVideo.transcript || 'ASR transcript will appear here after analysis completes.'}
                </Paragraph>
              </div>

              <div className="result-block highlight">
                <Space>
                  <ApiOutlined />
                  <Text strong>Summary</Text>
                </Space>
                <Paragraph>
                  {activeVideo.summary || 'AI summary will appear here after analysis completes.'}
                </Paragraph>
              </div>
            </Space>
          ) : (
            <Empty description="Choose or upload a video to inspect the result" />
          )}
        </div>
      </section>
    </main>
  );
}

export default App;

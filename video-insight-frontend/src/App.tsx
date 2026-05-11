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

const statusMeta: Record<VideoStatus, { color: string; label: string }> = {
  PENDING: { color: 'default', label: '待分析' },
  PROCESSING: { color: 'processing', label: '分析中' },
  COMPLETED: { color: 'success', label: '已完成' },
  FAILED: { color: 'error', label: '失败' },
};

function getStatusIcon(status: VideoStatus) {
  if (status === 'PROCESSING') return <LoadingOutlined />;
  if (status === 'COMPLETED') return <CheckCircleFilled />;
  if (status === 'FAILED') return <ApiOutlined />;
  return <PlayCircleOutlined />;
}

function App() {
  const { message } = AntdApp.useApp();
  const [mode, setMode] = useState<'upload' | 'link'>('upload');
  const [file, setFile] = useState<File | null>(null);
  const [fileTitle, setFileTitle] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [videos, setVideos] = useState<VideoInfo[]>([]);
  const [activeVideo, setActiveVideo] = useState<VideoInfo | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const pollTimerRef = useRef<number | null>(null);

  const completedCount = useMemo(
    () => videos.filter((video) => video.videoStatus === 'COMPLETED').length,
    [videos],
  );

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
      message.error(error instanceof Error ? error.message : '视频列表加载失败');
    } finally {
      if (!silent) setRefreshing(false);
    }
  }, [message]);

  useEffect(() => {
    void loadVideos();
  }, [loadVideos]);

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
      message.warning('先选择一个本地视频');
      return;
    }

    setSubmitting(true);
    try {
      const created = await uploadVideo(file, fileTitle || file.name);
      const analyzing = await analyzeVideo(created.id);
      setActiveVideo(analyzing);
      await loadVideos(true);
      message.success('视频已提交分析');
      setFile(null);
      setFileTitle('');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '提交失败');
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
      message.success('链接已提交分析');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '提交失败');
    } finally {
      setSubmitting(false);
    }
  };

  const uploadProps: UploadProps = {
    accept: '.mp4,.mov,.avi,.mkv,.webm',
    multiple: false,
    maxCount: 1,
    showUploadList: file
      ? {
          showRemoveIcon: true,
        }
      : false,
    beforeUpload: (selectedFile) => {
      setFile(selectedFile);
      if (!fileTitle) {
        setFileTitle(selectedFile.name);
      }
      return false;
    },
    onRemove: () => {
      setFile(null);
    },
  };

  return (
    <main className="page-shell">
        <section className="hero-grid">
          <div className="brand-panel">
            <div className="brand-mark">
              <span className="brand-dot" />
              <span>VidInsight AI</span>
            </div>
            <div className="cover-art" aria-hidden="true">
              <div className="video-strip">
                <div className="frame frame-a" />
                <div className="frame frame-b" />
                <div className="frame frame-c" />
              </div>
              <div className="wave-line" />
              <div className="tag-chip chip-a">Scene</div>
              <div className="tag-chip chip-b">ASR</div>
              <div className="tag-chip chip-c">Summary</div>
            </div>
            <div className="hero-copy">
              <Text className="eyebrow">VIDEO UNDERSTANDING WORKBENCH</Text>
              <Title>Analyze video. Extract insight.</Title>
              <Paragraph>
                上传视频或提交链接，后端会完成抽音频、语音识别和内容总结。首页只保留最短路径，让用户直接开始分析。
              </Paragraph>
            </div>
            <Row gutter={12} className="stats-row">
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
          </div>

          <div className="action-panel">
            <div className="panel-head">
              <div>
                <Text className="section-kicker">Start</Text>
                <Title level={2}>提交分析任务</Title>
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
                        本地上传
                      </Space>
                    ),
                  },
                  {
                    value: 'link',
                    label: (
                      <Space size={6}>
                        <LinkOutlined />
                        视频链接
                      </Space>
                    ),
                  },
                ]}
              />
            </div>

            {mode === 'upload' ? (
              <div className="flow-card">
                <Input
                  placeholder="视频标题，可默认使用文件名"
                  value={fileTitle}
                  onChange={(event) => setFileTitle(event.target.value)}
                />
                <Dragger {...uploadProps} className="upload-drop">
                  <p className="ant-upload-drag-icon">
                    <CloudUploadOutlined />
                  </p>
                  <p className="ant-upload-text">拖拽视频到这里，或点击选择文件</p>
                  <p className="ant-upload-hint">支持 mp4、mov、avi、mkv、webm</p>
                </Dragger>
                <Button
                  type="primary"
                  icon={<ArrowRightOutlined />}
                  loading={submitting}
                  disabled={!file}
                  onClick={handleUpload}
                  block
                >
                  上传并开始分析
                </Button>
              </div>
            ) : (
              <div className="flow-card">
                <Alert
                  type="warning"
                  showIcon
                  message="当前后端还没有接入 yt-dlp，链接任务可以创建，但真实在线下载分析需要后续补齐下载适配层。"
                />
                <Form layout="vertical" onFinish={handleUrlSubmit}>
                  <Form.Item
                    name="title"
                    label="标题"
                    rules={[{ required: true, message: '请输入视频标题' }]}
                  >
                    <Input placeholder="例如：产品演示视频" />
                  </Form.Item>
                  <Form.Item
                    name="sourceUrl"
                    label="视频链接"
                    rules={[{ required: true, message: '请输入视频链接' }]}
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
                    提交链接并开始分析
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
                <Title level={3}>最近视频</Title>
              </div>
              <Button
                icon={<ReloadOutlined />}
                loading={refreshing}
                onClick={() => void loadVideos()}
              >
                刷新
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
              <Empty description="还没有视频记录" />
            )}
          </div>

          <div className="result-panel">
            <div className="section-title">
              <div>
                <Text className="section-kicker">Result</Text>
                <Title level={3}>分析结果</Title>
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
                    <Button
                      icon={<PlayCircleOutlined />}
                      onClick={() => void handleStartAnalysis(activeVideo)}
                    >
                      重新分析
                    </Button>
                  )}
                </div>

                <div className="result-block">
                  <Space>
                    <FileTextOutlined />
                    <Text strong>Transcript</Text>
                  </Space>
                  <Paragraph>
                    {activeVideo.transcript || '分析完成后，这里会展示 ASR 转写文本。'}
                  </Paragraph>
                </div>

                <div className="result-block highlight">
                  <Space>
                    <ApiOutlined />
                    <Text strong>Summary</Text>
                  </Space>
                  <Paragraph>
                    {activeVideo.summary || '分析完成后，这里会展示 AI 生成的内容总结。'}
                  </Paragraph>
                </div>
              </Space>
            ) : (
              <Empty description="选择或上传一个视频后查看结果" />
            )}
          </div>
        </section>
    </main>
  );
}

export default App;

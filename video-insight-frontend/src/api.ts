export type VideoStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface VideoInfo {
  id: number;
  title: string;
  sourceUrl: string;
  audioUrl?: string;
  transcript?: string;
  videoStatus: VideoStatus;
  summary?: string;
  createdAt?: string;
  updatedAt?: string;
}

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  const payload = (await response.json()) as ApiResponse<T>;

  if (!response.ok || payload.code !== 200) {
    throw new Error(payload.message || 'Request failed');
  }

  return payload.data;
}

export async function listVideos(): Promise<VideoInfo[]> {
  return request<VideoInfo[]>('/api/videos');
}

export async function getVideo(id: number): Promise<VideoInfo> {
  return request<VideoInfo>(`/api/videos/${id}`);
}

export async function uploadVideo(file: File, title?: string): Promise<VideoInfo> {
  const formData = new FormData();
  formData.append('file', file);
  if (title?.trim()) {
    formData.append('title', title.trim());
  }

  return request<VideoInfo>('/api/videos/upload', {
    method: 'POST',
    body: formData,
  });
}

export async function createVideoByUrl(title: string, sourceUrl: string): Promise<VideoInfo> {
  return request<VideoInfo>('/api/videos', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      title,
      sourceUrl,
    }),
  });
}

export async function analyzeVideo(id: number): Promise<VideoInfo> {
  return request<VideoInfo>(`/api/videos/${id}/analyze`, {
    method: 'POST',
  });
}

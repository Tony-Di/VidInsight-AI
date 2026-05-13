export type VideoStatus =
  | 'UPLOADING'
  | 'IMPORTING'
  | 'IMPORT_FAILED'
  | 'PENDING'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'FAILED';

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

interface ChunkUploadInitResponse {
  uploadId: string;
}

interface ChunkUploadResponse {
  uploadId: string;
  uploadedChunks: number;
  totalChunks: number;
}

const CHUNK_SIZE = 5 * 1024 * 1024; // 5 MB per chunk

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  const text = await response.text();
  const payload = text ? (JSON.parse(text) as ApiResponse<T>) : null;

  if (!response.ok || !payload || payload.code !== 200) {
    throw new Error(payload?.message || `Request failed: ${response.status}`);
  }

  return payload.data;
}

async function initChunkUpload(
  title: string,
  fileName: string,
  totalChunks: number,
): Promise<ChunkUploadInitResponse> {
  return request<ChunkUploadInitResponse>('/api/videos/chunks/init', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title, fileName, totalChunks }),
  });
}

async function uploadChunk(
  uploadId: string,
  chunkIndex: number,
  chunk: Blob,
): Promise<ChunkUploadResponse> {
  const formData = new FormData();
  formData.append('file', chunk);
  return request<ChunkUploadResponse>(
    `/api/videos/chunks/upload?uploadId=${encodeURIComponent(uploadId)}&chunkIndex=${chunkIndex}`,
    { method: 'POST', body: formData },
  );
}

async function completeChunkUpload(uploadId: string): Promise<VideoInfo> {
  return request<VideoInfo>(
    `/api/videos/chunks/complete?uploadId=${encodeURIComponent(uploadId)}`,
    { method: 'POST' },
  );
}

export async function uploadVideoChunked(
  file: File,
  title: string,
  onProgress?: (pct: number) => void,
): Promise<VideoInfo> {
  const totalChunks = Math.max(1, Math.ceil(file.size / CHUNK_SIZE));
  const { uploadId } = await initChunkUpload(title || file.name, file.name, totalChunks);

  for (let i = 0; i < totalChunks; i++) {
    const start = i * CHUNK_SIZE;
    const chunk = file.slice(start, Math.min(start + CHUNK_SIZE, file.size));
    await uploadChunk(uploadId, i, chunk);
    // reserve last 5% for the merge step
    onProgress?.(Math.round(((i + 1) / totalChunks) * 95));
  }

  const videoInfo = await completeChunkUpload(uploadId);
  onProgress?.(100);
  return videoInfo;
}

export interface PageResult<T> {
  total: number;
  page: number;
  pageSize: number;
  records: T[];
}

export async function listVideos(page = 1, pageSize = 10): Promise<PageResult<VideoInfo>> {
  return request<PageResult<VideoInfo>>(`/api/videos?page=${page}&pageSize=${pageSize}`);
}

export async function getVideo(id: number): Promise<VideoInfo> {
  return request<VideoInfo>(`/api/videos/${id}`);
}

export async function createVideoByUrl(title: string, sourceUrl: string): Promise<VideoInfo> {
  return request<VideoInfo>('/api/videos', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title, sourceUrl }),
  });
}

export async function importVideoByUrl(title: string, sourceUrl: string): Promise<VideoInfo> {
  return request<VideoInfo>('/api/videos/import-url', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title, sourceUrl }),
  });
}

export async function analyzeVideo(id: number): Promise<VideoInfo> {
  return request<VideoInfo>(`/api/videos/${id}/analyze`, {
    method: 'POST',
  });
}

export async function deleteVideo(id: number): Promise<void> {
  await request<void>(`/api/videos/${id}`, { method: 'DELETE' });
}

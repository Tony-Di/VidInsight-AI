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
  /** Browser-playable URL (presigned for S3/MinIO, same as sourceUrl for local storage). */
  playUrl?: string;
  audioUrl?: string;
  /** Browser-playable audio URL (presigned for S3/MinIO, same as audioUrl for local storage). */
  audioPlayUrl?: string;
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

export interface UserProfile {
  id: number;
  email: string;
  displayName: string;
}

export interface AuthResponse {
  token: string;
  expiresInSeconds: number;
  user: UserProfile;
}

const CHUNK_SIZE = 5 * 1024 * 1024; // 5 MB per chunk
const TOKEN_KEY = 'vi-token';
const USER_KEY = 'vi-user';

export function getStoredToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function getStoredUser(): UserProfile | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as UserProfile;
  } catch {
    return null;
  }
}

export function saveAuth(res: AuthResponse): void {
  localStorage.setItem(TOKEN_KEY, res.token);
  localStorage.setItem(USER_KEY, JSON.stringify(res.user));
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

/**
 * 当 token 过期 / 失效时由 request() dispatch,App.tsx 监听后切回登录页。
 * 用全局事件而不是 React state 因为 fetch 调用脱离了组件树,没法直接 setState。
 */
export const AUTH_REQUIRED_EVENT = 'vi-auth-required';

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const token = getStoredToken();
  const headers = new Headers(init?.headers);
  if (token) headers.set('Authorization', `Bearer ${token}`);

  const response = await fetch(url, { ...init, headers });

  // HTTP 401 = Spring Security filter chain 拒绝 (token 过期 / 无效 / 缺失受保护接口)。
  // 登录接口的"密码错"走的是 HTTP 200 + body code=401,不会进这里,所以这里清 token 是安全的。
  if (response.status === 401) {
    clearAuth();
    window.dispatchEvent(new CustomEvent(AUTH_REQUIRED_EVENT));
    throw new Error('session expired, please login again');
  }

  const text = await response.text();
  const payload = text ? (JSON.parse(text) as ApiResponse<T>) : null;

  if (!response.ok || !payload || payload.code !== 200) {
    throw new Error(payload?.message || `Request failed: ${response.status}`);
  }

  return payload.data;
}

export async function loginApi(email: string, password: string): Promise<AuthResponse> {
  return request<AuthResponse>('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
}

export async function registerApi(
  email: string,
  password: string,
  displayName?: string,
): Promise<AuthResponse> {
  return request<AuthResponse>('/api/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password, displayName }),
  });
}

export async function getCurrentUserApi(): Promise<UserProfile> {
  return request<UserProfile>('/api/auth/me');
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

export async function retryImport(id: number): Promise<VideoInfo> {
  return request<VideoInfo>(`/api/videos/${id}/retry-import`, {
    method: 'POST',
  });
}

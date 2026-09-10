import { getToken, setToken } from './auth.js';
import { getLocale, t } from './i18n.js';

export const API_BASE = '/api';
export class ApiError extends Error {
  constructor(message, status = 0) { super(message); this.name = 'ApiError'; this.status = status; }
}

// Each request owns its timeout; callers can also cancel superseded work.
export async function apiFetch(path, options = {}) {
  const { timeoutMs = 15000, signal, ...request } = options;
  if (path.startsWith('/auth/') && typeof request.body === 'string') {
    const payload = JSON.parse(request.body);
    if (['password', 'newPassword'].some(key => typeof payload[key] === 'string' && payload[key].length > 128)) throw new ApiError(t('密码不能超过 128 个字符'));
  }
  const controller = new AbortController();
  const abort = () => controller.abort(signal?.reason);
  if (signal?.aborted) abort();
  else signal?.addEventListener('abort', abort, { once: true });
  const timer = setTimeout(() => controller.abort(new DOMException('Timed out', 'TimeoutError')), timeoutMs);
  const headers = new Headers(request.headers);
  if (!(request.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  headers.set('Accept-Language', getLocale());
  const token = getToken();
  if (token) headers.set('Authorization', `Bearer ${token}`);
  try {
    const response = await fetch(`${API_BASE}${path}`, { ...request, headers, signal: controller.signal });
    const json = response.headers.get('content-type')?.includes('application/json') ? await response.json() : null;
    if (!response.ok || (json && json.success === false)) {
      if (response.status === 401) setToken('');
      throw new ApiError(json?.message || t('请求失败'), response.status);
    }
    return json?.data ?? null;
  } catch (error) {
    if (signal?.aborted) throw error;
    if (controller.signal.aborted) throw new ApiError(t('请求超时，请重试'));
    if (error instanceof TypeError) throw new ApiError(t('无法连接服务器，请检查网络'));
    throw error;
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener('abort', abort);
  }
}

export async function uploadFile(path, file, options = {}) {
  if (!getToken()) throw new ApiError(t('请先登录'), 401);
  const limit = path.includes('avatar') ? 5 : 10;
  if (!['image/png', 'image/jpeg', 'image/gif'].includes(file.type)) throw new ApiError(t('请选择 PNG、JPEG 或 GIF 图片'));
  if (file.size > limit * 1024 * 1024) throw new ApiError(t('图片大小不能超过 {size} MB', { size: limit }));
  const body = new FormData(); body.append('file', file);
  return apiFetch(path, { ...options, method: 'POST', body, timeoutMs: 30000 });
}

// Private chat media is fetched with the session header, then displayed locally.
export async function fetchChatMedia(url, { signal } = {}) {
  if (!/^\/api\/chat-media\/[a-f0-9]{32}$/.test(String(url))) throw new ApiError(t('图片地址无效'));
  const controller = new AbortController(), abort = () => controller.abort();
  if (signal?.aborted) abort(); else signal?.addEventListener('abort', abort, { once: true });
  const timer = setTimeout(abort, 30000);
  try {
    const response = await fetch(url, { headers: { Authorization: `Bearer ${getToken()}`, 'Accept-Language': getLocale() }, signal: controller.signal });
    if (!response.ok) throw new ApiError(t('无法加载图片'), response.status);
    const blob = await response.blob();
    if (!blob.type.startsWith('image/')) throw new ApiError(t('图片格式无效'));
    return URL.createObjectURL(blob);
  } finally { clearTimeout(timer); signal?.removeEventListener('abort', abort); }
}

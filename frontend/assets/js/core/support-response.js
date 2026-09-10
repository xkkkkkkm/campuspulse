import { t } from './i18n.js';

export function supportSourceLabel(source) {
  if (source === 'HUMAN' || source === 'HUMAN_SUPPORT') return t('人工客服');
  if (source === 'LOCAL_KNOWLEDGE') return t('帮助中心');
  if (source === 'LANGGRAPH_RETRIEVAL') return t('帮助文档');
  return t('智能客服');
}

// Never turn arbitrary URI schemes, credentials, or protocol-relative URLs
// from a response into clickable page content.
export function safeCitationUrl(value, origin = globalThis.location?.origin || 'http://localhost') {
  if (typeof value !== 'string' || !value.trim() || /[\u0000-\u0020\u007f\\]/.test(value) || value.startsWith('//')) return '';
  try {
    if (!/^https?:\/\//i.test(value) && !/^\/(?!\/)/.test(value)) return '';
    const url = new URL(value, origin);
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) return '';
    return url.href;
  } catch { return ''; }
}

export function supportCitations(citations) {
  if (!Array.isArray(citations)) return [];
  return citations.slice(0, 8).flatMap(citation => {
    const url = safeCitationUrl(citation?.url);
    return url && typeof citation?.title === 'string' && citation.title.trim() ? [{ title: citation.title, url }] : [];
  });
}

import { getLocale, t } from './i18n.js';

// These are platform taxonomy values, never arbitrary user-authored prose.
const standardLabels = new Set(['运动健身', '学术讲座', '文艺演出', '志愿服务', '竞赛组队', '兼职实习', '兴趣社交', '考研搭子', '校园生活', '其他', '天赐庄校区', '独墅湖校区', '阳澄湖校区', '未来校区']);
export function taxonomyLabel(value) {
  return standardLabels.has(value) ? t(value) : value;
}

// Read-only display access: the API retains originals for editing and writes.
// Only explicitly supplied, trusted translations can replace content fields.
export function contentField(entity, field) {
  const original = entity?.[field];
  if (getLocale() !== 'en-US') return original;
  const translated = entity?.translations?.en?.[field];
  if (field === 'tags') {
    if (!Array.isArray(original)) return original;
    return original.map((value, index) => Array.isArray(translated) && typeof translated[index] === 'string' && translated[index].trim() ? translated[index] : taxonomyLabel(value));
  }
  if (typeof translated === 'string' && translated.trim()) return translated;
  return field === 'campus' ? taxonomyLabel(original) : original;
}

export function contentView(entity) {
  if (!entity) return entity;
  const view = { ...entity };
  for (const field of ['title', 'description', 'location', 'activityTitle', 'tags', 'campus']) {
    if (field in entity) view[field] = contentField(entity, field);
  }
  return view;
}

export function tagLabel(tag) {
  return typeof tag === 'string' ? taxonomyLabel(tag) : taxonomyLabel(contentField(tag, 'name'));
}

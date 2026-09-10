import test from 'node:test';
import assert from 'node:assert/strict';
import { contentField, contentView, tagLabel, taxonomyLabel } from '../assets/js/core/content-i18n.js';
import { buildActivityCoverUrl, hasCustomActivityCover, teamDisplayTitle } from '../assets/js/core/render.js';
import { safeCitationUrl, supportCitations, supportSourceLabel } from '../assets/js/core/support-response.js';
let locale = 'en-US';
globalThis.localStorage = { getItem: key => key === 'campus_pulse_locale' ? locale : null };

test('trusted display translations never alter originals or untranslated user content', () => {
  const original = { title: '我的讲座', description: '自己写的介绍', tags: ['学术讲座', '自定义标签'], campus: '独墅湖校区', translations: { en: { title: 'Campus talk', tags: ['Academic talks'] } } };
  const saved = JSON.stringify(original);
  const display = contentView(original);
  assert.equal(display.title, 'Campus talk');
  assert.equal(display.description, '自己写的介绍');
  assert.deepEqual(display.tags, ['Academic talks', '自定义标签']);
  assert.equal(display.campus, 'Dushu Lake Campus');
  assert.equal(JSON.stringify(original), saved);
  assert.equal(contentField({ title: '学术讲座' }, 'title'), '学术讲座');
  assert.equal(taxonomyLabel('用户新写的标签'), '用户新写的标签');
  assert.equal(tagLabel({ name: '学术讲座' }), 'Academic Talks');
  locale = 'zh-CN';
  assert.deepEqual(contentView(original), original);
  locale = 'en-US';
});

test('template posters and linked team titles share localized content while image URLs stay intact', () => {
  const activity = { title: '原始标题', location: '原始地点', tags: ['学术讲座'], translations: { en: { title: 'AI Campus Talk', location: 'Dushu Lake Library' } } };
  const svg = decodeURIComponent(buildActivityCoverUrl(activity).split(',').slice(1).join(','));
  assert.match(svg, /AI Campus Talk/); assert.match(svg, /Dushu Lake Library/); assert.doesNotMatch(svg, /[\u3400-\u9fff]/);
  assert.equal(hasCustomActivityCover('/uploads/user-cover.png'), true);
  const team = { title: '学术讲座｜接待小组', activityId: 1, activityTitle: activity.title, translations: { en: { title: 'Academic talks｜Welcome team', activityTitle: 'AI Campus Talk' } } };
  assert.match(teamDisplayTitle(team), /Welcome team/); assert.match(teamDisplayTitle(team), /AI Campus Talk/);
  locale = 'zh-CN';
  assert.match(decodeURIComponent(buildActivityCoverUrl(activity)), /原始标题/);
  locale = 'en-US';
});

test('support citations reject executable, credentialed, malformed and ambiguous links', () => {
  for (const url of ['javascript:alert(1)', 'data:text/html,evil', '//evil.example', 'https://user:pass@example.test', 'https:\\evil.test', '/\n/evil', 'mailto:a@b.test', null, {}]) assert.equal(safeCitationUrl(url), '', String(url));
  assert.equal(safeCitationUrl('/activity-lobby.html', 'https://campus.test'), 'https://campus.test/activity-lobby.html');
  assert.equal(safeCitationUrl('https://docs.example.test/guide'), 'https://docs.example.test/guide');
  assert.deepEqual(supportCitations([{ title: 'Bad', url: 'javascript:alert(1)' }, { title: 'Guide', url: 'https://docs.example.test/guide' }]), [{ title: 'Guide', url: 'https://docs.example.test/guide' }]);
  assert.equal(supportSourceLabel('LANGGRAPH_RETRIEVAL'), 'Help documentation');
  assert.doesNotMatch(supportSourceLabel('LANGGRAPH_LLM'), /LANGGRAPH|DIFY/i);
});

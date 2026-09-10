import test from 'node:test';
import assert from 'node:assert/strict';
import { safePageRef } from '../assets/js/core/navigation.js';
import { apiFetch } from '../assets/js/core/api.js';
import { createPagedLoader } from '../assets/js/core/pagination.js';
import { mergeMessages, messageRequest, refreshReadReceipts } from '../assets/js/core/chat-state.js';
import { reconnectDelay, createRealtimeChannel } from '../assets/js/core/realtime.js';
import { t, ui } from '../assets/js/core/i18n.js';
const storage = new Map();
globalThis.localStorage = { getItem: key => storage.get(key) || null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) };
const json = data => new Response(JSON.stringify({ success: true, data }), { headers: { 'Content-Type': 'application/json' } });

test('navigation rejects dangerous protocols, hosts, paths, and ambiguous separators', () => {
  const origin = 'https://campus.example';
  for (const input of ['javascript:alert(1)', 'data:text/html,hi', '//evil.example/home.html', 'https://evil.example/home.html', '/admin/../private', '/%ZZ', 'home.html\\evil', '\u0000home.html', 'https://user:pass@campus.example/home.html', '/uploads/a.html']) assert.equal(safePageRef(input, 'profile.html', origin), 'profile.html', input);
  assert.equal(safePageRef('/activity-detail.html?id=42&from=home.html', 'profile.html', origin), 'activity-detail.html?id=42&from=home.html');
});

test('locale translates source text without modifying user substitutions', () => {
  storage.set('campus_pulse_locale', 'en-US');
  assert.equal(t('保存'), 'Save');
  assert.equal(ui`<p>查看详情</p><span>${'保存 <user content>'}</span>`, '<p>View details</p><span>保存 <user content></span>');
  storage.set('campus_pulse_locale', 'zh-CN'); assert.equal(t('保存'), '保存');
  storage.set('campus_pulse_locale', 'en-US');
});

test('requests have timeouts, propagate cancellation, and clear invalid authentication', async () => {
  const original = globalThis.fetch;
  try {
    globalThis.fetch = (_, options) => new Promise((resolve, reject) => options.signal.addEventListener('abort', () => reject(options.signal.reason), { once: true }));
    await assert.rejects(apiFetch('/slow', { timeoutMs: 5 }), /timed out/i);
    const controller = new AbortController(); const request = apiFetch('/cancel', { signal: controller.signal }); controller.abort();
    await assert.rejects(request, error => error.name === 'AbortError');
    storage.set('campus_pulse_token', 'old-token');
    globalThis.fetch = async (_, options) => { assert.equal(options.headers.get('Accept-Language'), 'en-US'); return new Response(JSON.stringify({ success: false, message: 'Expired' }), { status: 401, headers: { 'Content-Type': 'application/json' } }); };
    await assert.rejects(apiFetch('/private'), /Expired/); assert.equal(storage.has('campus_pulse_token'), false);
  } finally { globalThis.fetch = original; }
});

test('pagination reaches records after 50 and sends filters to the server', async () => {
  const original = globalThis.fetch; let state; const calls = [];
  globalThis.fetch = async url => { const parsed = new URL(url, 'http://local'); calls.push(parsed); const page = Number(parsed.searchParams.get('page')); const size = Number(parsed.searchParams.get('size')); return json({ items: Array.from({ length: Math.min(size, 63 - (page - 1) * size) }, (_, index) => ({ id: (page - 1) * size + index + 1 })), total: 63, page }); };
  try {
    const loader = createPagedLoader('/activities', { size: 12, onChange: value => { state = value; } });
    await loader.reset({ tag: 'Music', keyword: 'jazz' });
    while (state.hasMore) await loader.more();
    assert.equal(state.items.length, 63); assert.equal(state.items.at(-1).id, 63);
    assert.ok(calls.every(url => url.searchParams.get('keyword') === 'jazz' && url.searchParams.get('tag') === 'Music'));
  } finally { globalThis.fetch = original; }
});

test('stale search response cannot replace a newer result even if transport ignores abort', async () => {
  const original = globalThis.fetch; let finishOld, state;
  globalThis.fetch = url => String(url).includes('keyword=old') ? new Promise(resolve => { finishOld = resolve; }) : Promise.resolve(json({ items: [{ id: 2 }], total: 1, page: 1 }));
  try {
    const loader = createPagedLoader('/activities', { onChange: value => { state = value; } });
    const old = loader.reset({ keyword: 'old' }); await loader.reset({ keyword: 'new' });
    finishOld(json({ items: [{ id: 1 }], total: 1, page: 1 })); await old;
    assert.deepEqual(state.items, [{ id: 2 }]);
  } finally { globalThis.fetch = original; }
});

test('chat merges overlapping history and retains a retry id for the same send', () => {
  assert.deepEqual(mergeMessages([{ id: 52 }, { id: 53 }], [{ id: 1 }, { id: 52 }, { id: 54 }]).map(message => message.id), [1, 52, 53, 54]);
  const pending = messageRequest('hello', null, () => 'first');
  assert.equal(messageRequest('hello', pending, () => 'other'), pending);
  assert.equal(messageRequest('edited', pending, () => 'second').clientMessageId, 'second');
  assert.equal(reconnectDelay(0, () => 0), 1000); assert.equal(reconnectDelay(99, () => 0), 30000);
});

test('SSE reconnect consumes a fresh ticket instead of replaying its original URL', async () => {
  const original = globalThis.fetch, oldSource = globalThis.EventSource; let tickets = 0; const sources = [];
  storage.set('campus_pulse_token', 'token');
  globalThis.fetch = async () => json({ ticket: `ticket-${++tickets}` });
  globalThis.EventSource = class { constructor(url) { this.url = url; sources.push(this); } addEventListener() {} close() { this.closed = true; } };
  let channel;
  try {
    channel = await createRealtimeChannel({ userId: 2, onEvent: async () => {} }); sources[0].onerror();
    await new Promise(resolve => setTimeout(resolve, 1600));
    assert.equal(sources[0].closed, true); assert.equal(tickets, 2); assert.match(sources[1].url, /ticket-2/);
  } finally { channel?.close(); globalThis.fetch = original; globalThis.EventSource = oldSource; storage.delete('campus_pulse_token'); }
});

test('read receipt snapshot cannot skip messages committed between catchup and snapshot', () => {
  const delivered = Array.from({ length: 100 }, (_, index) => ({ id: index + 1, readCount: 0 }));
  const receiptPage = Array.from({ length: 50 }, (_, index) => ({ id: index + 111, readCount: 1 }));
  const updated = refreshReadReceipts(delivered, receiptPage);
  assert.equal(updated.length, 100); assert.equal(updated.at(-1).id, 100);
  const caughtUp = mergeMessages(updated, Array.from({ length: 60 }, (_, index) => ({ id: index + 101 })));
  assert.equal(caughtUp.length, 160); assert.equal(caughtUp[100].id, 101);
  assert.equal(refreshReadReceipts(delivered, [{ id: 100, readCount: 2 }]).at(-1).readCount, 2);
});

test('online events coalesce pending tickets and stale source errors cannot close the replacement', async () => {
  const originals = { fetch: globalThis.fetch, EventSource: globalThis.EventSource, addEventListener: globalThis.addEventListener, removeEventListener: globalThis.removeEventListener };
  const listeners = new Map(), sources = []; let resolveTicket, requests = 0, channel;
  storage.set('campus_pulse_token', 'token');
  globalThis.addEventListener = (name, callback) => listeners.set(name, callback);
  globalThis.removeEventListener = name => listeners.delete(name);
  globalThis.fetch = async () => { requests++; return requests === 1 ? new Promise(resolve => { resolveTicket = resolve; }) : json({ ticket: 'replacement' }); };
  globalThis.EventSource = class { constructor(url) { this.url = url; sources.push(this); } addEventListener() {} close() { this.closed = true; } };
  try {
    const pending = createRealtimeChannel({ userId: 2, onEvent: async () => {} });
    listeners.get('online')(); listeners.get('online')();
    assert.equal(requests, 1);
    resolveTicket(json({ ticket: 'initial' })); channel = await pending;
    assert.equal(sources.length, 1);
    listeners.get('online')(); await new Promise(resolve => setTimeout(resolve, 0));
    assert.equal(sources.length, 2); assert.equal(requests, 2);
    sources[0].onerror(); assert.equal(sources[1].closed, undefined);
    channel.close(); assert.ok(sources.every(source => source.closed));
  } finally { channel?.close(); Object.assign(globalThis, originals); storage.delete('campus_pulse_token'); }
});

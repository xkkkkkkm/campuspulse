import { test, expect } from '@playwright/test';
const ok = data => ({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data }) });
const errors = new WeakMap();
test.beforeEach(async ({ page }) => { errors.set(page, []); page.on('pageerror', error => errors.get(page).push(error.message)); });
test.afterEach(async ({ page }) => { expect(errors.get(page)).toEqual([]); });
const student = { id: 1, nickname: 'Test Student', role: 'USER', interests: [] };
async function api(page, handler = () => undefined) {
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    const result = await handler(url, route.request());
    if (result !== undefined) return route.fulfill(ok(result));
    if (url.pathname === '/api/auth/me') return route.fulfill(ok(student));
    if (url.pathname === '/api/tags') return route.fulfill(ok([{ id: 1, name: 'Music' }]));
    return route.fulfill(ok([]));
  });
}

test('English entry, persistent locale, and Chinese text in user content', async ({ page }) => {
  await page.goto('/index.html');
  await expect(page.locator('html')).toHaveAttribute('lang', 'en-US');
  await expect(page.locator('.auth-card-title')).toHaveText('Get started');
  await page.locator('#languageSwitcher [data-locale="zh-CN"]').click();
  await expect(page.locator('.auth-card-title')).toHaveText('开始使用');
  await page.reload(); await expect(page.locator('html')).toHaveAttribute('lang', 'zh-CN');
});

test('server pagination reaches item 63 and search filters all records', async ({ page }) => {
  const requests = [];
  await api(page, url => {
    if (url.pathname !== '/api/activities') return;
    requests.push(url);
    const all = Array.from({ length: 63 }, (_, i) => ({ id: i + 1, title: `活动用户内容 ${i + 1}`, location: 'Campus', startTime: '2030-10-01T10:00:00', participants: 0, maxParticipants: 20, tags: ['Music'] }));
    const keyword = url.searchParams.get('keyword'); const selected = keyword ? all.filter(item => item.title.endsWith(keyword)) : all;
    const pageNum = Number(url.searchParams.get('page')), size = Number(url.searchParams.get('size'));
    return { items: selected.slice((pageNum - 1) * size, pageNum * size), total: selected.length, page: pageNum };
  });
  await page.goto('/activity-lobby.html');
  await expect(page.locator('.activity-card')).toHaveCount(12);
  for (let i = 0; i < 5; i++) { await page.locator('#activityLoadMoreBtn').click(); await expect(page.locator('.activity-card')).toHaveCount(Math.min(63, 24 + i * 12)); }
  await expect(page.getByText('活动用户内容 63', { exact: true })).toBeVisible();
  await page.locator('#activityLobbySearchInput').fill('63');
  await expect(page.locator('.activity-card')).toHaveCount(1);
  expect(requests.at(-1).searchParams.get('keyword')).toBe('63');
});

test('avatar file stays local until registration returns its token', async ({ page }) => {
  const calls = [];
  await api(page, (url, request) => {
    calls.push({ path: url.pathname, authorization: request.headers().authorization });
    if (url.pathname === '/api/auth/register-email') return { token: 'registered-token', user: student };
    if (url.pathname === '/api/upload/avatar') return { url: '/uploads/owned-avatar.png' };
    if (url.pathname === '/api/activities') return { items: [], total: 0 };
  });
  await page.goto('/register.html');
  await page.locator('#avatarFile').setInputFiles({ name: 'avatar.png', mimeType: 'image/png', buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l1sAAAAASUVORK5CYII=', 'base64') });
  await expect(page.locator('#avatarUploadHint')).toContainText('after registration');
  expect(calls.some(call => call.path === '/api/upload/avatar')).toBe(false);
  await page.locator('#regNickname').fill('Student'); await page.locator('#regUsername').fill('test12345'); await page.locator('#regEmail').fill('test@example.test'); await page.locator('#regEmailCode').fill('123456'); await page.locator('#regPassword').fill('Strong!123'); await page.locator('#regPassword2').fill('Strong!123');
  await page.locator('#registerBtn').click(); await expect(page).toHaveURL(/home\.html/);
  const upload = calls.find(call => call.path === '/api/upload/avatar'); expect(upload.authorization).toBe('Bearer registered-token');
  expect(calls.findIndex(call => call.path === '/api/auth/register-email')).toBeLessThan(calls.findIndex(call => call.path === '/api/upload/avatar'));
});

test('chat starts at latest 50, loads older history, drains catchup, and retries idempotently', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('campus_pulse_token', 'test-token'));
  let total = 125; const sends = []; let failedOnce = false;
  await api(page, url => {
    if (url.pathname === '/api/users/2/brief') return { id: 2, nickname: 'Peer' };
    if (url.pathname === '/api/realtime/chat/ticket') return { ticket: 'test-ticket' };
    if (url.pathname === '/api/dm/2/messages') {
      const after = url.searchParams.get('afterId'), before = url.searchParams.get('beforeId');
      const messages = Array.from({ length: total }, (_, i) => ({ id: i + 1, senderId: 2, content: `Message ${i + 1}`, createdAt: '2030-01-01T12:00:00' }));
      return before ? messages.filter(item => item.id < Number(before)).slice(-50) : after !== null ? messages.filter(item => item.id > Number(after)).slice(0, 50) : messages.slice(-50);
    }
  });
  await page.route('**/api/dm/2/messages', async route => {
    if (route.request().method() !== 'POST') return route.fallback();
    sends.push(route.request().postDataJSON());
    if (!failedOnce) { failedOnce = true; return route.abort('failed'); }
    return route.fulfill(ok({ id: 246 }));
  });
  await page.goto('/chat.html?userId=2');
  await expect(page.locator('.chat-message')).toHaveCount(50);
  await expect(page.getByText('Message 125', { exact: true })).toBeVisible();
  await page.locator('#chatHistoryBtn').click(); await expect(page.locator('.chat-message')).toHaveCount(100);
  await page.locator('#chatHistoryBtn').click(); await expect(page.locator('.chat-message')).toHaveCount(125);
  total = 245; await page.evaluate(() => window.dispatchEvent(new Event('online')));
  await expect(page.locator('.chat-message')).toHaveCount(245);
  expect(await page.locator('.chat-message').count()).toBe(245);
  await page.locator('#chatInput').fill('Retry this message');
  await page.locator('#chatSendBtn').click();
  await expect(page.locator('#appModal')).toBeVisible();
  await page.locator('#appModalOk').click();
  await page.locator('#chatSendBtn').click();
  await expect(page.locator('#chatInput')).toHaveValue('');
  expect(sends).toHaveLength(2);
  expect(sends[0].clientMessageId).toBe(sends[1].clientMessageId);
});

test('dangerous back link is replaced and error modal supports keyboard dismissal', async ({ page }) => {
  await page.goto('/customer-service.html?from=javascript:alert(1)');
  await expect(page.locator('#supportBackLink')).toHaveAttribute('href', 'home.html');
  await page.goto('/register.html'); await page.locator('#registerBtn').click();
  await expect(page.locator('#appModal')).toBeVisible(); await expect(page.locator('#appModal')).toHaveAttribute('role', 'dialog');
  await page.keyboard.press('Escape'); await expect(page.locator('#appModal')).toBeHidden();
});

test('private image upload retries the same message and renders through an authenticated blob', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('campus_pulse_token', 'private-token'));
  const mediaUrl = '/api/chat-media/0123456789abcdef0123456789abcdef';
  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l1sAAAAASUVORK5CYII=', 'base64');
  let uploads = 0, sent = false; const attempts = []; let mediaAuthorization;
  await api(page, (url, request) => {
    if (url.pathname === '/api/users/2/brief') return { id: 2, nickname: 'Peer' };
    if (url.pathname === '/api/realtime/chat/ticket') return { ticket: 'test-ticket' };
    if (url.pathname === '/api/upload/chat') { uploads++; expect(request.headers().authorization).toBe('Bearer private-token'); return { url: mediaUrl }; }
    if (url.pathname === '/api/dm/2/messages') return sent && !url.searchParams.has('afterId') || sent && url.searchParams.get('afterId') === '0' ? [{ id: 1, senderId: 1, contentType: 'IMAGE', imageUrl: mediaUrl, content: '', createdAt: '2030-01-01T12:00:00' }] : [];
  });
  await page.route('**/api/dm/2/messages', async route => {
    if (route.request().method() !== 'POST') return route.fallback();
    attempts.push(route.request().postDataJSON());
    if (attempts.length === 1) return route.abort('failed');
    sent = true; return route.fulfill(ok({ id: 1 }));
  });
  await page.route(`**${mediaUrl}`, async route => { mediaAuthorization = route.request().headers().authorization; await route.fulfill({ status: 200, contentType: 'image/png', body: png }); });
  await page.goto('/chat.html?userId=2');
  await page.locator('#chatImageFile').setInputFiles({ name: 'image.png', mimeType: 'image/png', buffer: png });
  await page.locator('[data-image-send]').click();
  await expect(page.locator('[data-image-error]')).not.toHaveText('');
  await page.locator('[data-image-send]').click();
  await expect(page.locator('.chat-media-image')).toHaveAttribute('src', /^blob:/);
  expect(attempts).toHaveLength(2); expect(attempts[0].clientMessageId).toBe(attempts[1].clientMessageId); expect(uploads).toBe(1);
  expect(mediaAuthorization).toBe('Bearer private-token');
});

test('saved records paginate beyond the first server page', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('campus_pulse_token', 'test-token'));
  await api(page, url => {
    if (url.pathname !== '/api/profile/favorites') return;
    const pageNum = Number(url.searchParams.get('page')), size = Number(url.searchParams.get('size'));
    return Array.from({ length: 61 }, (_, i) => ({ id: i + 1, title: `Saved ${i + 1}` })).slice((pageNum - 1) * size, pageNum * size);
  });
  await page.goto('/profile-records.html?kind=favorites');
  await expect(page.locator('#profileRecordsList .menu-item')).toHaveCount(20);
  for (let i = 0; i < 3; i++) await page.locator('.array-pager').getByRole('button', { name: 'Next' }).click();
  await expect(page.getByText('Saved 61', { exact: true })).toBeVisible();
  await expect(page.locator('.array-pager').getByRole('button', { name: 'Next' })).toBeDisabled();
  await page.locator('.array-pager').getByRole('button', { name: 'Previous' }).click();
  await expect(page.getByText('Saved 41', { exact: true })).toBeVisible();
});

test('team captain transfer and member leave use the supported lifecycle routes', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('campus_pulse_token', 'test-token'));
  const actions = [];
  const team = { id: 1, title: 'Study group', description: 'Study together', status: 'OPEN', creatorId: 1, creatorName: 'Captain', maxMembers: 5, members: [{ userId: 1, nickname: 'Captain', role: 'CREATOR' }, { userId: 2, nickname: 'Next captain', role: 'MEMBER' }] };
  await api(page, (url, request) => {
    if (url.pathname === '/api/teams/1') return team;
    if (url.pathname === '/api/teams/1/transfer') { actions.push(request.postDataJSON()); team.creatorId = 2; return {}; }
    if (url.pathname === '/api/teams/1/leave') { actions.push('leave'); team.members = team.members.filter(item => item.userId !== 1); return {}; }
  });
  await page.goto('/team-success.html?teamId=1');
  await page.locator('[data-action="transfer"]').click(); await page.locator('#newCaptain').selectOption('2'); await page.locator('[data-confirm]').click();
  await expect(page.locator('[data-action="leave"]')).toBeVisible();
  expect(actions[0]).toEqual({ userId: 2 });
  await page.locator('[data-action="leave"]').click(); await page.locator('[data-confirm]').click();
  await expect(page.locator('[data-action="leave"]')).toHaveCount(0);
  expect(actions).toContain('leave');
});

test('English publishing templates keep canonical server tag values', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('campus_pulse_token', 'test-token'));
  await api(page, url => url.pathname === '/api/tags' ? [{ id: 1, name: '学术讲座' }, { id: 2, name: '竞赛组队' }] : undefined);
  await page.goto('/publish-activity.html');
  await page.locator('#publishTemplates').getByText('Academic Talks', { exact: true }).click();
  await expect(page.locator('#pubTitle')).toHaveValue('Academic talk | Topic discussion');
  await expect(page.locator('#pubTags')).toHaveValue('学术讲座');
  await page.locator('#publishTypeTeam').click();
  await page.locator('#publishTemplates').getByText('Competition Teams', { exact: true }).click();
  await expect(page.locator('#pubTags')).toHaveValue('竞赛组队');
});

test('a receipt snapshot cannot move the catchup cursor beyond unseen messages', async ({ page }) => {
  await page.clock.install();
  await page.addInitScript(() => localStorage.setItem('campus_pulse_token', 'test-token'));
  let raceArmed = false, emptyAfterReturned = false, receiptSeen = false;
  const message = id => ({ id, senderId: 2, content: `Race ${id}`, createdAt: '2030-01-01T12:00:00', readCount: 0 });
  await api(page, url => {
    if (url.pathname === '/api/users/2/brief') return { id: 2, nickname: 'Peer' };
    if (url.pathname === '/api/realtime/chat/ticket') return { ticket: 'ticket' };
    if (url.pathname !== '/api/dm/2/messages') return;
    const after = url.searchParams.get('afterId'), before = url.searchParams.get('beforeId');
    if (before) return Array.from({ length: 50 }, (_, i) => message(i + 1));
    if (after !== null) {
      if (raceArmed && !emptyAfterReturned) { emptyAfterReturned = true; return []; }
      if (!receiptSeen) return [];
      return Array.from({ length: 160 }, (_, i) => message(i + 1)).filter(item => item.id > Number(after)).slice(0, 50);
    }
    if (raceArmed && emptyAfterReturned) { receiptSeen = true; return Array.from({ length: 50 }, (_, i) => message(i + 111)); }
    return Array.from({ length: 50 }, (_, i) => message(i + 51));
  });
  await page.goto('/chat.html?userId=2');
  await expect(page.locator('.chat-message')).toHaveCount(50);
  await page.locator('#chatHistoryBtn').click(); await expect(page.locator('.chat-message')).toHaveCount(100);
  raceArmed = true;
  await page.clock.setFixedTime(new Date(Date.now() + 20000));
  await page.evaluate(() => window.dispatchEvent(new Event('online')));
  await expect.poll(() => receiptSeen).toBe(true);
  await expect(page.locator('.chat-message')).toHaveCount(100);
  await page.evaluate(() => window.dispatchEvent(new Event('online')));
  await expect(page.locator('.chat-message')).toHaveCount(160);
  await expect(page.getByText('Race 101', { exact: true })).toBeAttached();
});

test('a new draft survives completion of an earlier send and a restored-page reload', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('campus_pulse_token', 'test-token'));
  let releaseSend, posted;
  await api(page, url => {
    if (url.pathname === '/api/users/2/brief') return { id: 2, nickname: 'Peer' };
    if (url.pathname === '/api/realtime/chat/ticket') return { ticket: 'ticket' };
  });
  await page.route('**/api/dm/2/messages', async route => {
    if (route.request().method() !== 'POST') return route.fallback();
    posted = route.request().postDataJSON();
    await new Promise(resolve => { releaseSend = resolve; });
    return route.fulfill(ok({ id: 1 }));
  });
  await page.goto('/chat.html?userId=2');
  await page.locator('#chatInput').fill('first message'); await page.locator('#chatSendBtn').click();
  await expect.poll(() => Boolean(releaseSend)).toBe(true);
  await page.locator('#chatInput').fill('next unsent message'); releaseSend();
  await expect(page.locator('#chatSendBtn')).toBeEnabled();
  await expect(page.locator('#chatInput')).toHaveValue('next unsent message'); expect(posted.content).toBe('first message');
  const reloaded = page.waitForEvent('framenavigated');
  await page.evaluate(() => window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true })));
  await reloaded;
  await expect(page.locator('#chatInput')).toHaveValue('next unsent message');
});

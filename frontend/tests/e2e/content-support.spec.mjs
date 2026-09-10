import { test, expect } from '@playwright/test';
const ok = data => ({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data }) });
const activity = { id: 41, title: '原始活动标题', description: '原始介绍', location: '原始活动地点', organizerId: 1, organizerName: 'User name', startTime: '2030-10-01T10:00:00', endTime: '2030-10-01T12:00:00', participants: 0, maxParticipants: 20, tags: ['学术讲座'], translations: { en: { title: 'AI Campus Talk', description: 'Meet students and explore AI.', location: 'Dushu Lake Library', tags: ['Academic Talks'] } } };
const team = { id: 51, title: '学术讲座｜原始队伍', description: '原始队伍说明', activityId: 41, activityTitle: activity.title, creatorId: 2, creatorName: 'Peer', members: 1, maxMembers: 5, translations: { en: { title: 'Academic Talks | Welcome team', description: 'Help welcome students.', activityTitle: 'AI Campus Talk' } } };
const student = { id: 1, nickname: 'Test Student', role: 'USER', interests: ['学术讲座'], campus: '独墅湖校区' };
const errors = new WeakMap();
test.beforeEach(async ({ page }) => { errors.set(page, []); page.on('pageerror', error => errors.get(page).push(error.message)); });
test.afterEach(async ({ page }) => { expect(errors.get(page)).toEqual([]); });
async function mockApi(page, handler = () => undefined, loggedIn = true) {
  if (loggedIn) await page.addInitScript(() => localStorage.setItem('campus_pulse_token', 'test-token'));
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url()); const custom = await handler(url, route.request());
    if (custom !== undefined) return route.fulfill(ok(custom));
    if (url.pathname === '/api/auth/me') return route.fulfill(ok(student));
    if (url.pathname === '/api/tags') return route.fulfill(ok([{ id: 1, name: '学术讲座', translations: { en: { name: 'Academic Talks' } } }]));
    if (url.pathname === '/api/activities/41') return route.fulfill(ok(activity));
    if (url.pathname === '/api/teams/51') return route.fulfill(ok({ ...team, members: [] }));
    if (url.pathname === '/api/activities') return route.fulfill(ok({ items: [activity], total: 1 }));
    if (url.pathname === '/api/teams/page') return route.fulfill(ok({ items: [team], total: 1 }));
    return route.fulfill(ok([]));
  });
}

test('English demo cards, poster and canonical filters switch back to original Chinese', async ({ page }) => {
  const filters = [];
  await mockApi(page, url => { if (url.pathname === '/api/activities') filters.push(url.searchParams.get('tag')); });
  await page.goto('/activity-lobby.html');
  await expect(page.locator('.activity-card h3')).toHaveText('AI Campus Talk');
  await expect(page.locator('.activity-card')).toContainText('Dushu Lake Library');
  const poster = decodeURIComponent(await page.locator('.activity-card img').getAttribute('src'));
  expect(poster).toContain('AI Campus Talk'); expect(poster).toContain('Dushu Lake Library'); expect(poster).not.toMatch(/[\u3400-\u9fff]/);
  await page.screenshot({ path: 'test-results/english-content.png', fullPage: true });
  await page.getByRole('button', { name: 'Academic Talks' }).click();
  await expect.poll(() => filters.at(-1)).toBe('学术讲座');
  await page.locator('#languageSwitcher [data-locale="zh-CN"]').click();
  await expect(page.locator('.activity-card h3')).toHaveText(activity.title);
  await expect(page.locator('.activity-card')).toContainText(activity.location);
  await page.reload(); await expect(page.locator('.activity-card h3')).toHaveText(activity.title);
});

test('detail and personal references use translations; edit and save keep the source text', async ({ page }) => {
  let saved;
  await mockApi(page, (url, request) => {
    if (url.pathname === '/api/profile/favorites') return [activity];
    if (url.pathname === '/api/activities/41' && request.method() === 'PUT') { saved = request.postDataJSON(); return activity; }
    if (url.pathname === '/api/activities/41/registrations/stats') return { approved: 0, pending: 0, rejected: 0 };
  });
  await page.goto('/activity-detail.html?id=41');
  await expect(page.locator('#activityTitle')).toHaveText('AI Campus Talk');
  await expect(page.locator('#activityDesc')).toHaveText('Meet students and explore AI.');
  await expect(page.locator('#activityLoc')).toHaveText('Dushu Lake Library');
  await page.goto('/profile-records.html?kind=favorites');
  await expect(page.locator('#profileRecordsList')).toContainText('AI Campus Talk');
  await page.goto('/activity-manage.html?id=41');
  await expect(page.locator('#activityManageTitle')).toHaveText('AI Campus Talk');
  await expect(page.locator('#activityEditTitle')).toHaveValue(activity.title);
  await expect(page.locator('#activityEditLocation')).toHaveValue(activity.location);
  await expect(page.locator('#activityEditDesc')).toHaveValue(activity.description);
  await page.locator('#activityEditSaveBtn').click();
  await expect.poll(() => saved?.title).toBe(activity.title);
  expect(saved.location).toBe(activity.location); expect(saved.description).toBe(activity.description); expect(saved.tags).toEqual(activity.tags);
});

test('team cards and details use translated type, description and activity reference', async ({ page }) => {
  await mockApi(page);
  await page.goto('/team-lobby.html');
  await expect(page.locator('.home-team-title')).toContainText('Welcome team');
  await expect(page.locator('.home-team-title')).toContainText('AI Campus Talk');
  await expect(page.locator('.home-team-desc')).toHaveText('Help welcome students.');
  await expect(page.locator('.home-team-top .badge-pill')).toHaveText('Academic Talks');
  await page.evaluate(async () => { const { openTeamDetailModal } = await import('/assets/js/core/render.js'); await openTeamDetailModal({ id: 51 }, { id: 1 }); });
  await expect(page.locator('#utilityModalBody')).toContainText('Welcome team');
  await expect(page.locator('#utilityModalBody')).toContainText('Help welcome students.');
  await expect(page.locator('#utilityModalBody')).not.toContainText('原始队伍');
});

test('support carries context, safely cites help and waits for an explicit human request', async ({ page }) => {
  const chats = [], escalations = [], deleted = [];
  await mockApi(page, (url, request) => {
    if (url.pathname.startsWith('/api/support/conversations/') && request.method() === 'DELETE') { deleted.push(url.pathname); return {}; }
    if (url.pathname === '/api/support/chat') {
      chats.push({ ...request.postDataJSON(), locale: request.headers()['accept-language'] });
      return { answer: 'Here is the answer <script>bad()</script>', conversationId: 'conversation-1', source: 'LANGGRAPH_RETRIEVAL', suggestEscalation: true, escalated: false, citations: [{ id: 'help', title: 'Registration guide', url: '/register.html' }, { id: 'bad', title: 'Unsafe', url: 'javascript:alert(1)' }] };
    }
    if (url.pathname === '/api/support/escalate') { escalations.push(request.postDataJSON()); return { answer: 'Your request has been submitted.', conversationId: 'conversation-1', source: 'HUMAN', escalated: true, ticketId: 8 }; }
  });
  await page.goto('/customer-service.html');
  await page.locator('#supportQuickList [data-question]').first().click();
  await expect(page.locator('.support-citations a')).toHaveCount(1);
  expect(chats[0].message).not.toMatch(/[\u3400-\u9fff]/); expect(chats[0].locale).toBe('en-US');
  await expect(page.locator('.support-message-text').last()).toContainText('<script>bad()</script>');
  await expect(page.getByRole('link', { name: 'Registration guide' })).toHaveAttribute('rel', 'noopener noreferrer');
  expect(escalations).toHaveLength(0);
  await page.locator('#supportInput').fill('What about my team?'); await page.locator('#supportForm button').click();
  await expect.poll(() => chats.length).toBe(2); expect(chats[1].conversationId).toBe('conversation-1');
  await page.locator('.support-escalate').last().click();
  await expect.poll(() => escalations.length).toBe(1); expect(escalations[0].conversationId).toBe('conversation-1'); expect(escalations[0].message).toBe('What about my team?');
  await expect(page.locator('#supportStatus')).toHaveText('Transferred to human support');
  await expect(page.locator('main')).not.toContainText('Dify'); await expect(page.locator('main')).not.toContainText('LANGGRAPH');
  await page.locator('#supportClearBtn').click();
  await expect(page.locator('.support-message')).toHaveCount(1);
  expect(deleted).toEqual(['/api/support/conversations/conversation-1']);
  await page.locator('#supportInput').fill('A fresh question'); await page.locator('#supportForm button').click();
  await expect.poll(() => chats.length).toBe(3); expect(chats[2].conversationId).toBeNull();
});

test('support failure retains a retry draft and clear ignores a late reply', async ({ page }) => {
  await mockApi(page);
  await page.route('**/api/support/chat', route => route.abort('failed'));
  await page.goto('/customer-service.html');
  await page.locator('#supportInput').fill('Keep my draft'); await page.locator('#supportForm button').click();
  await expect(page.locator('#supportStatus')).toHaveText('Service unavailable');
  await expect(page.locator('#supportInput')).toHaveValue('Keep my draft');
  await expect(page.locator('#supportForm button')).toBeEnabled();
  let release;
  await page.route('**/api/support/chat', async route => { await new Promise(resolve => { release = resolve; }); await route.fulfill(ok({ answer: 'Late obsolete reply', conversationId: 'old-id' })).catch(() => {}); });
  await page.locator('#supportForm button').click();
  await expect(page.locator('#supportForm')).toHaveAttribute('aria-busy', 'true');
  await expect(page.locator('#supportForm button')).toBeDisabled();
  await page.locator('#supportClearBtn').click(); release();
  await expect(page.locator('.support-message')).toHaveCount(1);
  await expect(page.locator('#supportForm button')).toBeEnabled();
  await expect(page.locator('#supportChatList')).not.toContainText('Late obsolete reply');
});

test('failed server conversation deletion retains the ID for retry and missing conversations clear safely', async ({ page }) => {
  const chats = []; let deletions = 0;
  await mockApi(page, (url, request) => {
    if (url.pathname === '/api/support/chat') { chats.push(request.postDataJSON()); return { answer: 'A reply', conversationId: 'kept-conversation' }; }
  });
  await page.route('**/api/support/conversations/kept-conversation', async route => {
    deletions++;
    await route.fulfill({ status: deletions === 1 ? 503 : 404, contentType: 'application/json', body: JSON.stringify({ success: false, message: 'Internal service unavailable' }) });
  });
  await page.goto('/customer-service.html');
  await page.locator('#supportInput').fill('First question'); await page.locator('#supportForm button').click();
  await expect(page.locator('#supportStatus')).toHaveText('Reply received');
  await page.locator('#supportClearBtn').click();
  await expect(page.locator('#supportChatList')).toContainText('Could not clear the conversation. Please try again.');
  await expect(page.locator('#supportChatList')).not.toContainText('Internal service unavailable');
  await page.locator('#supportInput').fill('Follow-up question'); await page.locator('#supportForm button').click();
  await expect.poll(() => chats.length).toBe(2); expect(chats[1].conversationId).toBe('kept-conversation');
  await expect(page.locator('#supportStatus')).toHaveText('Reply received');
  await page.locator('#supportClearBtn').click();
  await expect(page.locator('.support-message')).toHaveCount(1); expect(deletions).toBe(2);
});

test('support restores after navigation and every history can be selected and deleted', async ({ page }) => {
  const stored = new Map([
    ['recent', { conversationId: 'recent', messages: [{ role: 'user', content: 'Recent question' }, { role: 'assistant', content: 'Recent answer', source: 'LANGGRAPH_RETRIEVAL', citations: [{ title: 'Help guide', url: '/customer-service.html' }] }] }],
    ['older', { conversationId: 'older', messages: [{ role: 'user', content: 'Older question' }, { role: 'assistant', content: 'Older answer', suggestEscalation: true }] }],
  ]);
  const chats = [], deleted = [];
  await mockApi(page, (url, request) => {
    if (url.pathname === '/api/support/conversations') return [...stored.keys()].map(id => ({ id, preview: `${id} question`, updatedAt: '2030-01-01T10:00:00' }));
    if (url.pathname.startsWith('/api/support/conversations/')) {
      const id = url.pathname.split('/').at(-1);
      if (request.method() === 'DELETE') { deleted.push(id); stored.delete(id); return {}; }
      return stored.get(id);
    }
    if (url.pathname === '/api/support/chat') { const body = request.postDataJSON(); chats.push(body); return { answer: 'Follow-up answer', conversationId: body.conversationId || 'fresh' }; }
  });
  await page.goto('/customer-service.html');
  await expect(page.locator('#supportConversationSelect')).toHaveValue('recent');
  await expect(page.locator('#supportChatList')).toContainText('Recent answer');
  await expect(page.getByRole('link', { name: 'Help guide' })).toBeVisible();
  await page.goto('/index.html'); await page.goto('/customer-service.html');
  await expect(page.locator('#supportChatList')).toContainText('Recent answer');
  await page.locator('#supportInput').fill('Continue after navigation'); await page.locator('#supportForm button').click();
  await expect.poll(() => chats.length).toBe(1); expect(chats[0].conversationId).toBe('recent');
  await page.locator('#supportConversationSelect').selectOption('older');
  await expect(page.locator('#supportChatList')).toContainText('Older answer');
  await expect(page.locator('#supportChatList')).not.toContainText('Recent answer');
  await expect(page.locator('.support-escalate')).toHaveCount(1);
  await page.locator('#supportClearBtn').click();
  await expect(page.locator('#supportConversationSelect')).toHaveValue('recent');
  await expect(page.locator('#supportChatList')).toContainText('Recent answer');
  await expect(page.locator('#supportConversationSelect option')).toHaveCount(2);
  await page.locator('#supportClearBtn').click();
  await expect(page.locator('#supportConversationSelect')).toHaveValue('');
  await expect(page.locator('#supportConversationSelect option')).toHaveCount(1);
  expect(deleted).toEqual(['older', 'recent']);
  await page.locator('#supportInput').fill('Start fresh'); await page.locator('#supportForm button').click();
  await expect.poll(() => chats.length).toBe(2); expect(chats[1].conversationId).toBeNull();
});

test('support history loading disables conflicting actions and account changes discard late history', async ({ page, context }) => {
  await page.addInitScript(() => { if (!localStorage.getItem('campus_pulse_token')) localStorage.setItem('campus_pulse_token', 'account-a'); });
  let release;
  await mockApi(page, (url, request) => {
    const account = request.headers().authorization;
    if (url.pathname === '/api/support/conversations') return account === 'Bearer account-a' ? [{ id: 'private-a', preview: 'Account A', updatedAt: '2030-01-01T10:00:00' }] : [];
  }, false);
  await page.route('**/api/support/conversations/private-a', async route => {
    await new Promise(resolve => { release = resolve; });
    await route.fulfill(ok({ conversationId: 'private-a', messages: [{ role: 'assistant', content: 'Private account A answer' }] })).catch(() => {});
  });
  await page.goto('/customer-service.html');
  await expect.poll(() => typeof release).toBe('function');
  await expect(page.locator('#supportConversationSelect')).toBeDisabled();
  await expect(page.locator('#supportClearBtn')).toBeDisabled();
  await expect(page.locator('#supportForm button')).toBeDisabled();
  const otherTab = await context.newPage(); await otherTab.goto('/index.html');
  await otherTab.evaluate(() => localStorage.setItem('campus_pulse_token', 'account-b')); release();
  await expect(page.locator('#supportForm button')).toBeEnabled();
  await expect(page.locator('#supportConversationSelect option')).toHaveCount(1);
  await expect(page.locator('#supportChatList')).not.toContainText('Private account A answer');
  await otherTab.close();
});

test('an expired history session falls back to the guest assistant without an error', async ({ page }) => {
  await mockApi(page);
  await page.route('**/api/support/conversations', route => route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ success: false, message: 'Please sign in' }) }));
  await page.goto('/customer-service.html');
  await expect(page.locator('#supportHistoryControls')).toBeHidden();
  await expect(page.locator('#supportForm button')).toBeEnabled();
  await expect(page.locator('#supportStatus')).toHaveText('Ready to help');
  await expect(page.locator('.support-message')).toHaveCount(1);
});

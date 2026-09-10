import { test, expect } from '@playwright/test';
test.skip(process.env.E2E_LIVE !== '1', 'Set E2E_LIVE=1 against an isolated demo stack.');
const studentAccount = process.env.E2E_USERNAME || 'linzhixia';
const studentPassword = process.env.E2E_PASSWORD || 'demo12345';

async function login(request, username, password) {
  const response = await request.post('/api/auth/login', { data: { usernameOrStudentNo: username, password }, headers: { 'Accept-Language': 'en-US' } });
  expect(response.ok()).toBeTruthy();
  const body = await response.json(); expect(body.success).toBeTruthy(); return body.data;
}
async function authenticatedPage(browser, baseURL, token) {
  const context = await browser.newContext({ baseURL, locale: 'en-US' });
  await context.addInitScript(value => { localStorage.setItem('campus_pulse_token', value); localStorage.setItem('campus_pulse_locale', 'en-US'); }, token);
  return { context, page: await context.newPage() };
}

test('live student sign-in and all main page initializers', async ({ page, request }) => {
  const failures = []; page.on('pageerror', error => failures.push(error.message));
  await page.goto('/login.html'); await page.locator('#loginUsername').fill(studentAccount); await page.locator('#loginPassword').fill(studentPassword); await page.locator('#loginBtn').click();
  await expect(page).toHaveURL(/home\.html/);
  const activities = await (await request.get('/api/activities?page=1&size=1')).json();
  for (const path of ['home.html', 'activity-lobby.html', 'team-lobby.html', 'profile.html', 'profile-edit.html', 'profile-records.html?kind=activities', 'interest-tags.html', 'messages.html', 'notifications.html', 'publish-activity.html', 'customer-service.html', `activity-detail.html?id=${activities.data.items[0].id}`]) {
    await page.goto(`/${path}`); await expect(page.locator('body')).not.toHaveAttribute('aria-busy', 'true');
    await expect(page.locator('#appModal')).not.toBeVisible();
    expect(await page.locator('html').getAttribute('lang')).toBe('en-US');
  }
  expect(failures).toEqual([]);
});

test('live support ticket receives an admin reply and closes visibly for the student', async ({ browser, request, baseURL }) => {
  const student = await login(request, studentAccount, studentPassword);
  const admin = await login(request, process.env.E2E_ADMIN_USERNAME || 'admin', process.env.E2E_ADMIN_PASSWORD || 'admin123');
  const subject = `Browser support test ${Date.now()}`;
  const created = await request.post('/api/support/tickets', { headers: { Authorization: `Bearer ${student.token}` }, data: { message: subject } });
  expect(created.ok()).toBeTruthy(); const ticketId = (await created.json()).data.id;
  const adminBrowser = await authenticatedPage(browser, baseURL, admin.token);
  try {
    await adminBrowser.page.goto('/admin.html');
    await expect(adminBrowser.page.locator('#supportTickets')).toBeVisible();
    await adminBrowser.page.locator(`[data-ticket="${ticketId}"]`).click();
    await adminBrowser.page.locator('#ticketReply').fill('We have reviewed your request.');
    await adminBrowser.page.locator('[data-reply-form] button').click();
    await expect(adminBrowser.page.getByText('We have reviewed your request.', { exact: true })).toBeVisible();
    await adminBrowser.page.locator('#ticketStatus').selectOption('CLOSED'); await adminBrowser.page.locator('[data-status-save]').click();
    await expect(adminBrowser.page.locator('#ticketReply')).toBeDisabled();
  } finally { await adminBrowser.context.close(); }
  const userBrowser = await authenticatedPage(browser, baseURL, student.token);
  try {
    await userBrowser.page.goto('/customer-service.html'); await userBrowser.page.locator(`[data-ticket="${ticketId}"]`).click();
    await expect(userBrowser.page.getByText('We have reviewed your request.', { exact: true })).toBeVisible();
    await expect(userBrowser.page.locator('#ticketReply')).toBeDisabled();
  } finally { await userBrowser.context.close(); }
});

test('live demo content and generated covers switch language without changing source records', async ({ page, request }, testInfo) => {
  const response = await request.get('/api/activities?size=12');
  const activity = (await response.json()).data.items.find(item => item.translations?.en?.title);
  expect(activity).toBeTruthy();
  await page.goto('/activity-lobby.html');
  const card = page.locator(`[data-activity-id="${activity.id}"]`);
  await expect(card.locator('h3')).toHaveText(activity.translations.en.title);
  const cover = await card.locator('img').getAttribute('src');
  const coverText = await page.evaluate(svg => [...new DOMParser().parseFromString(svg, 'image/svg+xml').querySelectorAll('text')]
    .map(node => node.textContent).join(' '), decodeURIComponent(cover.split(',').slice(1).join(',')));
  expect(coverText).toContain(activity.translations.en.title);
  expect(coverText).not.toMatch(/[\u3400-\u9fff]/);
  await page.screenshot({ path: testInfo.outputPath('english-activities.png'), fullPage: true });
  await page.locator('#languageSwitcher [data-locale="zh-CN"]').click();
  await expect(card.locator('h3')).toHaveText(activity.title);
  await page.locator('#languageSwitcher [data-locale="en-US"]').click();
  await expect(card.locator('h3')).toHaveText(activity.translations.en.title);
  const detail = await (await request.get(`/api/activities/${activity.id}`)).json();
  expect(detail.data.title).toBe(activity.title);
  const teams = (await (await request.get('/api/teams/page?size=12')).json()).data.items;
  const team = teams.find(item => item.translations?.en?.title);
  expect(team).toBeTruthy();
  await page.goto('/team-lobby.html');
  const teamCard = page.locator(`.home-team-card[data-team-id="${team.id}"]`);
  await expect(teamCard).toContainText(team.translations.en.title.split('|').at(-1).trim());
  await expect(teamCard).toContainText(team.translations.en.description);
});

test('live LangGraph answers cite guides and conversation history survives navigation', async ({ browser, request, baseURL }, testInfo) => {
  const student = await login(request, studentAccount, studentPassword);
  const { context, page } = await authenticatedPage(browser, baseURL, student.token);
  let conversationId;
  try {
    await page.goto('/customer-service.html');
    await expect(page.locator('#supportConversationSelect')).toBeEnabled();
    await page.locator('#supportConversationSelect').selectOption('');
    await expect(page.locator('#supportForm')).toHaveAttribute('aria-busy', 'false');
    await page.locator('#supportInput').fill('How can I join a team?');
    await page.locator('#supportForm button[type="submit"]').click();
    await expect(page.locator('.support-citations a').first()).toBeVisible();
    await expect(page.locator('#supportForm')).toHaveAttribute('aria-busy', 'false');
    conversationId = await page.locator('#supportConversationSelect').inputValue();
    expect(conversationId).toMatch(/^[0-9a-f-]{36}$/);
    await page.goto('/home.html'); await page.goto('/customer-service.html');
    await expect(page.locator('#supportConversationSelect')).toHaveValue(conversationId);
    await expect(page.getByText('How can I join a team?', { exact: true }).last()).toBeVisible();
    await page.locator('#supportInput').fill('What about the requirements?');
    await page.locator('#supportForm button[type="submit"]').click();
    await expect(page.locator('#supportForm')).toHaveAttribute('aria-busy', 'false');
    await expect(page.locator('.support-citations')).toHaveCount(2);
    await page.screenshot({ path: testInfo.outputPath('english-support.png'), fullPage: true });
    await page.locator('#supportClearBtn').click();
    await expect(page.locator('#supportForm')).toHaveAttribute('aria-busy', 'false');
    const deleted = await request.get(`/api/support/conversations/${conversationId}`, { headers: { Authorization: `Bearer ${student.token}` } });
    expect(deleted.status()).toBe(404); conversationId = null;
  } finally {
    if (conversationId) await request.delete(`/api/support/conversations/${conversationId}`, { headers: { Authorization: `Bearer ${student.token}` } });
    await context.close();
  }
});

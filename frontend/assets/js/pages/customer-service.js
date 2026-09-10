import { mountSupportTickets } from '../core/support-tickets.js';
import { getLocale, t } from '../core/i18n.js';
import { qs, escapeHtml, formatDateTime } from '../core/ui.js';
import { applyBackLink } from '../core/navigation.js';
import { apiFetch } from '../core/api.js';
import { getToken, TOKEN_KEY } from '../core/auth.js';
import { mountBottomNavActive } from '../core/render.js';
import { supportSourceLabel, supportCitations } from '../core/support-response.js';

export async function initCustomerService() {
  mountBottomNavActive();
  applyBackLink('#supportBackLink', 'home.html');
  const list = qs('#supportChatList'), form = qs('#supportForm'), input = qs('#supportInput');
  const quickList = qs('#supportQuickList'), status = qs('#supportStatus'), clearButton = qs('#supportClearBtn');
  const historySelect = qs('#supportConversationSelect'), historyControls = qs('#supportHistoryControls'), historyRefresh = qs('#supportHistoryRefreshBtn');
  let conversationId = '', conversations = [], lastQuestion = '', busy = false, generation = 0, controller;
  const refreshTickets = await mountSupportTickets(qs('main'));

  function setBusy(value) {
    busy = value;
    form?.setAttribute('aria-busy', String(value));
    if (historySelect) historySelect.disabled = value;
    if (historyRefresh) historyRefresh.disabled = value;
    document.querySelectorAll('#supportForm button, #supportQuickList button, #supportChatList [data-escalate]').forEach(button => { button.disabled = value; });
  }

  function appendMessage(role, text, reply = {}) {
    if (!list) return null;
    const item = document.createElement('div');
    item.className = `support-message ${role === 'user' ? 'is-user' : 'is-bot'}`;
    const citations = supportCitations(reply.citations);
    item.innerHTML = `
      <div class="support-message-avatar"><i class="fa-solid ${role === 'user' ? 'fa-user' : 'fa-headset'}" aria-hidden="true"></i></div>
      <div class="support-message-body">
        <div class="support-message-text">${escapeHtml(text).replaceAll('\n', '<br>')}</div>
        ${reply.source ? `<div class="support-message-source">${escapeHtml(supportSourceLabel(reply.source))}</div>` : ''}
        ${citations.length ? `<div class="support-citations"><span>${escapeHtml(t('参考资料'))}</span><ul>${citations.map(citation => `<li><a href="${escapeHtml(citation.url)}" target="_blank" rel="noopener noreferrer">${escapeHtml(citation.title)}</a></li>`).join('')}</ul></div>` : ''}
        ${reply.suggestEscalation && !reply.escalated ? `<button class="secondary-btn support-escalate" type="button" data-escalate>${escapeHtml(t('请求人工协助'))}</button>` : ''}
      </div>`;
    list.appendChild(item); list.scrollTop = list.scrollHeight;
    return item;
  }

  function welcome() {
    appendMessage('bot', t('你好，我是 CampusPulse 智能客服。你可以问我注册登录、邮箱验证码、活动报名、组队申请、消息通知、个人中心和后台管理相关问题。'));
    if (status) status.textContent = t('随时为你提供帮助');
  }

  function operation(clearEnabled = false) {
    generation++; controller?.abort(); controller = new AbortController(); setBusy(true);
    if (clearButton) clearButton.disabled = !clearEnabled;
    return { generation, controller, token: getToken() };
  }
  const currentOperation = op => op.generation === generation && op.token === getToken();
  function finish(op) {
    if (op.generation !== generation) return;
    controller = null; setBusy(false); if (clearButton) clearButton.disabled = false;
  }
  function resetMessages() {
    conversationId = ''; lastQuestion = ''; list?.replaceChildren();
    if (input) input.value = ''; welcome();
  }
  function renderConversations() {
    if (historyControls) historyControls.hidden = !getToken();
    if (!historySelect) return;
    historySelect.innerHTML = `<option value="">${escapeHtml(t('新会话'))}</option>` + conversations.map(item => `<option value="${escapeHtml(item.id)}">${escapeHtml(item.preview || t('客服会话'))} · ${escapeHtml(formatDateTime(item.updatedAt))}</option>`).join('');
    historySelect.value = conversationId;
  }
  async function fetchConversations(op) {
    if (!op.token) { conversations = []; renderConversations(); return; }
    const result = await apiFetch('/support/conversations', { signal: op.controller.signal });
    if (!currentOperation(op)) return;
    conversations = Array.isArray(result) ? result : [];
    renderConversations();
  }
  async function restoreConversation(id, op) {
    const detail = await apiFetch(`/support/conversations/${encodeURIComponent(id)}`, { signal: op.controller.signal });
    if (!currentOperation(op)) return;
    conversationId = String(detail?.conversationId || id); lastQuestion = '';
    list?.replaceChildren(); if (input) input.value = '';
    for (const message of detail?.messages || []) {
      if (!['user', 'assistant'].includes(message.role)) continue;
      appendMessage(message.role === 'user' ? 'user' : 'bot', message.content || '', message.role === 'assistant' ? message : {});
      if (message.role === 'user') lastQuestion = message.content || '';
    }
    if (!list?.children.length) welcome();
    else if (status) status.textContent = t('已恢复会话');
    renderConversations();
  }
  async function loadHistory(selectedId) {
    const op = operation();
    let requestedId = selectedId;
    if (status) status.textContent = t('正在加载会话...');
    try {
      await fetchConversations(op);
      if (!currentOperation(op)) return;
      requestedId = selectedId === undefined ? conversations[0]?.id : selectedId;
      if (requestedId) await restoreConversation(requestedId, op);
      else { resetMessages(); renderConversations(); }
    } catch (error) {
      if (op.generation !== generation || op.controller.signal.aborted) return;
      if (error.status === 401) { conversations = []; resetMessages(); renderConversations(); }
      else if (currentOperation(op)) {
        if (error.status === 404) { conversations = conversations.filter(item => String(item.id) !== String(requestedId)); resetMessages(); }
        renderConversations();
        appendMessage('bot', t('暂时无法加载历史会话，请重试。'));
        if (status) status.textContent = t('服务暂不可用');
      }
    } finally { finish(op); }
  }

  async function send(question, escalate = false) {
    const text = String(question || '').trim();
    if (!text || busy) return;
    const op = operation(true);
    const requestController = op.controller;
    lastQuestion = text;
    appendMessage('user', escalate ? t('请求人工协助') : text);
    if (input) input.value = '';
    const loading = appendMessage('bot', escalate ? t('正在提交人工客服请求...') : t('正在查询知识库...'));
    loading?.classList.add('is-pending');
    setBusy(true);
    if (status) status.textContent = t('正在处理...');
    try {
      const reply = await apiFetch(escalate ? '/support/escalate' : '/support/chat', {
        method: 'POST', signal: requestController.signal,
        body: JSON.stringify({ message: text, conversationId: conversationId || null, language: getLocale() }),
      });
      if (!currentOperation(op)) return;
      conversationId = reply?.conversationId || conversationId;
      loading?.remove();
      appendMessage('bot', reply?.answer || t('暂时没有找到答案。'), reply || {});
      if (status) status.textContent = reply?.escalated ? t('已转人工') : t('已回复');
      if (conversationId) {
        conversations = [{ id: conversationId, preview: text.slice(0, 80), updatedAt: new Date().toISOString() }, ...conversations.filter(item => String(item.id) !== String(conversationId))];
        renderConversations();
      }
      if (reply?.ticketId) await refreshTickets();
    } catch (error) {
      if (op.generation !== generation || requestController.signal.aborted) return;
      if (error.status === 401) { conversations = []; resetMessages(); renderConversations(); }
      else if (!currentOperation(op)) return;
      loading?.remove();
      if (error.status === 404) conversationId = '';
      appendMessage('bot', error.status === 401 ? t('请先登录后再试。') : escalate ? t('提交人工客服请求失败。') : t('智能客服暂时不可用，请稍后再试。'));
      if (input && !input.value) input.value = text;
      if (status) status.textContent = t('服务暂不可用');
    } finally {
      finish(op);
    }
  }

  const escalate = () => send(input?.value.trim() || lastQuestion || t('请求人工客服协助'), true);
  form?.addEventListener('submit', event => { event.preventDefault(); send(input?.value); });
  quickList?.addEventListener('click', event => {
    if (event.target.closest('[data-escalate]')) return escalate();
    const button = event.target.closest('[data-question]');
    if (button) send(t(button.dataset.question));
  });
  list?.addEventListener('click', event => { if (event.target.closest('[data-escalate]')) escalate(); });
  historyRefresh?.addEventListener('click', () => { if (!busy) loadHistory(conversationId || undefined); });
  historySelect?.addEventListener('change', () => { if (!busy) loadHistory(historySelect.value); });
  clearButton?.addEventListener('click', async () => {
    const op = operation(), deletingId = conversationId;
    let cleared = false;
    list?.querySelectorAll('.is-pending').forEach(node => node.remove());
    if (status) status.textContent = t('正在清空会话...');
    try {
      if (deletingId) {
        try { await apiFetch(`/support/conversations/${encodeURIComponent(deletingId)}`, { method: 'DELETE', signal: op.controller.signal }); }
        catch (error) { if (error.status !== 404) throw error; }
      }
      if (!currentOperation(op)) return;
      cleared = true;
      conversations = conversations.filter(item => String(item.id) !== String(deletingId));
      resetMessages(); renderConversations();
      if (op.token) {
        await fetchConversations(op);
        if (currentOperation(op) && conversations.length) await restoreConversation(conversations[0].id, op);
      }
      input?.focus();
    } catch (error) {
      if (op.generation !== generation || op.controller.signal.aborted) return;
      if (error.status === 401) { conversations = []; resetMessages(); renderConversations(); }
      else if (currentOperation(op)) {
        appendMessage('bot', cleared ? t('暂时无法加载历史会话，请重试。') : t('未能清空会话，请重试。'));
        if (status) status.textContent = t('服务暂不可用');
      }
    } finally { finish(op); }
  });
  window.addEventListener('storage', event => {
    if (event.key === TOKEN_KEY) { generation++; controller?.abort(); location.reload(); }
  });
  window.addEventListener('pagehide', () => { generation++; controller?.abort(); }, { once: true });
  welcome(); renderConversations();
  if (getToken()) await loadHistory();
}

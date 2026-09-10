import { t as _tr, ui as _ui, translateStatic as _static } from '../core/i18n.js';
import { apiFetch } from './api.js';
import { getToken } from './auth.js';
import { escapeHtml, formatDateTime, openUtilityModal, showToast } from './ui.js';
import { t, ui } from './i18n.js';

const statusLabel = value => t({ OPEN: _tr('待处理'), IN_PROGRESS: _tr('处理中'), CLOSED: _tr('已关闭') }[value] || value);

export async function mountSupportTickets(container, admin = false) {
  if (!container || !getToken()) return () => {};
  const panel = document.createElement('section'); panel.className = 'ticket-panel'; panel.id = 'supportTickets';
  panel.innerHTML = ui`<h2>支持工单</h2><p>查看人工客服回复和处理进度。</p><div class="ticket-list"></div><div class="stack-card-actions"><button class="secondary-btn" data-refresh>刷新</button><button class="secondary-btn" data-more>查看更多</button></div>`;
  container.append(panel);
  const list = panel.querySelector('.ticket-list'), more = panel.querySelector('[data-more]');
  let tickets = [], busy = false;
  async function refresh(append = false) {
    if (busy) return;
    busy = true;
    try {
      const cursor = append && tickets.length ? `&beforeId=${tickets.at(-1).id}` : '';
      const batch = await apiFetch(`/support/${admin ? 'admin/' : ''}tickets?limit=20${cursor}`);
      tickets = append ? [...tickets, ...batch] : batch;
      list.innerHTML = tickets.length ? tickets.map(ticket => `<div class="ticket-item"><div><strong>#${ticket.id} ${escapeHtml(ticket.subject)}</strong><p>${escapeHtml(statusLabel(ticket.status))} · ${escapeHtml(formatDateTime(ticket.updatedAt))}</p></div><button class="secondary-btn" data-ticket="${ticket.id}">${escapeHtml(t('查看详情'))}</button></div>`).join('') : `<p>${escapeHtml(t('暂无工单'))}</p>`;
      more.hidden = batch.length < 20;
    } catch (error) { showToast(error.message, 'danger'); }
    finally { busy = false; }
  }
  async function openTicket(id) {
    const detail = await apiFetch(`/support/tickets/${id}`);
    let replies = detail.replies;
    openUtilityModal({ title: `#${id} ${detail.ticket.subject}`, maxWidth: 720, html: ui`
      <p>${escapeHtml(statusLabel(detail.ticket.status))}</p>
      <button class="secondary-btn" data-earlier ${detail.hasMoreReplies ? '' : 'hidden'}>加载更早回复</button>
      <div data-replies></div>
      <form data-reply-form><label for="ticketReply">回复内容</label><textarea id="ticketReply" required maxlength="2000" rows="3" ${detail.ticket.status === 'CLOSED' ? 'disabled' : ''}></textarea><button class="primary-btn" ${detail.ticket.status === 'CLOSED' ? 'disabled' : ''}>发送回复</button></form>
      ${admin ? ui`<label for="ticketStatus">工单状态</label><select id="ticketStatus"><option value="OPEN">${escapeHtml(t('待处理'))}</option><option value="IN_PROGRESS">${escapeHtml(t('处理中'))}</option><option value="CLOSED">${escapeHtml(t('已关闭'))}</option></select><button class="secondary-btn" data-status-save>保存状态</button>` : ''}
    `, onOpen(modal, close) {
      const render = () => { modal.querySelector('[data-replies]').innerHTML = replies.map(reply => `<article class="ticket-reply"><strong>${escapeHtml(reply.authorName || String(reply.authorId))}</strong> · ${escapeHtml(formatDateTime(reply.createdAt))}<p>${escapeHtml(reply.message)}</p></article>`).join(''); };
      render();
      modal.querySelector('[data-earlier]').addEventListener('click', async event => {
        event.target.disabled = true;
        try { const older = await apiFetch(`/support/tickets/${id}?beforeReplyId=${replies[0].id}`); replies = [...older.replies, ...replies]; render(); event.target.hidden = !older.hasMoreReplies; }
        catch (error) { showToast(error.message, 'danger'); }
        finally { event.target.disabled = false; }
      });
      modal.querySelector('[data-reply-form]').addEventListener('submit', async event => {
        event.preventDefault(); const button = event.target.querySelector('button'); button.disabled = true;
        try { await apiFetch(`/support/tickets/${id}/replies`, { method: 'POST', body: JSON.stringify({ message: modal.querySelector('#ticketReply').value.trim() }) }); close(); await openTicket(id); await refresh(); }
        catch (error) { button.disabled = false; showToast(error.message, 'danger'); }
      });
      if (admin) {
        modal.querySelector('#ticketStatus').value = detail.ticket.status;
        modal.querySelector('[data-status-save]').addEventListener('click', async event => {
          event.target.disabled = true;
          try { await apiFetch(`/support/admin/tickets/${id}`, { method: 'PATCH', body: JSON.stringify({ status: modal.querySelector('#ticketStatus').value }) }); close(); await refresh(); await openTicket(id); }
          catch (error) { event.target.disabled = false; showToast(error.message, 'danger'); }
        });
      }
    }});
  }
  list.addEventListener('click', event => { const id = event.target.closest('[data-ticket]')?.dataset.ticket; if (id) openTicket(id).catch(error => showToast(error.message, 'danger')); });
  panel.querySelector('[data-refresh]').addEventListener('click', () => refresh());
  more.addEventListener('click', () => refresh(true));
  await refresh();
  return refresh;
}

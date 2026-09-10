import { contentField } from '../core/content-i18n.js';
import { mountPageNavigator } from './array-pagination.js';
import { apiFetch } from './api.js';
import { openUtilityModal, escapeHtml, showToast, formatDateTime } from './ui.js';
import { t, ui } from './i18n.js';
import { createPagedLoader } from './pagination.js';

export async function mountAdminTools(container) {
  const panel = document.createElement('section'); panel.className = 'ticket-panel'; panel.id = 'adminGovernance';
  panel.innerHTML = ui`<h2>管理操作与审计</h2><div class="stack-card-actions"><button class="secondary-btn" data-roles>调整用户角色</button><button class="secondary-btn" data-tags>编辑或删除标签</button><button class="secondary-btn" data-featured>移除精选活动</button></div><h3>最近管理记录</h3><div data-audit></div><button class="secondary-btn" data-more>查看更多</button>`;
  container.append(panel);
  const loader = createPagedLoader('/admin/audit-log', { onChange({ items, hasMore }) {
    panel.querySelector('[data-audit]').innerHTML = items.map(item => `<div class="ticket-reply"><strong>${escapeHtml(item.actor_name || String(item.actor_id))}</strong> · ${escapeHtml(item.action)} · ${escapeHtml(item.target_type)} #${escapeHtml(String(item.target_id ?? ''))}<p>${escapeHtml(item.reason || '')}</p><small>${escapeHtml(formatDateTime(item.created_at))}</small></div>`).join('') || `<p>${escapeHtml(t('暂无管理记录'))}</p>`;
    panel.querySelector('[data-more]').hidden = !hasMore;
  }, onError: error => showToast(error.message, 'danger') });
  panel.querySelector('[data-more]').addEventListener('click', () => loader.more());
  panel.querySelector('[data-roles]').addEventListener('click', () => openUtilityModal({ title: t('调整用户角色'), html: ui`<form data-role-form><label for="roleUserId">用户 ID</label><input id="roleUserId" type="number" min="1" required><label for="roleValue">角色</label><select id="roleValue"><option value="USER">普通用户</option><option value="ORGANIZER">组织者</option><option value="ADMIN">管理员</option></select><label for="roleReason">变更原因</label><textarea id="roleReason" required maxlength="500"></textarea><button class="primary-btn">确认修改</button></form>`, onOpen(modal, close) {
    modal.querySelector('form').addEventListener('submit', async event => { event.preventDefault(); const button = event.target.querySelector('button'); button.disabled = true;
      try { await apiFetch(`/admin/users/${Number(modal.querySelector('#roleUserId').value)}/role`, { method: 'POST', body: JSON.stringify({ role: modal.querySelector('#roleValue').value, reason: modal.querySelector('#roleReason').value.trim() }) }); close(); await loader.reset({}); showToast(t('已更新')); }
      catch (error) { button.disabled = false; showToast(error.message, 'danger'); }
    });
  }}));
  let tagPage = 1, featuredPage = 1;
  panel.querySelector('[data-tags]').addEventListener('click', async function openTagManager() {
    const tags = await apiFetch(`/admin/tags?page=${tagPage}&size=20`);
    openUtilityModal({ title: t('编辑或删除标签'), html: tags.map(tag => `<form data-tag="${tag.id}" class="ticket-reply"><label for="tag-name-${tag.id}">${escapeHtml(t('标签名称'))}</label><input id="tag-name-${tag.id}" name="name" value="${escapeHtml(tag.name)}" required maxlength="64"><select name="type" aria-label="${escapeHtml(t('类型'))}"><option value="CATEGORY" ${tag.type === 'CATEGORY' ? 'selected' : ''}>${escapeHtml(t('分类'))}</option><option value="INTEREST" ${tag.type === 'INTEREST' ? 'selected' : ''}>${escapeHtml(t('兴趣'))}</option></select><button class="secondary-btn">${escapeHtml(t('保存'))}</button><button type="button" class="secondary-btn" data-delete-tag="${tag.id}">${escapeHtml(t('移除标签'))}</button></form>`).join(''), onOpen(modal) {
      mountPageNavigator(modal.querySelector('#utilityModalBody'), { page: tagPage, count: tags.length, hasMore: tags.length === 20, onChange: next => { tagPage = next; return openTagManager(); } });
      modal.querySelectorAll('form').forEach(form => form.addEventListener('submit', async event => {
        event.preventDefault(); const button = form.querySelector('button'); button.disabled = true;
        try { await apiFetch(`/admin/tags/${form.dataset.tag}`, { method: 'PUT', body: JSON.stringify(Object.fromEntries(new FormData(form))) }); showToast(t('已更新')); await loader.reset({}); }
        catch (error) { showToast(error.message, 'danger'); } finally { button.disabled = false; }
      }));
      modal.querySelectorAll('[data-delete-tag]').forEach(button => button.addEventListener('click', async () => {
        button.disabled = true;
        try { await apiFetch(`/admin/tags/${button.dataset.deleteTag}`, { method: 'DELETE' }); button.closest('form').remove(); await loader.reset({}); }
        catch (error) { button.disabled = false; showToast(error.message, 'danger'); }
      }));
    }});
  });
  panel.querySelector('[data-featured]').addEventListener('click', async function openFeaturedManager() {
    const items = await apiFetch(`/admin/featured?page=${featuredPage}&size=20`);
    openUtilityModal({ title: t('移除精选活动'), html: items.map(item => `<div class="ticket-item"><span>${escapeHtml(contentField(item, 'activityTitle') || contentField(item, 'title'))}</span><button class="secondary-btn" data-remove-featured="${item.id}">${escapeHtml(t('移除精选'))}</button></div>`).join('') || escapeHtml(t('暂无数据')), onOpen(modal) {
      mountPageNavigator(modal.querySelector('#utilityModalBody'), { page: featuredPage, count: items.length, hasMore: items.length === 20, onChange: next => { featuredPage = next; return openFeaturedManager(); } });
      modal.querySelectorAll('[data-remove-featured]').forEach(button => button.addEventListener('click', async () => {
        button.disabled = true;
        try { await apiFetch(`/admin/featured/${button.dataset.removeFeatured}`, { method: 'DELETE' }); button.closest('.ticket-item').remove(); await loader.reset({}); }
        catch (error) { button.disabled = false; showToast(error.message, 'danger'); }
      }));
    }});
  });
  await loader.reset({});
}

import { taxonomyLabel } from '../core/content-i18n.js';
import { t as _tr, ui as _ui, translateStatic as _static } from '../core/i18n.js';
import { qs, qsa, escapeHtml } from './../core/ui.js';
import { apiFetch } from './../core/api.js';
import { requireAuth } from './../core/auth.js';

export async function initInterestTags() {
    requireAuth();
    const me = await apiFetch('/auth/me');
    const box = qs('#interestBox');
    const choices = qs('#interestChoices');
    const saveBtn = qs('#interestSaveBtn');
    let selected = new Set(Array.isArray(me.interests) ? me.interests.filter(Boolean) : []);

    function render() {
      if (!box) return;
      const tags = Array.from(selected);
      box.innerHTML = tags
        .map((t) => {
          return `<span style="display:inline-flex; align-items:center; gap:8px; background: rgba(0, 113, 227, 0.08); color: var(--accent-color); padding: 8px 12px; border-radius: 999px; font-size: 13px; font-weight: 600; margin: 6px 6px 0 0;">#${escapeHtml(
            taxonomyLabel(t)
          )}<i data-tag="${escapeHtml(t)}" class="fa-solid fa-xmark" style="cursor:pointer; opacity:0.7;"></i></span>`;
        })
        .join('');
      qsa('#interestBox i[data-tag]').forEach((i) => {
        i.addEventListener('click', () => {
          const tag = i.getAttribute('data-tag') || '';
          selected.delete(tag);
          render();
        });
      });
    }

    render();

    if (choices) {
      try {
        const list = await apiFetch('/tags');
        const tags = (list || []).map((t) => t.name).filter(Boolean);
        choices.innerHTML = tags
          .map((t) => {
            const on = selected.has(t);
            const bg = on ? 'rgba(0, 113, 227, 0.10)' : 'rgba(0,0,0,0.04)';
            const color = on ? 'var(--accent-color)' : 'var(--text-primary)';
            return `<span class="interest-chip" data-tag="${escapeHtml(t)}" style="user-select:none; cursor:pointer; padding: 8px 12px; border-radius: 999px; background: ${bg}; color: ${color}; font-size: 13px; font-weight: 600;">#${escapeHtml(taxonomyLabel(t))}</span>`;
          })
          .join('');
        qsa('.interest-chip').forEach((el) => {
          el.addEventListener('click', () => {
            const tag = el.getAttribute('data-tag') || '';
            if (!tag) return;
            if (selected.has(tag)) {
              selected.delete(tag);
              el.style.background = 'rgba(0,0,0,0.04)';
              el.style.color = 'var(--text-primary)';
            } else {
              selected.add(tag);
              el.style.background = 'rgba(0, 113, 227, 0.10)';
              el.style.color = 'var(--accent-color)';
            }
            render();
          });
        });
      } catch (e) {
        choices.innerHTML = _ui`<div style="color: var(--text-secondary); font-size: 13px;">标签加载失败，请刷新重试</div>`;
      }
    }
    if (saveBtn) {
      saveBtn.addEventListener('click', async () => {
        await apiFetch('/auth/me/interests', { method: 'PUT', body: JSON.stringify({ tags: Array.from(selected) }) });
        alert(_tr('保存成功'));
        window.location.href = 'profile.html';
      });
    }
  }

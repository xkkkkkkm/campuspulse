import { t } from './i18n.js';
export function initAccessibility() {
  let previousFocus = null, activeModal = null;
  const enhance = () => {
    document.querySelectorAll('input,textarea,select').forEach((input, index) => {
      if (!input.id) input.id = `field-${index}`;
      const nearby = input.closest('.form-group, .input-group, .auth-input-group')?.querySelector('label');
      if (nearby && !nearby.htmlFor && nearby.parentElement.querySelector('input,textarea,select') === input) nearby.htmlFor = input.id;
      if (!document.querySelector(`label[for="${CSS.escape(input.id)}"]`) && !input.hasAttribute('aria-label')) input.setAttribute('aria-label', input.placeholder || t('输入内容'));
    });
    document.querySelectorAll('.modal').forEach(modal => {
      modal.setAttribute('role', 'dialog'); modal.setAttribute('aria-modal', 'true');
      const title = modal.querySelector('h2,h3'); if (title) { if (!title.id) title.id = `${modal.id}-title`; modal.setAttribute('aria-labelledby', title.id); }
      const visible = getComputedStyle(modal).display !== 'none';
      if (visible && activeModal !== modal) { previousFocus = document.activeElement; activeModal = modal; (modal.querySelector('input,button,textarea,select,a[href]') || modal).focus(); }
      if (!visible && activeModal === modal) { activeModal = null; previousFocus?.focus?.(); }
    });
    document.querySelectorAll('i[id*="toggle"],i[id*="Toggle"]').forEach(icon => {
      if (icon.dataset.keyboardReady) return;
      icon.dataset.keyboardReady = 'true'; icon.tabIndex = 0; icon.setAttribute('role', 'button'); icon.setAttribute('aria-label', t('显示或隐藏密码'));
      icon.addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); icon.click(); } });
    });
    document.querySelectorAll('button').forEach(button => { if (!button.textContent.trim() && !button.hasAttribute('aria-label')) button.setAttribute('aria-label', button.title || t('操作')); });
  };
  let queued = false;
  new MutationObserver(records => { if (records.some(record => record.type === 'childList' || record.attributeName === 'style') && !queued) { queued = true; queueMicrotask(() => { queued = false; enhance(); }); } }).observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['style'] });
  document.addEventListener('keydown', event => {
    if (!activeModal) return;
    if (event.key === 'Escape') { const close = activeModal.querySelector('#appModalOk,#utilityModalClose,[data-close],.modal-close'); if (close) close.click(); else activeModal.style.display = 'none'; }
    if (event.key !== 'Tab') return;
    const focusable = [...activeModal.querySelectorAll('button,input,textarea,select,a[href],[tabindex="0"]')].filter(node => !node.disabled && node.getClientRects().length);
    const first = focusable[0], last = focusable.at(-1);
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
  });
  document.addEventListener('error', event => { if (event.target instanceof HTMLImageElement && !event.target.src.endsWith('/assets/images/avatar.svg')) event.target.src = 'assets/images/avatar.svg'; }, true);
  enhance();
}

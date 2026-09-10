import { apiFetch } from './core/api.js';
import { getToken, setToken, requireAuth, ensureMe } from './core/auth.js';
import { showMessage, showToast } from './core/ui.js';
import { mountSupportEntry } from './core/render.js';
import { initI18n, t } from './core/i18n.js';
import { initAccessibility } from './core/accessibility.js';

const pages = {
  'home.html': ['home', 'initHome'],
  'activity-lobby.html': ['activity-lobby', 'initActivityLobby'],
  'activity-detail.html': ['activity-detail', 'initActivityDetail'],
  'activity-manage.html': ['activity-manage', 'initActivityManage'],
  'login.html': ['login', 'initLogin'],
  'register.html': ['register', 'initRegister'],
  'team-lobby.html': ['team-lobby', 'initTeamLobby'],
  'team-success.html': ['team-success', 'initTeamSuccess'],
  'chat.html': ['chat', 'initChat'],
  'messages.html': ['messages', 'initMessages'],
  'profile.html': ['profile', 'initProfile'],
  'notifications.html': ['notifications', 'initNotifications'],
  'profile-edit.html': ['profile-edit', 'initProfileEdit'],
  'profile-records.html': ['profile-records', 'initProfileRecords'],
  'interest-tags.html': ['interest-tags', 'initInterestTags'],
  'publish-activity.html': ['publish', 'initPublish'],
  'admin.html': ['admin', 'initAdmin'],
  'customer-service.html': ['customer-service', 'initCustomerService']
};
let reporting = false;
function reportError(error) {
  if (error?.name === 'AbortError' || reporting) return;
  reporting = true;
  console.error('CampusPulse UI error', error);
  showToast(error?.message || t('操作失败，请重试'), 'danger', 5000);
  setTimeout(() => { reporting = false; }, 500);
}
window.addEventListener('unhandledrejection', event => { event.preventDefault(); reportError(event.reason); });
window.addEventListener('error', event => { if (event.error) reportError(event.error); });
// BFCache restores this document's old resources; recreate channels on return.
window.addEventListener('pageshow', event => { if (event.persisted) location.reload(); });
window.CampusPulse = { apiFetch, getToken, setToken, requireAuth, ensureMe };
async function init() {
  initI18n(); initAccessibility(); mountSupportEntry();
  document.body.setAttribute('aria-busy', 'true');
  try {
    const page = pages[location.pathname.split('/').pop()];
    if (page) { const module = await import(`./pages/${page[0]}.js`); await module[page[1]](); }
  } catch (error) {
    console.error('Page initialization failed', error);
    showMessage(error.message || t('页面加载失败，请重试'), t('页面加载失败'), t('重新加载'), { onClose: () => location.reload() });
  } finally { document.body.removeAttribute('aria-busy'); }
}
if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
else init();

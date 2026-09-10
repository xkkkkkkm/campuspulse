import { apiFetch, API_BASE } from './api.js';
import { getToken } from './auth.js';

export function reconnectDelay(attempt, random = Math.random) {
  return Math.min(30000, 1000 * 2 ** Math.min(attempt, 5)) + Math.floor(random() * 500);
}

// A channel owns at most one ticket request and one EventSource. Old callbacks
// cannot close a replacement connection or schedule another reconnect.
export async function createRealtimeChannel({ teamId = 0, userId = 0, activityId = 0, onEvent }) {
  if (!getToken()) return null;
  let source, retryTimer, connecting, ticketController;
  let closed = false, attempt = 0, notifying = false, generation = 0;
  const notify = async payload => {
    if (closed || notifying) return;
    notifying = true;
    try { await onEvent?.(payload); }
    finally { notifying = false; }
  };
  const pollTimer = setInterval(() => notify({ type: 'POLL' }).catch(console.error), 5000);
  function retry(failed, version) {
    failed?.close();
    if (closed || version !== generation || (failed && failed !== source)) return;
    source = null; clearTimeout(retryTimer);
    if (getToken()) retryTimer = setTimeout(connect, reconnectDelay(attempt++));
  }
  function connect() {
    if (closed || typeof EventSource === 'undefined') return Promise.resolve();
    // Online events while a ticket is pending reuse the request.
    if (connecting) return connecting;
    const version = ++generation;
    ticketController = new AbortController();
    connecting = (async () => {
      try {
        const { ticket } = await apiFetch('/realtime/chat/ticket', { method: 'POST', body: '{}', signal: ticketController.signal });
        if (closed || version !== generation) return;
        const params = new URLSearchParams({ ticket });
        if (teamId) params.set('teamId', teamId);
        if (userId) params.set('userId', userId);
        if (activityId) params.set('activityId', activityId);
        const current = new EventSource(`${API_BASE}/realtime/chat/stream?${params}`);
        source = current;
        const active = () => !closed && version === generation && source === current;
        current.onopen = () => { if (!active()) { current.close(); return; } attempt = 0; notify({ type: 'RECONNECTED' }).catch(console.error); };
        for (const type of ['TEAM_MESSAGE', 'DM_MESSAGE', 'ACTIVITY_MESSAGE']) current.addEventListener(type, event => {
          if (!active()) return;
          try { notify(JSON.parse(event.data)).catch(console.error); }
          catch (error) { console.warn('Invalid realtime event', error); }
        });
        current.onerror = () => retry(current, version);
      } catch { retry(null, version); }
      finally { if (version === generation) connecting = null; }
    })();
    return connecting;
  }
  const online = () => { clearTimeout(retryTimer); source?.close(); source = null; attempt = 0; connect(); notify({ type: 'ONLINE' }).catch(console.error); };
  globalThis.addEventListener?.('online', online);
  await connect();
  return { close() { closed = true; generation++; ticketController?.abort(); source?.close(); clearTimeout(retryTimer); clearInterval(pollTimer); globalThis.removeEventListener?.('online', online); } };
}

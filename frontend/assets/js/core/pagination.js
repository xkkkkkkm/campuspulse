import { apiFetch } from './api.js';

// A reset cancels old work and discards stale responses even when the server
// completed just before cancellation. Never filter a truncated local cache.
export function createPagedLoader(endpoint, { size = 12, onChange, onError } = {}) {
  let page = 0, total = 0, items = [], filters = {}, controller, generation = 0, busy = false;
  async function load(reset = false, nextFilters = filters) {
    if (reset) { controller?.abort(); generation++; filters = nextFilters; page = 0; total = 0; items = []; busy = false; }
    else if (busy || (page > 0 && items.length >= total)) return;
    const version = generation;
    controller = new AbortController(); busy = true;
    const query = new URLSearchParams({ ...filters, page: page + 1, size });
    try {
      const result = await apiFetch(`${endpoint}?${query}`, { signal: controller.signal });
      if (version !== generation) return;
      page = Number(result.page || page + 1); total = Number(result.total || 0);
      const seen = new Set(items.map(item => item.id));
      items.push(...(result.items || []).filter(item => !seen.has(item.id)));
      onChange?.({ items: [...items], total, page, hasMore: items.length < total });
    } catch (error) {
      if (version === generation && error.name !== 'AbortError') { if (onError) onError(error); else throw error; }
    } finally { if (version === generation) busy = false; }
  }
  return { reset: filters => load(true, filters), more: () => load(), close: () => { generation++; controller?.abort(); } };
}

import { t as _tr, ui as _ui, translateStatic as _static } from '../core/i18n.js';
import { qs, escapeHtml } from './../core/ui.js';
import { withFrom } from './../core/navigation.js';
import { apiFetch } from './../core/api.js';
import { ensureMe } from './../core/auth.js';
import { mountBottomNavActive, parseTeamTitle, renderActivityCards, renderHomeTeamCards, logBehavior } from './../core/render.js';

export async function initHome() {
    mountBottomNavActive();
    const notificationBtn = qs('#homeNotificationBtn');
    const notificationBadge = qs('#homeNotificationBadge');
    const list = qs('#activityList');
    const recommendWrap = qs('#recommendSection');
    const recommendTitle = qs('#recommendTitle');
    const recommendList = qs('#recommendList');
    const recommendHint = qs('#recommendHint');
    const teamRecommendSection = qs('#teamRecommendSection');
    const teamRecommendTitle = qs('#teamRecommendTitle');
    const teamRecommendList = qs('#teamRecommendList');
    const teamRecommendHint = qs('#teamRecommendHint');
    const activityOverviewSection = qs('#activityOverviewSection');
    const homeActivityCount = qs('#homeActivityCount');
    const homeTeamCount = qs('#homeTeamCount');
    const input = qs('#searchInput');
    const searchBtn = qs('#homeSearchButton');
    const searchShell = qs('.home-search-shell');
    const searchSuggest = qs('#homeSearchSuggest');
    const searchTagList = qs('#homeSearchTagList');
    const activitySectionTitle = qs('#activitySectionTitle');
    const activitySectionHint = qs('#activitySectionHint');
    let teamCache = null;
    let totalActivityCount = 0;
    let suggestionTerms = [
      _tr('讲座'),
      _tr('志愿活动'),
      _tr('竞赛组队'),
      _tr('考研搭子'),
      _tr('自习搭子'),
      _tr('社团招新'),
      _tr('羽毛球'),
      _tr('项目协作'),
    ];

    async function syncHomeNotificationEntry() {
      if (!notificationBtn) return;
      notificationBtn.addEventListener('click', () => {
        window.location.href = withFrom('notifications.html');
      });
      const me = await ensureMe();
      if (!me) {
        if (notificationBadge) notificationBadge.style.display = 'none';
        return;
      }
      try {
        const page = await apiFetch('/notifications?status=unread&page=1&size=1');
        const unread = Number(page.total || 0);
        if (!notificationBadge) return;
        if (unread > 0) {
          notificationBadge.textContent = unread > 99 ? '99+' : String(unread);
          notificationBadge.style.display = 'inline-block';
        } else {
          notificationBadge.style.display = 'none';
        }
      } catch (e) {
        if (notificationBadge) notificationBadge.style.display = 'none';
      }
    }

    async function getTeams() {
      if (teamCache) return teamCache;
      teamCache = await apiFetch('/teams');
      return teamCache || [];
    }

    function setDefaultActivityHead() {
      if (activitySectionTitle) activitySectionTitle.textContent = _tr('活动速览');
      if (activitySectionHint) activitySectionHint.textContent = _tr('首页只展示部分精选活动，完整浏览请进入活动大厅');
    }

    function setDefaultSearchSections() {
      if (recommendTitle) recommendTitle.textContent = _tr('推荐活动');
      if (recommendHint) recommendHint.textContent = _tr('登录后会结合兴趣与行为给出推荐');
      if (teamRecommendTitle) teamRecommendTitle.textContent = _tr('推荐组队');
      if (teamRecommendHint) teamRecommendHint.textContent = _tr('优先展示与你兴趣更接近、仍有空位的队伍');
      if (activityOverviewSection) activityOverviewSection.style.display = '';
    }

    function searchTeamsByKeyword(keyword, teams) {
      const kw = String(keyword || '').trim().toLowerCase();
      if (!kw) return teams || [];
      return (teams || [])
        .map((team) => {
          const parsed = parseTeamTitle(team.title);
          const title = String(parsed.title || team.title || '').toLowerCase();
          const category = String(parsed.category || '').toLowerCase();
          const desc = String(team.description || '').toLowerCase();
          const creator = String(team.creatorName || '').toLowerCase();
          if (![title, category, desc, creator].some((text) => text.includes(kw))) return null;
          let score = 0;
          if (title === kw) score += 100;
          if (title.startsWith(kw)) score += 30;
          if (category.includes(kw)) score += 20;
          if (desc.includes(kw)) score += 10;
          if (creator.includes(kw)) score += 6;
          return { score, team };
        })
        .filter(Boolean)
        .sort((a, b) => b.score - a.score || a.team.id - b.team.id)
        .map((item) => item.team);
    }

    async function loadHomeTopCards(me) {
      const [teams, page] = await Promise.all([
        getTeams(),
        apiFetch('/activities?page=1&size=1'),
      ]);
      totalActivityCount = page?.total || 0;
      if (homeActivityCount) homeActivityCount.textContent = String(totalActivityCount || 0);
      if (homeTeamCount) homeTeamCount.textContent = String((teams || []).length);
      if (teamRecommendList) {
        const recTeams = await apiFetch('/recommendations/teams?size=8');
        renderHomeTeamCards(teamRecommendList, recTeams || [], _tr('还没有可推荐的队伍'));
        if (teamRecommendHint) {
          teamRecommendHint.textContent = me && Array.isArray(me.interests) && me.interests.length > 0
            ? _tr('已结合兴趣标签、组队互动与行为日志综合推荐')
            : _tr('根据近期活跃、名额状态和运营推荐综合排序');
        }
      }
    }

    function renderSearchSuggestions(keyword = '') {
      if (!searchTagList) return;
      const kw = String(keyword || '').trim().toLowerCase();
      const items = suggestionTerms
        .filter((term) => !kw || term.toLowerCase().includes(kw))
        .slice(0, 10);
      searchTagList.innerHTML = items.map((term) => `
        <button class="home-search-tag" type="button" data-search-term="${escapeHtml(term)}">
          <i class="fa-solid fa-hashtag"></i>
          <span>${escapeHtml(term)}</span>
        </button>
      `).join('');
      Array.from(searchTagList.querySelectorAll('.home-search-tag[data-search-term]')).forEach((btn) => {
        btn.addEventListener('click', async () => {
          const term = btn.getAttribute('data-search-term') || '';
          if (!input || !term) return;
          input.value = term;
          hideSearchSuggestions();
          await runSearch();
        });
      });
    }

    function showSearchSuggestions() {
      if (!searchSuggest) return;
      renderSearchSuggestions(input && input.value ? input.value : '');
      searchSuggest.hidden = false;
    }

    function hideSearchSuggestions() {
      if (!searchSuggest) return;
      searchSuggest.hidden = true;
    }

    let searchVersion = 0, searchController;
    const runSearch = async () => {
      const version = ++searchVersion;
      searchController?.abort(); searchController = new AbortController();
      const kw = (input && input.value ? input.value : '').trim();
      hideSearchSuggestions();
      if (kw) {
        if (recommendWrap) recommendWrap.style.display = '';
        if (teamRecommendSection) teamRecommendSection.style.display = '';
        if (activityOverviewSection) activityOverviewSection.style.display = 'none';
        const page = await apiFetch(`/search?keyword=${encodeURIComponent(kw)}&type=all&page=1&size=10`, { signal: searchController.signal });
        if (version !== searchVersion) return;
        const results = page?.items || [];
        const activityItems = results
          .filter((item) => item.targetType === 'ACTIVITY')
          .slice(0, 6)
          .map((item) => ({
            id: item.id,
            translations: item.translations,
            title: item.title,
            location: item.location || '',
            startTime: item.startTime,
            coverUrl: item.coverUrl,
            participants: item.participants,
            maxParticipants: item.maxParticipants,
            tags: item.tags || [],
            promoted: item.promoted,
          }));
        const teamItems = results
          .filter((item) => item.targetType === 'TEAM')
          .slice(0, 4)
          .map((item) => ({
            id: item.id,
            translations: item.translations,
            activityId: item.activityId,
            title: item.title,
            description: item.description,
            creatorId: item.creatorId,
            creatorName: item.creatorName,
            activityTitle: item.activityTitle,
            members: item.participants,
            maxMembers: item.maxParticipants,
            startTime: item.startTime,
            endTime: item.endTime,
            joined: item.joined,
            pending: item.pending,
            tags: item.tags || [],
            promoted: item.promoted,
          }));
        if (recommendTitle) recommendTitle.textContent = _tr('活动搜索结果');
        if (recommendHint) recommendHint.textContent = activityItems.length > 0 ? _ui`找到 ${activityItems.length} 条相关活动` : _tr('没有找到相关活动');
        renderActivityCards(recommendList, activityItems);
        if (teamRecommendTitle) teamRecommendTitle.textContent = _tr('组队搜索结果');
        if (teamRecommendHint) teamRecommendHint.textContent = teamItems.length > 0 ? _ui`找到 ${teamItems.length} 条相关组队` : _tr('没有找到相关组队');
        renderHomeTeamCards(teamRecommendList, teamItems, _tr('未找到相关组队'));
        logBehavior({ targetType: 'SEARCH', eventType: 'SEARCH', scene: 'home_search', query: kw });
        return;
      }
      if (recommendWrap) recommendWrap.style.display = '';
      if (teamRecommendSection) teamRecommendSection.style.display = '';
      setDefaultSearchSections();
      const me = await ensureMe();
      if (version !== searchVersion) return;
      await loadHomeTopCards(me);
      if (version !== searchVersion) return;
      if (recommendList) {
        if (me) {
          const recs = await apiFetch('/recommendations/activities?size=8');
          if (version !== searchVersion) return;
          renderActivityCards(recommendList, recs || []);
          if (recommendHint) {
            recommendHint.textContent = (me.interests && me.interests.length > 0)
              ? _tr('根据你的兴趣标签与互动行为综合推荐')
              : _tr('根据近期热门活动与时间优先级推荐');
          }
        } else {
          recommendList.innerHTML = _ui`
            <div class="stack-card" style="grid-column: 1 / -1; text-align: center;">
              <div style="font-size: 16px; font-weight: 700; margin-bottom: 10px;">登录后解锁个性化推荐</div>
              <div style="color: var(--text-secondary); font-size: 14px; line-height: 1.7; margin-bottom: 14px;">系统会结合你的兴趣标签、收藏和报名行为，为你优先展示更相关的校园活动。</div>
              <a href="login.html" class="primary-btn">去登录</a>
            </div>
          `;
          if (recommendHint) recommendHint.textContent = _tr('当前展示的是通用推荐说明');
        }
      }
      setDefaultActivityHead();
      const items = await apiFetch(`/activities/highlights?size=8`);
      if (version !== searchVersion) return;
      renderActivityCards(list, items || []);
    };
    if (input) {
      input.addEventListener('focus', () => showSearchSuggestions());
      input.addEventListener('input', () => {
        if (!searchSuggest || searchSuggest.hidden) showSearchSuggestions();
        renderSearchSuggestions(input.value);
      });
      input.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') runSearch();
      });
    }
    if (searchBtn) {
      searchBtn.addEventListener('click', () => {
        if (input && String(input.value || '').trim()) {
          runSearch();
          return;
        }
        showSearchSuggestions();
        if (input) input.focus();
      });
    }
    if (searchShell) {
      searchShell.addEventListener('click', (e) => {
        if (e.target && e.target.closest('#homeSearchButton')) return;
        showSearchSuggestions();
        if (input) input.focus();
      });
    }
    document.addEventListener('click', (e) => {
      const target = e.target;
      if (target && (target.closest('.search-bar') || target.closest('#homeSearchSuggest'))) return;
      hideSearchSuggestions();
    });
    await Promise.all([runSearch(), syncHomeNotificationEntry()]);
  }

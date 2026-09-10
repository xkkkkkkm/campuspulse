import { contentField, contentView, taxonomyLabel } from '../core/content-i18n.js';
import { mountTeamLifecycle } from './team-lifecycle.js';
import { getLocale, t as _tr, ui as _ui, translateStatic as _static } from '../core/i18n.js';
import { isLoggedIn } from './auth.js';
import { qs, qsa, escapeHtml, showToast, formatDateTime, openUtilityModal, renderEmptyCard, openJoinTeamModal } from './ui.js';
import { withFrom } from './navigation.js';
import { apiFetch } from './api.js';

export function mountBottomNavActive() {
    const file = window.location.pathname.split('/').pop() || '';
    qsa('.bottom-nav a').forEach((a) => {
      const href = a.getAttribute('href') || '';
      if (href === file) a.classList.add('active');
      else a.classList.remove('active');
    });
  }

export function mountSupportEntry() {
    const file = window.location.pathname.split('/').pop() || 'home.html';
    if (file === 'customer-service.html' || file === 'chat.html' || qs('#supportFloatEntry')) return;
    const entry = document.createElement('a');
    entry.id = 'supportFloatEntry';
    const authFooter = document.querySelector('.auth-card-footer');
    entry.className = authFooter ? 'auth-support-entry' : 'support-float-entry';
    entry.href = withFrom('customer-service.html');
    entry.setAttribute('aria-label', _tr('打开智能客服'));
    entry.innerHTML = _ui`<i class="fa-solid fa-headset"></i><span>客服</span>`;
    (authFooter || document.body).appendChild(entry);
  }

export function renderTags(tags) {
    if (!tags || tags.length === 0) return '';
    return tags.map((t) => `<span>#${escapeHtml(taxonomyLabel(t))}</span>`).join('');
  }

export function iconForCategory(tag) {
    const text = String(tag || '');
    if (/(运动|篮球|足球|羽毛球|健身|跑步)/.test(text)) return 'fa-basketball';
    if (/(讲座|学术|科研|论坛)/.test(text)) return 'fa-book-open';
    if (/(演出|文艺|音乐|舞蹈|戏剧)/.test(text)) return 'fa-masks-theater';
    if (/(志愿|公益|服务)/.test(text)) return 'fa-hand-holding-heart';
    if (/(竞赛|比赛|挑战)/.test(text)) return 'fa-trophy';
    if (/(兼职|实习|求职|招聘)/.test(text)) return 'fa-briefcase';
    if (/(社交|兴趣|交流|桌游|电影)/.test(text)) return 'fa-comments';
    if (/(考研|升学|搭子|自习)/.test(text)) return 'fa-graduation-cap';
    if (/(校园|生活|社团|宿舍|市集)/.test(text)) return 'fa-school';
    return 'fa-ellipsis';
  }

export function isTeamFocusedTag(tag) {
    const text = String(tag || '');
    return /(组队|搭子|考研|交友|自习|学习|竞赛)/.test(text);
  }

export function isProbablyImageUrl(url) {
    if (!url) return false;
    const u = String(url).trim();
    if (!u) return false;
    if (u.startsWith('data:image/')) return true;
    const low = u.toLowerCase();
    if (low.endsWith('.html')) return false;
    if (low.includes('publish-activity.html')) return false;
    return /\.(png|jpg|jpeg|gif|webp)(\?|$)/.test(low) || low.includes('images.unsplash.com');
  }

export const LEGACY_ACTIVITY_DEFAULT_COVERS = new Set([
    'assets/images/avatar.svg',
    'assets/images/avatar.svg',
    'assets/images/avatar.svg',
    'assets/images/avatar.svg',
    'assets/images/avatar.svg',
    'assets/images/avatar.svg',
    'assets/images/avatar.svg',
    'assets/images/avatar.svg',
    'assets/images/avatar.svg',
    'http://localhost:8080/publish-activity.html'
  ]);

export function hasCustomActivityCover(url) {
    if (!isProbablyImageUrl(url)) return false;
    const value = String(url || '').trim();
    return !LEGACY_ACTIVITY_DEFAULT_COVERS.has(value);
  }

export function escapeSvgText(value) {
    return String(value || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

export function splitCoverText(text, maxCharsPerLine, maxLines) {
    const chars = Array.from(String(text || '').trim());
    if (!chars.length) return [];
    if (!/[\u3400-\u9fff]/.test(text) && /\s/.test(text)) {
      const words = String(text).trim().split(/\s+/), lines = [];
      let line = '';
      for (const word of words) {
        if (line && line.length + word.length + 1 > maxCharsPerLine) { lines.push(line); line = ''; }
        if (word.length > maxCharsPerLine) {
          if (line) { lines.push(line); line = ''; }
          for (let i = 0; i < word.length; i += maxCharsPerLine) lines.push(word.slice(i, i + maxCharsPerLine));
        } else line = line ? `${line} ${word}` : word;
      }
      if (line) lines.push(line);
      const visible = lines.slice(0, maxLines);
      if (lines.length > maxLines) visible[maxLines - 1] = `${visible[maxLines - 1].slice(0, maxCharsPerLine - 1)}…`;
      return visible;
    }
    const lines = [];
    for (let i = 0; i < chars.length && lines.length < maxLines; i += maxCharsPerLine) {
      let part = chars.slice(i, i + maxCharsPerLine).join('');
      if (lines.length === maxLines - 1 && i + maxCharsPerLine < chars.length) {
        part = `${part.slice(0, Math.max(0, maxCharsPerLine - 1))}…`;
      }
      lines.push(part);
    }
    return lines;
  }

export function activityCoverTheme(seedText) {
    const themes = [
      {
        surface: '#fdfefe',
        panel: 'rgba(255,255,255,0.82)',
        panelStroke: 'rgba(255,255,255,0.92)',
        ink: '#141821',
        muted: '#697180',
        accent: '#3d66d6',
        accentSoft: 'rgba(61,102,214,0.10)',
        glowA: 'rgba(61,102,214,0.14)',
        glowB: 'rgba(64,156,255,0.10)',
        line: 'rgba(61,102,214,0.10)'
      },
      {
        surface: '#fdfefe',
        panel: 'rgba(255,255,255,0.82)',
        panelStroke: 'rgba(255,255,255,0.92)',
        ink: '#14211d',
        muted: '#63756d',
        accent: '#1d8b6a',
        accentSoft: 'rgba(29,139,106,0.10)',
        glowA: 'rgba(29,139,106,0.14)',
        glowB: 'rgba(255,159,10,0.08)',
        line: 'rgba(29,139,106,0.10)'
      },
      {
        surface: '#fffdfb',
        panel: 'rgba(255,255,255,0.84)',
        panelStroke: 'rgba(255,255,255,0.92)',
        ink: '#261c15',
        muted: '#7e6a5f',
        accent: '#c87425',
        accentSoft: 'rgba(200,116,37,0.10)',
        glowA: 'rgba(200,116,37,0.14)',
        glowB: 'rgba(255,196,142,0.10)',
        line: 'rgba(200,116,37,0.10)'
      },
      {
        surface: '#fdfdff',
        panel: 'rgba(255,255,255,0.82)',
        panelStroke: 'rgba(255,255,255,0.92)',
        ink: '#1d1830',
        muted: '#73698b',
        accent: '#7551d3',
        accentSoft: 'rgba(117,81,211,0.10)',
        glowA: 'rgba(117,81,211,0.14)',
        glowB: 'rgba(178,124,255,0.10)',
        line: 'rgba(117,81,211,0.10)'
      }
    ];
    const seed = Array.from(String(seedText || '')).reduce((acc, ch) => acc + ch.charCodeAt(0), 0);
    return themes[seed % themes.length];
  }

export function buildActivityCoverUrl(activity) {
    activity = contentView(activity);
    const category = (activity?.tags && activity.tags[0]) || _tr('校园活动');
    const title = String(activity?.title || _tr('校园活动')).trim();
    const location = String(activity?.location || '').trim();
    const time = activity?.startTime ? formatDateTime(activity.startTime) : '';
    const meta = [location || _tr('地点待定'), time || _tr('时间待定')];
    const theme = activityCoverTheme(`${category}-${title}`);
    const english = getLocale() === 'en-US';
    const latinTitle = !/[\u3400-\u9fff]/.test(title);
    const titleLines = splitCoverText(title, latinTitle ? 25 : 8, latinTitle ? 4 : 3);
    const categoryWidth = Math.min(570, Math.max(122, category.length * (english ? 12 : 20) + 44));

    const titleSvg = titleLines
      .map((line, idx) => `<text x="80" y="${252 + idx * (latinTitle ? 62 : 84)}" font-size="${latinTitle ? 43 : 70}" font-weight="800" fill="${theme.ink}" letter-spacing="-1.6">${escapeSvgText(line)}</text>`)
      .join('');
    const metaSvg = meta.map((line, idx) => {
      const visible = splitCoverText(line, english ? 72 : 38, 1)[0] || '';
      const width = Math.min(1040, Math.max(170, 48 + Array.from(visible).length * (english ? 12 : 23)));
      const x = 80, y = 548 + idx * 54;
      return `
        <rect x="${x}" y="${y}" width="${width}" height="46" rx="18" fill="rgba(255,255,255,0.84)" stroke="${theme.line}"/>
        <text x="${x + 22}" y="${y + 30}" font-size="22" font-weight="700" fill="${idx === 0 ? theme.muted : theme.accent}">${escapeSvgText(visible)}</text>
      `;
    }).join('');

    const svg = `
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 720" width="1200" height="720">
        <defs>
          <filter id="heroShadow" x="-10%" y="-10%" width="130%" height="130%">
            <feDropShadow dx="0" dy="18" stdDeviation="26" flood-color="rgba(26,33,52,0.10)"/>
          </filter>
          <filter id="softBlur">
            <feGaussianBlur stdDeviation="36"/>
          </filter>
        </defs>
        <rect width="1200" height="720" rx="42" fill="${theme.surface}"/>
        <rect x="38" y="38" width="1124" height="644" rx="34" fill="rgba(255,255,255,0.72)" stroke="rgba(255,255,255,0.92)"/>
        <circle cx="170" cy="110" r="170" fill="${theme.glowA}" filter="url(#softBlur)"/>
        <circle cx="1088" cy="132" r="152" fill="${theme.glowB}" filter="url(#softBlur)"/>
        <circle cx="1036" cy="160" r="74" fill="rgba(255,255,255,0.44)"/>
        <circle cx="1100" cy="96" r="36" fill="rgba(255,255,255,0.62)"/>
        <path d="M872 26 C1018 138 1110 252 1192 418" stroke="${theme.line}" stroke-width="2.2" fill="none"/>
        <path d="M806 0 C970 130 1082 276 1200 468" stroke="${theme.line}" stroke-width="2.2" fill="none"/>
        <rect x="56" y="56" width="664" height="608" rx="32" fill="${theme.panel}" stroke="${theme.panelStroke}" filter="url(#heroShadow)"/>
        <rect x="80" y="86" width="${categoryWidth}" height="36" rx="999" fill="${theme.accentSoft}"/>
        <text x="${80 + categoryWidth / 2}" y="109" text-anchor="middle" font-size="18" font-weight="800" fill="${theme.accent}" letter-spacing="1">${escapeSvgText(category)}</text>
        <text x="80" y="162" font-size="18" font-weight="800" fill="${theme.muted}" letter-spacing="2.3">CAMPUS PULSE</text>
        <rect x="80" y="188" width="68" height="6" rx="999" fill="${theme.accent}"/>
        ${titleSvg}
        <line x1="80" y1="520" x2="634" y2="520" stroke="${theme.line}" stroke-width="2"/>
        ${metaSvg}
        <text x="820" y="584" font-size="${english ? 74 : 144}" font-weight="800" fill="rgba(255,255,255,0.34)">${escapeSvgText(splitCoverText(category, english ? 8 : 4, 1)[0] || _tr('活动'))}</text>
      </svg>
    `;
    return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
  }

export function parseTeamTitle(title) {
    const t = String(title || '');
    const idx = t.search(/[｜|]/);
    if (idx > 0) {
      return { category: taxonomyLabel(t.slice(0, idx).trim()), title: t.slice(idx + 1).trim() };
    }
    return { category: '', title: t };
  }

export function deriveGradeLabel(studentNo) {
    const s = String(studentNo || '').trim();
    if (!s || s.length < 2) return '';
    let year = '';
    if (/^20\d{2}/.test(s)) {
      year = s.slice(0, 4);
    } else if (/^\d{2}/.test(s)) {
      year = `20${s.slice(0, 2)}`;
    } else {
      return '';
    }
    return _ui`${year}级`;
  }

export function educationLabel(level) {
    const v = String(level || '').trim();
    if (!v) return '';
    if (v === 'UNDERGRAD') return _tr('本科');
    if (v === 'MASTER') return _tr('硕士');
    if (v === 'PHD') return _tr('博士');
    if (v === 'OTHER') return _tr('其他');
    return '';
  }

export function renderActivityCards(container, activities) {
    if (!container) return;
    if (!activities || activities.length === 0) {
      container.innerHTML = _ui`<div style="grid-column: 1 / -1; padding: 30px; text-align: center; color: var(--text-secondary); background: white; border-radius: 20px;">暂无活动，试试换个关键词</div>`;
      return;
    }
    container.innerHTML = activities
      .map((original) => {
        const a = contentView(original);
        const cover = hasCustomActivityCover(a.coverUrl) ? a.coverUrl : buildActivityCoverUrl(a);
        return _ui`
          <a href="${withFrom(`activity-detail.html?id=${encodeURIComponent(a.id)}`)}" class="activity-card-link" data-activity-id="${a.id}">
            <div class="activity-card">
              <img src="${escapeHtml(cover)}" alt="${escapeHtml(contentField(a, 'title'))}">
              <div class="activity-content">
                <h3>${escapeHtml(contentField(a, 'title'))}</h3>
                <div class="activity-meta">
                  <p><i class="fa-solid fa-location-dot"></i> ${escapeHtml(contentField(a, 'location'))} · ${escapeHtml(formatDateTime(a.startTime))}</p>
                  <p><i class="fa-solid fa-users"></i> ${a.participants}/${a.maxParticipants || _tr('不限')} 人已报名</p>
                </div>
                <div class="tags">${renderTags(a.tags || [])}</div>
              </div>
            </div>
          </a>
        `;
      })
      .join('');
    container.querySelectorAll('a.activity-card-link').forEach((a) => {
      a.addEventListener('click', () => {
        const id = Number(a.dataset.activityId || '0');
        if (id) {
          logEvent(id, 'CLICK');
          logBehavior({ targetType: 'ACTIVITY', targetId: id, eventType: 'CLICK', scene: 'activity_card' });
        }
      });
    });
    (activities || []).forEach((a, index) => {
      if (a && a.id) {
        logBehavior({
          targetType: 'ACTIVITY',
          targetId: a.id,
          eventType: a.promoted ? 'FEATURED_IMPRESSION' : 'IMPRESSION',
          scene: 'activity_card',
          rankPosition: index + 1,
        });
      }
    });
  }

export function formatTeamMemberLimit(members, maxMembers) {
    const memberCount = Number(members || 0);
    const limit = Number(maxMembers || 0);
    return limit > 0 ? _ui`${memberCount}/${limit} 人` : _ui`${memberCount} 人在队`;
  }

export function registrationStatusLabel(status) {
    const normalized = String(status || '').toUpperCase();
    if (normalized === 'APPLIED' || normalized === 'PENDING') return _tr('待审批');
    if (normalized === 'APPROVED') return _tr('已通过');
    if (normalized === 'REJECTED') return _tr('已拒绝');
    if (normalized === 'CANCELLED') return _tr('已取消');
    return status || _tr('未知状态');
  }

export function profileStatusBadge(label, type = '') {
    const cls = type ? ` badge-${type}` : '';
    return `<span class="badge-pill profile-status-badge${cls}">${escapeHtml(label)}</span>`;
  }

export function profileActivityStatusBadge(status) {
    const normalized = String(status || '').toUpperCase();
    const label = registrationStatusLabel(status);
    if (normalized === 'APPLIED' || normalized === 'PENDING') return profileStatusBadge(label, 'pending');
    if (normalized === 'APPROVED') return profileStatusBadge(label, 'approved');
    if (normalized === 'REJECTED' || normalized === 'CANCELLED') return profileStatusBadge(label, 'rejected');
    return profileStatusBadge(label);
  }

export function profileTeamStatusBadge(team) {
    const status = String(team?.participationStatus || team?.requestStatus || team?.memberStatus || '').toUpperCase();
    if (status === 'PENDING') return profileStatusBadge(_tr('待审批'), 'pending');
    if (status === 'CREATOR') return profileStatusBadge(_tr('我发起'), 'approved');
    return profileStatusBadge(_tr('已加入'), 'approved');
  }

export function profileListItem({ href, icon, title, sub = '', badge = '', actionHtml = '' }) {
    return `
      <div class="menu-item profile-list-row">
        <a class="profile-list-main" href="${href}">
          <div class="menu-item-left">
            <i class="${escapeHtml(icon)}"></i>
            ${escapeHtml(title)}
            ${sub ? `<span class="menu-item-sub">${escapeHtml(sub)}</span>` : ''}
          </div>
        </a>
        <div class="profile-list-actions">
          ${badge || ''}
          ${actionHtml || ''}
        </div>
      </div>
    `;
  }

export function profileInlineActionButton(label, action, id) {
    return `<button type="button" class="profile-inline-action" data-action="${escapeHtml(action)}" data-id="${escapeHtml(id)}">${escapeHtml(label)}</button>`;
  }

export async function handleProfileInlineAction(btn, refresh = () => window.location.reload()) {
    if (!btn) return;
    const id = btn.dataset.id;
    const action = btn.dataset.action;
    btn.disabled = true;
    try {
      if (action === 'cancel-activity-registration') {
        await apiFetch(`/activities/${encodeURIComponent(id)}/cancel-registration`, { method: 'POST' });
        showToast(_tr('已撤回报名申请。'));
      } else if (action === 'cancel-team-request') {
        await apiFetch(`/teams/${encodeURIComponent(id)}/cancel-request`, { method: 'POST' });
        showToast(_tr('已取消入队申请。'));
      }
      window.setTimeout(refresh, 700);
    } catch (err) {
      btn.disabled = false;
      showToast(err.message || _tr('操作失败'), 'danger');
    }
  }

export function teamDisplayTitle(team) {
    team = contentView(team);
    const parsed = parseTeamTitle(team?.title || '');
    const baseTitle = parsed.title || team?.title || _tr('未命名队伍');
    if (team?.activityId && team?.activityTitle) {
      return _ui`${baseTitle}（来自活动：${team.activityTitle}）`;
    }
    return baseTitle;
  }

export function teamManageHref(teamId) {
    return withFrom(`team-success.html?teamId=${encodeURIComponent(teamId)}`);
  }

export function teamPrimaryHref(team, viewerId) {
    if (team && String(team.creatorId || '') && String(team.creatorId) === String(viewerId || '')) {
      return teamManageHref(team.id);
    }
    return withFrom(`chat.html?teamId=${encodeURIComponent(team.id)}`);
  }

export function formatProfileGrade(grade) {
    const g = String(grade || '').trim();
    if (!g) return '';
    return _ui`${g.replace(/级$/, '')}级`;
  }

export function profileSummaryParts(profile) {
    if (!profile) return [];
    const parts = [];
    const edu = educationLabel(profile.educationLevel);
    const grade = formatProfileGrade(profile.grade);
    if (edu) parts.push(edu);
    if (profile.college) parts.push(profile.college);
    if (profile.campus) parts.push(contentField(profile, 'campus'));
    if (profile.major) parts.push(profile.major);
    if (grade) parts.push(grade);
    return parts;
  }

export const PROFILE_DRAWER_PREVIEW_LIMIT = 8;

export function buildProfileRecordPageHref(kind) {
    return withFrom(`profile-records.html?kind=${encodeURIComponent(kind)}`, 'profile.html');
  }

export function renderProfileDrawerContent(items, emptyMessage, moreHref) {
    const preview = items.slice(0, PROFILE_DRAWER_PREVIEW_LIMIT);
    const listHtml = preview.join('');
    const moreHtml = items.length > PROFILE_DRAWER_PREVIEW_LIMIT ? _ui`
      <div style="padding: 16px 18px 6px;">
        <a href="${moreHref}" class="primary-btn" style="width: 100%;">查看更多</a>
      </div>
    ` : '';
    return listHtml ? `${listHtml}${moreHtml}` : `<div style="padding: 18px 24px; color: var(--text-secondary);">${escapeHtml(emptyMessage)}</div>`;
  }

export async function openTeamDetailModal(team, me) {
    const detail = contentView(await apiFetch(`/teams/${encodeURIComponent(team.id)}`));
    const full = detail.maxMembers > 0 && Array.isArray(detail.members) && detail.members.length >= detail.maxMembers;
    const joined = !!team.joined;
    const pending = !!team.pending;
    const isCreator = !!(me && String(detail.creatorId || '') === String(me.id));
    const title = teamDisplayTitle(detail);
    const parsed = parseTeamTitle(detail.title);
    const badge = parsed.category || _tr('组队');
    const creatorProfile = detail.creatorProfile || null;
    const profiles = creatorProfile ? [{ key: 'creator', label: _tr('队伍发起人'), profile: creatorProfile }] : [];

    const renderProfileCard = ({ label, profile }) => {
      const summary = profileSummaryParts(profile).join(' · ') || _tr('暂未完善公开资料');
      const avatar = profile.avatarUrl || 'assets/images/avatar.svg';
      return `
        <div class="stack-card" style="padding: 16px; box-shadow:none;">
          <div class="stack-card-meta">${escapeHtml(label)}</div>
          <div style="display:flex; gap:14px; align-items:flex-start; margin-top: 10px;">
            <div style="width:64px; height:64px; border-radius:20px; overflow:hidden; border:1px solid rgba(0,0,0,0.05); flex-shrink:0;">
              <img src="${escapeHtml(avatar)}" alt="avatar" style="width:100%; height:100%; object-fit:cover;">
            </div>
            <div style="min-width:0; flex:1;">
              <div style="font-size:18px; font-weight:750; margin-bottom: 4px;">${escapeHtml(profile.nickname || _tr('未命名用户'))}</div>
              <div class="stack-card-meta">${escapeHtml(summary)}</div>
              <div style="font-size:14px; color:var(--text-primary); line-height:1.7; margin-top: 10px;">${escapeHtml(profile.bio || _tr('TA 暂未填写更多公开介绍。'))}</div>
            </div>
          </div>
        </div>
      `;
    };

    let primaryActionHtml = '';
    if (isCreator) {
      primaryActionHtml = _ui`<a href="${teamManageHref(detail.id)}" class="primary-btn">队长管理</a>`;
    } else if (joined) {
      primaryActionHtml = _ui`<a href="${teamPrimaryHref(detail, me && me.id)}" class="primary-btn">进入交流</a>`;
    } else if (pending) {
      primaryActionHtml = _ui`<button type="button" class="secondary-btn" disabled style="opacity:0.7;">申请审核中</button>`;
    } else {
      primaryActionHtml = `<button type="button" class="primary-btn js-team-detail-apply" ${full ? 'disabled' : ''}>${full ? _tr('名额已满') : _tr('申请加入')}</button>`;
    }

    const contactActions = [];
    if (creatorProfile?.userId) {
      contactActions.push(_ui`<a href="${withFrom(`chat.html?userId=${encodeURIComponent(creatorProfile.userId)}`)}" class="secondary-btn">联系队伍发起人</a>`);
    }
    contactActions.push(primaryActionHtml);

    openUtilityModal({
      title: _tr('组队详情'),
      maxWidth: 760,
      html: _ui`
        <div style="display:flex; justify-content:space-between; align-items:flex-start; gap: 14px; margin-bottom: 18px;">
          <div>
            <div style="font-size: 26px; font-weight: 800; line-height: 1.35;">${escapeHtml(title)}</div>
            ${detail.activityTitle ? _ui`<div class="stack-card-meta" style="margin-top: 6px;">关联活动：${escapeHtml(contentField(detail, 'activityTitle'))}</div>` : ''}
          </div>
          <span class="badge-pill">${escapeHtml(badge)}</span>
        </div>
        <div class="stack-card" style="padding: 16px; box-shadow:none; margin-bottom: 14px;">
          <div class="stack-card-meta">队伍概览</div>
          <div style="display:grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; margin-top: 10px;">
            <div class="field-hint">当前人数：${escapeHtml(formatTeamMemberLimit((detail.members || []).length, detail.maxMembers))}</div>
            <div class="field-hint">时间窗口：${escapeHtml(detail.startTime ? formatDateTime(detail.startTime) : _tr('未设置'))} · ${escapeHtml(detail.endTime ? formatDateTime(detail.endTime) : _tr('未设置'))}</div>
          </div>
        </div>
        <div class="stack-card" style="padding: 16px; box-shadow:none; margin-bottom: 14px;">
          <div class="stack-card-meta">内容介绍</div>
          <div style="font-size:15px; color:var(--text-primary); line-height:1.8; margin-top: 10px;">${escapeHtml(contentField(detail, 'description') || _tr('队伍发起者暂未填写更多说明。'))}</div>
        </div>
        <div style="display:grid; gap: 12px; margin-bottom: 16px;">
          ${profiles.map(renderProfileCard).join('')}
        </div>
        <div class="utility-actions">
          ${contactActions.join('')}
        </div>
      `,
      onOpen(modal, close) {
        mountTeamLifecycle(detail, me, modal.querySelector('#utilityModalBody'));
        modal.querySelector('.js-team-detail-apply')?.addEventListener('click', () => {
          close();
          openJoinTeamModal({
            teamId: detail.id,
            teamTitle: title,
            onSuccess: () => window.location.reload(),
          });
        });
      },
    });
  }

export function renderHomeTeamCards(container, teams, emptyMessage = _tr('暂无推荐组队')) {
    if (!container) return;
    if (!teams || teams.length === 0) {
      container.innerHTML = renderEmptyCard(emptyMessage);
      return;
    }
    const viewerId = window.__meCache && window.__meCache.id;
    container.innerHTML = teams.map((original) => {
      const t = contentView(original);
      const parsed = parseTeamTitle(t.title);
      const category = parsed.category || _tr('组队');
      const displayTitle = teamDisplayTitle(t);
      const full = t.maxMembers > 0 && t.members >= t.maxMembers;
      const joined = !!t.joined;
      const pending = !!t.pending;
      let actionHtml = '';
      if (String(t.creatorId || '') === String(viewerId || '')) {
        actionHtml = _ui`<a href="${teamManageHref(t.id)}" class="primary-btn">队长管理</a>`;
      } else if (joined) {
        actionHtml = _ui`<a href="${teamPrimaryHref(t, viewerId)}" class="primary-btn">进入交流</a>`;
      } else if (pending) {
        actionHtml = _ui`
          ${t.creatorId ? _ui`<a href="${withFrom(`chat.html?userId=${encodeURIComponent(t.creatorId)}`)}" class="secondary-btn">联系发起者</a>` : ''}
          <button class="secondary-btn" type="button" disabled style="opacity: 0.7;">申请审核中</button>
        `;
      } else {
        actionHtml = `
          ${t.creatorId ? _ui`<a href="${withFrom(`chat.html?userId=${encodeURIComponent(t.creatorId)}`)}" class="secondary-btn">联系发起者</a>` : ''}
          <button class="primary-btn home-team-apply-btn" type="button" data-team-id="${t.id}" data-team-title="${escapeHtml(displayTitle)}" ${full ? 'disabled' : ''}>${full ? _tr('名额已满') : _tr('申请加入')}</button>
        `;
      }
      return _ui`
        <div class="home-team-card" data-team-id="${t.id}" data-promoted="${t.promoted ? '1' : '0'}">
          <div class="home-team-top">
            <div>
              <div class="home-team-title">${escapeHtml(displayTitle)}</div>
              <div class="home-team-sub">类型：${escapeHtml(category)} · 发起人：${escapeHtml(t.creatorName)} · ${escapeHtml(formatTeamMemberLimit(t.members, t.maxMembers))}</div>
            </div>
            <span class="badge-pill">${escapeHtml(category)}</span>
          </div>
          ${(t.startTime || t.endTime) ? _ui`<div class="home-team-sub" style="margin-top: 8px;">时间窗口：${escapeHtml(t.startTime ? formatDateTime(t.startTime) : _tr('长期有效'))} · ${escapeHtml(t.endTime ? formatDateTime(t.endTime) : _tr('未设置截止'))}</div>` : ''}
          <div class="home-team-desc">${escapeHtml(contentField(t, 'description') || _tr('队伍发起者暂未填写更多说明。'))}</div>
          <div class="home-team-actions">${actionHtml}</div>
        </div>
      `;
    }).join('');
    qsa('.home-team-apply-btn').forEach((btn) => {
      btn.addEventListener('click', () => {
        const teamId = Number(btn.getAttribute('data-team-id') || '0');
        const teamTitle = btn.getAttribute('data-team-title') || '';
        if (!teamId) return;
        logBehavior({ targetType: 'TEAM', targetId: teamId, eventType: 'TEAM_APPLY', scene: 'home_team_card' });
        openJoinTeamModal({
          teamId,
          teamTitle,
          onSuccess: () => window.location.reload(),
        });
      });
    });
    qsa('.home-team-card').forEach((card, index) => {
      const teamId = Number(card.getAttribute('data-team-id') || '0');
      if (teamId) {
        logBehavior({
          targetType: 'TEAM',
          targetId: teamId,
          eventType: card.getAttribute('data-promoted') === '1' ? 'FEATURED_IMPRESSION' : 'IMPRESSION',
          scene: 'home_team_card',
          rankPosition: index + 1,
        });
      }
    });
  }

export async function logEvent(activityId, eventType, extraJson) {
    if (!["IMPRESSION", "CLICK", "DETAIL_VIEW", "SEARCH_CLICK", "FEATURED_IMPRESSION", "FEATURED_CLICK"].includes(eventType)) return;
    if (!isLoggedIn()) return;
    try {
      await apiFetch('/events', {
        method: 'POST',
        body: JSON.stringify({ activityId, eventType, extraJson: extraJson || null }),
      });
    } catch (e) {}
  }

export async function logBehavior(payload) {
    if (!["IMPRESSION", "CLICK", "DETAIL_VIEW", "SEARCH_CLICK", "FEATURED_IMPRESSION", "FEATURED_CLICK", "SEARCH"].includes(payload.eventType)) return;
    if (!isLoggedIn() || !payload) return;
    try {
      await apiFetch('/behavior', {
        method: 'POST',
        body: JSON.stringify(payload),
      });
    } catch (e) {}
  }

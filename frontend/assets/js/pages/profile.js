import { contentField } from '../core/content-i18n.js';
import { t as _tr, ui as _ui, translateStatic as _static } from '../core/i18n.js';
import { setToken, requireAuth } from './../core/auth.js';
import { qs, qsa, escapeHtml, showSuccessMessage } from './../core/ui.js';
import { withFrom } from './../core/navigation.js';
import { apiFetch, uploadFile } from './../core/api.js';
import { mountBottomNavActive, deriveGradeLabel, educationLabel, formatTeamMemberLimit, profileActivityStatusBadge, profileTeamStatusBadge, profileListItem, profileInlineActionButton, handleProfileInlineAction, buildProfileRecordPageHref, renderProfileDrawerContent } from './../core/render.js';

export async function initProfile() {
    mountBottomNavActive();
    requireAuth();
    const me = await apiFetch('/auth/me');
    const nameEl = qs('#profileName');
    const subEl = qs('#profileSub');
    const avatarImg = qs('#profileAvatarImg');
    if (nameEl) nameEl.textContent = me.nickname;
    if (subEl) {
      const parts = [];
      const edu = educationLabel(me.educationLevel);
      if (edu) parts.push(edu);
      if (me.campus) parts.push(contentField(me, 'campus'));
      parts.push(me.college || _tr('未填写学院'));
      const g = deriveGradeLabel(me.studentNo);
      if (g) parts.push(g);
      parts.push(me.studentNo || _tr('未填写学号'));
      subEl.textContent = parts.join(' · ');
    }
    if (avatarImg && me.avatarUrl) avatarImg.src = me.avatarUrl;

    const emailStatus = qs('#emailStatus');
    if (emailStatus) {
      if (me.email && me.emailVerified) emailStatus.textContent = _tr('已绑定');
      else if (me.email && !me.emailVerified) emailStatus.textContent = _tr('未验证');
      else emailStatus.textContent = _tr('未绑定');
    }

    const [regs, joinedTeams, createdTeams, favs] = await Promise.all([
      apiFetch('/profile/registrations'),
      apiFetch('/profile/teams-joined'),
      apiFetch('/profile/teams-created'),
      apiFetch('/profile/favorites'),
    ]);

    const actCount = qs('#profileActCount');
    const teamCount = qs('#profileTeamCount');
    const favCount = qs('#profileFavCount');
    if (actCount) actCount.textContent = String((regs || []).length) + ((regs || []).length >= 100 ? "+" : "");
    if (teamCount) teamCount.textContent = String((joinedTeams || []).length) + ((joinedTeams || []).length >= 100 ? "+" : "");
    if (favCount) favCount.textContent = String((favs || []).length) + ((favs || []).length >= 100 ? "+" : "");

    const logoutBtn = qs('#logoutBtn');
    if (logoutBtn) {
      logoutBtn.addEventListener('click', async () => {
        try { await apiFetch('/auth/logout-all', { method: 'POST' }); } catch (error) { if (error.status !== 401) { showMessage(error.message); return; } }
        setToken('');
        window.location.href = 'index.html';
      });
    }

    const myActBtn = qs('#myActivitiesBtn');
    const myPublishedBtn = qs('#myPublishedBtn');
    const myJoinedTeamBtn = qs('#myJoinedTeamsBtn');
    const myCreatedTeamBtn = qs('#myCreatedTeamsBtn');
    const myFavBtn = qs('#myFavoritesBtn');
    const bindEmailBtn = qs('#bindEmailBtn');
    const changePwdBtn = qs('#changePwdBtn');
    const editProfileBtn = qs('#editProfileBtn');
    const adminCenterBtn = qs('#adminCenterBtn');
    const drawer = qs('#profileDrawer');
    const drawerTitle = qs('#drawerTitle');
    const drawerBody = qs('#drawerBody');
    const closeDrawerBtn = qs('#closeDrawerBtn');

    const openDrawer = (title, html) => {
      if (drawerTitle) drawerTitle.textContent = title;
      if (drawerBody) drawerBody.innerHTML = html;
      if (drawer) drawer.style.display = 'flex';
    };
    const closeDrawer = () => {
      if (drawer) drawer.style.display = 'none';
    };
    if (closeDrawerBtn) closeDrawerBtn.addEventListener('click', closeDrawer);
    if (drawer) drawer.addEventListener('click', (e) => {
      if (e.target === drawer) closeDrawer();
    });
    if (drawerBody) {
      drawerBody.addEventListener('click', async (e) => {
        const btn = e.target.closest('.profile-inline-action');
        if (!btn) return;
        e.preventDefault();
        e.stopPropagation();
        await handleProfileInlineAction(btn);
      });
    }

    if (myActBtn) {
      myActBtn.addEventListener('click', (e) => {
        e.preventDefault();
        const items = (regs || [])
          .map((r) => {
            const status = String(r.status || '').toUpperCase();
            return profileListItem({
              href: withFrom(`activity-detail.html?id=${encodeURIComponent(r.activity.id)}`, 'profile.html'),
              icon: 'fa-solid fa-calendar-check',
              title: contentField(r.activity, 'title'),
              badge: profileActivityStatusBadge(r.status),
              actionHtml: (status === 'APPLIED' || status === 'PENDING')
                ? profileInlineActionButton(_tr('撤回报名'), 'cancel-activity-registration', r.activity.id)
                : '',
            });
          });
        openDrawer(_tr('我参加的活动'), renderProfileDrawerContent(items, _tr('暂无报名记录'), buildProfileRecordPageHref('activities')));
      });
    }
    if (myJoinedTeamBtn) {
      myJoinedTeamBtn.addEventListener('click', (e) => {
        e.preventDefault();
        const items = (joinedTeams || [])
          .map((t) => {
            const pending = String(t.participationStatus || '').toUpperCase() === 'PENDING';
            const href = pending
              ? withFrom(`team-lobby.html?teamId=${encodeURIComponent(t.id)}`, 'profile.html')
              : withFrom(`chat.html?teamId=${encodeURIComponent(t.id)}`, 'profile.html');
            return profileListItem({
              href,
              icon: 'fa-solid fa-user-group',
              title: contentField(t, 'title'),
              sub: formatTeamMemberLimit(t.members, t.maxMembers),
              badge: profileTeamStatusBadge(t),
              actionHtml: pending ? profileInlineActionButton(_tr('取消申请'), 'cancel-team-request', t.id) : '',
            });
          });
        openDrawer(_tr('我参加的组队'), renderProfileDrawerContent(items, _tr('暂无参与中的队伍'), buildProfileRecordPageHref('teams-joined')));
      });
    }
    if (myCreatedTeamBtn) {
      myCreatedTeamBtn.addEventListener('click', (e) => {
        e.preventDefault();
        const items = (createdTeams || [])
          .map((t) => {
            const href = withFrom(`team-success.html?teamId=${encodeURIComponent(t.id)}`, 'profile.html');
            return _ui`<a class="menu-item" href="${href}"><div class="menu-item-left"><i class="fa-solid fa-user-group"></i>${escapeHtml(contentField(t, 'title'))}</div><span style="color: var(--text-secondary); font-size: 12px;">进入管理</span></a>`;
          });
        openDrawer(_tr('我发起的组队'), renderProfileDrawerContent(items, _tr('暂无你发起的队伍'), buildProfileRecordPageHref('teams-created')));
      });
    }
    if (myFavBtn) {
      myFavBtn.addEventListener('click', (e) => {
        e.preventDefault();
        const html = (favs || [])
          .map((a) => {
            return `<a class="menu-item" href="${withFrom(`activity-detail.html?id=${encodeURIComponent(a.id)}`, 'profile.html')}"><div class="menu-item-left"><i class="fa-regular fa-bookmark"></i>${escapeHtml(contentField(a, 'title'))}</div><i class="fa-solid fa-chevron-right"></i></a>`;
          })
          .join('');
        openDrawer(_tr('我的收藏'), (html ? `${html}<a class="primary-btn" href="${buildProfileRecordPageHref('favorites')}">${_tr('查看更多')}</a>` : '') || _ui`<div style="padding: 18px 24px; color: var(--text-secondary);">暂无收藏</div>`);
      });
    }
    if (myPublishedBtn) {
      myPublishedBtn.addEventListener('click', async (e) => {
        e.preventDefault();
        const pubs = await apiFetch('/profile/published');
        const items = (pubs || [])
          .map((a) => _ui`<a class="menu-item" href="${withFrom(`activity-manage.html?id=${encodeURIComponent(a.id)}`, 'profile.html')}"><div class="menu-item-left"><i class="fa-solid fa-bullhorn"></i>${escapeHtml(contentField(a, 'title'))}</div><span style="color: var(--text-secondary); font-size: 12px;">进入管理</span></a>`)
        ;
        openDrawer(_tr('我发布的活动'), renderProfileDrawerContent(items, _tr('暂无发布活动'), buildProfileRecordPageHref('published')));
      });
    }

    if (adminCenterBtn) {
      adminCenterBtn.style.display = me.role === 'ADMIN' ? '' : 'none';
    }

    if (editProfileBtn) {
      editProfileBtn.addEventListener('click', () => {});
    }

    const startCountdown = (button, seconds) => {
      if (!button) return;
      let left = seconds;
      button.disabled = true;
      const originText = button.textContent;
      button.textContent = `${left}s`;
      const timer = setInterval(() => {
        left -= 1;
        if (left <= 0) {
          clearInterval(timer);
          button.disabled = false;
          button.textContent = originText;
        } else {
          button.textContent = `${left}s`;
        }
      }, 1000);
    };

    if (bindEmailBtn) {
      bindEmailBtn.addEventListener('click', (e) => {
        e.preventDefault();
        const current = me.email ? `${me.email}${me.emailVerified ? _tr('（已绑定）') : _tr('（未验证）')}` : _tr('未绑定');
        if (me.emailVerified) {
          const html = _ui`
            <div style="padding: 18px 24px;">
              <div style="font-size: 13px; color: var(--text-secondary); margin-bottom: 10px;">当前：${escapeHtml(current)}</div>
              <div style="font-size: 13px; color: var(--text-secondary); margin-bottom: 14px;">可选择换绑到新邮箱，换绑需同时验证原邮箱和新邮箱。</div>

              <div style="background: rgba(0,0,0,0.03); border: 1px solid rgba(0,0,0,0.05); border-radius: 18px; padding: 14px 14px;">
                <div style="font-size: 14px; font-weight: 750; margin-bottom: 10px;">验证原邮箱</div>
                <div style="display:flex; gap: 10px;">
                  <input id="oldEmailCode" type="text" placeholder="6位验证码" inputmode="numeric" style="flex:1;">
                  <button id="oldEmailSendBtn" class="secondary-btn" style="width:auto; padding: 12px 14px; font-size: 14px; border-radius: 14px;">发送验证码</button>
                </div>
              </div>

              <div style="height: 12px;"></div>

              <div style="background: rgba(0,0,0,0.03); border: 1px solid rgba(0,0,0,0.05); border-radius: 18px; padding: 14px 14px;">
                <div style="font-size: 14px; font-weight: 750; margin-bottom: 10px;">验证新邮箱</div>
                <input id="newEmailInput" type="email" placeholder="输入新邮箱" autocomplete="email">
                <div style="display:flex; gap: 10px; margin-top: 10px;">
                  <input id="newEmailCode" type="text" placeholder="6位验证码" inputmode="numeric" style="flex:1;">
                  <button id="newEmailSendBtn" class="secondary-btn" style="width:auto; padding: 12px 14px; font-size: 14px; border-radius: 14px;">发送验证码</button>
                </div>
              </div>

              <div id="bindEmailHint" style="font-size: 12px; color: var(--text-secondary); margin-top: 12px;"></div>
              <button id="changeEmailSubmitBtn" class="primary-btn" style="width: 100%; margin-top: 14px;">确认换绑</button>
            </div>
          `;
          openDrawer(_tr('换绑邮箱'), html);

          const hint = qs('#bindEmailHint');
          const oldSendBtn = qs('#oldEmailSendBtn');
          const newSendBtn = qs('#newEmailSendBtn');
          const submitBtn = qs('#changeEmailSubmitBtn');
          const oldCodeInput = qs('#oldEmailCode');
          const newEmailInput = qs('#newEmailInput');
          const newCodeInput = qs('#newEmailCode');

          if (oldSendBtn) {
            oldSendBtn.addEventListener('click', async (ev) => {
              ev.preventDefault();
              try {
                const data = await apiFetch('/auth/me/email/change/send-old', { method: 'POST', body: '{}' });
                startCountdown(oldSendBtn, 60);
                if (hint) hint.textContent = data.devCode ? _ui`原邮箱验证码已发送（开发码：${data.devCode}）` : _tr('原邮箱验证码已发送');
              } catch (err) {
                if (hint) hint.textContent = err.message || _tr('发送失败');
              }
            });
          }
          if (newSendBtn) {
            newSendBtn.addEventListener('click', async (ev) => {
              ev.preventDefault();
              const email = (newEmailInput?.value || '').trim();
              if (!email) {
                if (hint) hint.textContent = _tr('请输入新邮箱');
                return;
              }
              try {
                const data = await apiFetch('/auth/me/email/change/send-new', { method: 'POST', body: JSON.stringify({ email }) });
                startCountdown(newSendBtn, 60);
                if (hint) hint.textContent = data.devCode ? _ui`新邮箱验证码已发送（开发码：${data.devCode}）` : _tr('新邮箱验证码已发送');
              } catch (err) {
                if (hint) hint.textContent = err.message || _tr('发送失败');
              }
            });
          }
          if (submitBtn) {
            submitBtn.addEventListener('click', async (ev) => {
              ev.preventDefault();
              const oldCode = (oldCodeInput?.value || '').trim();
              const newEmail = (newEmailInput?.value || '').trim();
              const newCode = (newCodeInput?.value || '').trim();
              if (!oldCode || !newEmail || !newCode) {
                if (hint) hint.textContent = _tr('请完整填写原邮箱验证码、新邮箱和新验证码');
                return;
              }
              try {
                await apiFetch('/auth/me/email/change', { method: 'PUT', body: JSON.stringify({ oldCode, newEmail, newCode }) });
                showSuccessMessage(_tr('换绑成功'), _tr('邮箱已更新，后续验证码会发送到新邮箱。'), _tr('我知道了'), () => window.location.reload());
              } catch (err) {
                if (hint) hint.textContent = err.message || _tr('换绑失败');
              }
            });
          }
          return;
        }
        const html = _ui`
          <div style="padding: 18px 24px;">
            <div style="font-size: 13px; color: var(--text-secondary); margin-bottom: 10px;">当前：${escapeHtml(current)}</div>
            <input id="bindEmailInput" type="email" placeholder="输入邮箱" autocomplete="email">
            <div style="display:flex; gap: 10px; margin-top: 10px;">
              <input id="bindEmailCode" type="text" placeholder="6位验证码" inputmode="numeric" style="flex:1;">
              <button id="bindEmailSendBtn" class="secondary-btn" style="width:auto; padding: 12px 14px; font-size: 14px; border-radius: 14px;">发送验证码</button>
            </div>
            <div id="bindEmailHint" style="font-size: 12px; color: var(--text-secondary); margin-top: 10px;"></div>
            <button id="bindEmailSubmitBtn" class="primary-btn" style="width: 100%; margin-top: 14px;">确认绑定</button>
          </div>
        `;
        openDrawer(_tr('绑定邮箱'), html);

        const sendBtn = qs('#bindEmailSendBtn');
        const submitBtn = qs('#bindEmailSubmitBtn');
        const hint = qs('#bindEmailHint');
        if (sendBtn) {
          sendBtn.addEventListener('click', async (ev) => {
            ev.preventDefault();
            const email = (qs('#bindEmailInput')?.value || '').trim();
            if (!email) {
              if (hint) hint.textContent = _tr('请输入邮箱');
              return;
            }
            try {
              const data = await apiFetch('/auth/email/send', { method: 'POST', body: JSON.stringify({ email, purpose: 'BIND' }) });
              startCountdown(sendBtn, 60);
              if (hint) hint.textContent = data.devCode ? _ui`验证码已发送（开发码：${data.devCode}）` : _tr('验证码已发送');
            } catch (err) {
              if (hint) hint.textContent = err.message || _tr('发送失败');
            }
          });
        }
        if (submitBtn) {
          submitBtn.addEventListener('click', async (ev) => {
            ev.preventDefault();
            const email = (qs('#bindEmailInput')?.value || '').trim();
            const code = (qs('#bindEmailCode')?.value || '').trim();
            if (!email || !code) {
              if (hint) hint.textContent = _tr('请填写邮箱和验证码');
              return;
            }
            try {
              await apiFetch('/auth/me/email/bind', { method: 'PUT', body: JSON.stringify({ email, code }) });
              showSuccessMessage(_tr('绑定成功'), _tr('邮箱已绑定成功，现在可以使用邮箱验证码登录、找回密码和修改密码。'), _tr('开始使用'), () => window.location.reload());
            } catch (err) {
              if (hint) hint.textContent = err.message || _tr('绑定失败');
            }
          });
        }
      });
    }

    function strengthHtml(pwd) {
      const p = String(pwd || '');
      const hasLower = /[a-z]/.test(p);
      const hasUpper = /[A-Z]/.test(p);
      const hasDigit = /[0-9]/.test(p);
      const hasSpecial = /[^A-Za-z0-9]/.test(p);
      const ok = p.length >= 8 && hasLower && hasUpper && hasDigit && hasSpecial;
      const color = ok ? 'var(--success-color)' : p.length >= 8 ? 'var(--accent-color)' : 'var(--danger-color)';
      const percent = ok ? 100 : Math.min(100, (p.length / 12) * 100);
      return _ui`
        <div style="display:flex; align-items:center; justify-content: space-between; margin-bottom: 6px;">
          <div style="font-size: 12px; color: var(--text-secondary);">密码要求</div>
          <div style="font-size: 12px; color: ${color}; font-weight: 650;">${ok ? _tr('符合规则') : _tr('密码不符合规则')}</div>
        </div>
        <div style="height: 8px; background: rgba(0,0,0,0.06); border-radius: 999px; overflow:hidden;">
          <div style="width:${percent}%; height: 8px; background:${color}; border-radius: 999px;"></div>
        </div>
        <div style="display:flex; gap: 8px; flex-wrap: wrap; margin-top: 10px; font-size: 12px; color: var(--text-secondary);">
          <span style="padding:4px 10px; border-radius: 999px; background: ${p.length >= 8 ? 'rgba(52,199,89,0.12)' : 'rgba(255,59,48,0.10)'};">8–128${_tr('个字符')}</span>
          <span style="padding:4px 10px; border-radius: 999px; background: ${hasLower ? 'rgba(52,199,89,0.12)' : 'rgba(255,59,48,0.10)'};">小写</span>
          <span style="padding:4px 10px; border-radius: 999px; background: ${hasUpper ? 'rgba(52,199,89,0.12)' : 'rgba(255,59,48,0.10)'};">大写</span>
          <span style="padding:4px 10px; border-radius: 999px; background: ${hasDigit ? 'rgba(52,199,89,0.12)' : 'rgba(255,59,48,0.10)'};">数字</span>
          <span style="padding:4px 10px; border-radius: 999px; background: ${hasSpecial ? 'rgba(52,199,89,0.12)' : 'rgba(255,59,48,0.10)'};">特殊字符</span>
        </div>
      `;
    }

    if (changePwdBtn) {
      changePwdBtn.addEventListener('click', (e) => {
        e.preventDefault();
        if (!me.email || !me.emailVerified) {
          alert(_tr('修改密码需要先绑定邮箱并完成验证'));
          return;
        }
        const html = _ui`
          <div style="padding: 18px 24px;">
            <div style="font-size: 13px; color: var(--text-secondary); margin-bottom: 10px;">验证码将发送至：${escapeHtml(me.email)}</div>
            <div style="display:flex; gap: 10px;">
              <input id="changePwdCode" type="text" placeholder="6位验证码" inputmode="numeric" style="flex:1;">
              <button id="changePwdSendBtn" class="secondary-btn" style="width:auto; padding: 12px 14px; font-size: 14px; border-radius: 14px;">发送验证码</button>
            </div>
            <div id="pwdChangeFields" style="margin-top: 12px; opacity: 0.45; pointer-events: none;">
              <div style="position: relative;">
                <input id="newPwd" type="password" maxlength="128" placeholder="新密码" autocomplete="new-password" style="padding-right: 46px;">
                <i id="toggleNewPwd" class="fa-regular fa-eye" style="position:absolute; right: 14px; top: 50%; transform: translateY(-50%); color: var(--text-secondary); cursor:pointer;"></i>
              </div>
              <div id="newPwdStrength" style="margin-top: 10px;">${strengthHtml('')}</div>
              <input id="newPwd2" type="password" maxlength="128" placeholder="确认新密码" autocomplete="new-password" style="margin-top: 10px;">
            </div>
            <div id="changePwdHint" style="font-size: 12px; color: var(--text-secondary); margin-top: 10px;"></div>
            <button id="changePwdSubmit" class="primary-btn" style="width: 100%; margin-top: 14px;">确认修改</button>
          </div>
        `;
        openDrawer(_tr('修改密码'), html);
        const hint = qs('#changePwdHint');
        const codeInput = qs('#changePwdCode');
        const sendBtn = qs('#changePwdSendBtn');
        const newPwd = qs('#newPwd');
        const newPwd2 = qs('#newPwd2');
        const strength = qs('#newPwdStrength');
        const submit = qs('#changePwdSubmit');
        const fields = qs('#pwdChangeFields');

        const toggle = (btnId, input) => {
          const b = qs(btnId);
          if (!b || !input) return;
          b.addEventListener('click', () => {
            const isPwd = input.getAttribute('type') === 'password';
            input.setAttribute('type', isPwd ? 'text' : 'password');
            b.className = isPwd ? 'fa-regular fa-eye-slash' : 'fa-regular fa-eye';
          });
        };
        toggle('#toggleNewPwd', newPwd);

        if (newPwd) {
          newPwd.addEventListener('input', () => { if (strength) strength.innerHTML = strengthHtml(newPwd.value); });
        }
        const startCountdown = (button, seconds) => {
          if (!button) return;
          let left = seconds;
          button.disabled = true;
          const originText = button.textContent;
          button.textContent = `${left}s`;
          const timer = setInterval(() => {
            left -= 1;
            if (left <= 0) {
              clearInterval(timer);
              button.disabled = false;
              button.textContent = originText;
            } else {
              button.textContent = `${left}s`;
            }
          }, 1000);
        };
        if (sendBtn) {
          sendBtn.addEventListener('click', async (ev) => {
            ev.preventDefault();
            try {
              const data = await apiFetch('/auth/me/password/email/send', { method: 'POST', body: '{}' });
              startCountdown(sendBtn, 60);
              if (hint) hint.textContent = data.devCode ? _ui`验证码已发送（开发码：${data.devCode}）` : _tr('验证码已发送');
              if (fields) {
                fields.style.opacity = '1';
                fields.style.pointerEvents = 'auto';
              }
            } catch (err) {
              if (hint) hint.textContent = err.message || _tr('发送失败');
            }
          });
        }
        if (submit) {
          submit.addEventListener('click', async (ev) => {
            ev.preventDefault();
            const code = (codeInput?.value || '').trim();
            const n = (newPwd?.value || '');
            const n2 = (newPwd2?.value || '');
            if (!code || !n || !n2) {
              if (hint) hint.textContent = _tr('请完整填写信息');
              return;
            }
            if (n !== n2) {
              if (hint) hint.textContent = _tr('两次输入的新密码不一致');
              return;
            }
            try {
              await apiFetch('/auth/me/password/change-email', { method: 'POST', body: JSON.stringify({ code, newPassword: n }) });
              showSuccessMessage(_tr('密码已更新'), _tr('新密码已生效，请使用新密码继续登录。'), _tr('完成'), () => { setToken(''); window.location.href = 'login.html'; });
            } catch (err) {
              if (hint) hint.textContent = err.message || _tr('修改失败');
            }
          });
        }
      });
    }

    // change avatar
    const changeAvatarBtn = qs('#changeAvatarBtn');
    if (changeAvatarBtn) {
      changeAvatarBtn.addEventListener('click', async () => {
        const list = await apiFetch('/profile/default-avatars');
        const grid = (list || []).map((u) => `<div class="avatar-option" data-url="${escapeHtml(u)}" style="display:inline-block; margin:8px; border:2px solid transparent; border-radius:16px; padding:4px; cursor:pointer;"><img src="${escapeHtml(u)}" style="width:72px; height:72px; border-radius:16px; object-fit:cover;"></div>`).join('');
        const html = _ui`
          <div style="padding: 18px 24px;">
            <input id="drawerAvatarFile" type="file" accept="image/*" style="display:none;">
            <button id="drawerAvatarUploadBtn" class="primary-btn" style="width:auto; padding: 10px 16px; font-size: 14px;">上传图片</button>
            <span id="drawerAvatarHint" style="font-size:12px; color: var(--text-secondary); margin-left: 8px;"></span>
          </div>
          <div style="padding: 0 24px 10px; color: var(--text-secondary); font-size: 13px;">或选择默认头像</div>
          <div style="padding: 0 12px 12px;">${grid || ''}</div>
        `;
        openDrawer(_tr('更换头像'), html || _ui`<div style="padding: 18px 24px; color: var(--text-secondary);">暂无可选头像</div>`);

        const uploadBtn = qs('#drawerAvatarUploadBtn');
        const fileInput = qs('#drawerAvatarFile');
        const hint = qs('#drawerAvatarHint');
        if (uploadBtn && fileInput) {
          uploadBtn.addEventListener('click', () => fileInput.click());
          fileInput.addEventListener('change', async (e) => {
            const file = e.target.files && e.target.files[0];
            if (!file) return;
            if (hint) hint.textContent = _tr('上传中...');
            try {
              const url = (await uploadFile('/upload/avatar', file)).url;
              await apiFetch('/profile/avatar', { method: 'PUT', body: JSON.stringify({ avatarUrl: url }) });
              if (hint) hint.textContent = _tr('已更新');
              window.location.reload();
            } catch (err) {
              if (hint) hint.textContent = _tr('上传失败');
            }
          });
        }

        qsa('.avatar-option').forEach((el) => {
          el.addEventListener('click', async () => {
            qsa('.avatar-option').forEach((e) => (e.style.borderColor = 'transparent'));
            el.style.borderColor = 'var(--accent-color)';
            const url = el.getAttribute('data-url') || '';
            await apiFetch('/profile/avatar', { method: 'PUT', body: JSON.stringify({ avatarUrl: url }) });
            alert(_tr('头像已更新'));
            window.location.reload();
          });
        });
      });
    }
  }

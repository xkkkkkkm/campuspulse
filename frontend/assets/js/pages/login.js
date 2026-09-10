import { t as _tr, ui as _ui, translateStatic as _static } from '../core/i18n.js';
import { setToken } from './../core/auth.js';
import { qs, showMessage, showSuccessMessage } from './../core/ui.js';
import { apiFetch } from './../core/api.js';

export async function initLogin() {
    const btn = qs('#loginBtn');
    if (!btn) return;

    const tabPwd = qs('#tabPwd');
    const tabEmail = qs('#tabEmail');
    const pwdPanel = qs('#pwdPanel');
    const emailPanel = qs('#emailPanel');
    const togglePwd = qs('#togglePwd');
    const forgotPwdLink = qs('#forgotPwdLink');

    const emailSendBtn = qs('#emailSendBtn');
    const emailHint = qs('#emailHint');

    const resetModal = qs('#resetModal');
    const resetCloseBtn = qs('#resetCloseBtn');
    const resetSendBtn = qs('#resetSendBtn');
    const resetSubmitBtn = qs('#resetSubmitBtn');
    const resetHint = qs('#resetHint');

    const togglePanel = (type) => {
      const isPwd = type === 'pwd';
      if (pwdPanel) pwdPanel.style.display = isPwd ? '' : 'none';
      if (emailPanel) emailPanel.style.display = isPwd ? 'none' : '';
      if (tabPwd) tabPwd.className = isPwd ? 'primary-btn' : 'secondary-btn';
      if (tabEmail) tabEmail.className = isPwd ? 'secondary-btn' : 'primary-btn';
    };
    if (tabPwd) tabPwd.addEventListener('click', (e) => { e.preventDefault(); togglePanel('pwd'); });
    if (tabEmail) tabEmail.addEventListener('click', (e) => { e.preventDefault(); togglePanel('email'); });
    togglePanel('pwd');

    if (togglePwd) {
      togglePwd.addEventListener('click', () => {
        const input = qs('#loginPassword');
        if (!input) return;
        const isPwd = input.getAttribute('type') === 'password';
        input.setAttribute('type', isPwd ? 'text' : 'password');
        togglePwd.className = isPwd ? 'fa-regular fa-eye-slash' : 'fa-regular fa-eye';
      });
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

    if (emailSendBtn) {
      emailSendBtn.addEventListener('click', async (e) => {
        e.preventDefault();
        const email = (qs('#loginEmail')?.value || '').trim();
        if (!email) {
          if (emailHint) emailHint.textContent = _tr('请输入邮箱');
          return;
        }
        try {
          const data = await apiFetch('/auth/email/send', { method: 'POST', body: JSON.stringify({ email, purpose: 'LOGIN' }) });
          startCountdown(emailSendBtn, 60);
          if (emailHint) emailHint.textContent = data.devCode ? _ui`验证码已发送（开发码：${data.devCode}）` : _tr('验证码已发送');
        } catch (err) {
          if (emailHint) emailHint.textContent = err.message || _tr('发送失败');
        }
      });
    }

    if (forgotPwdLink && resetModal) {
      forgotPwdLink.addEventListener('click', () => {
        resetModal.style.display = 'flex';
        if (resetHint) resetHint.textContent = '';
      });
    }
    if (resetCloseBtn && resetModal) {
      resetCloseBtn.addEventListener('click', () => { resetModal.style.display = 'none'; });
    }
    if (resetModal) {
      resetModal.addEventListener('click', (e) => {
        if (e.target === resetModal) resetModal.style.display = 'none';
      });
    }

    function pwdStrength(pwd) {
      const p = String(pwd || '');
      const len = p.length;
      const hasLower = /[a-z]/.test(p);
      const hasUpper = /[A-Z]/.test(p);
      const hasDigit = /[0-9]/.test(p);
      const hasSpecial = /[^A-Za-z0-9]/.test(p);
      let score = 0;
      if (len >= 8) score += 1;
      if (hasLower) score += 1;
      if (hasUpper) score += 1;
      if (hasDigit) score += 1;
      if (hasSpecial) score += 1;
      score = Math.min(4, Math.floor((score / 5) * 4));
      const ok = len >= 8 && hasLower && hasUpper && hasDigit && hasSpecial;
      const label = ok ? _tr('强') : score >= 3 ? _tr('中') : score >= 2 ? _tr('弱') : _tr('很弱');
      return { score, ok, label, hasLower, hasUpper, hasDigit, hasSpecial, len };
    }

    function renderStrength(container, pwd) {
      const c = container;
      if (!c) return;
      const s = pwdStrength(pwd);
      const percent = s.ok ? 100 : ([10, 35, 60, 85, 100][s.score] || 10);
      const color = s.ok ? 'var(--success-color)' : s.score >= 3 ? 'var(--accent-color)' : 'var(--danger-color)';
      c.innerHTML = _ui`
        <div style="display:flex; align-items:center; justify-content: space-between; margin-bottom: 6px;">
          <div style="font-size: 12px; color: var(--text-secondary);">密码强度：${s.label}</div>
          <div style="font-size: 12px; color: ${color}; font-weight: 650;">${s.ok ? _tr('符合规则') : _tr('密码不符合规则')}</div>
        </div>
        <div style="height: 8px; background: rgba(0,0,0,0.06); border-radius: 999px; overflow:hidden;">
          <div style="width:${percent}%; height: 8px; background:${color}; border-radius: 999px;"></div>
        </div>
        <div style="display:flex; gap: 8px; flex-wrap: wrap; margin-top: 10px; font-size: 12px; color: var(--text-secondary);">
          <span style="padding:4px 10px; border-radius: 999px; background: ${s.len >= 8 ? 'rgba(52,199,89,0.12)' : 'rgba(255,59,48,0.10)'};">8–128${_tr('个字符')}</span>
          <span style="padding:4px 10px; border-radius: 999px; background: ${s.hasLower ? 'rgba(52,199,89,0.12)' : 'rgba(255,59,48,0.10)'};">小写</span>
          <span style="padding:4px 10px; border-radius: 999px; background: ${s.hasUpper ? 'rgba(52,199,89,0.12)' : 'rgba(255,59,48,0.10)'};">大写</span>
          <span style="padding:4px 10px; border-radius: 999px; background: ${s.hasDigit ? 'rgba(52,199,89,0.12)' : 'rgba(255,59,48,0.10)'};">数字</span>
          <span style="padding:4px 10px; border-radius: 999px; background: ${s.hasSpecial ? 'rgba(52,199,89,0.12)' : 'rgba(255,59,48,0.10)'};">特殊字符</span>
        </div>
      `;
    }

    const resetNewPwd = qs('#resetNewPwd');
    const resetStrength = qs('#resetStrength');
    if (resetNewPwd) {
      resetNewPwd.addEventListener('input', () => renderStrength(resetStrength, resetNewPwd.value));
      renderStrength(resetStrength, resetNewPwd.value);
    }
    const toggleResetPwd = qs('#toggleResetPwd');
    if (toggleResetPwd) {
      toggleResetPwd.addEventListener('click', () => {
        if (!resetNewPwd) return;
        const isPwd = resetNewPwd.getAttribute('type') === 'password';
        resetNewPwd.setAttribute('type', isPwd ? 'text' : 'password');
        toggleResetPwd.className = isPwd ? 'fa-regular fa-eye-slash' : 'fa-regular fa-eye';
      });
    }
    if (resetSendBtn) {
      resetSendBtn.addEventListener('click', async (e) => {
        e.preventDefault();
        const email = (qs('#resetEmail')?.value || '').trim();
        if (!email) {
          if (resetHint) resetHint.textContent = _tr('请输入邮箱');
          return;
        }
        try {
          const data = await apiFetch('/auth/email/send', { method: 'POST', body: JSON.stringify({ email, purpose: 'RESET' }) });
          startCountdown(resetSendBtn, 60);
          if (resetHint) resetHint.textContent = data.devCode ? _ui`验证码已发送（开发码：${data.devCode}）` : _tr('验证码已发送');
        } catch (err) {
          if (resetHint) resetHint.textContent = err.message || _tr('发送失败');
        }
      });
    }
    if (resetSubmitBtn) {
      resetSubmitBtn.addEventListener('click', async (e) => {
        e.preventDefault();
        const email = (qs('#resetEmail')?.value || '').trim();
        const code = (qs('#resetCode')?.value || '').trim();
        const p1 = (qs('#resetNewPwd')?.value || '');
        const p2 = (qs('#resetNewPwd2')?.value || '');
        if (!email || !code || !p1 || !p2) {
          if (resetHint) resetHint.textContent = _tr('请完整填写信息');
          return;
        }
        if (p1 !== p2) {
          if (resetHint) resetHint.textContent = _tr('两次输入的密码不一致');
          return;
        }
        try {
          await apiFetch('/auth/password/reset', { method: 'POST', body: JSON.stringify({ email, code, newPassword: p1 }) });
          if (resetModal) resetModal.style.display = 'none';
          showSuccessMessage(_tr('密码已更新'), _tr('请使用新密码重新登录。'));
        } catch (err) {
          if (resetHint) resetHint.textContent = err.message || _tr('修改失败');
        }
      });
    }

    btn.addEventListener('click', async () => {
      const isPwd = pwdPanel && pwdPanel.style.display !== 'none';
      try {
        if (isPwd) {
          const u = (qs('#loginUsername')?.value || '').trim();
          const p = (qs('#loginPassword')?.value || '');
          if (!u) {
            showMessage(_tr('请输入学号或用户名'), _tr('提示'));
            qs('#loginUsername')?.focus();
            return;
          }
          if (!p) {
            showMessage(_tr('请输入密码'), _tr('提示'));
            qs('#loginPassword')?.focus();
            return;
          }
          const data = await apiFetch('/auth/login', { method: 'POST', body: JSON.stringify({ usernameOrStudentNo: u, password: p }) });
          setToken(data.token);
          window.location.href = 'home.html';
          return;
        }
        const email = (qs('#loginEmail')?.value || '').trim();
        const code = (qs('#emailCode')?.value || '').trim();
        if (!email) {
          showMessage(_tr('请输入邮箱'), _tr('提示'));
          qs('#loginEmail')?.focus();
          return;
        }
        if (!code) {
          showMessage(_tr('请输入验证码'), _tr('提示'));
          qs('#emailCode')?.focus();
          return;
        }
        const data = await apiFetch('/auth/login-email', { method: 'POST', body: JSON.stringify({ email, code }) });
        setToken(data.token);
        window.location.href = 'home.html';
      } catch (err) {
        const msg = err && err.message ? err.message : _tr('登录失败');
        if (emailHint && !isPwd) emailHint.textContent = msg;
        showMessage(msg, _tr('登录失败'));
      }
    });
  }

import { t as _tr, ui as _ui, translateStatic as _static } from '../core/i18n.js';
import { setToken } from './../core/auth.js';
import { qs, showMessage } from './../core/ui.js';
import { apiFetch, uploadFile } from './../core/api.js';

export async function initRegister() {
    const btn = qs('#registerBtn');
    if (!btn) return;
    let pendingAvatar = null, previewUrl = null;
    qs('#avatarUploadBtn')?.addEventListener('click', () => qs('#avatarFile')?.click());
    qs('#avatarFile')?.addEventListener('change', event => {
      const file = event.target.files?.[0];
      if (!file) return;
      if (!['image/png', 'image/jpeg', 'image/gif'].includes(file.type) || file.size > 5 * 1024 * 1024) {
        event.target.value = ''; showMessage(_tr('请选择不超过 5 MB 的 PNG、JPEG 或 GIF 图片')); return;
      }
      pendingAvatar = file;
      if (previewUrl) URL.revokeObjectURL(previewUrl);
      previewUrl = URL.createObjectURL(file);
      qs('#avatarChoices').innerHTML = `<img src="${previewUrl}" alt="Avatar preview" style="width:64px;height:64px;border-radius:14px">`;
      qs('#avatarUploadHint').textContent = _tr('图片将在注册成功后上传');
    });
    btn.addEventListener('click', async () => {
      if (btn.disabled) return;
      btn.disabled = true;
      try {
        const username = (qs('#regUsername')?.value || '').trim();
        const email = (qs('#regEmail')?.value || '').trim();
        const code = (qs('#regEmailCode')?.value || '').trim();
        const password = (qs('#regPassword')?.value || '');
        const password2 = (qs('#regPassword2')?.value || '');
        const nickname = (qs('#regNickname')?.value || '').trim();
        const avatarUrl = (qs('.avatar-option.selected')?.getAttribute('data-url') || '').trim();
        if (!nickname) {
          showMessage(_tr('请输入姓名'), _tr('提示'));
          return;
        }
        if (!username) {
          showMessage(_tr('请输入学号'), _tr('提示'));
          return;
        }
        if (!email) {
          showMessage(_tr('请输入邮箱'), _tr('提示'));
          return;
        }
        if (!code) {
          showMessage(_tr('请输入验证码'), _tr('提示'));
          return;
        }
        if (!password) {
          showMessage(_tr('请输入密码'), _tr('提示'));
          return;
        }
        if (!password2 || password !== password2) {
          showMessage(_tr('两次输入的密码不一致'), _tr('提示'));
          return;
        }
        const data = await apiFetch('/auth/register-email', {
          method: 'POST',
          body: JSON.stringify({ username, password, nickname, studentNo: username, email, code }),
        });
        setToken(data.token);
        if (pendingAvatar) {
          try {
            const uploaded = await uploadFile('/upload/avatar', pendingAvatar);
            await apiFetch('/profile/avatar', { method: 'PUT', body: JSON.stringify({ avatarUrl: uploaded.url }) });
          } catch (error) {
            showMessage(error.message, _tr('账号已创建，头像上传失败'), _tr('编辑资料'), { onClose: () => { window.location.href = 'profile.html'; } });
            return;
          }
        }
        window.location.href = 'home.html';
      } catch (err) {
        showMessage((err && err.message) || _tr('注册失败'), _tr('注册失败'));
      } finally { btn.disabled = false; }
    });

    const toggleRegPwd = qs('#toggleRegPwd');
    if (toggleRegPwd) {
      toggleRegPwd.addEventListener('click', () => {
        const input = qs('#regPassword');
        if (!input) return;
        const isPwd = input.getAttribute('type') === 'password';
        input.setAttribute('type', isPwd ? 'text' : 'password');
        toggleRegPwd.className = isPwd ? 'fa-regular fa-eye-slash' : 'fa-regular fa-eye';
      });
    }

    const strengthBox = qs('#pwdStrength');
    const pwdInput = qs('#regPassword');
    if (pwdInput) {
      const render = () => {
        const p = pwdInput.value || '';
        const hasLower = /[a-z]/.test(p);
        const hasUpper = /[A-Z]/.test(p);
        const hasDigit = /[0-9]/.test(p);
        const hasSpecial = /[^A-Za-z0-9]/.test(p);
        const ok = p.length >= 8 && hasLower && hasUpper && hasDigit && hasSpecial;
        const color = ok ? 'var(--success-color)' : p.length >= 8 ? 'var(--accent-color)' : 'var(--danger-color)';
        const percent = ok ? 100 : Math.min(100, (p.length / 12) * 100);
        if (!strengthBox) return;
        strengthBox.innerHTML = _ui`
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
      };
      pwdInput.addEventListener('input', render);
      render();
    }

    const regSendBtn = qs('#regSendBtn');
    if (regSendBtn) {
      const startCountdown = (button, seconds) => {
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
      regSendBtn.addEventListener('click', async (e) => {
        e.preventDefault();
        const email = (qs('#regEmail')?.value || '').trim();
        if (!email) {
          alert(_tr('请输入邮箱'));
          return;
        }
        try {
          const data = await apiFetch('/auth/email/send', { method: 'POST', body: JSON.stringify({ email, purpose: 'REGISTER' }) });
          startCountdown(regSendBtn, 60);
          alert(data.devCode ? _ui`验证码已发送（开发码：${data.devCode}）` : _tr('验证码已发送'));
        } catch (err) {
          alert(err.message || _tr('发送失败'));
        }
      });
    }
  }

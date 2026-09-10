import { t as _tr, ui as _ui, translateStatic as _static } from '../core/i18n.js';
import { qs, qsa } from './../core/ui.js';
import { apiFetch } from './../core/api.js';
import { requireAuth } from './../core/auth.js';
import { mountBottomNavActive, deriveGradeLabel } from './../core/render.js';

export async function initProfileEdit() {
    mountBottomNavActive();
    requireAuth();
    const me = await apiFetch('/auth/me');
    const college = qs('#editCollege');
    const campusInput = qs('#editCampus');
    const campusChoices = qs('#campusChoices');
    const eduInput = qs('#editEducationLevel');
    const eduChoices = qs('#eduChoices');
    const major = qs('#editMajor');
    const bio = qs('#editBio');
    const avatar = qs('#editAvatarPreview');
    const staticNickname = qs('#staticNickname');
    const staticStudentNo = qs('#staticStudentNo');
    const staticGrade = qs('#staticGrade');
    const hint = qs('#editHint');
    const saveBtn = qs('#saveProfileBtn');
    if (college) college.value = me.college || '';
    if (campusInput) campusInput.value = me.campus || '';
    if (major) major.value = me.major || '';
    if (bio) bio.value = me.bio || '';
    if (avatar && me.avatarUrl) avatar.src = me.avatarUrl;
    if (staticNickname) staticNickname.textContent = me.nickname || '-';
    if (staticStudentNo) staticStudentNo.textContent = me.studentNo || '-';
    if (staticGrade) staticGrade.textContent = deriveGradeLabel(me.studentNo) || '—';
    const setCampus = (v) => {
      if (campusInput) campusInput.value = v || '';
      qsa('.campus-chip').forEach((chip) => {
        const val = chip.getAttribute('data-value') || '';
        const on = val === v;
        chip.style.background = on ? 'rgba(0, 113, 227, 0.10)' : 'rgba(0,0,0,0.04)';
        chip.style.color = on ? 'var(--accent-color)' : 'var(--text-primary)';
        chip.style.border = on ? '1px solid rgba(0, 113, 227, 0.18)' : '1px solid transparent';
      });
    };
    const setEdu = (v) => {
      if (eduInput) eduInput.value = v || '';
      qsa('.edu-chip').forEach((chip) => {
        const val = chip.getAttribute('data-value') || '';
        const on = val === v;
        chip.style.background = on ? 'rgba(0, 113, 227, 0.10)' : 'rgba(0,0,0,0.04)';
        chip.style.color = on ? 'var(--accent-color)' : 'var(--text-primary)';
        chip.style.border = on ? '1px solid rgba(0, 113, 227, 0.18)' : '1px solid transparent';
      });
    };
    if (campusChoices) {
      qsa('.campus-chip').forEach((chip) => {
        chip.addEventListener('click', () => {
          const v = chip.getAttribute('data-value') || '';
          setCampus(v);
        });
      });
      setCampus(me.campus || '');
    }
    if (eduChoices) {
      qsa('.edu-chip').forEach((chip) => {
        chip.addEventListener('click', () => {
          const v = chip.getAttribute('data-value') || '';
          setEdu(v);
        });
      });
      setEdu(me.educationLevel || '');
    }

    if (saveBtn) {
      saveBtn.addEventListener('click', async () => {
        const body = {
          college: (college?.value || '').trim(),
          campus: (campusInput?.value || '').trim(),
          educationLevel: (eduInput?.value || '').trim(),
          major: (major?.value || '').trim(),
          bio: (bio?.value || '').trim(),
        };
        try {
          if (hint) hint.textContent = '';
          await apiFetch('/profile/me', { method: 'PUT', body: JSON.stringify(body) });
          window.location.href = 'profile.html';
        } catch (err) {
          if (hint) hint.textContent = err.message || _tr('保存失败');
        }
      });
    }
  }

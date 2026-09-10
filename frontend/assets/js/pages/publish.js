import { taxonomyLabel } from '../core/content-i18n.js';
import { t as _tr, ui as _ui, translateStatic as _static } from '../core/i18n.js';
import { qs, qsa, escapeHtml, showMessage, formatDateTime } from './../core/ui.js';
import { getQueryParam, withFrom } from './../core/navigation.js';
import { apiFetch, uploadFile } from './../core/api.js';
import { requireAuth } from './../core/auth.js';

export async function initPublish() {
    requireAuth();
    const typeInput = qs('#publishType');
    const typeActivityBtn = qs('#publishTypeActivity');
    const typeTeamBtn = qs('#publishTypeTeam');
    const coverGroup = qs('#activityCoverGroup');
    const timeGroup = qs('#activityTimeGroup');
    const startWrap = qs('#pubStartWrap');
    const startLabel = qs('#pubStartLabel');
    const endWrap = qs('#pubEndWrap');
    const locGroup = qs('#activityLocationGroup');
    const maxGroup = qs('#activityMaxGroup');
    const teamingGroup = qs('#activityTeamingGroup');
    const teamingCheckbox = qs('#pubTeamingEnabled');
    const tagHintEl = qs('#tagHint');
    const previewEl = qs('#publishPreview');
    const templatesEl = qs('#publishTemplates');
    const clearDraftBtn = qs('#clearDraftBtn');
    const publishHintEl = qs('#publishHint');

    const DRAFT_KEY = 'campus_pulse_publish_draft_v1';
    let selectedTags = new Set();
    let pendingTags = [];
    let selectedTemplateIdx = null;
    let lastDraftJson = '';
    let draftTimer = null;

    function setType(type) {
      if (typeInput) typeInput.value = type;
      const isActivity = type === 'activity';
      if (typeActivityBtn) typeActivityBtn.className = isActivity ? 'primary-btn' : 'secondary-btn';
      if (typeTeamBtn) typeTeamBtn.className = isActivity ? 'secondary-btn' : 'primary-btn';
      if (coverGroup) coverGroup.style.display = isActivity ? '' : 'none';
      if (timeGroup) timeGroup.style.display = '';
      if (startWrap) startWrap.style.gridColumn = '';
      if (startLabel) startLabel.textContent = isActivity ? _tr('开始时间') : _tr('组队开始时间');
      if (endWrap) endWrap.style.display = '';
      const endLabel = endWrap?.querySelector('label');
      if (endLabel) endLabel.textContent = isActivity ? _tr('结束时间') : _tr('组队截止时间');
      if (locGroup) locGroup.style.display = isActivity ? '' : 'none';
      if (teamingGroup) teamingGroup.style.display = isActivity ? '' : 'none';
      if (maxGroup) {
        maxGroup.querySelector('label').textContent = isActivity ? _tr('最大参与人数') : _tr('队伍人数上限');
        maxGroup.querySelector('input').placeholder = isActivity ? _tr('如果不限人数，请留空') : _tr('选填人数上限');
      }
      if (tagHintEl) tagHintEl.textContent = isActivity ? _tr('* 至少选择 1 个标签（可多选）') : _tr('* 请选择 1 个类型（仅单选）');
      selectedTemplateIdx = null;
      renderTemplates();
      renderPreview();
      saveDraftSoon();
    }

    if (typeActivityBtn) typeActivityBtn.addEventListener('click', (e) => { e.preventDefault(); setType('activity'); });
    if (typeTeamBtn) typeTeamBtn.addEventListener('click', (e) => { e.preventDefault(); setType('team'); });
    setType(typeInput?.value || 'activity');

    function currentType() {
      return (typeInput?.value || 'activity').trim();
    }

    function getFieldValue(id) {
      return (qs(id)?.value || '').trim();
    }

    function setFieldValue(id, value) {
      const el = qs(id);
      if (!el) return;
      el.value = value == null ? '' : String(value);
    }

    function getDraftState() {
      return {
        type: currentType(),
        title: getFieldValue('#pubTitle'),
        startTime: qs('#pubStart')?.value || '',
        endTime: qs('#pubEnd')?.value || '',
        location: getFieldValue('#pubLoc'),
        maxParticipants: qs('#pubMax')?.value || '',
        description: getFieldValue('#pubDesc'),
        tags: Array.from(selectedTags),
        teamingEnabled: !!(teamingCheckbox?.checked),
        selectedTemplateIdx,
      };
    }

    function saveDraftSoon() {
      if (draftTimer) clearTimeout(draftTimer);
      draftTimer = setTimeout(() => {
        const state = getDraftState();
        const json = JSON.stringify(state);
        if (json === lastDraftJson) return;
        lastDraftJson = json;
        try {
          localStorage.setItem(DRAFT_KEY, json);
        } catch (e) {
        }
      }, 350);
    }

    function applyTags(list) {
      selectedTags = new Set();
      const t = currentType();
      const arr = (list || []).filter(Boolean);
      if (t === 'team' && arr.length > 0) selectedTags.add(arr[0]);
      if (t === 'activity') arr.forEach((x) => selectedTags.add(x));
      syncTagsUI();
      renderPreview();
      saveDraftSoon();
    }

    function syncTagsUI() {
      const tagInput = qs('#pubTags');
      if (tagInput) tagInput.value = Array.from(selectedTags).join('|');
      qsa('.tag-chip').forEach((chip) => {
        const tag = chip.getAttribute('data-tag') || '';
        const on = selectedTags.has(tag);
        chip.style.background = on ? 'rgba(0, 113, 227, 0.10)' : 'rgba(0,0,0,0.04)';
        chip.style.color = on ? 'var(--accent-color)' : 'var(--text-primary)';
      });
      if (tagHintEl) {
        const t = currentType();
        if (selectedTags.size === 0) tagHintEl.textContent = t === 'team' ? _tr('* 请选择 1 个类型（仅单选）') : _tr('* 至少选择 1 个标签（可多选）');
        else tagHintEl.textContent = t === 'team' ? _ui`已选择：${Array.from(selectedTags)[0]}` : _ui`已选择 ${selectedTags.size} 个标签`;
      }
    }

    function loadDraftIfAny() {
      let raw = '';
      try {
        raw = localStorage.getItem(DRAFT_KEY) || '';
      } catch (e) {
      }
      if (!raw) return;
      try {
        const d = JSON.parse(raw);
        if (d && d.type) setType(d.type);
        setFieldValue('#pubTitle', d.title || '');
        const start = qs('#pubStart'); if (start) start.value = d.startTime || '';
        const end = qs('#pubEnd'); if (end) end.value = d.endTime || '';
        setFieldValue('#pubLoc', d.location || '');
        const max = qs('#pubMax'); if (max) max.value = d.maxParticipants || '';
        setFieldValue('#pubDesc', d.description || '');
        if (teamingCheckbox) teamingCheckbox.checked = d.teamingEnabled !== false;
        pendingTags = Array.isArray(d.tags) ? d.tags : [];
        selectedTemplateIdx = Number.isInteger(d.selectedTemplateIdx) ? d.selectedTemplateIdx : null;
        lastDraftJson = raw;
      } catch (e) {
      }
    }

    function applyEntryTypeIfAny() {
      const requestedType = (getQueryParam('type') || '').trim().toLowerCase();
      if (requestedType === 'activity' || requestedType === 'team') {
        setType(requestedType);
      }
    }

    function clearDraft() {
      try {
        localStorage.removeItem(DRAFT_KEY);
      } catch (e) {
      }
      if (typeof window.resetPublishCoverSelection === 'function') {
        window.resetPublishCoverSelection();
      }
      lastDraftJson = '';
      pendingTags = [];
      selectedTemplateIdx = null;
      selectedTags = new Set();
      setFieldValue('#pubTitle', '');
      const start = qs('#pubStart'); if (start) start.value = '';
      const end = qs('#pubEnd'); if (end) end.value = '';
      setFieldValue('#pubLoc', '');
      const max = qs('#pubMax'); if (max) max.value = '';
      setFieldValue('#pubDesc', '');
      if (teamingCheckbox) teamingCheckbox.checked = true;
      syncTagsUI();
      renderPreview();
      showMessage(_tr('草稿已清除'), _tr('提示'));
    }

    if (clearDraftBtn) clearDraftBtn.addEventListener('click', (e) => { e.preventDefault(); clearDraft(); });

    function renderPreview() {
      if (!previewEl) return;
      const t = currentType();
      const title = getFieldValue('#pubTitle') || (t === 'team' ? _tr('组队标题（预览）') : _tr('活动标题（预览）'));
      const desc = getFieldValue('#pubDesc') || _tr('填写详细描述后，这里会展示预览效果');
      const shortDesc = desc.length > 90 ? `${desc.slice(0, 90)}…` : desc;
      const tags = Array.from(selectedTags);
      const startTime = qs('#pubStart')?.value || '';
      const endTime = qs('#pubEnd')?.value || '';
      const location = getFieldValue('#pubLoc');
      const max = getFieldValue('#pubMax');
      const teamingEnabled = !!(teamingCheckbox?.checked);

      const badges = [];
      badges.push(t === 'team' ? _tr('组队') : _tr('活动'));
      if (tags.length > 0) {
        (t === 'team' ? tags.slice(0, 1) : tags.slice(0, 3)).forEach((x) => badges.push(taxonomyLabel(x)));
      }
      const metaParts = [];
      if (t === 'activity') {
        if (startTime) metaParts.push(_ui`开始：${formatDateTime(startTime)}`);
        if (endTime) metaParts.push(_ui`结束：${formatDateTime(endTime)}`);
        if (location) metaParts.push(_ui`地点：${location}`);
        if (max) metaParts.push(_ui`人数：${max}`);
        metaParts.push(teamingEnabled ? _tr('允许组队联动') : _tr('未开放组队'));
      } else {
        if (startTime) metaParts.push(_ui`开始：${formatDateTime(startTime)}`);
        if (endTime) metaParts.push(_ui`截止：${formatDateTime(endTime)}`);
        if (max) metaParts.push(_ui`人数上限：${max}`);
      }

      previewEl.innerHTML = `
        <div class="publish-preview-head">
          <div style="flex:1; min-width: 0;">
            <div class="publish-preview-title">${escapeHtml(title)}</div>
            <div class="publish-preview-badges">
              ${badges.map((b) => `<span class="publish-preview-badge">${escapeHtml(b)}</span>`).join('')}
            </div>
          </div>
        </div>
        <div class="publish-preview-meta">${escapeHtml(metaParts.join(' · ') || (t === 'team' ? _tr('选择类型并填写信息后会更完整') : _tr('选择标签并填写时间地点后会更完整')))}</div>
        <div class="publish-preview-desc">${escapeHtml(shortDesc)}</div>
      `;
    }

    function templatesForType(t) {
      if (t === 'team') {
        return [
          { label: _tr('考研搭子'), type: 'team', tag: '考研搭子', title: _tr('找考研搭子（互相监督）'), desc: _tr('目标院校/专业：\n学习时间：\n希望搭子：\n联系方式：\n'), max: '2' },
          { label: _tr('竞赛组队'), type: 'team', tag: '竞赛组队', title: _tr('竞赛缺人组队'), desc: _tr('竞赛名称：\n需要角色：\n截止时间：\n已有成员：\n'), max: '5' },
          { label: _tr('运动约球'), type: 'team', tag: '运动健身', title: _tr('约球/组队训练'), desc: _tr('项目：\n时间：\n地点：\n水平要求：\n'), max: '6' },
        ];
      }
      return [
        { label: _tr('学术讲座'), type: 'activity', tags: ['学术讲座'], title: _tr('学术讲座｜主题分享'), desc: _tr('讲座主题：\n主讲人：\n适合人群：\n注意事项：\n'), max: '100' },
        { label: _tr('志愿服务'), type: 'activity', tags: ['志愿服务'], title: _tr('志愿服务｜招募志愿者'), desc: _tr('服务内容：\n集合时间：\n集合地点：\n报名要求：\n'), max: '50' },
        { label: _tr('文艺演出'), type: 'activity', tags: ['文艺演出'], title: _tr('文艺演出｜观演/报名'), desc: _tr('演出信息：\n时间地点：\n报名方式：\n'), max: '200' },
      ];
    }

    function renderTemplates() {
      if (!templatesEl) return;
      const t = currentType();
      const list = templatesForType(t);
      templatesEl.innerHTML = list
        .map((x, idx) => `<span class="template-chip${selectedTemplateIdx === idx ? ' active' : ''}" data-idx="${idx}">${escapeHtml(x.label)}</span>`)
        .join('');
      qsa('.template-chip').forEach((chip) => {
        chip.addEventListener('click', () => {
          const idx = Number(chip.getAttribute('data-idx') || '0');
          const tpl = list[idx];
          if (!tpl) return;
          if (tpl.type && tpl.type !== currentType()) setType(tpl.type);
          selectedTemplateIdx = idx;
          setFieldValue('#pubTitle', tpl.title || '');
          setFieldValue('#pubDesc', tpl.desc || '');
          const max = qs('#pubMax'); if (max) max.value = tpl.max || '';
          if (tpl.tag) applyTags([tpl.tag]);
          if (tpl.tags) applyTags(tpl.tags);
          renderTemplates();
          renderPreview();
          saveDraftSoon();
        });
      });
    }

    loadDraftIfAny();
    applyEntryTypeIfAny();

    const btn = qs('#publishBtn');
    if (!btn) return;
    btn.addEventListener('click', async () => {
      const originalText = btn.textContent;
      const setPublishingState = (publishing, text) => {
        btn.disabled = !!publishing;
        btn.style.opacity = publishing ? '0.72' : '';
        btn.textContent = text || originalText;
      };
      const publishType = (typeInput?.value || 'activity').trim();
      const title = (qs('#pubTitle')?.value || '').trim();
      const maxParticipants = Number(qs('#pubMax')?.value || '0') || 0;
      const description = (qs('#pubDesc')?.value || '').trim();
      const tags = Array.from(selectedTags);
      if (publishHintEl) publishHintEl.textContent = '';
      if (!tags || tags.length === 0) {
        showMessage(publishType === 'team' ? _tr('请选择 1 个类型') : _tr('请至少选择 1 个标签'), _tr('提示'));
        return;
      }

      if (publishType === 'team' && tags.length > 1) {
        showMessage(_tr('发起组队时只能选择 1 个类型'), _tr('提示'));
        return;
      }
      if (!title) {
        showMessage(_tr('请填写标题'), _tr('提示'));
        qs('#pubTitle')?.focus();
        return;
      }
      if (!description) {
        showMessage(_tr('请填写详细描述'), _tr('提示'));
        qs('#pubDesc')?.focus();
        return;
      }

      try {
        setPublishingState(true, publishType === 'team' ? _tr('正在创建队伍...') : _tr('正在发布活动...'));

        let coverUrl = '';
        const coverFile = (window.__pubCoverFile || null);
        if (publishType === 'activity' && coverFile) {
          if (publishHintEl) publishHintEl.textContent = _tr('正在上传宣传图...');
          coverUrl = (await uploadFile('/upload/cover', coverFile)).url || '';
        }

        if (publishType === 'team') {
          if (publishHintEl) publishHintEl.textContent = _tr('正在创建队伍并初始化聊天空间...');
          const category = tags[0];
          const teamTitle = `${category}｜${title}`;
          const maxMembers = maxParticipants > 0 ? maxParticipants : 0;
          const startTime = qs('#pubStart')?.value || null;
          const endTime = qs('#pubEnd')?.value || null;
          const res = await apiFetch('/teams', {
            method: 'POST',
            body: JSON.stringify({ activityId: null, title: teamTitle, description, maxMembers, startTime, endTime }),
          });
          try { localStorage.removeItem(DRAFT_KEY); } catch (e) {}
          window.location.href = withFrom(`team-success.html?teamId=${encodeURIComponent(res.id)}`);
          return;
        }

        const startTime = qs('#pubStart')?.value;
        const endTime = qs('#pubEnd')?.value;
        const location = (qs('#pubLoc')?.value || '').trim();
        const teamingEnabled = !!(teamingCheckbox?.checked);
        if (!startTime) {
          showMessage(_tr('请填写开始时间'), _tr('提示'));
          qs('#pubStart')?.focus();
          return;
        }
        if (!location) {
          showMessage(_tr('请填写地点'), _tr('提示'));
          qs('#pubLoc')?.focus();
          return;
        }
        if (publishHintEl) publishHintEl.textContent = _tr('正在保存活动信息...');
        const data = await apiFetch('/activities', {
          method: 'POST',
          body: JSON.stringify({
            title,
            location,
            startTime,
            endTime: endTime || null,
            maxParticipants,
            coverUrl: coverUrl || null,
            description,
            tags,
            teamingEnabled,
          }),
        });
        try { localStorage.removeItem(DRAFT_KEY); } catch (e) {}
        window.location.href = withFrom(`activity-detail.html?id=${encodeURIComponent(data.id)}`);
      } catch (err) {
        if (publishHintEl) publishHintEl.textContent = err.message || _tr('发布失败，请稍后再试');
        showMessage(err.message || _tr('发布失败，请稍后再试'), _tr('发布失败'));
      } finally {
        setPublishingState(false, originalText);
      }
    });

    const tagBox = qs('#tagChoices');
    const tagInput = qs('#pubTags');
    if (tagBox && tagInput) {
      try {
        const list = await apiFetch('/tags');
        const tags = (list || []).map((t) => t.name).filter(Boolean);
        if (tags.length === 0) throw new Error('no tags');
        tagBox.innerHTML = tags
          .map((t) => `<span class="tag-chip" data-tag="${escapeHtml(t)}" style="user-select:none; cursor:pointer; padding: 8px 12px; border-radius: 999px; background: rgba(0,0,0,0.04); color: var(--text-primary); font-size: 13px; font-weight: 600;">#${escapeHtml(taxonomyLabel(t))}</span>`)
          .join('');
        qsa('.tag-chip').forEach((el) => {
          el.addEventListener('click', () => {
            const tag = el.getAttribute('data-tag') || '';
            if (!tag) return;
            const publishType = (typeInput?.value || 'activity').trim();
            if (selectedTags.has(tag)) {
              selectedTags.delete(tag);
            } else {
              if (publishType === 'team') {
                selectedTags = new Set();
              }
              selectedTags.add(tag);
            }
            syncTagsUI();
            renderPreview();
            saveDraftSoon();
          });
        });
        if (pendingTags && pendingTags.length > 0) applyTags(pendingTags);
        else syncTagsUI();
        renderTemplates();
        renderPreview();
      } catch (e) {
        tagBox.innerHTML = _ui`<div style="color: var(--text-secondary); font-size: 13px;">标签加载失败，请刷新重试</div>`;
      }
    }

    ['#pubTitle', '#pubLoc', '#pubMax', '#pubDesc'].forEach((id) => {
      const el = qs(id);
      if (el) el.addEventListener('input', () => { renderPreview(); saveDraftSoon(); });
    });
    const start = qs('#pubStart'); if (start) start.addEventListener('change', () => { renderPreview(); saveDraftSoon(); });
    const end = qs('#pubEnd'); if (end) end.addEventListener('change', () => { renderPreview(); saveDraftSoon(); });
    if (teamingCheckbox) teamingCheckbox.addEventListener('change', () => { renderPreview(); saveDraftSoon(); });
  }

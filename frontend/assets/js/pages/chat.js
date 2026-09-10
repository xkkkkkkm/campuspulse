import { contentField, taxonomyLabel } from '../core/content-i18n.js';
import { mountPageNavigator } from '../core/array-pagination.js';
import { t as _tr, ui as _ui, translateStatic as _static } from '../core/i18n.js';
import { mergeMessages, messageRequest, refreshReadReceipts } from '../core/chat-state.js';
import { getToken, ensureMe, redirectToLogin, requireAuth } from './../core/auth.js';
import { qs, escapeHtml, showMessage, formatDateTime, openUtilityModal, renderEmptyCard } from './../core/ui.js';
import { getQueryParam, withFrom } from './../core/navigation.js';
import { apiFetch, uploadFile, fetchChatMedia } from './../core/api.js';
import { createRealtimeChannel } from './../core/realtime.js';
import { deriveGradeLabel, educationLabel } from './../core/render.js';

export async function initChat() {
    requireAuth();
    const teamId = Number(getQueryParam('teamId') || '0');
    const peerUserId = Number(getQueryParam('userId') || '0');
    const activityId = Number(getQueryParam('activityId') || '0');
    const headerName = qs('#chatTitle');
    const manageBtn = qs('#chatManageBtn');
    const container = qs('#chatMessages');
    const input = qs('#chatInput');
    const sendBtn = qs('#chatSendBtn');
    const scrollBtn = qs('#scrollBottomBtn');
    let lastId = 0;
    let lastTs = 0, lastMarkedRead = 0, lastReceiptRefresh = Date.now();
    const mediaCache = new Map(), mediaController = new AbortController();
    let messages = [], firstLoad = true, historyLoading = false, hasHistory = true, pendingSend = null;
    const historyButton = document.createElement('button');
    historyButton.id = 'chatHistoryBtn'; historyButton.className = 'secondary-btn'; historyButton.textContent = _tr('加载更早消息');
    container?.before(historyButton);
    let channel = null;
    let loading = false;
    const me = await ensureMe();
    const isTeam = !!teamId;
    const isDM = !!peerUserId && !teamId && !activityId;
    const isAct = !!activityId && !teamId && !peerUserId;
    const defaultAvatar = 'assets/images/avatar.svg';
    const myAvatarUrl = (me && me.avatarUrl) ? me.avatarUrl : defaultAvatar;
    let peerAvatarUrl = '';
    let activityDetail = null;

    if (isTeam) {
      const team = await apiFetch(`/teams/${teamId}`);
      if (headerName) headerName.textContent = contentField(team, 'title');
    } else if (isDM) {
      try {
        const peer = await apiFetch(`/users/${peerUserId}/brief`);
        peerAvatarUrl = peer.avatarUrl || '';
        if (headerName) headerName.textContent = peer.nickname || _ui`私聊 #${peerUserId}`;
      } catch (e) {
        if (headerName) headerName.textContent = _ui`私聊 #${peerUserId}`;
      }
    } else if (isAct) {
      activityDetail = await apiFetch(`/activities/${activityId}`);
      if (headerName) headerName.textContent = _ui`${contentField(activityDetail, 'title')} · 活动群聊`;
    } else {
      window.location.href = 'messages.html';
      return;
    }

    function openActivityChatManage() {
      if (!isAct || !activityDetail || String(activityDetail.organizerId) !== String(me?.id || '')) return;
      let memberPage = 1;
      const renderManageModal = async () => {
        const regs = await apiFetch(`/activities/${activityId}/registrations?page=${memberPage}&size=20`);
        const approvedRegs = (regs || []).filter((item) => String(item.status || '').toUpperCase() === 'APPROVED');
        openUtilityModal({
          title: _tr('活动群聊成员管理'),
          maxWidth: 720,
          html: _ui`
            <div class="stack-card" style="padding:14px; box-shadow:none; margin-bottom:14px;">
              <div class="stack-card-meta">当前成员来自“报名已通过”名单。成员取消报名或被移出活动后，会自动失去群聊资格。</div>
            </div>
            <div class="stack-list">
              ${approvedRegs.length ? approvedRegs.map((item) => {
                const edu = educationLabel(item.educationLevel);
                const grade = deriveGradeLabel(item.studentNo);
                const meta = [edu, contentField(item, 'campus'), item.college, grade, item.major].filter(Boolean).join(' · ') || _tr('暂未完善学院年级信息');
                return _ui`
                  <div class="manage-review-card">
                    <div class="manage-review-head">
                      <div class="manage-review-main">
                        <div class="manage-review-avatar">
                          <img src="${escapeHtml(item.avatarUrl || defaultAvatar)}" alt="avatar">
                        </div>
                        <div class="manage-review-info">
                          <div class="manage-review-title">${escapeHtml(item.nickname || _tr('未命名成员'))}（${escapeHtml(item.realName || _tr('未填写真实姓名'))}）</div>
                          <div class="manage-review-sub">${escapeHtml(meta)}</div>
                          <div class="manage-review-sub">${escapeHtml(item.phone || _tr('未填写手机号'))}</div>
                        </div>
                      </div>
                      <span class="badge-pill badge-approved">群聊成员</span>
                    </div>
                    <div class="manage-review-text">${escapeHtml(item.intro || item.bio || _tr('成员未填写更多信息。'))}</div>
                    <div class="stack-card-actions">
                      <button type="button" class="secondary-btn js-activity-chat-detail"
                        data-id="${item.id}"
                        data-user-id="${item.userId}"
                        data-nickname="${escapeHtml(item.nickname || '')}"
                        data-avatar-url="${escapeHtml(item.avatarUrl || '')}"
                        data-student-no="${escapeHtml(item.studentNo || '')}"
                        data-major="${escapeHtml(item.major || '')}"
                        data-education-level="${escapeHtml(item.educationLevel || '')}"
                        data-campus="${escapeHtml(contentField(item, 'campus') || '')}"
                        data-bio="${escapeHtml(item.bio || '')}"
                        data-real-name="${escapeHtml(item.realName || '')}"
                        data-phone="${escapeHtml(item.phone || '')}"
                        data-college="${escapeHtml(item.college || '')}"
                        data-intro="${escapeHtml(item.intro || '')}"
                        data-created-at="${escapeHtml(item.createdAt || '')}">查看信息</button>
                      <a href="${withFrom(`chat.html?userId=${encodeURIComponent(item.userId)}`)}" class="secondary-btn">联系对方</a>
                      <button type="button" class="secondary-btn js-activity-chat-remove" data-id="${item.id}" style="background:#fff1f0; color:#d92d20;">移出活动</button>
                    </div>
                  </div>
                `;
              }).join('') : renderEmptyCard(_tr('当前还没有已通过报名的活动成员。'))}
            </div>
          `,
          onOpen(modal, close) {
            mountPageNavigator(modal.querySelector('#utilityModalBody'), { page: memberPage, count: approvedRegs.length, hasMore: regs.length === 20, onChange: next => { memberPage = next; return renderManageModal(); } });
            Array.from(modal.querySelectorAll('.js-activity-chat-detail')).forEach((btn) => {
              btn.addEventListener('click', () => {
                close();
                openUtilityModal({
                  title: btn.dataset.nickname || _tr('成员信息'),
                  maxWidth: 680,
                  html: _ui`
                    <div style="display:flex; align-items:center; gap:14px; margin-bottom: 16px;">
                      <div style="width:64px; height:64px; border-radius:20px; overflow:hidden; border:1px solid rgba(0,0,0,0.05); flex-shrink:0;">
                        <img src="${escapeHtml(btn.dataset.avatarUrl || defaultAvatar)}" alt="avatar" style="width:100%; height:100%; object-fit:cover;">
                      </div>
                      <div>
                        <div style="font-size:20px; font-weight:750;">${escapeHtml(btn.dataset.nickname || _tr('未命名成员'))}（${escapeHtml(btn.dataset.realName || _tr('未填写真实姓名'))}）</div>
                        <div class="stack-card-meta">${escapeHtml([educationLabel(btn.dataset.educationLevel), taxonomyLabel(btn.dataset.campus) || '', btn.dataset.college || '', deriveGradeLabel(btn.dataset.studentNo), btn.dataset.major || ''].filter(Boolean).join(' · ') || _tr('暂未完善学院年级信息'))}</div>
                        <div class="stack-card-meta">通过时间：${escapeHtml(formatDateTime(btn.dataset.createdAt || ''))}</div>
                      </div>
                    </div>
                    <div class="stack-card" style="padding: 14px; box-shadow:none; margin-bottom:14px;">
                      <div class="stack-card-meta">报名附言</div>
                      <div style="font-size:14px; color:var(--text-primary); line-height:1.7; margin-top:8px;">${escapeHtml(btn.dataset.intro || _tr('成员未填写报名附言。'))}</div>
                    </div>
                    <div class="stack-card" style="padding: 14px; box-shadow:none; margin-bottom:14px;">
                      <div class="stack-card-meta">个人简介</div>
                      <div style="font-size:14px; color:var(--text-primary); line-height:1.7; margin-top:8px;">${escapeHtml(btn.dataset.bio || _tr('成员未填写个人简介。'))}</div>
                    </div>
                    <div class="utility-actions" style="margin-top:16px;">
                      <a href="${withFrom(`chat.html?userId=${encodeURIComponent(btn.dataset.userId || '')}`)}" class="secondary-btn">联系对方</a>
                      <button type="button" class="secondary-btn js-activity-chat-remove-detail" data-id="${btn.dataset.id || ''}" style="background:#fff1f0; color:#d92d20;">移出活动</button>
                    </div>
                  `,
                  onOpen(detailModal, detailClose) {
                    detailModal.querySelector('.js-activity-chat-remove-detail')?.addEventListener('click', async () => {
                      try {
                        await apiFetch(`/activities/${activityId}/registrations/${encodeURIComponent(btn.dataset.id || '')}/reject`, { method: 'POST' });
                        detailClose();
                        await renderManageModal();
                      } catch (err) {
                        showMessage(err.message || _tr('处理失败'), _tr('提示'));
                      }
                    });
                  },
                });
              });
            });
            Array.from(modal.querySelectorAll('.js-activity-chat-remove')).forEach((btn) => {
              btn.addEventListener('click', async () => {
                try {
                  await apiFetch(`/activities/${activityId}/registrations/${encodeURIComponent(btn.dataset.id || '')}/reject`, { method: 'POST' });
                  close();
                  await renderManageModal();
                } catch (err) {
                  showMessage(err.message || _tr('处理失败'), _tr('提示'));
                }
              });
            });
          },
        });
      };
      renderManageModal();
    }

    if (manageBtn) {
      const canManageActivityChat = !!(isAct && activityDetail && String(activityDetail.organizerId) === String(me?.id || ''));
      manageBtn.style.display = canManageActivityChat ? 'inline-flex' : 'none';
      if (canManageActivityChat) {
        manageBtn.addEventListener('click', openActivityChatManage);
      }
    }

    function renderMessages(list, prepend = false) {
      if (!container) return;
      const wasAtBottom = container.scrollHeight - container.scrollTop - container.clientHeight < 40;
      const previousHeight = container.scrollHeight, previousTop = container.scrollTop;
      messages = mergeMessages(messages, list);
      container.replaceChildren(); lastTs = 0;
      messages.forEach((m) => {
        lastId = Math.max(lastId, m.id);
        const mine = String(m.senderId) === String(me?.id || '');
        const cls = mine ? 'sent' : 'received';
        const ts = new Date(m.createdAt).getTime() || Date.now();
        if (!lastTs || ts - lastTs > 5 * 60 * 1000) {
          const t = document.createElement('div');
          t.className = 'chat-time';
          t.innerHTML = `<span>${escapeHtml(formatDateTime(m.createdAt))}</span>`;
          container.appendChild(t);
        }
        lastTs = ts;
        const node = document.createElement('div');
        node.className = `chat-message ${cls} animate-fade-up`;
        const avatar = mine ? myAvatarUrl : (peerAvatarUrl || defaultAvatar);
        const bubble = m.contentType === 'IMAGE'
          ? `<div class="chat-bubble chat-image-bubble"><span>${escapeHtml(_tr('正在加载图片...'))}</span></div>`
          : `<div class="chat-bubble">${escapeHtml(m.content || '')}</div>`;
        const avatarHtml = `
          <div class="chat-avatar">
            <img src="${escapeHtml(avatar)}" alt="avatar">
          </div>
        `;
        node.innerHTML = mine ? `${bubble}${avatarHtml}` : `${avatarHtml}${bubble}`;
        container.appendChild(node);
        if (m.contentType === 'IMAGE') {
          if (!mediaCache.has(m.imageUrl)) mediaCache.set(m.imageUrl, fetchChatMedia(m.imageUrl, { signal: mediaController.signal }));
          mediaCache.get(m.imageUrl).then(url => {
            if (!node.isConnected) return;
            const image = document.createElement('img'); image.src = url; image.alt = _tr('聊天图片'); image.loading = 'lazy'; image.className = 'chat-media-image';
            image.addEventListener('load', () => { if (wasAtBottom && !prepend) container.scrollTop = container.scrollHeight; });
            node.querySelector('.chat-bubble').replaceChildren(image);
          }).catch(error => { if (node.isConnected) node.querySelector('.chat-bubble').textContent = _tr('无法加载图片'); mediaCache.delete(m.imageUrl); });
        }
        if (mine && Number(m.readCount) > 0) {
          const receipt = document.createElement('small'); receipt.className = 'chat-read-receipt'; receipt.textContent = _tr('{count} 人已读', { count: m.readCount }); node.append(receipt);
        }
      });
      if (prepend) container.scrollTop = previousTop + container.scrollHeight - previousHeight;
      else if (wasAtBottom) container.scrollTop = container.scrollHeight;
      toggleScrollBtn();
    }

    const endpoint = isTeam ? `/teams/${teamId}/messages` : isDM ? `/dm/${peerUserId}/messages` : `/activities/${activityId}/chat/messages`;
    const draftKey = `campuspulse-chat-draft:${endpoint}`;
    try { if (input && !input.value) input.value = sessionStorage.getItem(draftKey) || ''; } catch {}
    const saveDraft = () => { try { sessionStorage.setItem(draftKey, input?.value || ''); } catch {} };
    const readEndpoint = isTeam ? `/teams/${teamId}/read` : isDM ? `/dm/${peerUserId}/read` : `/activities/${activityId}/chat/read`;
    async function markRead() {
      if (!lastId || lastId <= lastMarkedRead || document.visibilityState !== 'visible' || container.scrollHeight - container.scrollTop - container.clientHeight > 60) return;
      const readId = lastId;
      await apiFetch(readEndpoint, { method: 'POST', body: JSON.stringify({ lastReadId: readId }) });
      lastMarkedRead = Math.max(lastMarkedRead, readId);
    }
    let loadingErrorShown = false, refreshAgain = false;
    async function loadMore() {
      if (loading) { refreshAgain = true; return; }
      loading = true;
      try {
        do {
          refreshAgain = false;
          let batch;
          do {
            const initial = firstLoad;
            batch = await apiFetch(`${endpoint}?size=50${initial ? '' : `&afterId=${lastId}`}`);
            firstLoad = false;
            if (initial) { hasHistory = batch.length === 50; historyButton.hidden = !hasHistory; }
            if (batch.length) renderMessages(batch);
            // On initial load show the newest page. On recovery drain every gap.
            if (initial) break;
          } while (batch.length === 50);
        } while (refreshAgain);
        loadingErrorShown = false;
        if (Date.now() - lastReceiptRefresh > 10000) {
          messages = refreshReadReceipts(messages, await apiFetch(`${endpoint}?size=50`));
          renderMessages([]); lastReceiptRefresh = Date.now();
        }
        await markRead();
      } catch (error) {
        if (!loadingErrorShown) { loadingErrorShown = true; showMessage(error.message || _tr('消息加载失败'), _tr('提示')); }
      } finally { loading = false; }
    }
    historyButton.addEventListener('click', async () => {
      if (historyLoading || !hasHistory || !messages.length) return;
      historyLoading = true; historyButton.disabled = true;
      try {
        const older = await apiFetch(`${endpoint}?beforeId=${messages[0].id}&size=50`);
        renderMessages(older, true);
        hasHistory = older.length === 50; historyButton.hidden = !hasHistory;
      } catch (error) { showMessage(error.message); }
      finally { historyLoading = false; historyButton.disabled = false; }
    });

    function setSendEnabled(enabled) {
      if (!sendBtn) return;
      const on = !!enabled;
      sendBtn.classList.toggle('disabled', !on);
      if ('disabled' in sendBtn) {
        sendBtn.disabled = !on;
      }
    }

    let sending = false;
    async function send() {
      const submittedDraft = input?.value || '';
      const content = submittedDraft.trim();
      if (!content) return;
      if (sending) return;
      sending = true;
      pendingSend = messageRequest(content, pendingSend);
      setSendEnabled(false);
      try {
        await ensureMe();
        if (isTeam) {
          await apiFetch(`/teams/${teamId}/messages`, { method: 'POST', body: JSON.stringify(pendingSend) });
        } else if (isDM) {
          await apiFetch(`/dm/${peerUserId}/messages`, { method: 'POST', body: JSON.stringify(pendingSend) });
        } else if (isAct) {
          await apiFetch(`/activities/${activityId}/chat/messages`, { method: 'POST', body: JSON.stringify(pendingSend) });
        }
        pendingSend = null;
        if (input.value === submittedDraft) input.value = '';
        saveDraft();
        await loadMore();
      } catch (err) {
        showMessage((err && err.message) || _tr('发送失败'), _tr('发送失败'));
        const token = getToken();
        if (!token) redirectToLogin();
      } finally {
        sending = false;
        setSendEnabled(!!(input && input.value.trim()));
      }
    }

    const imageButton = qs('#chatImageBtn'), imageInput = qs('#chatImageFile');
    imageButton?.addEventListener('click', () => { if (!sending) imageInput.click(); });
    imageInput?.addEventListener('change', () => {
      if (sending) { imageInput.value = ''; return; }
      const file = imageInput.files?.[0]; imageInput.value = '';
      if (!file) return;
      if (!['image/png', 'image/jpeg', 'image/gif'].includes(file.type) || file.size > 10 * 1024 * 1024) { showMessage(_tr('请选择不超过 10 MB 的 PNG、JPEG 或 GIF 图片')); return; }
      const preview = URL.createObjectURL(file);
      let attachment = { content: '', contentType: 'IMAGE', imageUrl: null, clientMessageId: crypto.randomUUID() };
      openUtilityModal({ title: _tr('发送图片'), onClose: () => URL.revokeObjectURL(preview), html: `<img src="${preview}" alt="${escapeHtml(_tr('图片预览'))}" style="max-width:100%;max-height:340px;object-fit:contain"><div class="utility-actions"><button class="secondary-btn" data-image-cancel>${escapeHtml(_tr('取消'))}</button><button class="primary-btn" data-image-send>${escapeHtml(_tr('发送'))}</button></div><p data-image-error role="status"></p>`, onOpen(modal, close) {
        modal.querySelector('[data-image-cancel]').addEventListener('click', () => { URL.revokeObjectURL(preview); close(); });
        modal.querySelector('[data-image-send]').addEventListener('click', async event => {
          if (sending) return; sending = true; event.target.disabled = true; imageButton.disabled = true;
          try {
            if (!attachment.imageUrl) attachment.imageUrl = (await uploadFile('/upload/chat', file)).url;
            await apiFetch(endpoint, { method: 'POST', body: JSON.stringify(attachment) });
            URL.revokeObjectURL(preview); close(); await loadMore();
          } catch (error) { modal.querySelector('[data-image-error]').textContent = error.message; }
          finally { sending = false; event.target.disabled = false; imageButton.disabled = false; }
        });
      }});
    });
    await loadMore();
    channel = await createRealtimeChannel({
      teamId,
      userId: peerUserId,
      activityId,
      onEvent: loadMore,
    });
    if (!channel) {
      const fallback = window.setInterval(loadMore, 5000);
      window.addEventListener('pagehide', () => clearInterval(fallback), { once: true });
    }
    if (sendBtn) sendBtn.addEventListener('click', (e) => { e.preventDefault(); if (!sendBtn.classList.contains('disabled')) send(); });
    if (input) {
      const autoGrow = () => {
        input.style.height = 'auto';
        input.style.height = Math.min(input.scrollHeight, 120) + 'px';
        setSendEnabled(!!input.value.trim() && !sending);
      };
      input.addEventListener('input', () => { autoGrow(); saveDraft(); });
      input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
          e.preventDefault();
          if (sendBtn && !sendBtn.classList.contains('disabled')) send();
        }
      });
      autoGrow();
    }
    function toggleScrollBtn() {
      if (!container || !scrollBtn) return;
      const atBottom = container.scrollHeight - container.scrollTop - container.clientHeight < 40;
      scrollBtn.style.display = atBottom ? 'none' : 'flex';
    }
    if (container) container.addEventListener('scroll', () => { toggleScrollBtn(); markRead().catch(console.warn); });
    document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') loadMore(); });
    if (scrollBtn) scrollBtn.addEventListener('click', () => { container.scrollTop = container.scrollHeight; toggleScrollBtn(); });
    window.addEventListener('pagehide', () => {
      saveDraft();
      if (channel) channel.close();
      mediaController.abort();
      for (const pending of mediaCache.values()) pending.then(url => URL.revokeObjectURL(url)).catch(() => {});
    }, { once: true });
  }

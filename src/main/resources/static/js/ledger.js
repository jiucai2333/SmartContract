if (!initAppShell('ledger', '合同台账', 'Contract Ledger')) throw new Error('auth required');

const filterPanel = document.querySelector('.filters')?.closest('.panel');
if (filterPanel) filterPanel.classList.add('ledger-filter-panel');

async function loadAttachmentCount(contractId) {
    try {
        const data = await api(`/api/contracts/${contractId}/attachment-count`);
        return data.count ?? data;
    } catch {
        return 0;
    }
}

async function getLatestVersionId(contractId) {
    try {
        const versions = await api(`/api/contracts/${contractId}/versions`);
        if (versions && versions.length > 0) return versions[0].versionId;
    } catch {}
    return null;
}

function renderActionButtons(row) {
    const buttons = [];
    const canOp = canOperateSealArchive();
    const cid = row.contractId;
    const title = escapeHtml(row.title);
    buttons.push(`<button class="secondary" data-detail="${cid}" data-title="${title}">查看</button>`);
    if (row.status === 'DRAFT' || row.status === 'APPROVING') {
        buttons.push(`<a class="secondary" href="/html/draft.html?contractId=${cid}">编辑</a>`);
        if (row.status === 'DRAFT') {
            const blocked = row.riskLevel === 'HIGH';
            buttons.push(`<button class="secondary" data-submit="${cid}" ${blocked ? 'disabled' : ''} title="${blocked ? '存在未复核的高风险问题，暂不可提交审批' : '提交审批'}">提交审批</button>`);
        }
    } else if (row.status === 'APPROVED') {
        if (canOp) buttons.push(`<a class="primary-btn" href="/html/seal.html?contractId=${cid}&versionId=${row._latestVersionId || ''}">签章登记</a>`);
    } else if (row.status === 'SIGNING') {
        if (canOp) buttons.push(`<a class="primary-btn" href="/html/archive.html?contractId=${cid}&versionId=${row._latestVersionId || ''}">归档确认</a>`);
    } else if (row.status === 'ARCHIVED') {
        buttons.push(`<a class="link-btn" href="/html/fulfillment.html?contractId=${cid}">创建履约计划</a>`);
    }
    return buttons.join(' ');
}

async function loadContracts() {
    const params = new URLSearchParams();
    if ($('#keyword').value) params.set('keyword', $('#keyword').value);
    if ($('#statusFilter').value) params.set('status', $('#statusFilter').value);
    if ($('#riskFilter').value) params.set('riskLevel', $('#riskFilter').value);
    const rows = await api(`/api/contracts?${params.toString()}`);
    const counts = await Promise.all(rows.map(r => loadAttachmentCount(r.contractId)));
    const versionIds = await Promise.all(rows.map(r => getLatestVersionId(r.contractId)));
    rows.forEach((r, i) => { r._latestVersionId = versionIds[i]; });
    $('#contractRows').innerHTML = rows.map((row, index) => {
        const attachCount = counts[index] || 0;
        const statusLabel = STATUS_TEXT[row.status] || row.status;
        const tagCls = statusTagClass[row.status] || 'tag-gray';
        const statusCell = state.roleCode === 'ADMIN'
            ? `<span class="tag ${tagCls} status-editable" data-status-edit="${row.contractId}" data-current="${escapeHtml(row.status)}" title="点击修改状态">${escapeHtml(statusLabel)}</span>`
            : `<span class="tag ${tagCls}">${escapeHtml(statusLabel)}</span>`;
        return `<tr><td>${escapeHtml(row.contractNo)}</td><td><strong>${escapeHtml(row.title)}</strong></td><td>${escapeHtml(row.counterparty)}</td><td>¥${Number(row.amount || 0).toLocaleString()}</td>`
            + `<td>${statusCell}</td>`
            + `<td><span class="tag ${escapeHtml(row.riskLevel)}">${escapeHtml(RISK_TEXT[row.riskLevel] || row.riskLevel || '未评估')}</span></td>`
            + `<td><button type="button" class="secondary" data-attachments="${row.contractId}" data-title="${escapeHtml(row.title)}">${attachCount} 个</button></td>`
            + `<td>${escapeHtml(row.dueDate || '-')}</td><td class="action-cell">${renderActionButtons(row)}</td></tr>`;
    }).join('');
}

function ocrStatusLabel(s) {
    return { PENDING: '待处理', PROCESSING: '处理中', SUCCESS: '成功', FAILED: '失败' }[s] || s;
}

async function openAttachmentModal(contractId, title) {
    const modal = $('#attachmentModal');
    $('#attachmentModalTitle').textContent = `${title} · 附件列表`;
    const body = $('#attachmentModalBody');
    body.innerHTML = '<p class="hint">加载中...</p>';
    modal.hidden = false;
    const list = await api(`/api/contracts/${contractId}/attachments`);
    if (!list.length) {
        body.innerHTML = '<p class="hint">暂无附件</p>';
        return;
    }
    body.innerHTML = list.map(item => `<article class="attachment-item"><div class="attachment-meta"><strong>${escapeHtml(item.fileName)}</strong><span>${escapeHtml(item.fileType || '')} · ${((item.fileSize || 0) / 1024).toFixed(1)} KB</span><span class="tag">${escapeHtml(ocrStatusLabel(item.ocrStatus))}</span></div><pre class="ocr-preview">${escapeHtml(item.ocrTextPreview || item.ocrError || '暂无识别内容')}</pre><div class="button-row"><a class="secondary" href="${item.downloadUrl || '#'}" data-download="${item.attachmentId}">下载原件</a></div></article>`).join('');
    body.querySelectorAll('[data-download]').forEach(link => {
        link.addEventListener('click', event => {
            event.preventDefault();
            downloadAttachment(link.dataset.download, link.closest('.attachment-item')?.querySelector('strong')?.textContent).catch(e => toast(e.message));
        });
    });
}

async function openDetailModal(contractId, title) {
    const modal = $('#detailModal');
    $('#detailModalTitle').textContent = `${title} · 合同详情`;
    const body = $('#detailModalBody');
    body.innerHTML = '<p class="hint">加载中...</p>';
    modal.hidden = false;
    try {
        const [contract, sealRecords, archiveRecords, versions, attachments, approvals] = await Promise.all([
            (async () => {
                const list = await api('/api/contracts');
                return list.find(c => c.contractId === Number(contractId)) || null;
            })(),
            api(`/api/contracts/${contractId}/seal-records`).catch(() => []),
            api(`/api/contracts/${contractId}/archive-records`).catch(() => []),
            api(`/api/contracts/${contractId}/versions`).catch(() => []),
            api(`/api/contracts/${contractId}/attachments`).catch(() => []),
            api('/api/approvals').catch(() => [])
        ]);
        if (!contract) {
            body.innerHTML = '<p class="hint">未找到合同详情</p>';
            return;
        }
        const tagCls = statusTagClass[contract.status] || 'tag-gray';
        const lockedIds = new Set();
        archiveRecords.forEach(r => { if (r.isLocked && r.versionId) lockedIds.add(r.versionId); });
        versions.forEach(v => { if (v.isLocked && v.versionId) lockedIds.add(v.versionId); });
        const signedAttachments = attachments.filter(a => a.attachType === 'SIGNED_FILE');
        const sealFileNames = new Set(sealRecords.map(r => r.fileName).filter(Boolean));
        const signedFiles = [
            ...sealRecords.map(r => ({
                fileName: r.fileName || '签章文件',
                fileType: '',
                fileSize: null,
                downloadUrl: r.fileUrl,
                attachmentId: null
            })),
            ...signedAttachments.filter(a => !sealFileNames.has(a.fileName))
        ];
        const contractApprovals = approvals.filter(a => a.contractId === Number(contractId));
        body.innerHTML = `
        <div class="detail-section"><h4>基本信息</h4><div class="detail-grid">
        <div class="detail-item"><label>合同编号</label><span>${escapeHtml(contract.contractNo)}</span></div>
        <div class="detail-item"><label>合同标题</label><span>${escapeHtml(contract.title)}</span></div>
        <div class="detail-item"><label>相对方</label><span>${escapeHtml(contract.counterparty)}</span></div>
        <div class="detail-item"><label>金额</label><span>¥${Number(contract.amount || 0).toLocaleString()}</span></div>
        <div class="detail-item"><label>类型</label><span>${escapeHtml(contract.type)}</span></div>
        <div class="detail-item"><label>状态</label><span class="tag ${tagCls}">${escapeHtml(STATUS_TEXT[contract.status] || contract.status)}</span></div>
        <div class="detail-item"><label>风险等级</label><span class="tag ${escapeHtml(contract.riskLevel)}">${escapeHtml(RISK_TEXT[contract.riskLevel] || contract.riskLevel || '未评估')}</span></div>
        <div class="detail-item"><label>签署日期</label><span>${escapeHtml(contract.signDate || '-')}</span></div>
        <div class="detail-item"><label>到期日期</label><span>${escapeHtml(contract.dueDate || '-')}</span></div></div></div>
        <div class="detail-section"><h4>签章记录</h4>${sealRecords.length === 0 ? '<p class="hint">暂无签章记录</p>' : sealRecords.map(r => `<div class="record-item"><span><strong>${escapeHtml(r.fileName || '签章文件')}</strong></span><span>状态：${escapeHtml(SEAL_STATUS_TEXT[r.sealStatus] || r.sealStatus || '-')}</span><span>时间：${escapeHtml(r.sealTime || '-')}</span>${r.remark ? `<span class="hint">备注：${escapeHtml(r.remark)}</span>` : ''}</div>`).join('')}</div>
        <div class="detail-section"><h4>归档信息</h4>${archiveRecords.length === 0 ? '<p class="hint">暂无归档记录</p>' : archiveRecords.map(r => `<div class="record-item"><span><strong>归档编号：${escapeHtml(r.archiveNo)}</strong></span><span>归档时间：${escapeHtml(r.archiveTime || '-')}</span><span>知识库ID：${escapeHtml(r.knowledgeId || '-')}</span><span>Merkle Root：${escapeHtml(r.merkleRoot || '-')}</span><span>状态：${r.isLocked ? '<span class="tag tag-green">已锁定</span>' : '<span class="tag tag-gray">未锁定</span>'}</span></div>`).join('')}</div>
        <div class="detail-section"><h4>签章文件</h4>${signedFiles.length === 0 ? '<p class="hint">暂无签章文件</p>' : signedFiles.map(a => `<div class="record-item"><strong>${escapeHtml(a.fileName)}</strong>${a.fileSize != null ? `<span>${escapeHtml(a.fileType || '')} · ${(a.fileSize / 1024).toFixed(1)} KB</span>` : ''}${a.downloadUrl ? `<a class="secondary" href="${escapeHtml(a.downloadUrl)}" data-direct-download="${escapeHtml(a.downloadUrl)}">下载</a>` : a.attachmentId ? `<button class="secondary" data-download="${a.attachmentId}">下载</button>` : ''}</div>`).join('')}</div>
        <div class="detail-section"><h4>历史版本 (${versions.length})</h4>${versions.length === 0 ? '<p class="hint">暂无版本记录</p>' : versions.map(v => `<div class="record-item"><span><strong>${escapeHtml(v.versionNo)}</strong>${lockedIds.has(v.versionId) ? ' <span class="tag tag-green">已锁定</span>' : ''}</span><span>创建时间：${escapeHtml(v.createdAt || '-')}</span><span>创建人：${escapeHtml(v.createdBy || '-')}</span></div>`).join('')}<p class="hint" style="margin-top:8px"><a href="/html/draft.html?contractId=${contractId}">查看或编辑合同正文</a></p></div>
        <div class="detail-section"><h4>审批记录</h4>${contractApprovals.length === 0 ? '<p class="hint">暂无审批记录</p>' : contractApprovals.map(a => `<div class="record-item"><span><strong>流程：${escapeHtml(a.flowType)}</strong></span><span>当前节点：${escapeHtml(a.currentNode || '-')}</span><span>状态：${escapeHtml(a.status)}</span><span>发起时间：${escapeHtml(a.startedAt || '-')}</span></div>`).join('')}</div>
        <div class="button-row" style="margin-top:16px"><a class="secondary" href="/html/draft.html?contractId=${contractId}">编辑合同</a><button type="button" class="secondary" id="closeDetailFromBody">关闭</button></div>`;
        body.querySelectorAll('[data-download]').forEach(link => {
            link.addEventListener('click', event => {
                event.preventDefault();
                downloadAttachment(link.dataset.download, link.closest('.record-item')?.querySelector('strong')?.textContent).catch(e => toast(e.message));
            });
        });
        body.querySelectorAll('[data-direct-download]').forEach(link => {
            link.addEventListener('click', event => {
                event.preventDefault();
                downloadUrl(link.dataset.directDownload, link.closest('.record-item')?.querySelector('strong')?.textContent).catch(e => toast(e.message));
            });
        });
        $('#closeDetailFromBody').addEventListener('click', () => { $('#detailModal').hidden = true; });
    } catch (error) {
        body.innerHTML = `<p class="hint">加载详情失败：${escapeHtml(error.message)}</p>`;
    }
}

async function downloadAttachment(attachmentId, filename) {
    const response = await fetch(`/api/attachments/${attachmentId}/download`, { headers: state.accessToken ? { Authorization: `Bearer ${state.accessToken}` } : {} });
    if (response.status === 401) { logout(); return; }
    if (!response.ok) throw new Error('下载失败');
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename || 'attachment';
    a.click();
    URL.revokeObjectURL(url);
}

async function downloadUrl(downloadUrl, filename) {
    const response = await fetch(downloadUrl, { headers: state.accessToken ? { Authorization: `Bearer ${state.accessToken}` } : {} });
    if (response.status === 401) { logout(); return; }
    if (!response.ok) throw new Error('下载失败');
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename || 'file';
    a.click();
    URL.revokeObjectURL(url);
}

$('#createForm').addEventListener('submit', async event => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const payload = Object.fromEntries(form.entries());
    payload.amount = Number(payload.amount);
    payload.deptId = 1;
    payload.ownerId = 1;
    payload.dueDate = new Date(Date.now() + 90 * 86400000).toISOString().slice(0, 10);
    await api('/api/contracts', { method: 'POST', body: JSON.stringify(payload) });
    event.currentTarget.reset();
    toast('合同创建成功');
    await loadContracts();
});

$('#contractRows').addEventListener('click', async event => {
    const attachBtn = event.target.closest('[data-attachments]');
    if (attachBtn) {
        try { await openAttachmentModal(attachBtn.dataset.attachments, attachBtn.dataset.title); } catch (e) { toast(e.message); }
        return;
    }
    const submitBtn = event.target.closest('[data-submit]');
    if (submitBtn) {
        try {
            await api(`/api/contracts/${submitBtn.dataset.submit}/submit`, { method: 'POST' });
            toast('已提交审批');
            await loadContracts();
        } catch (e) { toast(e.message); }
        return;
    }
    const detailBtn = event.target.closest('[data-detail]');
    if (detailBtn) {
        try { await openDetailModal(detailBtn.dataset.detail, detailBtn.dataset.title); } catch (e) { toast(e.message); }
        return;
    }
    const statusTag = event.target.closest('[data-status-edit]');
    if (statusTag) {
        event.stopPropagation();
        showStatusDropdown(statusTag);
    }
});

$('#closeAttachmentModal').addEventListener('click', () => { $('#attachmentModal').hidden = true; });
$('#attachmentModal').addEventListener('click', event => { if (event.target.id === 'attachmentModal') $('#attachmentModal').hidden = true; });
$('#closeDetailModal').addEventListener('click', () => { $('#detailModal').hidden = true; });
$('#detailModal').addEventListener('click', event => { if (event.target.id === 'detailModal') $('#detailModal').hidden = true; });

function showStatusDropdown(tagEl) {
    const contractId = tagEl.dataset.statusEdit;
    const currentStatus = tagEl.dataset.current;
    const allStatuses = ['DRAFT', 'APPROVING', 'APPROVED', 'SIGNING', 'ARCHIVED', 'EXECUTING', 'COMPLETED', 'EXPIRED', 'TERMINATED'];
    $$('.status-dropdown').forEach(d => d.remove());
    const dd = document.createElement('div');
    dd.className = 'status-dropdown';
    dd.innerHTML = allStatuses.map(s => {
        const cur = s === currentStatus;
        return `<div class="status-option${cur ? ' current' : ''}" data-status="${s}">${cur ? '✓ ' : ''}${STATUS_TEXT[s] || s}</div>`;
    }).join('');
    dd.querySelectorAll('.status-option').forEach(opt => {
        opt.addEventListener('click', async e => {
            e.stopPropagation();
            const target = opt.dataset.status;
            if (target === currentStatus) { dd.remove(); return; }
            try {
                opt.textContent = '更新中...';
                await api(`/api/admin/contracts/${contractId}/fields`, { method: 'PUT', body: JSON.stringify({ status: target }) });
                toast(`状态已更新为：${STATUS_TEXT[target] || target}`);
                dd.remove();
                await loadContracts();
            } catch (err) {
                toast(err.message);
                dd.remove();
            }
        });
    });
    const rect = tagEl.getBoundingClientRect();
    dd.style.top = (rect.bottom + 4) + 'px';
    dd.style.left = rect.left + 'px';
    document.body.appendChild(dd);
    setTimeout(() => {
        document.addEventListener('click', function closeDD(e) {
            if (!dd.contains(e.target) && e.target !== tagEl) {
                dd.remove();
                document.removeEventListener('click', closeDD);
            }
        });
    }, 0);
}

['keyword', 'statusFilter', 'riskFilter'].forEach(id => $(`#${id}`).addEventListener('input', () => loadContracts().catch(e => toast(e.message))));
$('#resetBtn').addEventListener('click', () => {
    $('#keyword').value = '';
    $('#statusFilter').value = '';
    $('#riskFilter').value = '';
    loadContracts().catch(e => toast(e.message));
});

loadContracts().catch(e => toast(e.message));

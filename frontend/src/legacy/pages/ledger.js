if (!initAppShell('ledger', '合同台账', '合同全量检索，支持多条件筛选与状态跟踪')) throw new Error('auth required');

// ==================== 合同台账表格 ====================
var allRows = [];
var pageSize = 10;
var currentPage = 1;

function collectFilters() {
    var params = new URLSearchParams();
    if ($('#keyword').value) params.set('keyword', $('#keyword').value);
    if ($('#statusFilter').value) params.set('status', $('#statusFilter').value);
    if ($('#riskFilter').value) params.set('riskLevel', $('#riskFilter').value);
    if ($('#typeFilter').value) params.set('type', $('#typeFilter').value);
    if ($('#dateFrom').value) params.set('signDateFrom', $('#dateFrom').value);
    if ($('#dateTo').value) params.set('signDateTo', $('#dateTo').value);
    if ($('#amountMin').value) params.set('amountMin', $('#amountMin').value);
    if ($('#amountMax').value) params.set('amountMax', $('#amountMax').value);
    return params.toString();
}

function loadAttachmentCount(contractId) {
    return api('/api/contracts/' + contractId + '/attachment-count').then(function (d) {
        return (d != null && typeof d.count === 'number') ? d.count : (typeof d === 'number' ? d : 0);
    }).catch(function () { return 0; });
}

async function getLatestVersionId(contractId) {
    try { var v = await api('/api/contracts/' + contractId + '/versions'); return (v && v.length) ? v[0].versionId : null; } catch (_) { return null; }
}

function renderActionButtons(row) {
    var btns = [];
    var canOp = canOperateSealArchive();
    var cid = row.contractId;
    btns.push('<button class="secondary" data-detail="' + cid + '" data-title="' + escapeHtml(row.title) + '">查看</button>');
    if (row.status === 'DRAFT' || row.status === 'APPROVING') {
        btns.push('<a class="secondary" href="/html/edit.html?contractId=' + cid + '">编辑</a>');
        if (row.status === 'DRAFT') {
            var blocked = row.riskLevel === 'HIGH';
            btns.push('<button class="secondary" data-submit="' + cid + '" ' + (blocked ? 'disabled' : '') + ' title="' + (blocked ? '存在未复核的高风险问题，暂不可提交审批' : '提交审批') + '">提交审批</button>');
        }
    } else if (row.status === 'APPROVED') {
        if (canOp) btns.push('<a class="primary-btn" href="/html/seal.html?contractId=' + cid + '&versionId=' + (row._latestVersionId || '') + '">签章登记</a>');
    } else if (row.status === 'SIGNING') {
        if (canOp) btns.push('<a class="primary-btn" href="/html/archive.html?contractId=' + cid + '&versionId=' + (row._latestVersionId || '') + '">归档确认</a>');
    } else if (row.status === 'ARCHIVED') {
        btns.push('<a class="link-btn" href="/html/fulfillment.html?contractId=' + cid + '">创建履约计划</a>');
    }
    return btns.join(' ');
}

function renderPage() {
    var totalPages = Math.ceil(allRows.length / pageSize) || 1;
    if (currentPage > totalPages) currentPage = totalPages;
    var start = (currentPage - 1) * pageSize;
    var pageRows = allRows.slice(start, start + pageSize);
    $('#contractRows').innerHTML = pageRows.join('');

    // 渲染分页控件
    var html = '';
    html += '<button class="secondary" ' + (currentPage <= 1 ? 'disabled' : '') + ' id="prevPage">上一页</button>';
    html += '<span style="color:var(--ink-secondary)">第 ' + currentPage + ' / ' + totalPages + ' 页，共 ' + allRows.length + ' 条</span>';
    html += '<button class="secondary" ' + (currentPage >= totalPages ? 'disabled' : '') + ' id="nextPage">下一页</button>';
    $('#pagination').innerHTML = html;

    var self = this;
    var prevBtn = $('#prevPage');
    var nextBtn = $('#nextPage');
    if (prevBtn) prevBtn.addEventListener('click', function () { if (currentPage > 1) { currentPage--; renderPage(); } });
    if (nextBtn) nextBtn.addEventListener('click', function () { if (currentPage < totalPages) { currentPage++; renderPage(); } });
}

async function loadContracts() {
    var qs = collectFilters();
    var rows = await api('/api/contracts?' + qs);
    var counts = await Promise.all(rows.map(function (r) { return loadAttachmentCount(r.contractId); }));
    var versionIds = await Promise.all(rows.map(function (r) { return getLatestVersionId(r.contractId); }));
    rows.forEach(function (r, i) { r._latestVersionId = versionIds[i]; });
    allRows = rows.map(function (row, index) {
        var attachCount = counts[index] || 0;
        var label = STATUS_TEXT[row.status] || row.status;
        var cls = statusTagClass[row.status] || 'tag-gray';
        var sc = state.roleCode === 'ADMIN'
            ? '<span class="tag ' + cls + ' status-editable" data-status-edit="' + row.contractId + '" data-current="' + escapeHtml(row.status) + '" title="点击修改状态">' + escapeHtml(label) + '</span>'
            : '<span class="tag ' + cls + '">' + escapeHtml(label) + '</span>';
        return '<tr><td>' + escapeHtml(row.contractNo) + '</td><td><strong>' + escapeHtml(row.title) + '</strong></td><td>' + escapeHtml(row.counterparty) + '</td><td>¥' + Number(row.amount || 0).toLocaleString() + '</td>'
            + '<td>' + sc + '</td>'
            + '<td><span class="tag ' + escapeHtml(row.riskLevel) + '">' + escapeHtml(RISK_TEXT[row.riskLevel] || row.riskLevel || '未评估') + '</span></td>'
            + '<td><button type="button" class="secondary" data-attachments="' + row.contractId + '" data-title="' + escapeHtml(row.title) + '">' + attachCount + ' 个</button></td>'
            + '<td>' + escapeHtml(row.dueDate || '-') + '</td><td class="action-cell">' + renderActionButtons(row) + '</td></tr>';
    });
    currentPage = 1;
    renderPage();
}

function ocrStatusLabel(s) { return {PENDING:'待处理',PROCESSING:'处理中',SUCCESS:'成功',FAILED:'失败'}[s] || s; }

async function openAttachmentModal(contractId, title) {
    var m = $('#attachmentModal');
    $('#attachmentModalTitle').textContent = title + ' · 附件列表';
    var b = $('#attachmentModalBody');
    b.innerHTML = '<p class="hint">加载中...</p>';
    m.hidden = false;
    var list = await api('/api/contracts/' + contractId + '/attachments');
    if (!list.length) { b.innerHTML = '<p class="hint">暂无附件</p>'; return; }
    b.innerHTML = list.map(function (item) {
        return '<article class="attachment-item"><div class="attachment-meta"><strong>' + escapeHtml(item.fileName) + '</strong><span>' + escapeHtml(item.fileType || '') + ' · ' + ((item.fileSize || 0) / 1024).toFixed(1) + ' KB</span><span class="tag">' + escapeHtml(ocrStatusLabel(item.ocrStatus)) + '</span></div><div class="button-row"><a class="secondary" href="' + (item.downloadUrl || '#') + '" data-download="' + item.attachmentId + '">下载原件</a></div></article>';
    }).join('');
    b.querySelectorAll('[data-download]').forEach(function (link) {
        link.addEventListener('click', function (e) { e.preventDefault(); downloadAttachment(link.dataset.download, link.closest('.attachment-item').querySelector('strong').textContent).catch(function (err) { toast(err.message); }); });
    });
}

async function openDetailModal(contractId, title) {
    var m = $('#detailModal');
    $('#detailModalTitle').textContent = title + ' · 合同详情';
    var b = $('#detailModalBody');
    b.innerHTML = '<p class="hint">加载中...</p>';
    m.hidden = false;
    try {
        var list = await api('/api/contracts');
        var contract = list.find(function (c) { return c.contractId === Number(contractId); }) || null;
        var sealRecords = await api('/api/contracts/' + contractId + '/seal-records').catch(function () { return []; });
        var archiveRecords = await api('/api/contracts/' + contractId + '/archive-records').catch(function () { return []; });
        var versions = await api('/api/contracts/' + contractId + '/versions').catch(function () { return []; });
        var attachments = await api('/api/contracts/' + contractId + '/attachments').catch(function () { return []; });
        var approvals = await api('/api/approvals').catch(function () { return []; });
        if (!contract) { b.innerHTML = '<p class="hint">未找到合同详情</p>'; return; }
        var cls = statusTagClass[contract.status] || 'tag-gray';
        var lockedIds = new Set();
        archiveRecords.forEach(function (r) { if (r.isLocked && r.versionId) lockedIds.add(r.versionId); });
        versions.forEach(function (v) { if (v.isLocked && v.versionId) lockedIds.add(v.versionId); });
        var signedAttachments = attachments.filter(function (a) { return a.attachType === 'SIGNED_FILE'; });
        var seenFileNames = new Set();
        var signedFiles = [];
        // SIGNED_FILE 附件优先（已签章文件）
        signedAttachments.forEach(function (a) {
            if (!seenFileNames.has(a.fileName)) {
                seenFileNames.add(a.fileName);
                signedFiles.push({ fileName: a.fileName, fileType: a.fileType || '', fileSize: a.fileSize, downloadUrl: a.downloadUrl, attachmentId: a.attachmentId });
            }
        });
        // 补充未覆盖的签章记录（物理盖章文件，必须有下载链接）
        sealRecords.forEach(function (r) {
            if (!r.fileUrl) return; // 无下载链接跳过（电子签章 NULL 文件）
            var name = r.fileName || '签章文件';
            if (!seenFileNames.has(name)) {
                seenFileNames.add(name);
                signedFiles.push({ fileName: name, fileType: '', fileSize: null, downloadUrl: r.fileUrl, attachmentId: null });
            }
        });
        var contractApprovals = approvals.filter(function (a) { return a.contractId === Number(contractId); });
        b.innerHTML =
            '<div class="detail-section"><h4>基本信息</h4><div class="detail-grid">' +
            '<div class="detail-item"><label>合同编号</label><span>' + escapeHtml(contract.contractNo) + '</span></div>' +
            '<div class="detail-item"><label>合同标题</label><span>' + escapeHtml(contract.title) + '</span></div>' +
            '<div class="detail-item"><label>相对方</label><span>' + escapeHtml(contract.counterparty) + '</span></div>' +
            '<div class="detail-item"><label>金额</label><span>¥' + Number(contract.amount || 0).toLocaleString() + '</span></div>' +
            '<div class="detail-item"><label>类型</label><span>' + escapeHtml(contract.type) + '</span></div>' +
            '<div class="detail-item"><label>状态</label><span class="tag ' + cls + '">' + escapeHtml(STATUS_TEXT[contract.status] || contract.status) + '</span></div>' +
            '<div class="detail-item"><label>风险等级</label><span class="tag ' + escapeHtml(contract.riskLevel) + '">' + escapeHtml(RISK_TEXT[contract.riskLevel] || contract.riskLevel || '未评估') + '</span></div>' +
            '<div class="detail-item"><label>签署日期</label><span>' + escapeHtml(contract.signDate || '-') + '</span></div>' +
            '<div class="detail-item"><label>到期日期</label><span>' + escapeHtml(contract.dueDate || '-') + '</span></div></div></div>' +
            '<div class="detail-section"><h4>签章记录</h4>' + (sealRecords.length === 0 ? '<p class="hint">暂无签章记录</p>' : sealRecords.map(function (r) { return '<div class="record-item"><span><strong>' + escapeHtml(r.fileName || '签章文件') + '</strong></span><span>状态：' + escapeHtml(SEAL_STATUS_TEXT[r.sealStatus] || r.sealStatus || '-') + '</span><span>时间：' + escapeHtml(r.sealTime || '-') + '</span>' + (r.remark ? '<span class="hint">备注：' + escapeHtml(r.remark) + '</span>' : '') + '</div>'; }).join('')) + '</div>' +
            '<div class="detail-section"><h4>归档信息</h4>' + (archiveRecords.length === 0 ? '<p class="hint">暂无归档记录</p>' : archiveRecords.map(function (r) { return '<div class="record-item"><span><strong>归档编号：' + escapeHtml(r.archiveNo) + '</strong></span><span>归档时间：' + escapeHtml(r.archiveTime || '-') + '</span><span>Merkle Root：' + escapeHtml(r.merkleRoot || '-') + '</span><span>状态：' + (r.isLocked ? '<span class="tag tag-green">已锁定</span>' : '<span class="tag tag-gray">未锁定</span>') + '</span></div>'; }).join('')) + '</div>' +
            '<div class="detail-section"><h4>签章文件</h4>' + (signedFiles.length === 0 ? '<p class="hint">暂无签章文件</p>' : signedFiles.map(function (a) { return '<div class="record-item"><strong>' + escapeHtml(a.fileName) + '</strong>' + (a.fileSize != null ? '<span>' + escapeHtml(a.fileType || '') + ' · ' + (a.fileSize / 1024).toFixed(1) + ' KB</span>' : '') + (a.downloadUrl ? '<a class="secondary" href="' + escapeHtml(a.downloadUrl) + '" data-direct-download="' + escapeHtml(a.downloadUrl) + '">下载</a>' : a.attachmentId ? '<button class="secondary" data-download="' + a.attachmentId + '">下载</button>' : '') + '</div>'; }).join('')) + '</div>' +
            '<div class="detail-section"><h4>历史版本 (' + versions.length + ')</h4>' + (versions.length === 0 ? '<p class="hint">暂无版本记录</p>' : versions.map(function (v) { return '<div class="record-item"><span><strong>' + escapeHtml(v.versionNo) + '</strong>' + (lockedIds.has(v.versionId) ? ' <span class="tag tag-green">已锁定</span>' : '') + '</span><span>创建时间：' + escapeHtml(v.createdAt || '-') + '</span><span>创建人：' + escapeHtml(v.createdBy || '-') + '</span><button type="button" class="secondary" data-version-download="' + escapeHtml(v.downloadUrl || ('/api/contracts/' + contractId + '/versions/' + v.versionId + '/download')) + '" data-version-file-name="' + escapeHtml('contract-' + contractId + '-' + v.versionNo + '.docx') + '">下载 Word</button></div>'; }).join('')) + '<p class="hint" style="margin-top:8px"><a href="/html/draft.html?contractId=' + contractId + '">查看或编辑合同正文</a></p></div>' +
            '<div class="detail-section"><h4>审批记录</h4>' + (contractApprovals.length === 0 ? '<p class="hint">暂无审批记录</p>' : contractApprovals.map(function (a) { return '<div class="record-item"><span><strong>流程：' + escapeHtml(a.flowType) + '</strong></span><span>当前节点：' + escapeHtml(a.currentNode || '-') + '</span><span>状态：' + escapeHtml(a.status) + '</span><span>发起时间：' + escapeHtml(a.startedAt || '-') + '</span></div>'; }).join('')) + '</div>' +
            '<div class="button-row" style="margin-top:16px"><a class="secondary" href="/html/draft.html?contractId=' + contractId + '">编辑合同</a><button type="button" class="secondary" id="closeDetailFromBody">关闭</button></div>';
        b.querySelectorAll('[data-download]').forEach(function (link) {
            link.addEventListener('click', function (e) { e.preventDefault(); downloadAttachment(link.dataset.download, link.closest('.record-item').querySelector('strong').textContent).catch(function (err) { toast(err.message); }); });
        });
        b.querySelectorAll('[data-direct-download]').forEach(function (link) {
            link.addEventListener('click', function (e) { e.preventDefault(); downloadUrl(link.dataset.directDownload, link.closest('.record-item').querySelector('strong').textContent).catch(function (err) { toast(err.message); }); });
        });
        b.querySelectorAll('[data-version-download]').forEach(function (button) {
            button.addEventListener('click', function () {
                button.disabled = true;
                downloadUrl(button.dataset.versionDownload, button.dataset.versionFileName)
                    .catch(function (err) { toast(err.message); })
                    .finally(function () { button.disabled = false; });
            });
        });
        $('#closeDetailFromBody').addEventListener('click', function () { $('#detailModal').hidden = true; });
    } catch (err) { b.innerHTML = '<p class="hint">加载详情失败：' + escapeHtml(err.message) + '</p>'; }
}

async function downloadAttachment(attachmentId, filename) {
    var resp = await fetch('/api/attachments/' + attachmentId + '/download', { headers: state.accessToken ? { Authorization: 'Bearer ' + state.accessToken } : {} });
    if (resp.status === 401) { logout(); return; }
    if (!resp.ok) throw new Error('下载失败');
    var blob = await resp.blob();
    var url = URL.createObjectURL(blob);
    var a = document.createElement('a'); a.href = url; a.download = filename || 'attachment'; a.click();
    URL.revokeObjectURL(url);
}

async function downloadUrl(downloadUrl, filename) {
    var resp = await fetch(downloadUrl, { headers: state.accessToken ? { Authorization: 'Bearer ' + state.accessToken } : {} });
    if (resp.status === 401) { logout(); return; }
    if (!resp.ok) throw new Error('下载失败');
    var blob = await resp.blob();
    var url = URL.createObjectURL(blob);
    var a = document.createElement('a'); a.href = url; a.download = filename || 'file'; a.click();
    URL.revokeObjectURL(url);
}

// ==================== 事件委托 ====================

$('#contractRows').addEventListener('click', async function (e) {
    var attachBtn = e.target.closest('[data-attachments]');
    if (attachBtn) { try { await openAttachmentModal(attachBtn.dataset.attachments, attachBtn.dataset.title); } catch (err) { toast(err.message); } return; }
    var submitBtn = e.target.closest('[data-submit]');
    if (submitBtn) { submitBtn.disabled = true; submitBtn.textContent = '提交中...'; try { await api('/api/contracts/' + submitBtn.dataset.submit + '/submit', { method: 'POST' }); toast('已提交审批'); await loadContracts(); } catch (err) { toast(err.message); } finally { submitBtn.disabled = false; submitBtn.textContent = '提交审批'; } return; }
    var detailBtn = e.target.closest('[data-detail]');
    if (detailBtn) { try { await openDetailModal(detailBtn.dataset.detail, detailBtn.dataset.title); } catch (err) { toast(err.message); } return; }
    var statusTag = e.target.closest('[data-status-edit]');
    if (statusTag) { e.stopPropagation(); showStatusDropdown(statusTag); }
});

$('#closeAttachmentModal').addEventListener('click', function () { $('#attachmentModal').hidden = true; });
$('#attachmentModal').addEventListener('click', function (e) { if (e.target.id === 'attachmentModal') $('#attachmentModal').hidden = true; });
$('#closeDetailModal').addEventListener('click', function () { $('#detailModal').hidden = true; });
$('#detailModal').addEventListener('click', function (e) { if (e.target.id === 'detailModal') $('#detailModal').hidden = true; });

function showStatusDropdown(tagEl) {
    var contractId = tagEl.dataset.statusEdit;
    var currentStatus = tagEl.dataset.current;
    var allStatuses = ['DRAFT', 'APPROVING', 'APPROVED', 'SIGNING', 'ARCHIVED', 'EXECUTING', 'COMPLETED', 'EXPIRED', 'TERMINATED'];
    $$('.status-dropdown').forEach(function (d) { d.remove(); });
    var dd = document.createElement('div');
    dd.className = 'status-dropdown';
    dd.innerHTML = allStatuses.map(function (s) {
        var cur = s === currentStatus;
        return '<div class="status-option' + (cur ? ' current' : '') + '" data-status="' + s + '">' + (cur ? '✓ ' : '') + (STATUS_TEXT[s] || s) + '</div>';
    }).join('');
    dd.querySelectorAll('.status-option').forEach(function (opt) {
        opt.addEventListener('click', async function (ev) {
            ev.stopPropagation();
            var target = opt.dataset.status;
            if (target === currentStatus) { dd.remove(); return; }
            try {
                opt.textContent = '更新中...';
                await api('/api/admin/contracts/' + contractId + '/fields', { method: 'PUT', body: JSON.stringify({ status: target }) });
                toast('状态已更新为：' + (STATUS_TEXT[target] || target));
                dd.remove();
                await loadContracts();
            } catch (err) { toast(err.message); dd.remove(); }
        });
    });
    var rect = tagEl.getBoundingClientRect();
    dd.style.top = (rect.bottom + 4) + 'px';
    dd.style.left = rect.left + 'px';
    document.body.appendChild(dd);
    setTimeout(function () {
        document.addEventListener('click', function closeDD(ev) { if (!dd.contains(ev.target) && ev.target !== tagEl) { dd.remove(); document.removeEventListener('click', closeDD); } });
    }, 0);
}

// ==================== 表格筛选：自动触发 ====================
['keyword', 'statusFilter', 'riskFilter', 'typeFilter', 'dateFrom', 'dateTo', 'amountMin', 'amountMax'].forEach(function (id) {
    $('#' + id).addEventListener('input', function () { loadContracts().catch(function (e) { toast(e.message); }); });
});
$('#resetBtn').addEventListener('click', function () {
    $('#keyword').value = '';
    $('#statusFilter').value = '';
    $('#riskFilter').value = '';
    $('#typeFilter').value = '';
    $('#dateFrom').value = '';
    $('#dateTo').value = '';
    $('#amountMin').value = '';
    $('#amountMax').value = '';
    loadContracts().catch(function (e) { toast(e.message); });
});

// ==================== 初始化 ====================
loadContracts().catch(function (e) { toast(e.message); });

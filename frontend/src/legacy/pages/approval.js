if (!initAppShell('approval', '审批中心', '查看并处理待审批合同，支持多级流程流转')) {
    throw new Error('auth required');
}

function canOperateNode(item) {
    if (item.status !== 'RUNNING') return false;
    const roles = APPROVAL_NODE_ROLES[item.currentNode] || APPROVAL_NODE_ROLES['部门主管审批'];
    return roles.includes(state.roleCode);
}

function normalizeApprovalNode(nodeName) {
    return ({
        '法务复核': '法务专员审批',
        '财务审批': '财务专员审批',
        '高管审批': '企业高管审批'
    })[nodeName] || nodeName || '';
}

function approvalNodeRole(nodeName) {
    return ({
        '提交审批': 'USER',
        '部门主管审批': 'DEPT_LEADER',
        '法务专员审批': 'LEGAL',
        '财务专员审批': 'FINANCE',
        '企业高管审批': 'EXECUTIVE'
    })[normalizeApprovalNode(nodeName)] || 'USER';
}

function approvalActionText(action) {
    return ({SUBMIT: '提交', AGREE: '同意', REJECT: '驳回'})[action] || action || '-';
}

function approvalTime(value) {
    if (!value) return '-';
    if (Array.isArray(value)) {
        const [year, month, day, hour = 0, minute = 0, second = 0] = value;
        return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')} ${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:${String(second).padStart(2, '0')}`;
    }
    return String(value).replace('T', ' ').substring(0, 19);
}

function formatFileSize(bytes) {
    if (bytes == null) return '-';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

// ==================== 合同详情弹窗 ====================

function ensureDetailModal() {
    if ($('#approvalDetailModal')) return;
    const overlay = document.createElement('div');
    overlay.id = 'approvalDetailModal';
    overlay.className = 'modal';
    overlay.hidden = true;
    overlay.innerHTML = `
        <div class="modal-card" style="width:min(720px,100%);max-height:88vh;padding:28px 28px 24px;">
            <div class="modal-head" style="margin-bottom:18px;padding-bottom:14px;">
                <h2 id="detailModalTitle">合同详情</h2>
                <button class="secondary" type="button" id="detailModalClose" style="padding:6px 14px;font-size:12px;">关闭</button>
            </div>
            <div id="detailModalBody" style="font-size:13px;line-height:1.7;color:var(--ink-secondary);"></div>
        </div>`;
    document.body.appendChild(overlay);

    const close = function () { $('#approvalDetailModal').hidden = true; };
    overlay.addEventListener('click', function (e) { if (e.target === overlay) close(); });
    $('#detailModalClose').addEventListener('click', close);
}

async function openContractDetail(contractId, contractLabel) {
    ensureDetailModal();
    const titleEl = $('#detailModalTitle');
    const bodyEl = $('#detailModalBody');
    titleEl.textContent = contractLabel;
    bodyEl.innerHTML = '<p class="hint" style="text-align:center;padding:24px 0;">加载中...</p>';
    $('#approvalDetailModal').hidden = false;

    try {
        const [contract, attachments, latestVersion] = await Promise.all([
            api('/api/contracts/' + contractId).catch(() => null),
            api('/api/contracts/' + contractId + '/attachments').catch(() => []),
            api('/api/contracts/' + contractId + '/versions/latest').catch(() => null)
        ]);

        if (!contract) {
            bodyEl.innerHTML = '<p class="hint" style="text-align:center;padding:24px 0;">未找到合同信息</p>';
            return;
        }

        const statusText = STATUS_TEXT[contract.status] || contract.status;
        const statusCls = statusTagClass[contract.status] || 'tag-gray';
        const riskText = RISK_TEXT[contract.riskLevel] || contract.riskLevel || '未评估';
        const riskCls = contract.riskLevel || '';

        // ---- 基本信息 ----
        let html = '<div class="approval-detail-section">'
            + '<h4 style="margin:0 0 12px;font-size:14px;color:var(--ink);">基本信息</h4>'
            + '<div class="approval-detail-grid">'
            + detailRow('合同编号', escapeHtml(contract.contractNo || '-'))
            + detailRow('合同标题', escapeHtml(contract.title || '-'))
            + detailRow('合同类型', escapeHtml(contract.type || '-'))
            + detailRow('合同金额', contract.amount != null ? '¥' + Number(contract.amount).toLocaleString() : '-')
            + detailRow('相对方', escapeHtml(contract.counterparty || '-'))
            + detailRow('签署日期', escapeHtml(contract.signDate || '-'))
            + detailRow('到期日期', escapeHtml(contract.dueDate || '-'))
            + detailRow('当前状态', '<span class="tag ' + statusCls + '">' + escapeHtml(statusText) + '</span>')
            + detailRow('风险等级', '<span class="tag ' + escapeHtml(riskCls) + '">' + escapeHtml(riskText) + '</span>')
            + '</div></div>';

        // ---- 合同正文（最新版本） ----
        html += '<div class="approval-detail-section">'
            + '<h4 style="margin:0 0 12px;font-size:14px;color:var(--ink);">合同正文</h4>';
        if (latestVersion && latestVersion.content) {
            // 去除 HTML 标签，显示纯文本预览（最多 2000 字）
            const plainText = latestVersion.content.replace(/<[^>]*>/g, '').replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').trim();
            const preview = plainText.length > 2000 ? plainText.substring(0, 2000) + '...' : plainText;
            html += '<div class="contract-text-preview">' + escapeHtml(preview) + '</div>';
            html += '<p class="hint" style="margin-top:6px;">版本：' + escapeHtml(latestVersion.versionNo || '-')
                + ' · 创建时间：' + escapeHtml(latestVersion.createdAt || '-');
            if (latestVersion.downloadUrl) {
                html += ' · <a href="' + escapeHtml(latestVersion.downloadUrl) + '" data-direct-download="' + escapeHtml(latestVersion.downloadUrl) + '">下载 Word</a>';
            }
            html += '</p>';
        } else {
            html += '<p class="hint">暂无合同正文版本，请前往在线编辑页面查看或上传。</p>';
        }
        html += '</div>';

        // ---- 附件列表 ----
        html += '<div class="approval-detail-section">'
            + '<h4 style="margin:0 0 12px;font-size:14px;color:var(--ink);">附件材料（' + (attachments || []).length + '）</h4>';
        if (!attachments || !attachments.length) {
            html += '<p class="hint">暂无附件</p>';
        } else {
            attachments.forEach(function (att) {
                html += '<div class="approval-detail-record">'
                    + '<div style="display:flex;align-items:center;justify-content:space-between;gap:12px;">'
                    + '<span><strong>' + escapeHtml(att.fileName || '未命名文件') + '</strong></span>'
                    + '<span style="color:var(--muted);font-size:12px;white-space:nowrap;">'
                    + escapeHtml(att.fileType || '') + ' · ' + formatFileSize(att.fileSize) + '</span>';
                if (att.downloadUrl) {
                    html += '<a class="secondary" href="' + escapeHtml(att.downloadUrl) + '" style="font-size:11px;padding:4px 10px;white-space:nowrap;" data-direct-download="' + escapeHtml(att.downloadUrl) + '">下载</a>';
                }
                html += '</div></div>';
            });
        }
        html += '</div>';

        // ---- 操作提示 ----
        html += '<p class="hint" style="margin-top:16px;text-align:center;">'
            + '以上为合同当前信息，请据此做出审批判断。'
            + '如需查看完整合同，请前往 <a href="/html/edit.html?contractId=' + contractId + '" target="_blank">在线编辑</a> 页面。</p>';

        bodyEl.innerHTML = html;

        // 下载链接事件
        bodyEl.querySelectorAll('[data-direct-download]').forEach(function (link) {
            link.addEventListener('click', function (e) {
                e.preventDefault();
                downloadFile(link.dataset.directDownload, link.textContent.trim() || 'download')
                    .catch(function (err) { toast(err.message); });
            });
        });
    } catch (err) {
        bodyEl.innerHTML = '<p class="hint" style="text-align:center;padding:24px 0;">加载合同详情失败：' + escapeHtml(err.message) + '</p>';
    }
}

function detailRow(label, valueHtml) {
    return '<div class="approval-detail-item">'
        + '<span class="approval-detail-label">' + escapeHtml(label) + '</span>'
        + '<span class="approval-detail-value">' + valueHtml + '</span></div>';
}

// ==================== 审批操作弹窗 ====================

function ensureApprovalModal() {
    if ($('#approvalActionModal')) return;
    const overlay = document.createElement('div');
    overlay.id = 'approvalActionModal';
    overlay.className = 'modal';
    overlay.hidden = true;
    overlay.innerHTML = `
        <div class="modal-card" style="width:min(480px,100%);padding:28px 28px 24px;">
            <div class="modal-head" style="margin-bottom:18px;padding-bottom:14px;">
                <h2 id="approvalModalTitle">审批操作</h2>
            </div>
            <div id="approvalModalInfo" style="margin-bottom:16px;color:var(--ink-secondary);font-size:13px;line-height:1.6;"></div>
            <label style="margin-bottom:16px;">
                <span style="margin-bottom:6px;display:block;" id="approvalModalLabel">审批意见</span>
                <textarea id="approvalModalComment" rows="4" style="min-height:80px;resize:vertical;"
                    placeholder="请输入审批意见（可选）"></textarea>
            </label>
            <div class="button-row" style="justify-content:flex-end;margin-top:0;">
                <button class="secondary" type="button" id="approvalModalCancel">取消</button>
                <button type="button" id="approvalModalConfirm">确认</button>
            </div>
        </div>`;
    document.body.appendChild(overlay);

    overlay.addEventListener('click', function (e) {
        if (e.target === overlay) closeApprovalModal();
    });
    $('#approvalModalCancel').addEventListener('click', closeApprovalModal);
}

let _approvalModalResolve = null;

function openApprovalModal({title, infoHtml, label, defaultComment, confirmText, confirmClass}) {
    ensureApprovalModal();
    $('#approvalModalTitle').textContent = title;
    $('#approvalModalInfo').innerHTML = infoHtml;
    $('#approvalModalLabel').textContent = label;
    $('#approvalModalComment').value = defaultComment;
    const confirmBtn = $('#approvalModalConfirm');
    confirmBtn.textContent = confirmText;
    confirmBtn.className = confirmClass || '';
    const newConfirmBtn = confirmBtn.cloneNode(true);
    confirmBtn.parentNode.replaceChild(newConfirmBtn, confirmBtn);

    $('#approvalActionModal').hidden = false;
    $('#approvalModalComment').focus();

    return new Promise(function (resolve) {
        _approvalModalResolve = resolve;
        newConfirmBtn.addEventListener('click', function () {
            const comment = $('#approvalModalComment').value.trim();
            $('#approvalActionModal').hidden = true;
            _approvalModalResolve = null;
            resolve(comment);
        });
    });
}

function closeApprovalModal() {
    $('#approvalActionModal').hidden = true;
    if (_approvalModalResolve) {
        _approvalModalResolve(null);
        _approvalModalResolve = null;
    }
}

// ==================== 审批列表 ====================

async function loadApprovals() {
    const approvals = await api('/api/approvals');
    $('#approvalList').innerHTML = approvals.map(item => {
        const records = item.records || [];
        const canOperate = canOperateNode(item);
        const currentNode = item.status === 'RUNNING'
            ? normalizeApprovalNode(item.currentNode) || '待分配节点'
            : '流程已结束';
        const flowType = ({NORMAL: '普通合同', MAJOR: '重大合同', SUPER: '超额合同', COUNTER: '重大合同'})[item.flowType]
            || FLOW_TYPE_TEXT[item.flowType]
            || item.flowType;
        const contractLabel = escapeHtml(item.contractNo || `合同 #${item.contractId}`) + ' - ' + escapeHtml(item.contractTitle || '');
        return `
        <article class="flow-node ${item.status === 'RUNNING' ? 'current' : ''}">
            <div class="approval-node-head">
                <strong class="approval-title-link" data-detail="${item.contractId}" data-contract="${contractLabel}" title="点击查看合同详情">${contractLabel}</strong>
                <span class="tag ${item.status === 'RUNNING' ? 'tag-blue' : item.status === 'REJECTED' ? 'tag-red' : 'tag-green'}">${escapeHtml(APPROVAL_STATUS_TEXT[item.status] || item.status)}</span>
            </div>
            <div class="approval-summary">
                <span>${escapeHtml(flowType)}</span>
                <span>${escapeHtml(currentNode)}</span>
            </div>
            <div class="approval-records" aria-label="审批记录">
                ${records.length ? records.map(record => `
                    <div class="approval-record">
                        <span class="tag role-tag-${approvalNodeRole(record.nodeName)}">${escapeHtml(normalizeApprovalNode(record.nodeName))}</span>
                        <div class="approval-record-body">
                            <div><strong>${escapeHtml(approvalActionText(record.action))}</strong><time>${escapeHtml(approvalTime(record.actionTime))}</time></div>
                            <p>${escapeHtml(record.comment || '无审批意见')}</p>
                        </div>
                    </div>
                `).join('') : '<p class="approval-empty">暂无审批记录</p>'}
            </div>
            ${canOperate ? `
                <div class="approval-actions">
                    <button class="primary-btn" data-agree="${item.instanceId}" data-contract="${contractLabel}">同意</button>
                    <button class="secondary" data-reject="${item.instanceId}" data-contract="${contractLabel}">驳回</button>
                </div>` : ''}
        </article>`;
    }).join('') || '<div class="flow-node"><span>暂无审批事项</span></div>';
}

async function handleApprovalAction(button, action) {
    const instanceId = button.dataset[action];
    const contractLabel = button.dataset.contract || ('合同 #' + instanceId);
    const isAgree = action === 'agree';

    const title = isAgree ? '审批通过' : '审批驳回';
    const infoHtml = '<div style="margin-bottom:4px;"><strong>合同：</strong>' + escapeHtml(contractLabel) + '</div>'
        + '<div><strong>操作：</strong>' + (isAgree
            ? '<span style="color:var(--success);font-weight:600;">同意审批</span>，流转至下一节点'
            : '<span style="color:var(--danger);font-weight:600;">驳回审批</span>，合同将退回草稿') + '</div>';
    const label = isAgree ? '审批意见（可选）' : '驳回原因（可选）';
    const defaultComment = isAgree ? '同意' : '驳回，请修改后重新提交';
    const confirmText = isAgree ? '确认通过' : '确认驳回';
    const confirmClass = isAgree ? '' : 'danger';

    const comment = await openApprovalModal({
        title: title,
        infoHtml: infoHtml,
        label: label,
        defaultComment: defaultComment,
        confirmText: confirmText,
        confirmClass: confirmClass
    });

    if (comment === null) return;

    button.disabled = true;
    const originalText = button.textContent;
    button.textContent = isAgree ? '处理中...' : '驳回中...';
    try {
        await api(`/api/approvals/${instanceId}/${action}`, {
            method: 'POST',
            body: JSON.stringify({comment: comment || defaultComment})
        });
        toast(isAgree ? '审批已通过' : '已驳回，合同已回到草稿');
        await loadApprovals();
    } catch (error) {
        toast(error.message);
        button.disabled = false;
        button.textContent = originalText;
    }
}

// ==================== 事件委托 ====================

$('#approvalList').addEventListener('click', async event => {
    const detailTrigger = event.target.closest('[data-detail]');
    if (detailTrigger) {
        event.preventDefault();
        // 标记为加载中（按钮或标题元素通用）
        const isBtn = detailTrigger.tagName === 'BUTTON';
        if (isBtn) detailTrigger.disabled = true;
        detailTrigger.style.opacity = '0.6';
        detailTrigger.style.pointerEvents = 'none';
        try {
            await openContractDetail(detailTrigger.dataset.detail, detailTrigger.dataset.contract);
        } catch (err) {
            toast(err.message);
        } finally {
            if (isBtn) detailTrigger.disabled = false;
            detailTrigger.style.opacity = '';
            detailTrigger.style.pointerEvents = '';
        }
        return;
    }
    const agreeButton = event.target.closest('[data-agree]');
    if (agreeButton) {
        await handleApprovalAction(agreeButton, 'agree');
        return;
    }
    const rejectButton = event.target.closest('[data-reject]');
    if (rejectButton) {
        await handleApprovalAction(rejectButton, 'reject');
    }
});

loadApprovals().catch(error => toast(error.message));

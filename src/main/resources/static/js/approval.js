if (!initAppShell('approval', '审批中心', 'Approval Flow')) {
    throw new Error('auth required');
}

function canOperateNode(item) {
    if (item.status !== 'RUNNING') return false;
    const roles = APPROVAL_NODE_ROLES[item.currentNode] || APPROVAL_NODE_ROLES['部门主管审批'];
    return roles.includes(state.roleCode);
}

async function loadApprovals() {
    const approvals = await api('/api/approvals');
    $('#approvalList').innerHTML = approvals.map(item => {
        const records = item.records || [];
        const canOperate = canOperateNode(item);
        return `
        <article class="flow-node ${item.status === 'RUNNING' ? 'current' : ''}">
            <strong>${escapeHtml(item.contractNo || `合同 #${item.contractId}`)} - ${escapeHtml(item.contractTitle || '')}</strong>
            <span>${escapeHtml(FLOW_TYPE_TEXT[item.flowType] || item.flowType)} · ${escapeHtml(item.currentNode || '-')} · ${escapeHtml(APPROVAL_STATUS_TEXT[item.status] || item.status)}</span>
            <div class="approval-records">
                ${records.length ? records.map(record => `
                    <small>${escapeHtml(record.nodeName)} · ${escapeHtml(record.action)} · ${escapeHtml(record.comment || '')} · ${escapeHtml(record.actionTime || '')}</small>
                `).join('') : '<small>暂无审批记录</small>'}
            </div>
            ${canOperate ? `
                <div class="approval-actions">
                    <button class="primary-btn" data-agree="${item.instanceId}">同意</button>
                    <button class="secondary" data-reject="${item.instanceId}">驳回</button>
                </div>` : ''}
        </article>`;
    }).join('') || '<div class="flow-node"><span>暂无审批事项</span></div>';
}

async function handleApprovalAction(button, action) {
    const promptText = action === 'agree' ? '审批意见（可选）' : '驳回原因（可选）';
    const defaultValue = action === 'agree' ? '同意' : '驳回，请修改后重新提交';
    const comment = prompt(promptText, defaultValue);
    if (comment === null) return;
    button.disabled = true;
    try {
        await api(`/api/approvals/${button.dataset[action]}/${action}`, {
            method: 'POST',
            body: JSON.stringify({comment})
        });
        toast(action === 'agree' ? '审批已通过' : '已驳回，合同已回到草稿');
        await loadApprovals();
    } catch (error) {
        toast(error.message);
        button.disabled = false;
    }
}

$('#approvalList').addEventListener('click', async event => {
    const agreeBtn = event.target.closest('[data-agree]');
    if (agreeBtn) {
        await handleApprovalAction(agreeBtn, 'agree');
        return;
    }
    const rejectBtn = event.target.closest('[data-reject]');
    if (rejectBtn) {
        await handleApprovalAction(rejectBtn, 'reject');
    }
});

loadApprovals().catch(error => toast(error.message));

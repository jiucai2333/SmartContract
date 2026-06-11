if (!initAppShell('approval', '审批中心', 'Approval Flow')) {
    throw new Error('auth required');
}

async function loadApprovals() {
    const approvals = await api('/api/approvals');
    $('#approvalList').innerHTML = approvals.map(item => {
        const records = item.records || [];
        const canAgree = item.status === 'RUNNING'
            && ['DEPT_LEADER', 'LEGAL', 'EXECUTIVE', 'ADMIN'].includes(state.roleCode);
        return `
        <article class="flow-node ${item.status === 'RUNNING' ? 'current' : ''}">
            <strong>${escapeHtml(item.contractNo || `合同 #${item.contractId}`)} - ${escapeHtml(item.contractTitle || '')}</strong>
            <span>${escapeHtml(item.flowType)} · ${escapeHtml(item.currentNode || '-')} · ${escapeHtml(APPROVAL_STATUS_TEXT[item.status] || item.status)}</span>
            <div class="approval-records">
                ${records.length ? records.map(record => `
                    <small>${escapeHtml(record.nodeName)} · ${escapeHtml(record.action)} · ${escapeHtml(record.comment || '')} · ${escapeHtml(record.actionTime || '')}</small>
                `).join('') : '<small>暂无审批记录</small>'}
            </div>
            ${canAgree ? `<button class="primary-btn" data-agree="${item.instanceId}">同意</button>` : ''}
        </article>`;
    }).join('') || '<div class="flow-node"><span>暂无审批事项</span></div>';
}

$('#approvalList').addEventListener('click', async event => {
    const button = event.target.closest('[data-agree]');
    if (!button) return;
    const comment = prompt('审批意见（可选）', '同意');
    if (comment === null) return;
    button.disabled = true;
    try {
        await api(`/api/approvals/${button.dataset.agree}/agree`, {
            method: 'POST',
            body: JSON.stringify({comment})
        });
        toast('审批已通过');
        await loadApprovals();
    } catch (error) {
        toast(error.message);
        button.disabled = false;
    }
});

loadApprovals().catch(error => toast(error.message));

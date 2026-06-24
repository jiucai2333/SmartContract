const n=`if (!initAppShell('approval', '审批中心', '查看并处理待审批合同，支持多级流程流转')) {
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
        return \`\${year}-\${String(month).padStart(2, '0')}-\${String(day).padStart(2, '0')} \${String(hour).padStart(2, '0')}:\${String(minute).padStart(2, '0')}:\${String(second).padStart(2, '0')}\`;
    }
    return String(value).replace('T', ' ').substring(0, 19);
}

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
        return \`
        <article class="flow-node \${item.status === 'RUNNING' ? 'current' : ''}">
            <div class="approval-node-head">
                <strong>\${escapeHtml(item.contractNo || \`合同 #\${item.contractId}\`)} - \${escapeHtml(item.contractTitle || '')}</strong>
                <span class="tag \${item.status === 'RUNNING' ? 'tag-blue' : 'tag-green'}">\${escapeHtml(APPROVAL_STATUS_TEXT[item.status] || item.status)}</span>
            </div>
            <div class="approval-summary">
                <span>\${escapeHtml(flowType)}</span>
                <span>\${escapeHtml(currentNode)}</span>
            </div>
            <div class="approval-records" aria-label="审批记录">
                \${records.length ? records.map(record => \`
                    <div class="approval-record">
                        <span class="tag role-tag-\${approvalNodeRole(record.nodeName)}">\${escapeHtml(normalizeApprovalNode(record.nodeName))}</span>
                        <div class="approval-record-body">
                            <div><strong>\${escapeHtml(approvalActionText(record.action))}</strong><time>\${escapeHtml(approvalTime(record.actionTime))}</time></div>
                            <p>\${escapeHtml(record.comment || '无审批意见')}</p>
                        </div>
                    </div>
                \`).join('') : '<p class="approval-empty">暂无审批记录</p>'}
            </div>
            \${canOperate ? \`
                <div class="approval-actions">
                    <button class="primary-btn" data-agree="\${item.instanceId}">同意</button>
                    <button class="secondary" data-reject="\${item.instanceId}">驳回</button>
                </div>\` : ''}
        </article>\`;
    }).join('') || '<div class="flow-node"><span>暂无审批事项</span></div>';
}

async function handleApprovalAction(button, action) {
    const promptText = action === 'agree' ? '审批意见（可选）' : '驳回原因（可选）';
    const defaultComment = action === 'agree' ? '同意' : '驳回，请修改后重新提交';
    const comment = prompt(promptText, defaultComment);
    if (comment === null) return;
    button.disabled = true;
    try {
        await api(\`/api/approvals/\${button.dataset[action]}/\${action}\`, {
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
`;export{n as default};

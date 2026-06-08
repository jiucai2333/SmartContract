if (!initAppShell('fulfillment', '履约预警', 'Fulfillment Timeline')) {
    throw new Error('auth required');
}

function nextStatus(status) {
    if (status === 'PENDING' || status === 'OVERDUE') return 'PROCESSING';
    if (status === 'PROCESSING') return 'FULFILLED';
    return null;
}

async function loadPlans() {
    const plans = await api('/api/fulfillment-plans');
    $('#planList').innerHTML = plans.map(plan => {
        const next = nextStatus(plan.status);
        return `
        <div class="gantt-row">
            <strong>${escapeHtml(plan.milestoneName)}</strong>
            <span>合同 #${escapeHtml(plan.contractId)} · ${escapeHtml(plan.dueDate)} ·
                <span class="tag ${escapeHtml(plan.status)}">${escapeHtml(FULFILLMENT_STATUS_TEXT[plan.status] || plan.status)}</span>
            </span>
            ${next ? `<button class="secondary" type="button" data-plan="${plan.planId}" data-status="${next}">
                ${next === 'FULFILLED' ? '标记完成' : '开始履约'}
            </button>` : ''}
        </div>`;
    }).join('') || '<div class="gantt-row"><span>暂无履约节点</span></div>';
}

$('#planList').addEventListener('click', async event => {
    const button = event.target.closest('[data-plan]');
    if (!button) return;
    button.disabled = true;
    try {
        await api(`/api/fulfillment-plans/${button.dataset.plan}/status`, {
            method: 'PUT',
            body: JSON.stringify({status: button.dataset.status})
        });
        await loadPlans();
        toast('履约状态已更新');
    } catch (error) {
        toast(error.message);
        button.disabled = false;
    }
});

loadPlans().catch(error => toast(error.message));

if (!initAppShell('fulfillment', '履约与付款', '履约计划、交付物确认、付款台账与逾期责任提示')) {
    throw new Error('auth required');
}

const statusTextMap = {
    TODO: '待开始',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成',
    OVERDUE: '已逾期',
    HANDLED: '已处置'
};
const planTypeMap = {
    PREPARE: '材料准备',
    CHECK: '进度确认',
    ACCEPTANCE: '验收交付',
    PAYMENT: '付款节点',
    OTHER: '其他'
};
const warningTextMap = {
    NORMAL: '正常',
    NONE: '已关闭',
    LEVEL1: '一级预警',
    LEVEL2: '二级预警',
    LEVEL3: '三级预警',
    OVERDUE: '已逾期'
};
const paymentStatusMap = {
    UNPAID: '未付款',
    PARTIAL: '部分到账',
    PAID: '已到账',
    OVERDUE: '已逾期'
};
const channelTextMap = {IN_APP: '站内消息'};
const PAYMENT_RATIO_TARGET = 100;

let contracts = [];
let plans = [];
let deliverables = [];
let paymentPlans = [];
let paymentRecords = [];

function selectedContractId(selector) {
    const value = $(selector).value;
    return value ? Number(value) : null;
}

function currentFilterContractId() {
    return selectedContractId('#filterContract');
}

function contractOption(contract) {
    const title = contract.title || contract.contractNo || `合同${contract.contractId}`;
    return `<option value="${contract.contractId}">${escapeHtml(title)}</option>`;
}

function fillContractSelects() {
    const allOptions = '<option value="">全部合同</option>' + contracts.map(contractOption).join('');
    const requiredOptions = contracts.map(contractOption).join('') || '<option value="">暂无合同</option>';
    $('#filterContract').innerHTML = allOptions;
    $('#extractContract').innerHTML = requiredOptions;
    $('#initContract').innerHTML = requiredOptions;
    $('#planContract').innerHTML = requiredOptions;
    $('#paymentContract').innerHTML = requiredOptions;
}

async function loadContracts() {
    contracts = await api('/api/contracts');
    fillContractSelects();
}

function endpointWithContract(path) {
    const params = new URLSearchParams();
    const contractId = currentFilterContractId();
    if (contractId) params.set('contractId', String(contractId));
    return `${path}?${params}`;
}

async function loadPlans() {
    const params = new URLSearchParams();
    const contractId = currentFilterContractId();
    if (contractId) params.set('contractId', String(contractId));
    if ($('#filterStatus').value) params.set('status', $('#filterStatus').value);
    if ($('#planKeyword').value.trim()) params.set('keyword', $('#planKeyword').value.trim());
    plans = await api(`/api/fulfillment/plans?${params}`);
    renderPlans(plans);
}

async function loadDeliverables() {
    deliverables = await api(endpointWithContract('/api/fulfillment/deliverables'));
    renderDeliverables(deliverables);
}

async function loadPaymentPlans() {
    paymentPlans = await api(endpointWithContract('/api/fulfillment/payments/plans'));
    renderPaymentPlans(paymentPlans);
}

async function loadPaymentRecords() {
    paymentRecords = await api(endpointWithContract('/api/fulfillment/payments/records'));
    renderPaymentRecords(paymentRecords);
}

async function loadReminders() {
    const list = await api(endpointWithContract('/api/fulfillment/reminders'));
    renderReminders(list);
    return list;
}

async function refreshAll() {
    const statPromise = api(endpointWithContract('/api/fulfillment/stats'));
    const reminderPromise = loadReminders();
    await Promise.all([loadPlans(), loadDeliverables(), loadPaymentPlans(), loadPaymentRecords()]);
    const [stat, reminders] = await Promise.all([statPromise, reminderPromise]);
    renderStats(stat, reminders);
}

function renderStats(stat, reminders = []) {
    const confirmedCount = deliverables.filter(item => item.confirmed).length;
    const overduePaymentCount = paymentPlans.filter(item => item.status === 'OVERDUE').length;
    $('#statTotal').textContent = stat.totalPlans || 0;
    $('#statWarning').textContent = stat.warningPlans || 0;
    $('#statOverdue').textContent = stat.overduePlans || 0;
    $('#statDeliverable').textContent = `${confirmedCount}/${deliverables.length}`;
    $('#statPaymentOverdue').textContent = overduePaymentCount;
    $('#statReminder').textContent = reminders.length;
}

function renderPlans(list) {
    const body = $('#planTbody');
    if (!list.length) {
        body.innerHTML = '<tr><td colspan="8" class="empty-cell">暂无履约节点</td></tr>';
        return;
    }
    body.innerHTML = list.map(plan => {
        const progress = Number(plan.progress || 0);
        const warningClass = `warning-${plan.warningLevel || 'NORMAL'}`;
        const canHandle = plan.warningLevel === 'OVERDUE' || plan.status === 'OVERDUE';
        return `
            <tr>
                <td><strong>${escapeHtml(plan.contractTitle || '-')}</strong><br><small>${escapeHtml(plan.contractNo || '')}</small></td>
                <td><strong>${escapeHtml(plan.nodeName || '-')}</strong><br><small>${escapeHtml(planTypeMap[plan.planType] || plan.planType || '-')}</small></td>
                <td>${escapeHtml(plan.dueDate || '-')}<br><small>${formatDays(plan.daysLeft)}</small></td>
                <td>
                    <div class="progress-cell">
                        <span>${progress}%</span>
                        <div class="progress-track"><i style="width:${progress}%"></i></div>
                    </div>
                </td>
                <td><span class="tag status-${plan.status}">${escapeHtml(statusTextMap[plan.status] || plan.status || '-')}</span></td>
                <td><span class="tag ${warningClass}">${escapeHtml(warningTextMap[plan.warningLevel] || plan.warningLevel || '-')}</span></td>
                <td>${escapeHtml(plan.ownerName || '-')}</td>
                <td>
                    <div class="row-actions">
                        <button class="icon-btn edit-btn" data-id="${plan.planId}" title="编辑"><i data-lucide="edit-3"></i></button>
                        <button class="icon-btn complete-btn" data-id="${plan.planId}" title="完成"><i data-lucide="circle-check"></i></button>
                        ${canHandle ? `<button class="link-btn handle-btn" data-id="${plan.planId}" type="button">处置</button>` : ''}
                    </div>
                </td>
            </tr>
        `;
    }).join('');
    renderLucideIcons();
}

function renderDeliverables(list) {
    const body = $('#deliverableTbody');
    if (!list.length) {
        body.innerHTML = '<tr><td colspan="7" class="empty-cell">暂无交付物，请先生成标准台账</td></tr>';
        return;
    }
    body.innerHTML = list.map(item => `
        <tr>
            <td>${escapeHtml(item.contractTitle || '-')}</td>
            <td><strong>${escapeHtml(item.deliverableName || '-')}</strong><br><small>${escapeHtml(item.deliverableType || '')}</small></td>
            <td>${escapeHtml(item.stageName || '-')}</td>
            <td>${escapeHtml(item.confirmMethod || '逐项勾选确认')}</td>
            <td>
                <label class="check-inline">
                    <input class="deliverable-check" type="checkbox" data-id="${item.deliverableId}" ${item.confirmed ? 'checked' : ''}>
                    <span class="tag ${item.confirmed ? 'status-COMPLETED' : 'status-TODO'}">${item.confirmed ? '已确认' : '未确认'}</span>
                </label>
            </td>
            <td>${escapeHtml(item.confirmer || '-')}<br><small>${item.confirmedAt ? new Date(item.confirmedAt).toLocaleString('zh-CN') : ''}</small></td>
            <td>${escapeHtml(item.remark || '-')}</td>
        </tr>
    `).join('');
}

function renderPaymentPlans(list) {
    renderPaymentRatioSummary(list);
    const body = $('#paymentPlanTbody');
    if (!list.length) {
        body.innerHTML = '<tr><td colspan="8" class="empty-cell">暂无付款计划，请先生成标准台账或新增付款计划</td></tr>';
        return;
    }
    body.innerHTML = list.map(item => {
        const prereqClass = item.prerequisiteCompleted ? 'status-COMPLETED' : 'warning-LEVEL2';
        const unpaidAmount = Number(item.unpaidAmount || 0);
        const canRecordPayment = item.status !== 'PAID' && unpaidAmount > 0;
        return `
            <tr>
                <td><strong>${escapeHtml(item.phaseName || '-')}</strong><br><small>${escapeHtml(item.contractTitle || '')}</small></td>
                <td>${formatPercent(item.percentage)}<br><small>${formatMoney(item.plannedAmount)}</small></td>
                <td>${escapeHtml(item.dueDate || '-')}<br><small>${item.overdueDays > 0 ? `逾期 ${item.overdueDays} 天` : '未逾期'}</small></td>
                <td><span class="tag ${prereqClass}">${item.prerequisiteCompleted ? '已完成' : '待确认'}</span><br><small>${escapeHtml(item.prerequisiteDelivery || '无')}</small></td>
                <td><span class="tag payment-${item.status}">${escapeHtml(paymentStatusMap[item.status] || item.status || '-')}</span><br><small>已收 ${formatMoney(item.paidAmount)} / 未收 ${formatMoney(item.unpaidAmount)}</small></td>
                <td>${formatMoney(item.penaltyAmount)}<br><small>每日 ${formatPercent(item.penaltyRate)}</small></td>
                <td class="hint-cell">${escapeHtml(item.responsibilityHint || '-')}</td>
                <td>
                    <div class="row-actions">
                        <button class="icon-btn edit-payment-btn" data-id="${item.paymentPlanId}" title="编辑"><i data-lucide="edit-3"></i></button>
                        ${canRecordPayment
                            ? `<button class="link-btn record-payment-btn" data-id="${item.paymentPlanId}" type="button">到账</button>`
                            : '<span class="tag payment-PAID">已足额到账</span>'}
                    </div>
                </td>
            </tr>
        `;
    }).join('');
    renderLucideIcons();
}

function renderPaymentRecords(list) {
    const body = $('#paymentRecordTbody');
    if (!list.length) {
        body.innerHTML = '<tr><td colspan="8" class="empty-cell">暂无到账记录</td></tr>';
        return;
    }
    body.innerHTML = list.map(item => `
        <tr>
            <td>${escapeHtml(item.contractTitle || '-')}</td>
            <td>${escapeHtml(item.phaseName || '-')}</td>
            <td>${formatMoney(item.paidAmount)}</td>
            <td>${escapeHtml(item.paidDate || '-')}</td>
            <td>${escapeHtml(item.payer || '-')}</td>
            <td>${escapeHtml(item.receiver || '-')}</td>
            <td>${escapeHtml(item.remark || '-')}</td>
            <td>
                <button class="icon-btn delete-payment-record-btn" data-id="${item.paymentRecordId}" title="删除到账记录">
                    <i data-lucide="trash-2"></i>
                </button>
            </td>
        </tr>
    `).join('');
    renderLucideIcons();
}

function renderReminders(list) {
    const body = $('#reminderTbody');
    if (!list.length) {
        body.innerHTML = '<tr><td colspan="7" class="empty-cell">暂无推送记录</td></tr>';
        return;
    }
    body.innerHTML = list.map(item => `
        <tr>
            <td>${escapeHtml(item.contractTitle || '-')}</td>
            <td>${escapeHtml(item.nodeName || '-')}</td>
            <td><span class="tag warning-${item.reminderLevel}">${escapeHtml(warningTextMap[item.reminderLevel] || item.reminderLevel || '-')}</span></td>
            <td>${escapeHtml(item.receiver || '-')}</td>
            <td>${escapeHtml(channelTextMap[item.channel] || item.channel || '-')}</td>
            <td>${item.sentAt ? new Date(item.sentAt).toLocaleString('zh-CN') : '-'}</td>
            <td>${escapeHtml(item.content || '-')}</td>
        </tr>
    `).join('');
}

function formatDays(days) {
    if (days === null || days === undefined) return '未设置';
    if (days < 0) return `逾期 ${Math.abs(days)} 天`;
    if (days === 0) return '今天到期';
    return `剩余 ${days} 天`;
}

function formatMoney(value) {
    const number = Number(value || 0);
    return number.toLocaleString('zh-CN', {style: 'currency', currency: 'CNY'});
}

function formatPercent(value) {
    const number = Number(value || 0);
    return `${number.toFixed(number % 1 === 0 ? 0 : 2)}%`;
}

function normalizedRatio(value) {
    return Math.round(Number(value || 0) * 100) / 100;
}

function paymentRatioClass(total) {
    const rounded = normalizedRatio(total);
    if (rounded > PAYMENT_RATIO_TARGET) return 'is-danger';
    if (rounded === PAYMENT_RATIO_TARGET) return 'is-valid';
    return 'is-warning';
}

function paymentRatioTotalFor(contractId, excludingPaymentPlanId = '') {
    return normalizedRatio(paymentPlans
        .filter(item => String(item.contractId) === String(contractId))
        .filter(item => !excludingPaymentPlanId || String(item.paymentPlanId) !== String(excludingPaymentPlanId))
        .reduce((sum, item) => sum + Number(item.percentage || 0), 0));
}

function renderPaymentRatioSummary(list) {
    const summary = $('#paymentRatioSummary');
    if (!summary) return;
    if (!list.length) {
        summary.textContent = '付款比例合计：暂无计划';
        summary.className = 'payment-ratio-summary';
        return;
    }
    const groups = new Map();
    list.forEach(item => {
        const key = String(item.contractId || '');
        const group = groups.get(key) || {title: item.contractTitle || '未命名合同', total: 0};
        group.total = normalizedRatio(group.total + Number(item.percentage || 0));
        groups.set(key, group);
    });
    const values = Array.from(groups.values());
    if (values.length === 1) {
        const total = values[0].total;
        const matched = normalizedRatio(total) === PAYMENT_RATIO_TARGET;
        summary.textContent = matched
            ? `付款比例合计：${formatPercent(total)}，已满足 100%`
            : `付款比例合计：${formatPercent(total)}，应为 100%`;
        summary.className = `payment-ratio-summary ${paymentRatioClass(total)}`;
        return;
    }
    const validCount = values.filter(item => normalizedRatio(item.total) === PAYMENT_RATIO_TARGET).length;
    const overCount = values.filter(item => normalizedRatio(item.total) > PAYMENT_RATIO_TARGET).length;
    summary.textContent = overCount > 0
        ? `付款比例校验：${validCount}/${values.length} 个合同合计为 100%，${overCount} 个超过 100%`
        : `付款比例校验：${validCount}/${values.length} 个合同合计为 100%`;
    summary.className = `payment-ratio-summary ${overCount > 0 ? 'is-danger' : (validCount === values.length ? 'is-valid' : 'is-warning')}`;
}

function renderPaymentRatioCheck() {
    const check = $('#paymentRatioCheck');
    if (!check) return;
    const contractId = Number($('#paymentContract').value || 0);
    const current = normalizedRatio($('#percentage').value);
    const existing = paymentRatioTotalFor(contractId, $('#paymentPlanId').value);
    const total = normalizedRatio(existing + current);
    if (!contractId) {
        check.textContent = '请选择合同后校验付款比例';
        check.className = 'ratio-check wide';
        return;
    }
    if (total > PAYMENT_RATIO_TARGET) {
        check.textContent = `保存后合计 ${formatPercent(total)}，超过 100%`;
    } else if (total === PAYMENT_RATIO_TARGET) {
        check.textContent = '保存后合计 100%，满足分期配置';
    } else {
        check.textContent = `保存后合计 ${formatPercent(total)}，距 100% 还差 ${formatPercent(PAYMENT_RATIO_TARGET - total)}`;
    }
    check.className = `ratio-check wide ${paymentRatioClass(total)}`;
}

function validatePaymentRatioBeforeSave(payload, paymentPlanId) {
    if (payload.percentage < 0 || payload.percentage > PAYMENT_RATIO_TARGET) {
        throw new Error('付款比例必须在 0% 到 100% 之间');
    }
    const total = normalizedRatio(paymentRatioTotalFor(payload.contractId, paymentPlanId) + payload.percentage);
    if (total > PAYMENT_RATIO_TARGET) {
        throw new Error(`同一合同付款比例合计不能超过 100%，当前保存后合计为 ${formatPercent(total)}`);
    }
}

function prerequisiteTokens(value) {
    return String(value || '')
        .split(/[,，、]/)
        .map(item => item.trim())
        .filter(Boolean);
}

function selectedPrerequisiteDelivery() {
    return Array.from($('#prerequisiteDelivery').selectedOptions)
        .map(option => option.value.trim())
        .filter(Boolean)
        .join('、');
}

function addPrerequisiteOption(select, value, text, selected) {
    const option = document.createElement('option');
    option.value = value;
    option.textContent = text;
    option.selected = selected;
    select.appendChild(option);
}

async function loadPrerequisiteDeliveryOptions(contractId, selectedValue = '') {
    const select = $('#prerequisiteDelivery');
    const selected = new Set(prerequisiteTokens(selectedValue));
    select.disabled = true;
    select.innerHTML = '';
    addPrerequisiteOption(select, '', '加载交付物中...', false);
    if (!contractId) {
        select.innerHTML = '';
        addPrerequisiteOption(select, '', '请先选择合同', false);
        select.disabled = true;
        return;
    }
    const items = await api(`/api/fulfillment/deliverables?contractId=${contractId}`);
    select.innerHTML = '';
    const unmatched = new Set(selected);
    items.forEach(item => {
        const value = item.deliverableName || item.deliverableType || '';
        if (!value) return;
        const selectedByName = selected.has(item.deliverableName);
        const selectedByType = selected.has(item.deliverableType);
        const text = item.deliverableName || value;
        addPrerequisiteOption(select, value, text, selectedByName || selectedByType);
        unmatched.delete(item.deliverableName);
        unmatched.delete(item.deliverableType);
    });
    unmatched.forEach(value => addPrerequisiteOption(select, value, value, true));
    if (!select.options.length) {
        addPrerequisiteOption(select, '', '暂无交付物，请先生成标准台账', false);
        select.disabled = true;
        return;
    }
    select.disabled = false;
}

function openPlanModal(plan = null) {
    $('#planModalTitle').textContent = plan ? '编辑履约节点' : '新增履约节点';
    $('#planId').value = plan?.planId || '';
    $('#planContract').value = plan?.contractId || currentFilterContractId() || contracts[0]?.contractId || '';
    $('#planContract').disabled = Boolean(plan);
    $('#planType').value = plan?.planType || 'OTHER';
    $('#nodeName').value = plan?.nodeName || '';
    $('#dueDate').value = plan?.dueDate || new Date().toISOString().slice(0, 10);
    $('#planStatus').value = plan?.status || 'TODO';
    $('#ownerName').value = plan?.ownerName || state.username || '';
    $('#progressRange').value = plan?.progress ?? 0;
    $('#progressValue').value = plan?.progress ?? 0;
    $('#planRemark').value = plan?.remark || '';
    $('#planModal').hidden = false;
    renderLucideIcons();
}

function closePlanModal() {
    $('#planModal').hidden = true;
    $('#planContract').disabled = false;
    $('#planForm').reset();
}

function planPayload(plan = null, overrides = {}) {
    return {
        contractId: plan ? plan.contractId : Number($('#planContract').value),
        nodeName: plan ? plan.nodeName : $('#nodeName').value.trim(),
        planType: plan ? plan.planType : $('#planType').value,
        dueDate: plan ? plan.dueDate : $('#dueDate').value,
        status: plan ? plan.status : $('#planStatus').value,
        progress: plan ? plan.progress : Number($('#progressValue').value || 0),
        ownerName: plan ? plan.ownerName : $('#ownerName').value.trim(),
        remark: plan ? plan.remark : $('#planRemark').value.trim(),
        ...overrides
    };
}

function openPaymentPlanModal(plan = null) {
    $('#paymentPlanModalTitle').textContent = plan ? '编辑付款计划' : '新增付款计划';
    $('#paymentPlanId').value = plan?.paymentPlanId || '';
    $('#paymentContract').value = plan?.contractId || currentFilterContractId() || contracts[0]?.contractId || '';
    $('#paymentContract').disabled = Boolean(plan);
    $('#phaseName').value = plan?.phaseName || '';
    $('#percentage').value = plan?.percentage ?? '';
    $('#plannedAmount').value = plan?.plannedAmount ?? '';
    $('#paymentDueDate').value = plan?.dueDate || new Date().toISOString().slice(0, 10);
    $('#penaltyRate').value = plan?.penaltyRate ?? 0.05;
    $('#paymentRemark').value = plan?.remark || '';
    $('#paymentPlanModal').hidden = false;
    renderPaymentRatioCheck();
    loadPrerequisiteDeliveryOptions($('#paymentContract').value, plan?.prerequisiteDelivery || '')
        .catch(error => toast(error.message));
    renderLucideIcons();
}

function closePaymentPlanModal() {
    $('#paymentPlanModal').hidden = true;
    $('#paymentContract').disabled = false;
    $('#paymentPlanForm').reset();
    $('#paymentRatioCheck').textContent = '';
    $('#paymentRatioCheck').className = 'ratio-check wide';
    $('#prerequisiteDelivery').innerHTML = '';
    $('#prerequisiteDelivery').disabled = false;
}

function paymentPlanPayload() {
    return {
        contractId: Number($('#paymentContract').value),
        phaseName: $('#phaseName').value.trim(),
        percentage: Number($('#percentage').value || 0),
        plannedAmount: Number($('#plannedAmount').value || 0),
        dueDate: $('#paymentDueDate').value,
        prerequisiteDelivery: selectedPrerequisiteDelivery(),
        penaltyRate: Number($('#penaltyRate').value || 0),
        remark: $('#paymentRemark').value.trim()
    };
}

function openPaymentRecordModal(planId) {
    const plan = paymentPlans.find(item => String(item.paymentPlanId) === String(planId));
    if (!plan || plan.status === 'PAID' || Number(plan.unpaidAmount || 0) <= 0) {
        toast('该付款计划已足额到账，无需重复登记');
        return;
    }
    $('#recordPaymentPlanId').value = planId;
    $('#paidAmount').value = plan?.unpaidAmount || plan?.plannedAmount || '';
    $('#paidDate').value = new Date().toISOString().slice(0, 10);
    $('#payer').value = '甲方';
    $('#receiver').value = '乙方';
    $('#paymentRecordRemark').value = '';
    $('#paymentRecordModal').hidden = false;
}

function closePaymentRecordModal() {
    $('#paymentRecordModal').hidden = true;
    $('#paymentRecordForm').reset();
}

async function savePlan(event) {
    event.preventDefault();
    const id = $('#planId').value;
    const url = id ? `/api/fulfillment/plans/${id}` : '/api/fulfillment/plans';
    await api(url, {method: id ? 'PUT' : 'POST', body: JSON.stringify(planPayload())});
    closePlanModal();
    await refreshAll();
    toast(id ? '履约节点已更新' : '履约节点已新增');
}

async function savePaymentPlan(event) {
    event.preventDefault();
    const id = $('#paymentPlanId').value;
    const payload = paymentPlanPayload();
    validatePaymentRatioBeforeSave(payload, id);
    const url = id ? `/api/fulfillment/payments/plans/${id}` : '/api/fulfillment/payments/plans';
    await api(url, {method: id ? 'PUT' : 'POST', body: JSON.stringify(payload)});
    closePaymentPlanModal();
    await refreshAll();
    toast(id ? '付款计划已更新' : '付款计划已新增');
}

async function savePaymentRecord(event) {
    event.preventDefault();
    const id = $('#recordPaymentPlanId').value;
    await api(`/api/fulfillment/payments/plans/${id}/records`, {
        method: 'POST',
        body: JSON.stringify({
            paidAmount: Number($('#paidAmount').value || 0),
            paidDate: $('#paidDate').value,
            payer: $('#payer').value.trim(),
            receiver: $('#receiver').value.trim(),
            remark: $('#paymentRecordRemark').value.trim()
        })
    });
    closePaymentRecordModal();
    await refreshAll();
    toast('到账记录已保存');
}

async function deletePaymentRecord(id) {
    if (!window.confirm('确认删除这条到账记录吗？删除后付款计划状态会重新计算。')) return;
    await api(`/api/fulfillment/payments/records/${id}`, {method: 'DELETE'});
    await refreshAll();
    toast('到账记录已删除');
}

async function initializeMemberE() {
    const contractId = selectedContractId('#initContract');
    if (!contractId) return toast('请先选择合同');
    await api(`/api/fulfillment/contracts/${contractId}/member-e/init`, {method: 'POST'});
    $('#filterContract').value = String(contractId);
    await refreshAll();
    toast('已生成交付物和 30% + 30% + 40% 付款台账');
}

async function extractPlans() {
    const contractId = selectedContractId('#extractContract');
    if (!contractId) return toast('请先选择合同');
    await api(`/api/fulfillment/plans/extract/${contractId}`, {method: 'POST'});
    $('#filterContract').value = String(contractId);
    await refreshAll();
    toast('履约节点已抽取');
}

async function dispatchReminders() {
    const contractId = currentFilterContractId();
    await api(endpointWithContract('/api/fulfillment/reminders/dispatch'), {method: 'POST'});
    await refreshAll();
    toast(contractId ? '当前合同预警提醒已生成' : '全部合同预警提醒已生成');
}

async function completePlan(id) {
    const plan = plans.find(item => String(item.planId) === String(id));
    if (!plan) return;
    await api(`/api/fulfillment/plans/${id}`, {
        method: 'PUT',
        body: JSON.stringify(planPayload(plan, {status: 'COMPLETED', progress: 100}))
    });
    await refreshAll();
    toast('节点已完成');
}

async function handleOverdue(id) {
    await api(`/api/fulfillment/plans/${id}/handle-overdue`, {method: 'POST'});
    await refreshAll();
    toast('逾期节点已处置');
}

async function confirmDeliverable(id, checked) {
    await api(`/api/fulfillment/deliverables/${id}/confirm?confirmed=${checked}`, {method: 'POST'});
    await refreshAll();
    toast(checked ? '交付物已确认' : '已取消确认');
}

let keywordTimer;
$('#planKeyword').addEventListener('input', () => {
    clearTimeout(keywordTimer);
    keywordTimer = setTimeout(() => loadPlans().catch(error => toast(error.message)), 250);
});
$('#filterContract').addEventListener('change', () => refreshAll().catch(error => toast(error.message)));
$('#filterStatus').addEventListener('change', () => loadPlans().catch(error => toast(error.message)));
$('#resetBtn').addEventListener('click', () => {
    $('#planKeyword').value = '';
    $('#filterContract').value = '';
    $('#filterStatus').value = '';
    refreshAll().catch(error => toast(error.message));
});

$('#newPlanBtn').addEventListener('click', () => openPlanModal());
$('#newPaymentPlanBtn').addEventListener('click', () => openPaymentPlanModal());
$('#closePlanModal').addEventListener('click', closePlanModal);
$('#closePaymentPlanModal').addEventListener('click', closePaymentPlanModal);
$('#closePaymentRecordModal').addEventListener('click', closePaymentRecordModal);
$('#planForm').addEventListener('submit', event => savePlan(event).catch(error => toast(error.message)));
$('#paymentPlanForm').addEventListener('submit', event => savePaymentPlan(event).catch(error => toast(error.message)));
$('#paymentRecordForm').addEventListener('submit', event => savePaymentRecord(event).catch(error => toast(error.message)));
$('#paymentContract').addEventListener('change', () => {
    renderPaymentRatioCheck();
    loadPrerequisiteDeliveryOptions($('#paymentContract').value)
        .catch(error => toast(error.message));
});
$('#percentage').addEventListener('input', renderPaymentRatioCheck);
$('#initMemberEBtn').addEventListener('click', () => initializeMemberE().catch(error => toast(error.message)));
$('#extractBtn').addEventListener('click', () => extractPlans().catch(error => toast(error.message)));
$('#dispatchBtn').addEventListener('click', () => dispatchReminders().catch(error => toast(error.message)));
$('#refreshReminderBtn').addEventListener('click', () => refreshAll().catch(error => toast(error.message)));
$('#progressRange').addEventListener('input', () => $('#progressValue').value = $('#progressRange').value);
$('#progressValue').addEventListener('input', () => $('#progressRange').value = $('#progressValue').value);

$('#planTbody').addEventListener('click', event => {
    const editBtn = event.target.closest('.edit-btn');
    const completeBtn = event.target.closest('.complete-btn');
    const handleBtn = event.target.closest('.handle-btn');
    if (editBtn) {
        const plan = plans.find(item => String(item.planId) === String(editBtn.dataset.id));
        if (plan) openPlanModal(plan);
    }
    if (completeBtn) completePlan(completeBtn.dataset.id).catch(error => toast(error.message));
    if (handleBtn) handleOverdue(handleBtn.dataset.id).catch(error => toast(error.message));
});

$('#deliverableTbody').addEventListener('change', event => {
    const checkbox = event.target.closest('.deliverable-check');
    if (checkbox) confirmDeliverable(checkbox.dataset.id, checkbox.checked).catch(error => toast(error.message));
});

$('#paymentPlanTbody').addEventListener('click', event => {
    const editBtn = event.target.closest('.edit-payment-btn');
    const recordBtn = event.target.closest('.record-payment-btn');
    if (editBtn) {
        const plan = paymentPlans.find(item => String(item.paymentPlanId) === String(editBtn.dataset.id));
        if (plan) openPaymentPlanModal(plan);
    }
    if (recordBtn) openPaymentRecordModal(recordBtn.dataset.id);
});

$('#paymentRecordTbody').addEventListener('click', event => {
    const deleteBtn = event.target.closest('.delete-payment-record-btn');
    if (deleteBtn) deletePaymentRecord(deleteBtn.dataset.id).catch(error => toast(error.message));
});

loadContracts()
    .then(refreshAll)
    .catch(error => toast(error.message));

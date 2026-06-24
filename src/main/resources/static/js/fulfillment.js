if (!initAppShell('fulfillment', '履约预警', '监控合同关键节点，触发分级履约到期提醒')) throw new Error('auth required');

let contractId = null;
let contracts = [];
let plans = [];
let deliverables = [];
let paymentPlans = [];

const money = value => Number(value || 0).toLocaleString('zh-CN', {minimumFractionDigits: 2});
const empty = text => `<div class="empty-state">${escapeHtml(text)}</div>`;

async function initPerformance() {
    contracts = await api('/api/performance/contracts') || [];
    const requested = Number(new URLSearchParams(location.search).get('contractId'));
    contractId = contracts.some(item => item.contractId === requested)
        ? requested : contracts[0]?.contractId || null;
    renderContractSelector();
    bindTabs();
    bindForms();
    if (contractId) await reloadAll();
    else showNoContract();
}

function renderContractSelector() {
    const selector = $('#contractSelector');
    selector.innerHTML = contracts.length
        ? contracts.map(item => `<option value="${item.contractId}" ${item.contractId === contractId ? 'selected' : ''}>${escapeHtml(item.contractNo)} ${escapeHtml(item.title)}</option>`).join('')
        : '<option value="">暂无可用合同</option>';
    selector.addEventListener('change', async () => {
        contractId = Number(selector.value);
        await reloadAll();
    });
}

function bindTabs() {
    $$('.performance-tabs [data-tab]').forEach(button => button.addEventListener('click', () => {
        $$('.performance-tabs button').forEach(item => item.classList.remove('active'));
        $$('.tab-panel').forEach(item => item.classList.remove('active'));
        button.classList.add('active');
        $(`#tab-${button.dataset.tab}`).classList.add('active');
    }));
}

async function reloadAll() {
    const contract = contracts.find(item => item.contractId === contractId);
    $('#contractTitle').textContent = contract ? contract.title : '履约预警与付款台账';
    [plans, deliverables, paymentPlans] = await Promise.all([
        api(`/api/performance/plans?contractId=${contractId}`),
        api(`/api/performance/deliverables?contractId=${contractId}`),
        api(`/api/performance/payment-plans?contractId=${contractId}`)
    ]);
    await Promise.all([renderProgress(), renderOverdue()]);
    renderPlans();
    renderDeliverables();
    renderPaymentPlans();
}

function showNoContract() {
    $('#planList').innerHTML = empty('暂无活跃合同');
    $('#deliverableList').innerHTML = empty('暂无活跃合同');
    $('#paymentPlanList').innerHTML = empty('暂无活跃合同');
}

async function renderProgress() {
    const data = await api(`/api/performance/progress/${contractId}`);
    $('#progressCards').innerHTML = [
        ['交付进度', `${data.deliveryProgress}%`, `${data.confirmedDeliverables}/${data.totalDeliverables} 已确认`],
        ['付款计划', data.totalPaymentPlans, `${data.paidPlans} 已付`],
        ['逾期计划', data.overduePlans, '请及时处理'],
        ['累计到账', `¥${money(data.totalPaid)}`, `未付 ¥${money(data.totalUnpaid)}`]
    ].map(item => `<div class="panel overview-card"><span>${item[0]}</span><strong>${item[1]}</strong><small>${item[2]}</small></div>`).join('');
}

function renderPlans() {
    $('#planList').innerHTML = plans.map(plan => {
        const next = ['FULFILLED', 'COMPLETED'].includes(plan.status) ? 'PENDING' : 'FULFILLED';
        return `<div class="performance-row"><div class="performance-row-head">
            <div><strong>${escapeHtml(plan.milestoneName)}</strong><div class="performance-meta">到期 ${escapeHtml(plan.dueDate || '--')} · ${escapeHtml(plan.statusName || plan.status)}</div></div>
            <div class="performance-row-actions"><button class="secondary" onclick="updatePlanStatus(${plan.planId},'${next}')">${next === 'FULFILLED' ? '标记完成' : '恢复待处理'}</button></div>
        </div></div>`;
    }).join('') || empty('暂无履约节点');
}

async function updatePlanStatus(planId, status) {
    await api(`/api/performance/plans/${planId}/status`, {method:'PUT', body:JSON.stringify({status})});
    toast('履约状态已更新');
    await reloadAll();
}

function renderDeliverables() {
    $('#deliverableList').innerHTML = deliverables.map(item => `<div class="performance-row">
        <div class="performance-row-head"><div><strong>${escapeHtml(item.itemName)}</strong>
        <div class="performance-meta">${escapeHtml(item.deliverableTypeName)} · ${item.isConfirmed ? '已确认' : '待确认'}</div></div>
        <div class="performance-row-actions">
            <button class="secondary" onclick="editDeliverable(${item.deliverableId})">编辑</button>
            <button class="secondary" onclick="${item.isConfirmed ? 'unconfirmDeliverable' : 'confirmDeliverable'}(${item.deliverableId})">${item.isConfirmed ? '取消确认' : '确认'}</button>
            <button class="danger" onclick="deleteDeliverable(${item.deliverableId})">删除</button>
        </div></div></div>`).join('') || empty('暂无交付物');
}

function openDeliverable(item) {
    $('#deliverableId').value = item?.deliverableId || '';
    $('#deliverablePlan').innerHTML = plans.map(plan => `<option value="${plan.planId}" ${plan.planId === item?.planId ? 'selected' : ''}>${escapeHtml(plan.milestoneName)}</option>`).join('');
    $('#deliverableName').value = item?.itemName || '';
    $('#deliverableType').value = item?.deliverableType || 'DESIGN_DOC';
    $('#deliverableStage').value = item?.contractStage || 'SIGNING';
    $('#deliverableSort').value = item?.sortOrder || 0;
    $('#deliverableDialog').showModal();
}

function editDeliverable(id) { openDeliverable(deliverables.find(item => item.deliverableId === id)); }
async function confirmDeliverable(id) {
    await api(`/api/performance/deliverables/${id}/confirm`, {method:'PUT', body:JSON.stringify({confirmedBy:state.username || ''})});
    toast('交付物已确认'); await reloadAll();
}
async function unconfirmDeliverable(id) {
    await api(`/api/performance/deliverables/${id}/unconfirm`, {method:'PUT'}); toast('已取消确认'); await reloadAll();
}
async function deleteDeliverable(id) {
    if (!confirm('确定删除该交付物？')) return;
    await api(`/api/performance/deliverables/${id}`, {method:'DELETE'}); toast('交付物已删除'); await reloadAll();
}

function renderPaymentPlans() {
    paymentPlans = paymentPlans || [];
    $('#paymentPlanList').innerHTML = paymentPlans.map(plan => `<div class="performance-row">
        <div class="performance-row-head"><div><strong>第 ${plan.installmentNo} 期 · ¥${money(plan.amount)}</strong>
        <div class="performance-meta">到期 ${escapeHtml(plan.dueDate)} · ${escapeHtml(plan.status)} · 已付 ¥${money(plan.totalPaid)}</div></div>
        <div class="performance-row-actions"><button class="secondary" onclick="editPaymentPlan(${plan.paymentPlanId})">编辑</button>
        <button class="secondary" onclick="openPaymentRecord(${plan.paymentPlanId})">确认到账</button>
        <button class="danger" onclick="deletePaymentPlan(${plan.paymentPlanId})">删除</button></div></div>
        <div class="records">${renderRecords(plan.records || [])}</div>
    </div>`).join('') || empty('暂无付款计划');
}

function renderRecords(records) {
    if (!records.length) return '<div class="performance-meta">暂无到账记录</div>';
    return `<table><thead><tr><th>时间</th><th>金额</th><th>凭证</th><th></th></tr></thead><tbody>${records.map(record =>
        `<tr><td>${escapeHtml(record.paidAt)}</td><td>¥${money(record.paidAmount)}</td><td>${escapeHtml(record.receiptNo || '--')}</td><td><button class="danger" onclick="deletePaymentRecord(${record.recordId})">删除</button></td></tr>`
    ).join('')}</tbody></table>`;
}

function openPaymentPlan(plan) {
    $('#paymentPlanId').value = plan?.paymentPlanId || '';
    $('#paymentPlanNode').innerHTML = plans.map(item => `<option value="${item.planId}" ${item.planId === plan?.planId ? 'selected' : ''}>${escapeHtml(item.milestoneName)}</option>`).join('');
    $('#installmentNo').value = plan?.installmentNo || paymentPlans.length + 1;
    $('#paymentRatio').value = plan?.ratio || '';
    $('#paymentAmount').value = plan?.amount || '';
    $('#paymentDueDate').value = plan?.dueDate || '';
    $('#paymentPrerequisite').innerHTML = '<option value="">无</option>' + deliverables.map(item => `<option value="${item.deliverableId}" ${item.deliverableId === plan?.prerequisiteDeliverableId ? 'selected' : ''}>${escapeHtml(item.itemName)}</option>`).join('');
    $('#paymentPlanDialog').showModal();
}
function editPaymentPlan(id) { openPaymentPlan(paymentPlans.find(item => item.paymentPlanId === id)); }
async function deletePaymentPlan(id) {
    if (!confirm('确定删除该付款计划？')) return;
    await api(`/api/performance/payment-plans/${id}`, {method:'DELETE'}); toast('付款计划已删除'); await reloadAll();
}
function openPaymentRecord(id) {
    $('#recordPlanId').value = id;
    $('#paidAmount').value = '';
    $('#paidAt').value = new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0,16);
    $('#receiptNo').value = '';
    $('#paymentNotes').value = '';
    $('#paymentRecordDialog').showModal();
}
async function deletePaymentRecord(id) {
    if (!confirm('确定删除该到账记录？')) return;
    await api(`/api/performance/payment-records/${id}`, {method:'DELETE'}); toast('到账记录已删除'); await reloadAll();
}

async function renderOverdue() {
    const items = (await api('/api/performance/overdue-list') || []).filter(item => item.contractId === contractId);
    $('#overduePanel').hidden = !items.length;
    $('#overdueList').innerHTML = items.length ? `<table><thead><tr><th>期次</th><th>到期日</th><th>逾期</th><th>责任提示</th></tr></thead><tbody>${items.map(item =>
        `<tr><td>第 ${item.installmentNo} 期</td><td>${escapeHtml(item.dueDate)}</td><td>${item.overdueDays} 天</td><td>${escapeHtml(item.responsibilityHint || '--')}</td></tr>`
    ).join('')}</tbody></table>` : '';
}

function bindForms() {
    $('#addDeliverable').addEventListener('click', () => openDeliverable(null));
    $('#addPaymentPlan').addEventListener('click', () => openPaymentPlan(null));
    $('#deliverableForm').addEventListener('submit', async event => {
        if (event.submitter?.value === 'cancel') return;
        event.preventDefault();
        await api('/api/performance/deliverables', {method:'POST', body:JSON.stringify({
            deliverableId: Number($('#deliverableId').value) || null, planId:Number($('#deliverablePlan').value),
            contractId, itemName:$('#deliverableName').value.trim(), deliverableType:$('#deliverableType').value,
            contractStage:$('#deliverableStage').value, sortOrder:Number($('#deliverableSort').value)
        })});
        $('#deliverableDialog').close(); toast('交付物已保存'); await reloadAll();
    });
    $('#paymentPlanForm').addEventListener('submit', async event => {
        if (event.submitter?.value === 'cancel') return;
        event.preventDefault();
        await api('/api/performance/payment-plans', {method:'POST', body:JSON.stringify({
            paymentPlanId:Number($('#paymentPlanId').value) || null, planId:Number($('#paymentPlanNode').value), contractId,
            installmentNo:Number($('#installmentNo').value), ratio:Number($('#paymentRatio').value),
            amount:Number($('#paymentAmount').value), dueDate:$('#paymentDueDate').value,
            prerequisiteDeliverableId:Number($('#paymentPrerequisite').value) || null
        })});
        $('#paymentPlanDialog').close(); toast('付款计划已保存'); await reloadAll();
    });
    $('#paymentRecordForm').addEventListener('submit', async event => {
        if (event.submitter?.value === 'cancel') return;
        event.preventDefault();
        await api('/api/performance/payment-records', {method:'POST', body:JSON.stringify({
            paymentPlanId:Number($('#recordPlanId').value), contractId, paidAmount:Number($('#paidAmount').value),
            paidAt:$('#paidAt').value, receiptNo:$('#receiptNo').value.trim(), notes:$('#paymentNotes').value.trim()
        })});
        $('#paymentRecordDialog').close(); toast('到账记录已保存'); await reloadAll();
    });
}

initPerformance().catch(error => toast(error.message));

/* ============================================================
   履约与付款台账 — 前端逻辑
   Tab 1: 履约节点 / Tab 2: 交付物清单 / Tab 3: 付款计划与记录
   ============================================================ */

if (!initAppShell('fulfillment', '履约与付款台账', '合同履行过程中的交付物核验与付款管理')) {
    throw new Error('auth required');
}

const STAGE_LABELS = { SIGNING: '签订阶段', MID_DELIVERY: '中期交付', ACCEPTANCE: '验收阶段' };
const STAGE_ORDER = ['SIGNING', 'MID_DELIVERY', 'ACCEPTANCE'];
const STAGE_DOT_CLASS = { SIGNING: 'signing', MID_DELIVERY: 'mid-delivery', ACCEPTANCE: 'acceptance' };
const STATUS_LABELS = { PENDING: '待付款', PAID: '已付款', OVERDUE: '逾期' };
const STATUS_BADGE_CLASS = { PENDING: 'badge-pending', PAID: 'badge-paid', OVERDUE: 'badge-overdue' };
const TYPE_LABELS = { DESIGN_DOC: '需求设计文档', SOURCE_CODE: '源代码', RUNNABLE_PROGRAM: '可运行程序', ACCEPTANCE_REPORT: '验收报告' };

let contractId = null;
let allDeliverables = [];
let allContracts = [];

/* ============================================================
   Initialization
   ============================================================ */

function getContractId() {
    const params = new URLSearchParams(location.search);
    const id = params.get('contractId');
    return id ? parseInt(id, 10) : null;
}

async function init() {
    await loadContracts();

    var urlId = getContractId();
    if (urlId && allContracts.some(function (c) { return c.contractId == urlId; })) {
        contractId = urlId;
    } else if (allContracts.length > 0) {
        contractId = allContracts[0].contractId;
    }

    // Always init UI, even if no contracts
    buildContractSelector();
    updateHeader();
    initTabSwitching();
    initDeliverableModal();
    initConfirmModal();
    initPaymentPlanModal();
    initRecordPaymentModal();
    bindButtons();

    if (allContracts.length === 0) {
        showEmptyState('暂无活跃合同，请先在合同管理模块创建合同');
    } else {
        loadTab('plans');
    }
}

function showEmptyState(msg) {
    var el;
    el = $('#planList'); if (el) el.innerHTML = '<div class="empty-state">' + msg + '</div>';
    el = $('#deliverableList'); if (el) el.innerHTML = '<div class="empty-state">' + msg + '</div>';
    el = $('#paymentPlanList'); if (el) el.innerHTML = '<div class="empty-state">' + msg + '</div>';
}

function buildContractSelector() {
    var sel = $('#contractSelector');
    if (!sel) return;
    sel.innerHTML = '';
    if (allContracts.length === 0) {
        sel.innerHTML = '<option value="">暂无合同</option>';
        return;
    }
    allContracts.forEach(function (c) {
        var selected = c.contractId == contractId ? ' selected' : '';
        sel.innerHTML += '<option value="' + c.contractId + '"' + selected + '>'
            + escapeHtml(c.contractNo) + ' ' + escapeHtml(c.title) + '</option>';
    });
    sel.addEventListener('change', function () {
        contractId = parseInt(this.value, 10);
        updateHeader();
        loadCurrentTab();
    });
}

function updateHeader() {
    var c = allContracts.find(function (x) { return x.contractId == contractId; });
    $('#contractTitle').textContent = c ? c.title : '履约与付款台账';
    $('#contractEyebrow').textContent = c ? ('合同编号：' + c.contractNo) : '';
}

function loadCurrentTab() {
    updateHeader();
    var activeTab = document.querySelector('.tab-btn.active');
    if (activeTab) loadTab(activeTab.dataset.tab);
}

async function loadContracts() {
    try {
        allContracts = await api('/api/performance/contracts') || [];
    } catch (e) {
        console.error('loadContracts failed:', e);
        allContracts = [];
    }
}

function populateContractSelect(selectId, selectedId) {
    var sel = $(selectId);
    if (!sel) return;
    sel.innerHTML = '<option value="">请选择合同</option>';
    if (allContracts.length === 0) {
        sel.innerHTML = '<option value="">合同列表为空，请刷新页面</option>';
        return;
    }
    allContracts.forEach(function (c) {
        var selected = (selectedId && c.contractId == selectedId) ? ' selected' : '';
        sel.innerHTML += '<option value="' + c.contractId + '"' + selected + '>'
            + escapeHtml(c.contractNo) + ' ' + escapeHtml(c.title) + '</option>';
    });
}

// Retry loading contracts if empty, then fill selectors
async function ensureContractsLoaded(selectId, selectedId) {
    if (allContracts.length === 0) {
        await loadContracts();
    }
    populateContractSelect(selectId, selectedId);
}

async function loadPlanOptionsForModal(contractId, planSelectId, selectedPlanId) {
    var sel = $(planSelectId);
    if (!sel) return;
    sel.innerHTML = '<option value="">加载中...</option>';
    if (!contractId) { sel.innerHTML = '<option value="">请先选择合同</option>'; return; }
    try {
        var plans = await api('/api/performance/plans?contractId=' + contractId) || [];
        sel.innerHTML = '<option value="">请选择履约节点</option>';
        plans.forEach(function (p) {
            var selected = (selectedPlanId && p.planId == selectedPlanId) ? ' selected' : '';
            sel.innerHTML += '<option value="' + p.planId + '"' + selected + '>'
                + escapeHtml(p.milestoneName) + ' (' + escapeHtml(p.statusName || p.status) + ')</option>';
        });
    } catch (e) {
        sel.innerHTML = '<option value="">加载失败</option>';
    }
}

/* ============================================================
   Tab Switching
   ============================================================ */

function initTabSwitching() {
    $$('.tab-btn').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var tabName = btn.dataset.tab;
            $$('.tab-btn').forEach(function (b) { b.classList.remove('active'); });
            btn.classList.add('active');
            $$('.tab-panel').forEach(function (p) { p.classList.remove('active'); });
            $('#tab-' + tabName).classList.add('active');
            loadTab(tabName);
        });
    });
}

function loadTab(tabName) {
    if (tabName === 'plans') loadPlans();
    else if (tabName === 'deliverables') loadDeliverables();
    else if (tabName === 'payments') { loadProgressCards(); loadPaymentPlans(); loadOverduePlans(); }
}

/* ============================================================
   Tab 1: 履约节点
   ============================================================ */

async function loadPlans() {
    try {
        var data = await api('/api/performance/plans?contractId=' + contractId);
        renderPlans(data || []);
    } catch (e) {
        toast(e.message);
        $('#planList').innerHTML = '<div class="empty-state">加载失败，请重试</div>';
    }
}

function renderPlans(list) {
    var container = $('#planList');
    if (!list || list.length === 0) {
        container.innerHTML = '<div class="empty-state">暂无履约节点</div>';
        return;
    }

    var planStatusBadge = {
        PENDING: '<span class="badge badge-pending">待处理</span>',
        IN_PROGRESS: '<span class="badge badge-confirmed">进行中</span>',
        COMPLETED: '<span class="badge badge-paid">已完成</span>',
        OVERDUE: '<span class="badge badge-overdue">已逾期</span>'
    };

    var html = '';
    list.forEach(function (p) {
        html += '<div class="deliverable-row" data-plan-id="' + p.planId + '">';
        html += '<div class="deliverable-info">';
        html += '<span class="deliverable-name">' + escapeHtml(p.milestoneName) + '</span>';
        html += '<span class="deliverable-type-tag">' + escapeHtml(p.contractNo || '') + ' ' + escapeHtml(p.contractTitle || '') + '</span>';
        html += '<span class="plan-status-badge" id="badge-' + p.planId + '">' + (planStatusBadge[p.status] || p.status) + '</span>';
        html += '<span style="font-size:12px;color:var(--muted);margin-left:8px;">到期：' + (p.dueDate || '--') + '</span>';
        if (p.actualDate) {
            html += '<span style="font-size:12px;color:var(--success);margin-left:4px;">实际：' + p.actualDate + '</span>';
        }
        html += '</div>';
        html += '<div class="deliverable-actions">';
        html += '<select class="plan-status-select" data-plan-id="' + p.planId + '" style="font-size:12px;padding:4px 8px;">';
        html += '<option value="">变更状态</option>';
        html += '<option value="PENDING">待处理</option>';
        html += '<option value="IN_PROGRESS">进行中</option>';
        html += '<option value="COMPLETED">已完成</option>';
        html += '<option value="OVERDUE">已逾期</option>';
        html += '</select>';
        html += '</div>';
        html += '</div>';
    });
    container.innerHTML = html;

    // Bind status change handlers
    $$('.plan-status-select', container).forEach(function (sel) {
        sel.addEventListener('change', function () {
            var planId = this.dataset.planId;
            var newStatus = this.value;
            if (!newStatus) return;
            updatePlanStatus(planId, newStatus);
        });
    });
}

async function updatePlanStatus(planId, newStatus) {
    try {
        await api('/api/performance/plans/' + planId + '/status', {
            method: 'PUT',
            body: JSON.stringify({ status: newStatus })
        });
        toast('状态已更新为：' + newStatus);
        loadPlans();
    } catch (e) {
        toast(e.message);
    }
}

/* ============================================================
   Tab 2: 交付物清单
   ============================================================ */

async function loadDeliverables() {
    try {
        var data = await api('/api/performance/deliverables?contractId=' + contractId);
        allDeliverables = data || [];
        renderDeliverables(allDeliverables);
    } catch (e) {
        toast(e.message);
        $('#deliverableList').innerHTML = '<div class="empty-state">加载失败，请重试</div>';
    }
}

function renderDeliverables(list) {
    var container = $('#deliverableList');
    if (!list || list.length === 0) {
        container.innerHTML = '<div class="empty-state">暂无交付物，点击右上角「新增交付物」开始添加</div>';
        return;
    }

    list.sort(function (a, b) {
        if (a.sortOrder !== b.sortOrder) return a.sortOrder - b.sortOrder;
        return a.deliverableId - b.deliverableId;
    });

    var groups = {};
    STAGE_ORDER.forEach(function (stage) { groups[stage] = []; });
    list.forEach(function (d) {
        var stage = d.contractStage || 'SIGNING';
        (groups[stage] = groups[stage] || []).push(d);
    });

    var html = '';
    STAGE_ORDER.forEach(function (stage) {
        var items = groups[stage] || [];
        html += '<div class="stage-group">';
        html += '<div class="stage-header">';
        html += '<span class="stage-dot ' + STAGE_DOT_CLASS[stage] + '"></span>';
        html += '<span>' + STAGE_LABELS[stage] + '</span>';
        html += '<span class="stage-count">' + items.length + ' 项</span>';
        html += '</div>';

        items.forEach(function (d) {
            var typeName = d.deliverableTypeName || TYPE_LABELS[d.deliverableType] || d.deliverableType || '';
            var confirmed = d.isConfirmed === 1;
            var badgeHtml = confirmed
                ? '<span class="badge badge-confirmed">已确认</span>'
                : '<span class="badge badge-unconfirmed">未确认</span>';

            html += '<div class="deliverable-row">';
            html += '<div class="deliverable-info">';
            html += '<span class="deliverable-name">' + escapeHtml(d.itemName) + '</span>';
            if (typeName) html += '<span class="deliverable-type-tag">' + escapeHtml(typeName) + '</span>';
            html += badgeHtml;
            if (confirmed && d.confirmedAt) {
                html += '<span style="font-size:11.5px;color:var(--muted);margin-left:8px;">确认于 ' + escapeHtml(d.confirmedAt) + '</span>';
            }
            html += '</div>';
            html += '<div class="deliverable-actions">';
            if (!confirmed) {
                html += '<button class="btn-sm" onclick="confirmDelivBtn(' + d.deliverableId + ',\'' + escapeHtml(d.itemName) + '\')">确认</button>';
            } else {
                html += '<button class="secondary btn-sm" onclick="unconfirmDeliv(' + d.deliverableId + ')">取消确认</button>';
            }
            html += '<button class="secondary btn-sm" onclick="editDelivBtn(' + d.deliverableId + ')">编辑</button>';
            html += '<button class="secondary btn-sm danger" onclick="delDeliverable(' + d.deliverableId + ')">删除</button>';
            html += '</div>';
            html += '</div>';
        });
        html += '</div>';
    });

    container.innerHTML = html;
    renderLucideIcons();
}

/* ---- Deliverable Modal ---- */

function initDeliverableModal() {
    var el;
    el = $('#closeDeliverableModal'); if (el) el.addEventListener('click', closeDeliverableModal);
    el = $('#cancelDeliverableBtn'); if (el) el.addEventListener('click', closeDeliverableModal);
    el = $('#deliverableModal'); if (el) el.addEventListener('click', function (e) { if (e.target.id === 'deliverableModal') closeDeliverableModal(); });
    el = $('#saveDeliverableBtn'); if (el) el.addEventListener('click', saveDeliverable);
    el = $('#delivContractId'); if (el) el.addEventListener('change', function () {
        loadPlanOptionsForModal(parseInt(this.value, 10), 'delivPlanSelect', null);
    });
}

function openDeliverableModal(item) {
    var isEdit = !!item;
    var cid = isEdit ? item.contractId : contractId;

    $('#deliverableModal').hidden = false;

    var elLab = $('#delivContractLabel'); if (elLab) elLab.style.display = 'none';

    // Always repopulate + retry if contracts not loaded
    ensureContractsLoaded('delivContractId', cid);
    // Then set the value after a brief delay for populate to finish if needed
    setTimeout(function () { var s = $('#delivContractId'); if (s) s.value = cid || ''; }, 100);

    // Load plan options for the selected contract
    loadPlanOptionsForModal(cid, 'delivPlanSelect', item ? item.planId : null);

    if (isEdit) {
        $('#deliverableModalTitle').textContent = '编辑交付物';
        $('#delivId').value = item.deliverableId;
        $('#delivType').value = item.deliverableType || '';
        $('#delivName').value = item.itemName || '';
        $('#delivStage').value = item.contractStage || '';
        $('#delivSort').value = item.sortOrder !== undefined ? item.sortOrder : 0;
    } else {
        $('#deliverableModalTitle').textContent = '新增交付物';
        $('#delivId').value = '';
        $('#delivType').value = '';
        $('#delivName').value = '';
        $('#delivStage').value = '';
        $('#delivSort').value = 0;
    }
}

function closeDeliverableModal() { $('#deliverableModal').hidden = true; }

async function saveDeliverable() {
    var type = $('#delivType').value.trim();
    var name = $('#delivName').value.trim();
    var stage = $('#delivStage').value.trim();
    var cid = parseInt($('#delivContractId').value, 10) || contractId;

    if (!cid) { toast('请选择合同'); return; }
    if (!type) { toast('请选择交付物类型'); return; }
    if (!name) { toast('请输入交付物名称'); return; }
    if (!stage) { toast('请选择合同阶段'); return; }

    var body = {
        deliverableId: $('#delivId').value ? Number($('#delivId').value) : null,
        planId: parseInt($('#delivPlanSelect').value, 10) || null,
        contractId: cid,
        deliverableType: type,
        itemName: name,
        contractStage: stage,
        sortOrder: parseInt($('#delivSort').value, 10) || 0
    };

    try {
        await api('/api/performance/deliverables', { method: 'POST', body: JSON.stringify(body) });
        closeDeliverableModal();
        toast(body.deliverableId ? '交付物已更新' : '交付物已创建');
        loadDeliverables();
    } catch (e) { toast(e.message); }
}

/* ---- Confirm Modal ---- */

var confirmDeliverableId = null;

function initConfirmModal() {
    var el;
    el = $('#closeConfirmModal'); if (el) el.addEventListener('click', closeConfirmModal);
    el = $('#cancelConfirmBtn'); if (el) el.addEventListener('click', closeConfirmModal);
    el = $('#confirmModal'); if (el) el.addEventListener('click', function (e) { if (e.target.id === 'confirmModal') closeConfirmModal(); });
    el = $('#confirmDeliverableBtn'); if (el) el.addEventListener('click', confirmDeliverable);
}

function openConfirmModal(id, name) {
    confirmDeliverableId = Number(id);
    $('#confirmDelivName').textContent = '确认交付物：' + name;
    $('#confirmBy').value = state.username || '';
    $('#confirmModal').hidden = false;
}

function closeConfirmModal() { $('#confirmModal').hidden = true; confirmDeliverableId = null; }

async function confirmDeliverable() {
    if (!confirmDeliverableId) return;
    try {
        await api('/api/performance/deliverables/' + confirmDeliverableId + '/confirm', {
            method: 'PUT',
            body: JSON.stringify({ deliverableId: confirmDeliverableId, confirmedBy: $('#confirmBy').value.trim() || undefined })
        });
        closeConfirmModal();
        toast('交付物已确认');
        loadDeliverables();
    } catch (e) { toast(e.message); }
}

async function unconfirmDeliverable(id) {
    try {
        await api('/api/performance/deliverables/' + id + '/unconfirm', { method: 'PUT' });
        toast('已取消确认');
        loadDeliverables();
    } catch (e) { toast(e.message); }
}

async function deleteDeliverable(id) {
    try {
        await api('/api/performance/deliverables/' + id, { method: 'DELETE' });
        toast('交付物已删除');
        loadDeliverables();
    } catch (e) { toast(e.message); }
}

/* ============================================================
   Tab 3: 付款计划与记录
   ============================================================ */

async function loadPaymentPlans() {
    try {
        var data = await api('/api/performance/payment-plans?contractId=' + contractId);
        renderPaymentPlans(data || []);
    } catch (e) {
        toast(e.message);
        $('#paymentPlanList').innerHTML = '<div class="empty-state">加载失败，请重试</div>';
    }
}

function renderPaymentPlans(list) {
    var container = $('#paymentPlanList');
    if (!list || list.length === 0) {
        container.innerHTML = '<div class="empty-state">暂无付款计划，点击右上角「新增分期」开始添加</div>';
        return;
    }

    // Sort: overdue first, then by installment
    list.sort(function (a, b) {
        if (a.status === 'OVERDUE' && b.status !== 'OVERDUE') return -1;
        if (a.status !== 'OVERDUE' && b.status === 'OVERDUE') return 1;
        return a.installmentNo - b.installmentNo;
    });

    // Overdue count banner
    var overdueCount = list.filter(function (p) { return p.status === 'OVERDUE'; }).length;
    var banner = $('#overdueBanner');
    if (banner) {
        if (overdueCount > 0) {
            banner.style.display = '';
            banner.innerHTML = '共有 <strong>' + overdueCount + '</strong> 个付款计划已逾期，请尽快确认到账或调整。逾期项排在列表最前面，可直接操作。';
        } else {
            banner.style.display = 'none';
        }
    }

    var html = '';
    list.forEach(function (plan) {
        var statusLabel = STATUS_LABELS[plan.status] || plan.status;
        var badgeClass = STATUS_BADGE_CLASS[plan.status] || 'badge-pending';
        var amount = Number(plan.amount || 0);
        var totalPaid = Number(plan.totalPaid || 0);

        html += '<div class="payment-plan-card">';
        html += '<div class="plan-header">';
        html += '<span class="plan-expand-icon">&#9654;</span>';
        html += '<div class="plan-summary">';
        html += '<span class="plan-installment">第 ' + plan.installmentNo + ' 期</span>';
        html += '<span class="plan-ratio">' + (plan.ratio * 100).toFixed(2) + '%</span>';
        html += '<span class="plan-amount">' + amount.toFixed(2) + ' 元</span>';
        html += '<span class="plan-due-date">到期：' + escapeHtml(plan.dueDate || '--') + '</span>';
        html += '<span class="badge ' + badgeClass + '">' + statusLabel + '</span>';
        if (plan.prerequisiteDeliverableName) {
            var preConfirmed = plan.prerequisiteConfirmed;
            html += '<span class="prerequisite-hint ' + (preConfirmed ? 'confirmed' : 'unconfirmed') + '">前置: ' + escapeHtml(plan.prerequisiteDeliverableName) + (preConfirmed ? ' (已确认)' : ' (未确认)') + '</span>';
        }
        html += '</div>';
        html += '<div class="plan-actions">';
        html += '<button class="secondary btn-sm" onclick="recordPayment(' + plan.paymentPlanId + ',' + plan.installmentNo + ')">确认到账</button>';
        html += '<button class="secondary btn-sm" onclick="editPaymentPlan(' + plan.paymentPlanId + ')">编辑</button>';
        html += '<button class="secondary btn-sm danger" onclick="delPaymentPlan(' + plan.paymentPlanId + ',' + plan.installmentNo + ')">删除</button>';
        html += '</div>';
        html += '</div>';

        html += '<div class="plan-body">';
        html += '<h4>付款记录</h4>';
        var records = plan.records || [];
        if (records.length === 0) {
            html += '<div class="records-empty">暂无付款记录</div>';
        } else {
            html += '<table class="records-table"><thead><tr><th>金额（元）</th><th>付款时间</th><th>凭证号</th><th>备注</th><th>操作</th></tr></thead><tbody>';
            records.forEach(function (rec) {
                html += '<tr>';
                html += '<td>' + Number(rec.paidAmount || 0).toFixed(2) + '</td>';
                html += '<td>' + escapeHtml(rec.paidAt || '--') + '</td>';
                html += '<td>' + escapeHtml(rec.receiptNo || '--') + '</td>';
                html += '<td>' + escapeHtml(rec.notes || '--') + '</td>';
                html += '<td><button class="record-delete-btn" onclick="delPaymentRecord(' + rec.recordId + ')">删除</button></td>';
                html += '</tr>';
            });
            html += '</tbody></table>';
        }
        if (plan.status === 'OVERDUE' && plan.responsibilityHint) {
            html += '<div class="plan-responsibility">' + escapeHtml(plan.responsibilityHint) + ' <span style="font-weight:400;font-size:11px;">（仅作辅助提示，最终由人工确认）</span></div>';
        }
        html += '<p style="margin-top:8px;font-size:12px;color:var(--muted);">实付总额：' + totalPaid.toFixed(2) + ' 元'
            + (plan.overdueDays > 0 ? ' | 逾期 ' + plan.overdueDays + ' 天 | 违约金 ' + Number(plan.penaltyAmount || 0).toFixed(2) + ' 元' : '') + '</p>';
        html += '</div>';
        html += '</div>';
    });

    container.innerHTML = html;

    // Store for onclick access
    window._currentPaymentPlans = list;

    // Only need expand/collapse listeners
    $$('.payment-plan-card', container).forEach(function (card) {
        card.querySelector('.plan-header').addEventListener('click', function (e) {
            if (e.target.closest('button')) return;
            card.classList.toggle('expanded');
        });
    });

    renderLucideIcons();
}

/* ---- Progress Cards ---- */

async function loadProgressCards() {
    try {
        var data = await api('/api/performance/progress/' + contractId);
        renderProgressCards(data);
    } catch (e) { /* silently ignore */ }
}

function renderProgressCards(data) {
    if (!data) return;
    var deliveryPct = Number(data.deliveryProgress || 0);
    var paymentPct = data.totalAmount > 0 ? (Number(data.totalPaid || 0) / Number(data.totalAmount) * 100) : 0;
    var overdueCount = Number(data.overduePlans || 0);

    var html = '';
    html += '<div class="panel overview-card"><p class="eyebrow">交付进度</p><div class="overview-number">' + deliveryPct + '%</div><div class="progress-bar-wrap"><div class="progress-bar-fill" style="width:' + deliveryPct + '%;"></div></div><p class="overview-detail">' + data.confirmedDeliverables + ' / ' + data.totalDeliverables + ' 已确认</p></div>';
    html += '<div class="panel overview-card"><p class="eyebrow">付款进度</p><div class="overview-number">' + paymentPct.toFixed(1) + '%</div><div class="progress-bar-wrap"><div class="progress-bar-fill payment-bar" style="width:' + paymentPct + '%;"></div></div><p class="overview-detail">' + data.totalPaid.toFixed(0) + ' / ' + data.totalAmount.toFixed(0) + ' 元</p></div>';
    html += '<div class="panel overview-card"><p class="eyebrow">合同总金额</p><div class="overview-number accent">' + Number(data.totalAmount).toLocaleString('zh-CN', {minimumFractionDigits: 0}) + '</div><p class="overview-detail">元</p></div>';
    html += '<div class="panel overview-card warn-card"><p class="eyebrow">逾期计划</p><div class="overview-number ' + (overdueCount > 0 ? 'danger' : '') + '">' + overdueCount + '</div><p class="overview-detail">个付款计划已逾期</p></div>';

    var container = $('#progressCards');
    if (container) container.innerHTML = html;
}

/* ---- Overdue Summary ---- */

async function loadOverduePlans() {
    try {
        var data = await api('/api/performance/overdue-list');
        renderOverduePlans(data || []);
    } catch (e) { $('#overdueSection').style.display = 'none'; }
}

function renderOverduePlans(list) {
    var section = $('#overdueSection');
    var tbody = $('#overdueTbody');
    if (!list || list.length === 0) { section.style.display = 'none'; return; }
    section.style.display = '';
    tbody.innerHTML = list.map(function (plan) {
        return '<tr>' +
            '<td><strong>第 ' + plan.installmentNo + ' 期</strong></td>' +
            '<td>' + (plan.ratio * 100).toFixed(2) + '%</td>' +
            '<td>' + Number(plan.amount || 0).toFixed(2) + '</td>' +
            '<td>' + escapeHtml(plan.dueDate || '--') + '</td>' +
            '<td><span class="badge badge-overdue">' + (plan.overdueDays || 0) + ' 天</span></td>' +
            '<td style="color:var(--danger);">' + Number(plan.penaltyAmount || 0).toFixed(2) + '</td>' +
            '<td style="color:var(--warn);font-weight:600;">' + escapeHtml(plan.responsibilityHint || '--') + '</td>' +
            '<td>' + Number(plan.totalPaid || 0).toFixed(2) + '</td>' +
            '</tr>';
    }).join('');
}

/* ---- Payment Plan Modal ---- */

var _contractTotalAmount = 0;

function initPaymentPlanModal() {
    var el;
    el = $('#closePaymentPlanModal'); if (el) el.addEventListener('click', closePaymentPlanModal);
    el = $('#cancelPaymentPlanBtn'); if (el) el.addEventListener('click', closePaymentPlanModal);
    el = $('#paymentPlanModal'); if (el) el.addEventListener('click', function (e) { if (e.target.id === 'paymentPlanModal') closePaymentPlanModal(); });
    el = $('#savePaymentPlanBtn'); if (el) el.addEventListener('click', savePaymentPlan);

    el = $('#ppRatio'); if (el) el.addEventListener('input', function () {
        var ratio = parseFloat($('#ppRatio').value) || 0;
        if (ratio > 0 && _contractTotalAmount > 0) {
            $('#ppAmount').value = (_contractTotalAmount * ratio).toFixed(2);
        }
    });

    // When contract changes in the plan modal, update the amount auto-calc base
    el = $('#ppContractId'); if (el) el.addEventListener('change', function () {
        var cid = parseInt($('#ppContractId').value, 10);
        var contract = allContracts.find(function (c) { return c.contractId == cid; });
        _contractTotalAmount = contract ? (Number(contract.amount) || 0) : 0;
        loadPlanOptionsForModal(cid, 'ppPlanSelect', null);
        loadPrerequisiteOptions(cid);
        var ratio = parseFloat($('#ppRatio').value) || 0;
        if (ratio > 0 && _contractTotalAmount > 0) {
            $('#ppAmount').value = (_contractTotalAmount * ratio).toFixed(2);
        }
    });
}

async function loadPrerequisiteOptions(cid) {
    var select = $('#ppPrerequisite');
    select.innerHTML = '<option value="">无前置条件</option>';
    if (!cid) return;
    try {
        var list = await api('/api/performance/deliverables?contractId=' + cid) || [];
        list.forEach(function (d) {
            select.innerHTML += '<option value="' + d.deliverableId + '">' + escapeHtml(d.itemName) + ' (' + (STAGE_LABELS[d.contractStage] || d.contractStage) + ')</option>';
        });
    } catch (e) { /* keep empty */ }
}

function openPaymentPlanModal(plan) {
    var cid = plan ? plan.contractId : contractId;
    var isEdit = !!plan;

    $('#paymentPlanModal').hidden = false;

    var elLab = $('#ppContractLabel'); if (elLab) elLab.style.display = 'none';

    // Always repopulate + retry if contracts not loaded
    ensureContractsLoaded('ppContractId', cid);
    setTimeout(function () { var s = $('#ppContractId'); if (s) s.value = cid || ''; }, 100);

    // Load plan options and prerequisites for the contract
    loadPlanOptionsForModal(cid, 'ppPlanSelect', plan ? plan.planId : null);

    var contract = allContracts.find(function (c) { return c.contractId == cid; });
    _contractTotalAmount = contract ? (Number(contract.amount) || 0) : 0;
    loadPrerequisiteOptions(cid);

    if (isEdit) {
        $('#paymentPlanModalTitle').textContent = '编辑分期';
        $('#ppId').value = plan.paymentPlanId;
        $('#ppInstallment').value = plan.installmentNo;
        $('#ppRatio').value = plan.ratio;
        $('#ppAmount').value = plan.amount;
        $('#ppDueDate').value = plan.dueDate || '';
        $('#ppPenaltyRate').value = '0.0005';
        if (plan.prerequisiteDeliverableId) {
            setTimeout(function () { $('#ppPrerequisite').value = plan.prerequisiteDeliverableId; }, 200);
        }
    } else {
        $('#paymentPlanModalTitle').textContent = '新增分期';
        $('#ppId').value = '';
        $('#ppInstallment').value = '';
        $('#ppRatio').value = '';
        $('#ppAmount').value = '';
        $('#ppDueDate').value = '';
        $('#ppPenaltyRate').value = '0.0005';
    }
}

function closePaymentPlanModal() { $('#paymentPlanModal').hidden = true; }

async function savePaymentPlan() {
    var cid = parseInt($('#ppContractId').value, 10) || contractId;
    var installmentNo = parseInt($('#ppInstallment').value, 10);
    var ratio = parseFloat($('#ppRatio').value);
    var amount = parseFloat($('#ppAmount').value);
    var dueDate = $('#ppDueDate').value;
    var prerequisiteId = $('#ppPrerequisite').value;

    if (!cid) { toast('请选择合同'); return; }
    if (!installmentNo || installmentNo < 1) { toast('请输入有效的分期期次'); return; }
    if (isNaN(ratio) || ratio <= 0 || ratio > 1) { toast('请输入有效的付款比例 (0-1)'); return; }
    if (!dueDate) { toast('请选择到期日'); return; }

    var body = {
        paymentPlanId: $('#ppId').value ? Number($('#ppId').value) : null,
        planId: parseInt($('#ppPlanSelect').value, 10) || null,
        contractId: cid,
        installmentNo: installmentNo,
        ratio: ratio,
        amount: isNaN(amount) ? null : amount,
        dueDate: dueDate,
        prerequisiteDeliverableId: prerequisiteId ? Number(prerequisiteId) : null
    };

    try {
        await api('/api/performance/payment-plans', { method: 'POST', body: JSON.stringify(body) });
        closePaymentPlanModal();
        toast(body.paymentPlanId ? '付款计划已更新' : '付款计划已创建');
        loadPaymentPlans();
        loadOverduePlans();
    } catch (e) { toast(e.message); }
}

async function deletePaymentPlan(id) {
    try {
        await api('/api/performance/payment-plans/' + id, { method: 'DELETE' });
        toast('付款计划已删除');
        loadPaymentPlans();
        loadOverduePlans();
    } catch (e) { toast(e.message); }
}

/* ---- Record Payment Modal (确认到账) ---- */

function initRecordPaymentModal() {
    var el;
    el = $('#closeRecordPaymentModal'); if (el) el.addEventListener('click', closeRecordPaymentModal);
    el = $('#cancelRecordPaymentBtn'); if (el) el.addEventListener('click', closeRecordPaymentModal);
    el = $('#recordPaymentModal'); if (el) el.addEventListener('click', function (e) { if (e.target.id === 'recordPaymentModal') closeRecordPaymentModal(); });
    el = $('#saveRecordPaymentBtn'); if (el) el.addEventListener('click', saveRecordPayment);
}

function openRecordPaymentModal(paymentPlanId, installmentNo) {
    $('#rpPaymentPlanId').value = paymentPlanId;
    $('#recordPaymentPlanInfo').textContent = '为第 ' + installmentNo + ' 期付款计划确认到账';
    $('#rpAmount').value = '';
    $('#rpPaidAt').value = '';
    $('#rpReceiptNo').value = '';
    $('#rpNotes').value = '';
    $('#recordPaymentModal').hidden = false;
}

function closeRecordPaymentModal() { $('#recordPaymentModal').hidden = true; }

async function saveRecordPayment() {
    var amount = parseFloat($('#rpAmount').value);
    var paidAt = $('#rpPaidAt').value;
    var receiptNo = $('#rpReceiptNo').value.trim();
    var notes = $('#rpNotes').value.trim();

    if (isNaN(amount) || amount <= 0) { toast('请输入有效的付款金额'); return; }
    if (!paidAt) { toast('请选择付款时间'); return; }
    if (!receiptNo) { toast('请输入凭证号'); return; }

    var body = {
        paymentPlanId: Number($('#rpPaymentPlanId').value),
        contractId: Number(contractId),
        paidAmount: amount,
        paidAt: paidAt,
        receiptNo: receiptNo,
        notes: notes || undefined
    };

    try {
        await api('/api/performance/payment-records', { method: 'POST', body: JSON.stringify(body) });
        closeRecordPaymentModal();
        toast('到账已确认');
        loadPaymentPlans();
        loadOverduePlans();
    } catch (e) { toast(e.message); }
}

async function deletePaymentRecord(recordId, planId) {
    try {
        await api('/api/performance/payment-records/' + recordId, { method: 'DELETE' });
        toast('付款记录已删除');
        loadPaymentPlans();
        loadOverduePlans();
    } catch (e) { toast(e.message); }
}

/* ============================================================
   Delete Confirmation — use browser confirm() for reliability
   ============================================================ */

function confirmDelete(message, callback) {
    if (confirm(message)) {
        callback();
    }
}

/* ============================================================
   Button Bindings
   ============================================================ */

function bindButtons() {
    var el;
    el = $('#addDeliverableBtn'); if (el) el.addEventListener('click', function () { openDeliverableModal(null); });
    el = $('#addPaymentPlanBtn'); if (el) el.addEventListener('click', function () { openPaymentPlanModal(null); });
}

/* ============================================================
   Expose delete functions for inline onclick
   ============================================================ */

window.delDeliverable = function (id) {
    if (confirm('确定要删除该交付物吗？')) deleteDeliverable(id);
};
window.unconfirmDeliv = function (id) {
    if (confirm('确定要取消确认吗？')) unconfirmDeliverable(id);
};
window.delPaymentPlan = function (id, installment) {
    if (confirm('确定要删除第 ' + installment + ' 期付款计划吗？')) deletePaymentPlan(id);
};
window.delPaymentRecord = function (recordId) {
    if (confirm('确定要删除该付款记录吗？')) deletePaymentRecord(recordId);
};
window.editPaymentPlan = function (id) {
    // Find plan from current list
    var plans = window._currentPaymentPlans || [];
    var plan = plans.find(function (p) { return p.paymentPlanId == id; });
    if (plan) openPaymentPlanModal(plan);
};
window.recordPayment = function (id, installment) {
    openRecordPaymentModal(id, installment);
};
window.confirmDelivBtn = function (id, name) {
    openConfirmModal(id, name);
};
window.editDelivBtn = function (id) {
    var item = allDeliverables.find(function (d) { return d.deliverableId == id; });
    if (item) openDeliverableModal(item);
};

/* ============================================================
   Initialize
   ============================================================ */

init();
renderLucideIcons();

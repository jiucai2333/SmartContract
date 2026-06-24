if (!initAppShell('fulfillment', '履约与付款', '履约计划、交付物确认、付款台账与逾期责任提示')) {
    throw new Error('auth required');
}

const statusTextMap = {
    NOT_STARTED: '待开始',
    ACTIVE: '待开始',
    TODO: '待开始',
    PENDING_CONFIRM: '待人工确认',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成',
    OVERDUE: '已逾期',
    CLOSED: '已关闭',
    HANDLED: '已关闭'
};
const voucherTypeMap = {
    PROGRESS: '进度凭证',
    COMPLETION: '完成凭证',
    EXCEPTION: '异常说明',
    PAYMENT: '付款凭证'
};
const voucherReviewMap = {
    PENDING_REVIEW: '待复核',
    APPROVED: '复核通过',
    REJECTED: '复核退回',
    NOT_REQUIRED: '无需复核'
};
const operationTextMap = {
    CREATE: '创建',
    UPDATE: '更新',
    DELAY_REQUEST: '延期申请',
    DELETE: '删除',
    DELAY_CONFIRM: '延期确认',
    CLOSE_OVERDUE: '逾期关闭',
    VOUCHER_UPLOAD: '凭证上传',
    VOUCHER_REVIEW: '凭证审核',
    MARK_OVERDUE: '标记逾期'
};
Object.assign(operationTextMap, {
    DELAY_APPROVED: '延期审批通过',
    DELAY_REJECTED: '延期审批驳回',
    OVERDUE_HANDLE: '逾期处置',
    FULFILLMENT_PLAN_CREATE: '创建履约节点',
    FULFILLMENT_PLAN_UPDATE: '更新履约节点',
    FULFILLMENT_PLAN_DELAY_REQUEST: '履约节点延期申请',
    FULFILLMENT_PLAN_DELAY_CONFIRM: '履约节点延期确认',
    FULFILLMENT_PLAN_OVERDUE_HANDLE: '履约节点逾期处置',
    FULFILLMENT_PLAN_OVERDUE_COMPLETE: '履约节点逾期标记完成',
    FULFILLMENT_PLAN_OVERDUE_DELAY_REQUEST: '履约节点逾期申请延期',
    FULFILLMENT_DELAY_APPROVE: '延期审批通过',
    FULFILLMENT_DELAY_REJECT: '延期审批驳回',
    FULFILLMENT_VOUCHER_UPLOAD: '凭证上传',
    FULFILLMENT_VOUCHER_REVIEW: '凭证审核',
    FULFILLMENT_DELIVERABLE_CREATE: '新增交付物',
    FULFILLMENT_DELIVERABLE_UPDATE: '编辑交付物',
    FULFILLMENT_DELIVERABLE_CONFIRM: '确认交付物',
    FULFILLMENT_DELIVERABLE_UNCONFIRM: '取消确认交付物',
    FULFILLMENT_DELIVERABLE_DELETE: '删除交付物',
    FULFILLMENT_DELIVERABLE_FILE_UPLOAD: '上传交付物文件',
    FULFILLMENT_DELIVERABLE_SUBMIT: '提交交付物',
    FULFILLMENT_DELIVERABLE_SUPPLEMENT: '要求补充交付物',
    FULFILLMENT_DELIVERABLE_REJECT: '驳回交付物',
    FULFILLMENT_DELIVERABLE_ACCEPT: '确认交付物',
    FULFILLMENT_DELIVERABLE_ACCEPTANCE: '交付物验收确认',
    OVERDUE_COMPLETE: '逾期标记完成',
    OVERDUE_DELAY_REQUEST: '逾期申请延期',
    DELAY_APPROVE: '延期审批通过',
    DELAY_REJECT: '延期审批驳回'
});

function progressOperationText(operation) {
    const value = String(operation || '').trim();
    if (!value) return '-';
    if (operationTextMap[value]) return operationTextMap[value];
    const upper = value.toUpperCase();
    if (operationTextMap[upper]) return operationTextMap[upper];
    if (!/^[A-Z0-9_]+$/.test(value)) return value;
    if (value.startsWith('FULFILLMENT_DELIVERABLE')) return '交付物操作';
    if (value.startsWith('FULFILLMENT_PLAN')) return '履约节点操作';
    if (value.startsWith('FULFILLMENT_VOUCHER') || value.startsWith('VOUCHER')) return '凭证操作';
    if (value.startsWith('OVERDUE')) return '逾期处置';
    if (value.startsWith('DELAY')) return '延期处理';
    return '系统操作';
}

const planTypeMap = {
    PREPARE: '材料准备',
    CHECK: '进度确认',
    ACCEPTANCE: '验收交付',
    PAYMENT: '付款节点',
    DELIVERY: '交付节点',
    WARRANTY: '质保节点',
    RENEWAL: '续签节点',
    TERMINATION: '终止节点',
    INVOICE: '发票节点',
    CONFIDENTIALITY: '保密期限',
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
    WAIT_CONDITION: '待条件满足',
    READY_TO_PAY: '待付款',
    PARTIAL_PAID: '部分付款',
    PAID: '已到账',
    OVERDUE: '已逾期',
    SUSPENDED: '已挂起'
};
const paymentConditionTypeMap = {
    NONE: '无前置条件',
    DELIVERABLE: '交付确认后付款',
    ACCEPTANCE: '验收确认后付款',
    INVOICE: '发票齐全后付款'
};
const deliverableStatusMap = {
    PENDING_SUBMIT: '待提交',
    SUBMITTED: '已提交',
    NEED_SUPPLEMENT: '待补充',
    REJECTED: '已驳回',
    ACCEPTED: '已确认',
    ACCEPTANCE_PASSED: '验收确认'
};
const deliverableTransitionText = {
    SUBMIT: '提交',
    SUPPLEMENT: '要求补充',
    REJECT: '驳回',
    ACCEPT: '确认',
    ACCEPTANCE: '验收确认'
};
const paymentConditionStatusMap = {
    SATISFIED: '已满足',
    PENDING: '待满足'
};
const invoiceStatusMap = {
    DRAFT: '草稿',
    RECEIVED: '已收票',
    VERIFIED: '已核验',
    VALID: '有效',
    INVALID: '无效',
    VOID: '已作废'
};
const channelTextMap = {
    IN_APP: '站内消息',
    EMAIL: '邮件',
    WECHAT: '企业微信',
    SMS: '短信'
};
const PAYMENT_RATIO_TARGET = 100;

let contracts = [];
let plans = [];
let deliverables = [];
let paymentPlans = [];
let paymentRecords = [];
let invoiceRecords = [];
let currentVoucherPlan = null;

function selectedContractId(selector) {
    const value = $(selector).value;
    return value ? Number(value) : null;
}

function currentFilterContractId() {
    return selectedContractId('#filterContract');
}

function todayString() {
    const now = new Date();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    return `${now.getFullYear()}-${month}-${day}`;
}

function setActualCompletedDateLimits() {
    const today = todayString();
    ['#actualCompletedDate', '#overdueActualCompletedDate'].forEach(selector => {
        const input = $(selector);
        if (input) input.max = today;
    });
}

function assertActualCompletedDateNotFuture(value) {
    if (value && value > todayString()) {
        throw new Error('\u5b9e\u9645\u5b8c\u6210\u65e5\u671f\u4e0d\u80fd\u665a\u4e8e\u4eca\u5929');
    }
}

function syncActualCompletedDateField() {
    const input = $('#actualCompletedDate');
    const field = input?.closest('label');
    const completed = normalizePlanStatus($('#planStatus').value) === 'COMPLETED';
    if (field) field.hidden = !completed;
    if (!completed && input) input.value = '';
    if (input) input.disabled = !completed || $('#planStatus').disabled;
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
    renderDeliverablesEnhanced(deliverables);
}

async function loadPaymentPlans() {
    paymentPlans = await api(endpointWithContract('/api/fulfillment/payments/plans'));
    renderPaymentPlans(paymentPlans);
}

async function loadPaymentRecords() {
    paymentRecords = await api(endpointWithContract('/api/fulfillment/payments/records'));
    renderPaymentRecords(paymentRecords);
}

async function loadInvoices() {
    invoiceRecords = await api(endpointWithContract('/api/fulfillment/payments/invoices'));
    renderInvoices(invoiceRecords);
}

async function loadReminders() {
    const list = await api(endpointWithContract('/api/fulfillment/reminders'));
    renderReminders(list);
    return list;
}

async function refreshAll() {
    const statPromise = api(endpointWithContract('/api/fulfillment/stats'));
    const reminderPromise = loadReminders();
    await Promise.all([loadPlans(), loadDeliverables(), loadPaymentPlans(), loadPaymentRecords(), loadInvoices()]);
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

function normalizePlanStatus(status) {
    const value = String(status || 'NOT_STARTED').trim().toUpperCase();
    if (['TODO', 'ACTIVE', 'NOT_STARTED'].includes(value)) return 'NOT_STARTED';
    if (['HANDLED', 'CLOSE', 'CLOSED'].includes(value)) return 'CLOSED';
    if (value === 'PROCESSING') return 'IN_PROGRESS';
    return value;
}

function formatConfidence(value) {
    if (value === null || value === undefined || value === '') {
        return '未返回';
    }
    const number = Number(value);
    if (Number.isNaN(number)) {
        return '未返回';
    }
    return `${Math.round(number * 100)}%`;
}

function planSourceText(plan) {
    if (plan.aiExtracted || plan.sourceType === 'AI') {
        return 'AI抽取';
    }
    if (plan.sourceType === 'AUTO') {
        return '系统生成';
    }
    return '人工维护';
}

function planSourceClass(plan, pendingConfirm) {
    if (plan.aiExtracted || plan.sourceType === 'AI') {
        return pendingConfirm ? 'status-PENDING_CONFIRM' : 'status-COMPLETED';
    }
    return plan.sourceType === 'AUTO' ? 'status-IN_PROGRESS' : 'status-TODO';
}

function deliverableConfirmText(status) {
    return status === 'PENDING_CONFIRM' ? '待人工确认' : '来源已确认';
}

function deliverableSourceText(item) {
    if (item.aiExtracted) return 'AI生成';
    return item.planId ? '节点关联' : '人工维护';
}

function ensureDeliverableUi() {
    const body = $('#deliverableTbody');
    if (!body) return;
    const panel = body.closest('.fulfillment-table-panel');
    const head = panel?.querySelector('.panel-head');
    if (head && !$('#newDeliverableBtn', head)) {
        head.insertAdjacentHTML('beforeend', `
            <div class="panel-action-group">
                <button id="newDeliverableBtn" class="page-action-btn" type="button">
                    <i data-lucide="plus"></i>
                    新增交付物
                </button>
            </div>
        `);
    }
    const headerRow = body.closest('table')?.querySelector('thead tr');
    if (headerRow && !headerRow.dataset.deliverableEnhanced) {
        headerRow.dataset.deliverableEnhanced = 'true';
        headerRow.innerHTML = `
            <th>合同</th>
            <th>交付物</th>
            <th>对应节点</th>
            <th>AI信息</th>
            <th>流程状态</th>
            <th>提交/审核信息</th>
            <th>交付物文件</th>
            <th>备注</th>
            <th>操作</th>
        `;
    }
    if (!$('#deliverableModal')) {
        document.body.insertAdjacentHTML('beforeend', `
            <div id="deliverableModal" class="modal" hidden>
                <div class="modal-card">
                    <div class="modal-head">
                        <h3 id="deliverableModalTitle">新增交付物</h3>
                        <button type="button" id="closeDeliverableModal" class="secondary">关闭</button>
                    </div>
                    <form id="deliverableForm" class="form-grid">
                        <input type="hidden" id="deliverableId">
                        <label>
                            合同
                            <select id="deliverableContract" required></select>
                        </label>
                        <label>
                            对应节点
                            <select id="deliverablePlan"></select>
                        </label>
                        <label>
                            交付物类型
                            <input id="deliverableType" required placeholder="如 ACCEPTANCE_REPORT">
                        </label>
                        <label>
                            交付物名称
                            <input id="deliverableName" required placeholder="请输入交付物名称">
                        </label>
                        <label>
                            所属阶段
                            <input id="deliverableStage" placeholder="如 验收阶段">
                        </label>
                        <label>
                            AI确认状态
                            <select id="deliverableConfirmStatus">
                                <option value="CONFIRMED">来源已确认</option>
                                <option value="PENDING_CONFIRM">待人工确认</option>
                            </select>
                        </label>
                        <label class="wide">
                            备注
                            <textarea id="deliverableRemark" rows="2" placeholder="可填写人工确认意见、异常说明或交付范围"></textarea>
                        </label>
                        <div class="button-row wide">
                            <button type="submit">
                                <i data-lucide="circle-check"></i>
                                保存
                            </button>
                        </div>
                    </form>
                </div>
            </div>
            <div id="deliverableFileModal" class="modal" hidden>
                <div class="modal-card">
                    <div class="modal-head">
                        <h3 id="deliverableFileModalTitle">上传交付物文件</h3>
                        <button type="button" id="closeDeliverableFileModal" class="secondary">关闭</button>
                    </div>
                    <form id="deliverableFileForm" class="form-grid">
                        <input type="hidden" id="deliverableFileId">
                        <label>
                            文件
                            <input id="deliverableFile" type="file" accept=".pdf,.jpg,.jpeg,.png,.doc,.docx,.xls,.xlsx">
                        </label>
                        <label class="wide">
                            上传备注
                            <textarea id="deliverableFileRemark" rows="2" placeholder="可填写文件版本、交付说明或补充说明"></textarea>
                        </label>
                        <div class="button-row wide">
                            <button type="submit">
                                <i data-lucide="upload"></i>
                                上传
                            </button>
                        </div>
                    </form>
                </div>
            </div>
            <div id="deliverableTransitionModal" class="modal" hidden>
                <div class="modal-card">
                    <div class="modal-head">
                        <h3 id="deliverableTransitionTitle">交付物审核</h3>
                        <button type="button" id="closeDeliverableTransitionModal" class="secondary">关闭</button>
                    </div>
                    <form id="deliverableTransitionForm" class="form-grid">
                        <input type="hidden" id="deliverableTransitionId">
                        <input type="hidden" id="deliverableTransitionAction">
                        <label class="wide">
                            审核意见
                            <textarea id="deliverableTransitionRemark" rows="3" placeholder="补充、驳回时必须填写具体原因"></textarea>
                        </label>
                        <div class="button-row wide">
                            <button type="submit">
                                <i data-lucide="circle-check"></i>
                                确认操作
                            </button>
                        </div>
                    </form>
                </div>
            </div>
        `);
    }
}

function renderPlans(list) {
    const body = $('#planTbody');
    if (!list.length) {
        body.innerHTML = '<tr><td colspan="10" class="empty-cell">暂无履约节点</td></tr>';
        return;
    }
    body.innerHTML = list.map(plan => {
        const status = normalizePlanStatus(plan.status);
        const progress = Number(plan.progress || 0);
        const warningClass = `warning-${plan.warningLevel || 'NORMAL'}`;
        const canHandle = plan.warningLevel === 'OVERDUE' || status === 'OVERDUE';
        const pendingConfirm = plan.confirmStatus === 'PENDING_CONFIRM' || status === 'PENDING_CONFIRM';
        const pendingDelay = plan.delayStatus === 'PENDING';
        const rejectedDelay = plan.delayStatus === 'REJECTED';
        const canConfirmDelay = pendingDelay && ['DEPT_LEADER', 'ADMIN'].includes(state.roleCode);
        return `
            <tr>
                <td><strong>${escapeHtml(plan.contractTitle || '-')}</strong><br><small>${escapeHtml(plan.contractNo || '')}</small></td>
                <td><strong>${escapeHtml(plan.nodeName || '-')}</strong><br><small>${escapeHtml(planTypeMap[plan.planType] || plan.planType || '-')}</small>${plan.sourceClause ? `<br><small>来源：${escapeHtml(plan.sourceClause)}</small>` : ''}</td>
                <td>
                    ${escapeHtml(plan.dueDate || (pendingConfirm ? '待确认' : '-'))}<br><small>${formatDays(plan.daysLeft)}</small>
                    ${Number(plan.overdueDays || 0) > 0 ? `<br><small>逾期 ${Number(plan.overdueDays || 0)} 天</small>` : ''}
                    ${pendingDelay ? `<br><small>延期申请：${escapeHtml(plan.delayRequestedDueDate || '-')} 待确认</small>` : ''}
                    ${pendingDelay && plan.delayReason ? `<br><small>原因：${escapeHtml(plan.delayReason)}</small>` : ''}
                </td>
                <td>${escapeHtml(status === 'COMPLETED' ? (plan.actualCompletedDate || '-') : '-')}</td>
                <td>
                    <div class="progress-cell">
                        <span>${progress}%</span>
                        <div class="progress-track"><i style="width:${progress}%"></i></div>
                    </div>
                </td>
                <td><span class="tag status-${status}">${escapeHtml(statusTextMap[status] || status || '-')}</span>${rejectedDelay ? `<br><small class="danger-link">延期驳回：${escapeHtml(plan.delayRejectReason || '-')}</small>` : ''}</td>
                <td><span class="tag ${warningClass}">${escapeHtml(warningTextMap[plan.warningLevel] || plan.warningLevel || '-')}</span></td>
                <td class="ai-meta">
                    <span class="tag ${planSourceClass(plan, pendingConfirm)}">${planSourceText(plan)}</span>
                    <small>置信度：${escapeHtml(formatConfidence(plan.aiConfidence))}</small>
                    ${pendingConfirm ? '<small class="ai-pending">待人工确认</small>' : ''}
                </td>
                <td>${escapeHtml(plan.ownerName || '-')}</td>
                <td>
                    <div class="row-actions">
                        <button class="icon-btn edit-btn" data-id="${plan.planId}" title="编辑"><i data-lucide="edit-3"></i></button>
                        <button class="icon-btn complete-btn" data-id="${plan.planId}" title="完成"><i data-lucide="circle-check"></i></button>
                        <button class="icon-btn voucher-btn" data-id="${plan.planId}" title="凭证与日志"><i data-lucide="paperclip"></i></button>
                        <button class="icon-btn delete-plan-btn" data-id="${plan.planId}" title="删除"><i data-lucide="trash-2"></i></button>
                        ${canConfirmDelay ? `<button class="link-btn confirm-delay-btn" data-id="${plan.planId}" type="button">确认延期</button>` : ''}
                        ${canHandle ? `<button class="link-btn handle-btn" data-id="${plan.planId}" type="button">处置</button>` : ''}
                    </div>
                </td>
            </tr>
        `;
    }).join('');
    body.querySelectorAll('.confirm-delay-btn').forEach(button => {
        if (!button.nextElementSibling?.classList.contains('reject-delay-btn')) {
            button.insertAdjacentHTML('afterend', `<button class="link-btn reject-delay-btn danger-link" data-id="${button.dataset.id}" type="button">驳回</button>`);
        }
    });
    renderLucideIcons();
}

function renderDeliverablesEnhanced(list) {
    ensureDeliverableUi();
    const body = $('#deliverableTbody');
    if (!list.length) {
        body.innerHTML = '<tr><td colspan="9" class="empty-cell">暂无交付物，请先生成标准台账或手工新增</td></tr>';
        return;
    }
    body.innerHTML = list.map(item => {
        const pendingConfirm = item.confirmStatus === 'PENDING_CONFIRM';
        const status = item.deliverableStatus || 'PENDING_SUBMIT';
        const editable = ['PENDING_SUBMIT', 'NEED_SUPPLEMENT', 'REJECTED'].includes(status);
        const reviewer = ['DEPT_LEADER', 'ADMIN'].includes(state.roleCode);
        const fileCell = item.downloadUrl
            ? `<button class="link-btn download-deliverable-file-btn" data-url="${escapeHtml(item.downloadUrl)}" data-name="${escapeHtml(item.fileName || item.deliverableName || 'deliverable')}" type="button">${escapeHtml(item.fileName || '下载文件')}</button>`
            : '<span class="muted-text">未上传</span>';
        return `
            <tr>
                <td>${escapeHtml(item.contractTitle || '-')}</td>
                <td><strong>${escapeHtml(item.deliverableName || '-')}</strong><br><small>${escapeHtml(item.deliverableType || '')}</small></td>
                <td>${escapeHtml(item.planName || item.stageName || '-')}<br><small>${escapeHtml(planTypeMap[item.planType] || item.planType || '')}</small></td>
                <td class="ai-meta">
                    <span class="tag ${pendingConfirm ? 'status-PENDING_CONFIRM' : 'status-COMPLETED'}">${deliverableConfirmText(item.confirmStatus)}</span>
                    <small>${deliverableSourceText(item)}</small>
                    <small>置信度：${escapeHtml(formatConfidence(item.aiConfidence))}</small>
                    ${item.sourceClause ? `<small>来源：${escapeHtml(item.sourceClause)}</small>` : ''}
                </td>
                <td>
                    <span class="tag status-${status}">${escapeHtml(deliverableStatusMap[status] || status)}</span>
                    <br><small>提交版本：${Number(item.submissionVersion || 0)}</small>
                </td>
                <td>${escapeHtml(item.submittedBy || '-')}<br><small>${item.submittedAt ? new Date(item.submittedAt).toLocaleString('zh-CN') : '尚未提交'}</small>
                    ${item.reviewerName ? `<br><small>审核：${escapeHtml(item.reviewerName)}</small>` : ''}
                    ${item.reviewComment ? `<br><small>${escapeHtml(item.reviewComment)}</small>` : ''}
                </td>
                <td>${fileCell}</td>
                <td>${escapeHtml(item.remark || '-')}</td>
                <td>
                    <div class="row-actions">
                        ${editable ? `<button class="icon-btn edit-deliverable-btn" data-id="${item.deliverableId}" title="编辑"><i data-lucide="edit-3"></i></button>
                        <button class="icon-btn upload-deliverable-file-btn" data-id="${item.deliverableId}" title="上传文件"><i data-lucide="upload"></i></button>
                        <button class="link-btn deliverable-transition-btn" data-id="${item.deliverableId}" data-action="SUBMIT" type="button">提交</button>
                        <button class="icon-btn delete-deliverable-btn" data-id="${item.deliverableId}" title="删除"><i data-lucide="trash-2"></i></button>` : ''}
                        ${status === 'SUBMITTED' && reviewer ? `<button class="link-btn deliverable-transition-btn" data-id="${item.deliverableId}" data-action="SUPPLEMENT" type="button">补充</button>
                        <button class="link-btn danger-link deliverable-transition-btn" data-id="${item.deliverableId}" data-action="REJECT" type="button">驳回</button>
                        <button class="link-btn deliverable-transition-btn" data-id="${item.deliverableId}" data-action="ACCEPT" type="button">确认</button>` : ''}
                        ${status === 'ACCEPTED' && reviewer ? `<button class="link-btn deliverable-transition-btn" data-id="${item.deliverableId}" data-action="ACCEPTANCE" type="button">验收确认</button>` : ''}
                    </div>
                </td>
            </tr>
        `;
    }).join('');
    renderLucideIcons();
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
        const canRecordPayment = !['PAID', 'WAIT_CONDITION', 'SUSPENDED'].includes(item.status) && unpaidAmount > 0;
        return `
            <tr>
                <td><strong>${escapeHtml(item.phaseName || '-')}</strong><br><small>${escapeHtml(item.contractTitle || '')}</small><br><small>${escapeHtml(item.fulfillmentNodeName || '')}</small></td>
                <td>${formatPercent(item.percentage)}<br><small>${formatMoney(item.plannedAmount)}</small></td>
                <td>${escapeHtml(item.dueDate || '-')}<br><small>${item.overdueDays > 0 ? `逾期 ${item.overdueDays} 天` : '未逾期'}</small></td>
                <td><span class="tag ${prereqClass}">${escapeHtml(paymentConditionStatusMap[item.conditionStatus] || (item.prerequisiteCompleted ? '已满足' : '待满足'))}</span><br><small>${escapeHtml(paymentConditionTypeMap[item.conditionType] || item.conditionType || '无前置条件')}</small><br><small>${escapeHtml(item.paymentCondition || item.prerequisiteDelivery || '无')}</small></td>
                <td><span class="tag payment-${item.status}">${escapeHtml(paymentStatusMap[item.status] || item.status || '-')}</span><br><small>已收 ${formatMoney(item.paidAmount)} / 未收 ${formatMoney(item.unpaidAmount)}</small></td>
                <td>${formatMoney(item.penaltyAmount)}<br><small>每日 ${formatPercent(item.penaltyRate)}</small></td>
                <td class="hint-cell">${escapeHtml(item.responsibilityHint || '-')}</td>
                <td>
                    <div class="row-actions">
                        <button class="icon-btn edit-payment-btn" data-id="${item.paymentPlanId}" title="编辑"><i data-lucide="edit-3"></i></button>
                        <button class="link-btn invoice-btn" data-id="${item.paymentPlanId}" type="button">发票</button>
                        ${canRecordPayment
                            ? `<button class="link-btn record-payment-btn" data-id="${item.paymentPlanId}" type="button">到账</button>`
                            : '<span class="tag payment-PAID">不可登记</span>'}
                        <button class="icon-btn delete-payment-plan-btn" data-id="${item.paymentPlanId}" title="删除付款计划"><i data-lucide="trash-2"></i></button>
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
            <td>${escapeHtml(item.payer || '-')}<br><small>${escapeHtml(item.bankSerialNo || '')}</small></td>
            <td>${escapeHtml(item.receiver || '-')}<br><small>${escapeHtml(item.handlerName || '')}</small></td>
            <td>${escapeHtml(item.remark || '-')}<br>${item.voucherDownloadUrl
                ? `<button class="link-btn download-payment-voucher-btn" data-url="${escapeHtml(item.voucherDownloadUrl)}" data-name="${escapeHtml(item.voucherFileName || '付款凭证')}" type="button">${escapeHtml(item.voucherFileName || '付款凭证')}</button>`
                : '<small>未上传凭证</small>'}</td>
            <td>
                <button class="icon-btn delete-payment-record-btn" data-id="${item.paymentRecordId}" title="删除到账记录">
                    <i data-lucide="trash-2"></i>
                </button>
            </td>
        </tr>
    `).join('');
    renderLucideIcons();
}

function renderInvoices(list) {
    const body = $('#invoiceTbody');
    if (!body) return;
    if (!list.length) {
        body.innerHTML = '<tr><td colspan="9" class="empty-cell">暂无发票记录</td></tr>';
        return;
    }
    body.innerHTML = list.map(item => `
        <tr>
            <td>${escapeHtml(item.contractTitle || '-')}</td>
            <td>${escapeHtml(item.phaseName || '-')}</td>
            <td>${escapeHtml(item.invoiceNo || '-')}</td>
            <td>${formatMoney(item.invoiceAmount)}</td>
            <td>${escapeHtml(item.invoiceDate || '-')}</td>
            <td><span class="tag">${escapeHtml(invoiceStatusMap[item.invoiceStatus] || item.invoiceStatus || '-')}</span></td>
            <td>${item.downloadUrl
                ? `<button class="link-btn download-invoice-file-btn" data-url="${escapeHtml(item.downloadUrl)}" data-name="${escapeHtml(item.fileName || '发票附件')}" type="button">${escapeHtml(item.fileName || '发票附件')}</button>`
                : '<small>未上传</small>'}</td>
            <td>${escapeHtml(item.remark || '-')}</td>
            <td><button class="link-btn upload-invoice-file-btn" data-id="${item.invoiceId}" type="button">上传</button></td>
        </tr>
    `).join('');
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

function renderVouchers(list) {
    const body = $('#voucherTbody');
    if (!list.length) {
        body.innerHTML = '<tr><td colspan="5" class="empty-cell">暂无履约凭证</td></tr>';
        return;
    }
    body.innerHTML = list.map(item => {
        const canReview = ['FINANCE', 'DEPT_LEADER', 'ADMIN'].includes(state.roleCode)
            && item.reviewStatus === 'PENDING_REVIEW';
        return `
            <tr>
                <td><strong>${escapeHtml(item.fileName || '-')}</strong><br><small>${escapeHtml(item.uploadedAt ? new Date(item.uploadedAt).toLocaleString('zh-CN') : '')}</small></td>
                <td>${escapeHtml(voucherTypeMap[item.voucherType] || item.voucherType || '-')}</td>
                <td><span class="tag voucher-${item.reviewStatus}">${escapeHtml(voucherReviewMap[item.reviewStatus] || item.reviewStatus || '-')}</span><br><small>${escapeHtml(item.reviewerName || '')}</small></td>
                <td>${escapeHtml(item.uploadedByName || '-')}</td>
                <td>
                    <div class="row-actions">
                        <button class="icon-btn download-voucher-btn" data-url="${escapeHtml(item.downloadUrl)}" data-name="${escapeHtml(item.fileName || 'voucher')}" title="下载"><i data-lucide="download"></i></button>
                        ${canReview ? `<button class="link-btn review-voucher-btn" data-id="${item.voucherId}" data-approved="true" type="button">通过</button>
                        <button class="link-btn review-voucher-btn danger-link" data-id="${item.voucherId}" data-approved="false" type="button">退回</button>` : ''}
                    </div>
                </td>
            </tr>
        `;
    }).join('');
    renderLucideIcons();
}

function renderProgressLogs(list) {
    const body = $('#progressLogTbody');
    if (!list.length) {
        body.innerHTML = '<tr><td colspan="5" class="empty-cell">暂无进度变更日志</td></tr>';
        return;
    }
    body.innerHTML = list.map(item => `
        <tr>
            <td>${escapeHtml(progressOperationText(item.operation))}</td>
            <td>${escapeHtml(statusTextMap[item.beforeStatus] || item.beforeStatus || '-')} → ${escapeHtml(statusTextMap[item.afterStatus] || item.afterStatus || '-')}</td>
            <td>${escapeHtml(item.operatorName || '-')}<br><small>${escapeHtml(item.clientIp || '')}</small></td>
            <td>${item.operateTime ? new Date(item.operateTime).toLocaleString('zh-CN') : '-'}</td>
            <td>${escapeHtml(item.remark || '-')}</td>
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
    const isOverduePlan = Boolean(plan)
        && (normalizePlanStatus(plan.status) === 'OVERDUE' || plan.warningLevel === 'OVERDUE');
    setActualCompletedDateLimits();
    $('#planType').value = plan?.planType || 'OTHER';
    $('#nodeName').value = plan?.nodeName || '';
    $('#dueDate').value = plan ? (plan.dueDate || '') : new Date().toISOString().slice(0, 10);
    $('#dueDate').disabled = isOverduePlan;
    $('#planStatus').value = normalizePlanStatus(plan?.status || 'NOT_STARTED');
    $('#planStatus').disabled = isOverduePlan;
    $('#actualCompletedDate').value = plan?.actualCompletedDate || '';
    $('#actualCompletedDate').disabled = isOverduePlan;
    syncActualCompletedDateField();
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
    $('#dueDate').disabled = false;
    $('#planStatus').disabled = false;
    $('#actualCompletedDate').disabled = false;
    $('#planForm').reset();
}

async function openVoucherModal(planId) {
    const plan = plans.find(item => String(item.planId) === String(planId));
    if (!plan) return;
    currentVoucherPlan = plan;
    $('#voucherPlanId').value = plan.planId;
    $('#voucherModalTitle').textContent = `履约凭证与进度日志 - ${plan.nodeName || ''}`;
    $('#voucherType').value = plan.planType === 'PAYMENT' ? 'PAYMENT' : 'PROGRESS';
    $('#voucherRemark').value = '';
    $('#voucherFile').value = '';
    $('#voucherForm').hidden = ['LEGAL', 'FINANCE'].includes(state.roleCode);
    $('#voucherModal').hidden = false;
    await reloadVoucherDetail();
    renderLucideIcons();
}

function closeVoucherModal() {
    $('#voucherModal').hidden = true;
    $('#voucherForm').reset();
    currentVoucherPlan = null;
}

function openOverdueModal(planId) {
    const plan = plans.find(item => String(item.planId) === String(planId));
    if (!plan) return;
    $('#overduePlanId').value = plan.planId;
    $('#overdueModalTitle').textContent = `逾期处置 - ${plan.nodeName || ''}`;
    $('#overdueAction').value = 'COMPLETE';
    setActualCompletedDateLimits();
    $('#overdueActualCompletedDate').value = todayString();
    $('#overdueNewPlannedDate').value = '';
    $('#overdueDelayReason').value = '';
    $('#overdueDisposalRemark').value = plan.remark || '';
    toggleOverdueFields();
    $('#overdueModal').hidden = false;
    renderLucideIcons();
}

function closeOverdueModal() {
    $('#overdueModal').hidden = true;
    $('#overdueForm').reset();
}

function toggleOverdueFields() {
    const isDelay = $('#overdueAction').value === 'DELAY';
    $$('.overdue-complete-field').forEach(item => item.hidden = isDelay);
    $$('.overdue-delay-field').forEach(item => item.hidden = !isDelay);
    $('#overdueHandleHint').textContent = isDelay
        ? '申请延期后会生成延期审批记录，并推送部门主管审批；审批通过前节点仍保持逾期。'
        : '标记完成前请先在“凭证/日志”中上传完成凭证；付款节点需上传付款凭证并复核通过。';
}

function overdueHandlePayload() {
    const action = $('#overdueAction').value;
    const actualCompletedDate = action === 'COMPLETE' ? ($('#overdueActualCompletedDate').value || null) : null;
    assertActualCompletedDateNotFuture(actualCompletedDate);
    return {
        action,
        actualCompletedDate,
        newPlannedDate: action === 'DELAY' ? ($('#overdueNewPlannedDate').value || null) : null,
        delayReason: action === 'DELAY' ? $('#overdueDelayReason').value.trim() : '',
        disposalRemark: $('#overdueDisposalRemark').value.trim()
    };
}

async function reloadVoucherDetail() {
    const planId = $('#voucherPlanId').value;
    if (!planId) return;
    const [vouchers, logs] = await Promise.all([
        api(`/api/fulfillment/vouchers?planId=${planId}`),
        api(`/api/fulfillment/progress-logs?planId=${planId}`)
    ]);
    renderVouchers(vouchers);
    renderProgressLogs(logs);
}

function planPayload(plan = null, overrides = {}) {
    const actualCompletedDate = plan ? (plan.actualCompletedDate || null) : ($('#actualCompletedDate').value || null);
    const payload = {
        contractId: plan ? plan.contractId : Number($('#planContract').value),
        nodeName: plan ? plan.nodeName : $('#nodeName').value.trim(),
        planType: plan ? plan.planType : $('#planType').value,
        dueDate: plan ? plan.dueDate : ($('#dueDate').value || null),
        status: plan ? normalizePlanStatus(plan.status) : $('#planStatus').value,
        progress: plan ? plan.progress : Number($('#progressValue').value || 0),
        actualCompletedDate,
        ownerName: plan ? plan.ownerName : $('#ownerName').value.trim(),
        remark: plan ? plan.remark : $('#planRemark').value.trim(),
        ...overrides
    };
    payload.status = normalizePlanStatus(payload.status);
    if (payload.status !== 'COMPLETED') {
        payload.actualCompletedDate = null;
    }
    assertActualCompletedDateNotFuture(payload.actualCompletedDate);
    return payload;
}

function fillDeliverableContractSelect(selectedValue = '') {
    const select = $('#deliverableContract');
    if (!select) return;
    select.innerHTML = contracts.map(contractOption).join('') || '<option value="">暂无合同</option>';
    select.value = selectedValue || currentFilterContractId() || contracts[0]?.contractId || '';
}

function fillDeliverablePlanSelect(contractId, selectedValue = '') {
    const select = $('#deliverablePlan');
    if (!select) return;
    const filtered = plans.filter(plan => !contractId || String(plan.contractId) === String(contractId));
    select.innerHTML = '<option value="">不绑定节点</option>' + filtered.map(plan => {
        const text = `${plan.nodeName || `节点${plan.planId}`} ${plan.planType ? `(${planTypeMap[plan.planType] || plan.planType})` : ''}`;
        return `<option value="${plan.planId}">${escapeHtml(text)}</option>`;
    }).join('');
    select.value = selectedValue || '';
}

function openDeliverableModal(item = null) {
    ensureDeliverableUi();
    $('#deliverableModalTitle').textContent = item ? '编辑交付物' : '新增交付物';
    $('#deliverableId').value = item?.deliverableId || '';
    fillDeliverableContractSelect(item?.contractId || '');
    $('#deliverableContract').disabled = Boolean(item);
    fillDeliverablePlanSelect($('#deliverableContract').value, item?.planId || '');
    $('#deliverableType').value = item?.deliverableType || 'OTHER';
    $('#deliverableName').value = item?.deliverableName || '';
    $('#deliverableStage').value = item?.stageName || '';
    $('#deliverableConfirmStatus').value = item?.confirmStatus || 'CONFIRMED';
    $('#deliverableRemark').value = item?.remark || '';
    $('#deliverableModal').hidden = false;
    renderLucideIcons();
}

function closeDeliverableModal() {
    $('#deliverableModal').hidden = true;
    $('#deliverableContract').disabled = false;
    $('#deliverableForm').reset();
}

function deliverablePayload() {
    const planId = $('#deliverablePlan').value;
    return {
        contractId: Number($('#deliverableContract').value),
        planId: planId ? Number(planId) : null,
        deliverableType: $('#deliverableType').value.trim(),
        deliverableName: $('#deliverableName').value.trim(),
        stageName: $('#deliverableStage').value.trim(),
        confirmStatus: $('#deliverableConfirmStatus').value,
        confirmed: null,
        acceptancePassed: null,
        remark: $('#deliverableRemark').value.trim()
    };
}

async function saveDeliverable(event) {
    event.preventDefault();
    const id = $('#deliverableId').value;
    const url = id ? `/api/fulfillment/deliverables/${id}` : '/api/fulfillment/deliverables';
    await api(url, {method: id ? 'PUT' : 'POST', body: JSON.stringify(deliverablePayload())});
    closeDeliverableModal();
    await refreshAll();
    toast(id ? '交付物已更新' : '交付物已新增');
}

function openDeliverableFileModal(id) {
    const item = deliverables.find(row => String(row.deliverableId) === String(id));
    if (!item) return;
    ensureDeliverableUi();
    $('#deliverableFileId').value = item.deliverableId;
    $('#deliverableFileModalTitle').textContent = `上传交付物文件 - ${item.deliverableName || ''}`;
    $('#deliverableFile').value = '';
    $('#deliverableFileRemark').value = item.remark || '';
    $('#deliverableFileModal').hidden = false;
    renderLucideIcons();
}

function closeDeliverableFileModal() {
    $('#deliverableFileModal').hidden = true;
    $('#deliverableFileForm').reset();
}

function openDeliverableTransitionModal(id, action) {
    const item = deliverables.find(row => String(row.deliverableId) === String(id));
    if (!item) return;
    $('#deliverableTransitionId').value = id;
    $('#deliverableTransitionAction').value = action;
    $('#deliverableTransitionRemark').value = '';
    $('#deliverableTransitionTitle').textContent =
        `${deliverableTransitionText[action] || '处理'} - ${item.deliverableName || ''}`;
    $('#deliverableTransitionRemark').required = ['SUPPLEMENT', 'REJECT'].includes(action);
    $('#deliverableTransitionModal').hidden = false;
    renderLucideIcons();
}

function closeDeliverableTransitionModal() {
    $('#deliverableTransitionModal').hidden = true;
    $('#deliverableTransitionForm').reset();
}

async function saveDeliverableTransition(event) {
    event.preventDefault();
    const id = $('#deliverableTransitionId').value;
    const action = $('#deliverableTransitionAction').value;
    const remark = $('#deliverableTransitionRemark').value.trim();
    if (['SUPPLEMENT', 'REJECT'].includes(action) && !remark) {
        return toast(action === 'SUPPLEMENT' ? '请填写需要补充的内容' : '请填写驳回原因');
    }
    await api(`/api/fulfillment/deliverables/${id}/transition`, {
        method: 'POST',
        body: JSON.stringify({action, remark})
    });
    closeDeliverableTransitionModal();
    await refreshAll();
    toast(`${deliverableTransitionText[action] || '操作'}已完成`);
}

async function uploadDeliverableFile(event) {
    event.preventDefault();
    const id = $('#deliverableFileId').value;
    const file = $('#deliverableFile').files[0];
    if (!id) return;
    if (!file) return toast('请先选择交付物文件');
    const formData = new FormData();
    formData.append('file', file);
    formData.append('remark', $('#deliverableFileRemark').value.trim());
    await uploadApi(`/api/fulfillment/deliverables/${id}/file`, formData);
    closeDeliverableFileModal();
    await refreshAll();
    toast('交付物文件已上传');
}

async function deleteDeliverable(id) {
    const item = deliverables.find(row => String(row.deliverableId) === String(id));
    const name = item?.deliverableName || '该交付物';
    if (!window.confirm(`确认删除“${name}”吗？`)) return;
    await api(`/api/fulfillment/deliverables/${id}`, {method: 'DELETE'});
    await refreshAll();
    toast('交付物已删除');
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
    $('#paymentPayee').value = plan?.payee || '';
    $('#paymentConditionType').value = plan?.conditionType || 'NONE';
    $('#paymentCondition').value = plan?.paymentCondition || '';
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
        payee: $('#paymentPayee').value.trim(),
        conditionType: $('#paymentConditionType').value,
        paymentCondition: $('#paymentCondition').value.trim(),
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
    $('#bankSerialNo').value = '';
    $('#paymentHandler').value = state.username || '';
    $('#payer').value = '甲方';
    $('#receiver').value = plan?.payee || '乙方';
    $('#paymentRecordRemark').value = '';
    $('#paymentVoucherFile').value = '';
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
    const saved = await api(url, {method: id ? 'PUT' : 'POST', body: JSON.stringify(planPayload())});
    closePlanModal();
    await refreshAll();
    toast(saved?.delayStatus === 'PENDING' ? '延期申请已提交，待部门主管确认' : (id ? '履约节点已更新' : '履约节点已新增'));
}

async function uploadVoucher(event) {
    event.preventDefault();
    const planId = $('#voucherPlanId').value;
    const file = $('#voucherFile').files[0];
    if (!planId) return;
    if (!file) return toast('请先选择履约凭证文件');
    const formData = new FormData();
    formData.append('file', file);
    formData.append('voucherType', $('#voucherType').value);
    formData.append('remark', $('#voucherRemark').value.trim());
    await uploadApi(`/api/fulfillment/plans/${planId}/vouchers`, formData);
    $('#voucherFile').value = '';
    $('#voucherRemark').value = '';
    await Promise.all([reloadVoucherDetail(), refreshAll()]);
    toast('履约凭证已上传');
}

async function reviewVoucher(id, approved) {
    const remark = $('#voucherRemark').value.trim();
    const params = new URLSearchParams({approved: String(approved)});
    if (remark) params.set('remark', remark);
    await api(`/api/fulfillment/vouchers/${id}/review?${params}`, {method: 'POST'});
    $('#voucherRemark').value = '';
    await reloadVoucherDetail();
    toast(approved ? '凭证复核通过' : '凭证已退回');
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
    const saved = await api(`/api/fulfillment/payments/plans/${id}/records`, {
        method: 'POST',
        body: JSON.stringify({
            paidAmount: Number($('#paidAmount').value || 0),
            paidDate: $('#paidDate').value,
            bankSerialNo: $('#bankSerialNo').value.trim(),
            handlerName: $('#paymentHandler').value.trim(),
            payer: $('#payer').value.trim(),
            receiver: $('#receiver').value.trim(),
            remark: $('#paymentRecordRemark').value.trim()
        })
    });
    const voucherFile = $('#paymentVoucherFile').files[0];
    if (voucherFile && saved?.paymentRecordId) {
        const formData = new FormData();
        formData.append('file', voucherFile);
        formData.append('remark', $('#paymentRecordRemark').value.trim());
        await uploadApi(`/api/fulfillment/payments/records/${saved.paymentRecordId}/voucher`, formData);
    }
    closePaymentRecordModal();
    await refreshAll();
    toast('到账记录已保存');
}

async function createInvoiceForPlan(id) {
    const plan = paymentPlans.find(item => String(item.paymentPlanId) === String(id));
    const invoiceNo = window.prompt('请输入发票号码');
    if (!invoiceNo || !invoiceNo.trim()) return;
    const amountText = window.prompt('请输入发票金额', String(plan?.unpaidAmount || plan?.plannedAmount || ''));
    if (!amountText) return;
    await api(`/api/fulfillment/payments/plans/${id}/invoices`, {
        method: 'POST',
        body: JSON.stringify({
            invoiceNo: invoiceNo.trim(),
            invoiceAmount: Number(amountText || 0),
            invoiceDate: todayString(),
            invoiceStatus: 'VERIFIED',
            remark: '前端快速登记'
        })
    });
    await refreshAll();
    toast('发票记录已保存');
}

async function uploadInvoiceFile(id) {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.pdf,.jpg,.jpeg,.png,.doc,.docx,.xls,.xlsx';
    input.onchange = async () => {
        const file = input.files[0];
        if (!file) return;
        const formData = new FormData();
        formData.append('file', file);
        await uploadApi(`/api/fulfillment/payments/invoices/${id}/file`, formData);
        await refreshAll();
        toast('发票附件已上传');
    };
    input.click();
}

async function deletePaymentRecord(id) {
    if (!window.confirm('确认删除这条到账记录吗？删除后付款计划状态会重新计算。')) return;
    await api(`/api/fulfillment/payments/records/${id}`, {method: 'DELETE'});
    await refreshAll();
    toast('到账记录已删除');
}

async function deletePaymentPlan(id) {
    const plan = paymentPlans.find(item => String(item.paymentPlanId) === String(id));
    const phaseName = plan?.phaseName ? `“${plan.phaseName}”` : '该';
    if (!window.confirm(`确认删除${phaseName}付款计划吗？已有到账记录或发票材料的计划不能删除。`)) return;
    await api(`/api/fulfillment/payments/plans/${id}`, {method: 'DELETE'});
    await refreshAll();
    toast('付款计划已删除');
}

let fulfillmentGenerationBusy = false;

async function runFulfillmentGeneration(buttonId, busyText, action) {
    if (fulfillmentGenerationBusy) {
        return toast('履约节点正在生成，请勿重复提交');
    }
    fulfillmentGenerationBusy = true;
    const buttons = [$('#initMemberEBtn'), $('#extractBtn')].filter(Boolean);
    const activeButton = $(buttonId);
    const originalHtml = activeButton?.innerHTML || '';
    buttons.forEach(button => {
        button.disabled = true;
    });
    if (activeButton) {
        activeButton.textContent = busyText;
    }
    try {
        return await action();
    } finally {
        fulfillmentGenerationBusy = false;
        buttons.forEach(button => {
            button.disabled = false;
        });
        if (activeButton) {
            activeButton.innerHTML = originalHtml;
        }
        renderLucideIcons();
    }
}

async function initializeMemberE() {
    const contractId = selectedContractId('#initContract');
    if (!contractId) return toast('请先选择合同');
    return runFulfillmentGeneration('#initMemberEBtn', '生成中...', async () => {
        await api(`/api/fulfillment/contracts/${contractId}/member-e/init`, {method: 'POST'});
        $('#filterContract').value = String(contractId);
        await refreshAll();
        toast('已生成交付物和付款台账');
    });
}

async function extractPlans() {
    const contractId = selectedContractId('#extractContract');
    if (!contractId) return toast('请先选择合同');
    return runFulfillmentGeneration('#extractBtn', '抽取中...', async () => {
        await api(`/api/fulfillment/plans/extract/${contractId}`, {method: 'POST'});
        $('#filterContract').value = String(contractId);
        await refreshAll();
        toast('履约节点已抽取');
    });
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
    if (plan.warningLevel === 'OVERDUE' || normalizePlanStatus(plan.status) === 'OVERDUE') {
        openOverdueModal(id);
        return;
    }
    await api(`/api/fulfillment/plans/${id}`, {
        method: 'PUT',
        body: JSON.stringify(planPayload(plan, {
            status: 'COMPLETED',
            progress: 100,
            actualCompletedDate: todayString()
        }))
    });
    await refreshAll();
    toast('节点已完成');
}

async function handleOverdue(id) {
    openOverdueModal(id);
    return;
    await api(`/api/fulfillment/plans/${id}/handle-overdue`, {method: 'POST'});
    await refreshAll();
    toast('逾期节点已处置');
}

async function saveOverdueHandle(event) {
    event.preventDefault();
    const id = $('#overduePlanId').value;
    if (!id) return;
    const saved = await api(`/api/fulfillment/plans/${id}/handle-overdue`, {
        method: 'POST',
        body: JSON.stringify(overdueHandlePayload())
    });
    closeOverdueModal();
    await refreshAll();
    toast(saved?.delayStatus === 'PENDING' ? '延期申请已提交，等待部门主管审批' : '逾期节点已标记完成');
}

async function confirmDelay(id) {
    await api(`/api/fulfillment/plans/${id}/delay/confirm`, {method: 'POST'});
    await refreshAll();
    toast('延期申请已确认');
}

async function rejectDelay(id) {
    const reason = window.prompt('请输入延期驳回原因');
    if (!reason || !reason.trim()) return;
    const approvals = await api(`/api/fulfillment/delay-approvals?planId=${id}&status=PENDING`);
    if (!approvals.length) {
        toast('没有待审批的延期申请');
        return;
    }
    await api(`/api/fulfillment/delay-approvals/${approvals[0].approvalId}/review`, {
        method: 'POST',
        body: JSON.stringify({approved: false, remark: reason.trim()})
    });
    await refreshAll();
    toast('延期申请已驳回');
}

async function deletePlan(id) {
    const plan = plans.find(item => String(item.planId) === String(id));
    const name = plan?.nodeName || '该履约节点';
    if (!window.confirm(`确认删除“${name}”吗？删除后该节点的提醒记录也会移除。`)) return;
    await api(`/api/fulfillment/plans/${id}`, {method: 'DELETE'});
    await refreshAll();
    toast('履约节点已删除');
}

let keywordTimer;
ensureDeliverableUi();
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
$('#newDeliverableBtn').addEventListener('click', () => openDeliverableModal());
$('#newPaymentPlanBtn').addEventListener('click', () => openPaymentPlanModal());
$('#closePlanModal').addEventListener('click', closePlanModal);
$('#closeDeliverableModal').addEventListener('click', closeDeliverableModal);
$('#closeDeliverableFileModal').addEventListener('click', closeDeliverableFileModal);
$('#closeDeliverableTransitionModal').addEventListener('click', closeDeliverableTransitionModal);
$('#closeVoucherModal').addEventListener('click', closeVoucherModal);
$('#closeOverdueModal').addEventListener('click', closeOverdueModal);
$('#closePaymentPlanModal').addEventListener('click', closePaymentPlanModal);
$('#closePaymentRecordModal').addEventListener('click', closePaymentRecordModal);
$('#planForm').addEventListener('submit', event => savePlan(event).catch(error => toast(error.message)));
$('#deliverableForm').addEventListener('submit', event => saveDeliverable(event).catch(error => toast(error.message)));
$('#deliverableFileForm').addEventListener('submit', event => uploadDeliverableFile(event).catch(error => toast(error.message)));
$('#deliverableTransitionForm').addEventListener('submit', event => saveDeliverableTransition(event).catch(error => toast(error.message)));
$('#voucherForm').addEventListener('submit', event => uploadVoucher(event).catch(error => toast(error.message)));
$('#overdueForm').addEventListener('submit', event => saveOverdueHandle(event).catch(error => toast(error.message)));
$('#paymentPlanForm').addEventListener('submit', event => savePaymentPlan(event).catch(error => toast(error.message)));
$('#paymentRecordForm').addEventListener('submit', event => savePaymentRecord(event).catch(error => toast(error.message)));
$('#paymentContract').addEventListener('change', () => {
    renderPaymentRatioCheck();
    loadPrerequisiteDeliveryOptions($('#paymentContract').value)
        .catch(error => toast(error.message));
});
$('#deliverableContract').addEventListener('change', () => fillDeliverablePlanSelect($('#deliverableContract').value));
$('#percentage').addEventListener('input', renderPaymentRatioCheck);
$('#initMemberEBtn').addEventListener('click', () => initializeMemberE().catch(error => toast(error.message)));
$('#extractBtn').addEventListener('click', () => extractPlans().catch(error => toast(error.message)));
$('#dispatchBtn').addEventListener('click', () => dispatchReminders().catch(error => toast(error.message)));
$('#refreshReminderBtn').addEventListener('click', () => refreshAll().catch(error => toast(error.message)));
$('#overdueAction').addEventListener('change', toggleOverdueFields);
$('#planStatus').addEventListener('change', syncActualCompletedDateField);
$('#progressRange').addEventListener('input', () => $('#progressValue').value = $('#progressRange').value);
$('#progressValue').addEventListener('input', () => $('#progressRange').value = $('#progressValue').value);

$('#planTbody').addEventListener('click', event => {
    const editBtn = event.target.closest('.edit-btn');
    const completeBtn = event.target.closest('.complete-btn');
    const voucherBtn = event.target.closest('.voucher-btn');
    const deleteBtn = event.target.closest('.delete-plan-btn');
    const confirmDelayBtn = event.target.closest('.confirm-delay-btn');
    const rejectDelayBtn = event.target.closest('.reject-delay-btn');
    const handleBtn = event.target.closest('.handle-btn');
    if (editBtn) {
        const plan = plans.find(item => String(item.planId) === String(editBtn.dataset.id));
        if (plan) openPlanModal(plan);
    }
    if (completeBtn) completePlan(completeBtn.dataset.id).catch(error => toast(error.message));
    if (voucherBtn) openVoucherModal(voucherBtn.dataset.id).catch(error => toast(error.message));
    if (deleteBtn) deletePlan(deleteBtn.dataset.id).catch(error => toast(error.message));
    if (confirmDelayBtn) confirmDelay(confirmDelayBtn.dataset.id).catch(error => toast(error.message));
    if (rejectDelayBtn) rejectDelay(rejectDelayBtn.dataset.id).catch(error => toast(error.message));
    if (handleBtn) handleOverdue(handleBtn.dataset.id).catch(error => toast(error.message));
});

$('#voucherTbody').addEventListener('click', event => {
    const downloadBtn = event.target.closest('.download-voucher-btn');
    const reviewBtn = event.target.closest('.review-voucher-btn');
    if (downloadBtn) {
        downloadFile(downloadBtn.dataset.url, downloadBtn.dataset.name || 'voucher')
            .catch(error => toast(error.message));
    }
    if (reviewBtn) {
        reviewVoucher(reviewBtn.dataset.id, reviewBtn.dataset.approved === 'true')
            .catch(error => toast(error.message));
    }
});

$('#deliverableTbody').addEventListener('click', event => {
    const editBtn = event.target.closest('.edit-deliverable-btn');
    const uploadBtn = event.target.closest('.upload-deliverable-file-btn');
    const deleteBtn = event.target.closest('.delete-deliverable-btn');
    const downloadBtn = event.target.closest('.download-deliverable-file-btn');
    const transitionBtn = event.target.closest('.deliverable-transition-btn');
    if (editBtn) {
        const item = deliverables.find(row => String(row.deliverableId) === String(editBtn.dataset.id));
        if (item) openDeliverableModal(item);
    }
    if (uploadBtn) openDeliverableFileModal(uploadBtn.dataset.id);
    if (deleteBtn) deleteDeliverable(deleteBtn.dataset.id).catch(error => toast(error.message));
    if (transitionBtn) openDeliverableTransitionModal(transitionBtn.dataset.id, transitionBtn.dataset.action);
    if (downloadBtn) {
        downloadFile(downloadBtn.dataset.url, downloadBtn.dataset.name || 'deliverable')
            .catch(error => toast(error.message));
    }
});

$('#paymentPlanTbody').addEventListener('click', event => {
    const editBtn = event.target.closest('.edit-payment-btn');
    const recordBtn = event.target.closest('.record-payment-btn');
    const invoiceBtn = event.target.closest('.invoice-btn');
    const deleteBtn = event.target.closest('.delete-payment-plan-btn');
    if (editBtn) {
        const plan = paymentPlans.find(item => String(item.paymentPlanId) === String(editBtn.dataset.id));
        if (plan) openPaymentPlanModal(plan);
    }
    if (recordBtn) openPaymentRecordModal(recordBtn.dataset.id);
    if (invoiceBtn) createInvoiceForPlan(invoiceBtn.dataset.id).catch(error => toast(error.message));
    if (deleteBtn) deletePaymentPlan(deleteBtn.dataset.id).catch(error => toast(error.message));
});

$('#paymentRecordTbody').addEventListener('click', event => {
    const deleteBtn = event.target.closest('.delete-payment-record-btn');
    const downloadBtn = event.target.closest('.download-payment-voucher-btn');
    if (deleteBtn) deletePaymentRecord(deleteBtn.dataset.id).catch(error => toast(error.message));
    if (downloadBtn) {
        downloadFile(downloadBtn.dataset.url, downloadBtn.dataset.name || '付款凭证')
            .catch(error => toast(error.message));
    }
});

$('#invoiceTbody')?.addEventListener('click', event => {
    const uploadBtn = event.target.closest('.upload-invoice-file-btn');
    const downloadBtn = event.target.closest('.download-invoice-file-btn');
    if (uploadBtn) uploadInvoiceFile(uploadBtn.dataset.id).catch(error => toast(error.message));
    if (downloadBtn) {
        downloadFile(downloadBtn.dataset.url, downloadBtn.dataset.name || '发票附件')
            .catch(error => toast(error.message));
    }
});

loadContracts()
    .then(refreshAll)
    .catch(error => toast(error.message));

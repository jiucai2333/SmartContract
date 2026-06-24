const e=`if (!initAppShell('fulfillment', '履约与付款', '履约计划、交付物确认、付款台账与逾期责任提示')) {\r
    throw new Error('auth required');\r
}\r
\r
const statusTextMap = {\r
    NOT_STARTED: '待开始',\r
    ACTIVE: '待开始',\r
    TODO: '待开始',\r
    PENDING_CONFIRM: '待人工确认',\r
    IN_PROGRESS: '进行中',\r
    COMPLETED: '已完成',\r
    OVERDUE: '已逾期',\r
    CLOSED: '已关闭',\r
    HANDLED: '已关闭'\r
};\r
const voucherTypeMap = {\r
    PROGRESS: '进度凭证',\r
    COMPLETION: '完成凭证',\r
    EXCEPTION: '异常说明',\r
    PAYMENT: '付款凭证'\r
};\r
const voucherReviewMap = {\r
    PENDING_REVIEW: '待复核',\r
    APPROVED: '复核通过',\r
    REJECTED: '复核退回',\r
    NOT_REQUIRED: '无需复核'\r
};\r
const operationTextMap = {\r
    CREATE: '创建',\r
    UPDATE: '更新',\r
    DELAY_REQUEST: '延期申请',\r
    DELETE: '删除',\r
    DELAY_CONFIRM: '延期确认',\r
    CLOSE_OVERDUE: '逾期关闭',\r
    VOUCHER_UPLOAD: '凭证上传',\r
    VOUCHER_REVIEW: '凭证审核',\r
    MARK_OVERDUE: '标记逾期'\r
};\r
Object.assign(operationTextMap, {\r
    DELAY_APPROVED: '延期审批通过',\r
    DELAY_REJECTED: '延期审批驳回',\r
    OVERDUE_HANDLE: '逾期处置',\r
    FULFILLMENT_PLAN_CREATE: '创建履约节点',\r
    FULFILLMENT_PLAN_UPDATE: '更新履约节点',\r
    FULFILLMENT_PLAN_DELAY_REQUEST: '履约节点延期申请',\r
    FULFILLMENT_PLAN_DELAY_CONFIRM: '履约节点延期确认',\r
    FULFILLMENT_PLAN_OVERDUE_HANDLE: '履约节点逾期处置',\r
    FULFILLMENT_PLAN_OVERDUE_COMPLETE: '履约节点逾期标记完成',\r
    FULFILLMENT_PLAN_OVERDUE_DELAY_REQUEST: '履约节点逾期申请延期',\r
    FULFILLMENT_DELAY_APPROVE: '延期审批通过',\r
    FULFILLMENT_DELAY_REJECT: '延期审批驳回',\r
    FULFILLMENT_VOUCHER_UPLOAD: '凭证上传',\r
    FULFILLMENT_VOUCHER_REVIEW: '凭证审核',\r
    FULFILLMENT_DELIVERABLE_CREATE: '新增交付物',\r
    FULFILLMENT_DELIVERABLE_UPDATE: '编辑交付物',\r
    FULFILLMENT_DELIVERABLE_CONFIRM: '确认交付物',\r
    FULFILLMENT_DELIVERABLE_UNCONFIRM: '取消确认交付物',\r
    FULFILLMENT_DELIVERABLE_DELETE: '删除交付物',\r
    FULFILLMENT_DELIVERABLE_FILE_UPLOAD: '上传交付物文件',\r
    FULFILLMENT_DELIVERABLE_SUBMIT: '提交交付物',\r
    FULFILLMENT_DELIVERABLE_SUPPLEMENT: '要求补充交付物',\r
    FULFILLMENT_DELIVERABLE_REJECT: '驳回交付物',\r
    FULFILLMENT_DELIVERABLE_ACCEPT: '确认交付物',\r
    FULFILLMENT_DELIVERABLE_ACCEPTANCE: '交付物验收确认',\r
    OVERDUE_COMPLETE: '逾期标记完成',\r
    OVERDUE_DELAY_REQUEST: '逾期申请延期',\r
    DELAY_APPROVE: '延期审批通过',\r
    DELAY_REJECT: '延期审批驳回'\r
});\r
\r
function progressOperationText(operation) {\r
    const value = String(operation || '').trim();\r
    if (!value) return '-';\r
    if (operationTextMap[value]) return operationTextMap[value];\r
    const upper = value.toUpperCase();\r
    if (operationTextMap[upper]) return operationTextMap[upper];\r
    if (!/^[A-Z0-9_]+$/.test(value)) return value;\r
    if (value.startsWith('FULFILLMENT_DELIVERABLE')) return '交付物操作';\r
    if (value.startsWith('FULFILLMENT_PLAN')) return '履约节点操作';\r
    if (value.startsWith('FULFILLMENT_VOUCHER') || value.startsWith('VOUCHER')) return '凭证操作';\r
    if (value.startsWith('OVERDUE')) return '逾期处置';\r
    if (value.startsWith('DELAY')) return '延期处理';\r
    return '系统操作';\r
}\r
\r
const planTypeMap = {\r
    PREPARE: '材料准备',\r
    CHECK: '进度确认',\r
    ACCEPTANCE: '验收交付',\r
    PAYMENT: '付款节点',\r
    DELIVERY: '交付节点',\r
    WARRANTY: '质保节点',\r
    RENEWAL: '续签节点',\r
    TERMINATION: '终止节点',\r
    INVOICE: '发票节点',\r
    CONFIDENTIALITY: '保密期限',\r
    OTHER: '其他'\r
};\r
const warningTextMap = {\r
    NORMAL: '正常',\r
    NONE: '已关闭',\r
    LEVEL1: '一级预警',\r
    LEVEL2: '二级预警',\r
    LEVEL3: '三级预警',\r
    OVERDUE: '已逾期'\r
};\r
const paymentStatusMap = {\r
    UNPAID: '未付款',\r
    PARTIAL: '部分到账',\r
    WAIT_CONDITION: '待条件满足',\r
    READY_TO_PAY: '待付款',\r
    PARTIAL_PAID: '部分付款',\r
    PAID: '已到账',\r
    OVERDUE: '已逾期',\r
    SUSPENDED: '已挂起'\r
};\r
const paymentConditionTypeMap = {\r
    NONE: '无前置条件',\r
    DELIVERABLE: '交付确认后付款',\r
    ACCEPTANCE: '验收确认后付款',\r
    INVOICE: '发票齐全后付款'\r
};\r
const deliverableStatusMap = {\r
    PENDING_SUBMIT: '待提交',\r
    SUBMITTED: '已提交',\r
    NEED_SUPPLEMENT: '待补充',\r
    REJECTED: '已驳回',\r
    ACCEPTED: '已确认',\r
    ACCEPTANCE_PASSED: '验收确认'\r
};\r
const deliverableTransitionText = {\r
    SUBMIT: '提交',\r
    SUPPLEMENT: '要求补充',\r
    REJECT: '驳回',\r
    ACCEPT: '确认',\r
    ACCEPTANCE: '验收确认'\r
};\r
const paymentConditionStatusMap = {\r
    SATISFIED: '已满足',\r
    PENDING: '待满足'\r
};\r
const invoiceStatusMap = {\r
    DRAFT: '草稿',\r
    RECEIVED: '已收票',\r
    VERIFIED: '已核验',\r
    VALID: '有效',\r
    INVALID: '无效',\r
    VOID: '已作废'\r
};\r
const channelTextMap = {\r
    IN_APP: '站内消息',\r
    EMAIL: '邮件',\r
    WECHAT: '企业微信',\r
    SMS: '短信'\r
};\r
const PAYMENT_RATIO_TARGET = 100;\r
\r
let contracts = [];\r
let plans = [];\r
let deliverables = [];\r
let paymentPlans = [];\r
let paymentRecords = [];\r
let invoiceRecords = [];\r
let currentVoucherPlan = null;\r
\r
function selectedContractId(selector) {\r
    const value = $(selector).value;\r
    return value ? Number(value) : null;\r
}\r
\r
function currentFilterContractId() {\r
    return selectedContractId('#filterContract');\r
}\r
\r
function todayString() {\r
    const now = new Date();\r
    const month = String(now.getMonth() + 1).padStart(2, '0');\r
    const day = String(now.getDate()).padStart(2, '0');\r
    return \`\${now.getFullYear()}-\${month}-\${day}\`;\r
}\r
\r
function setActualCompletedDateLimits() {\r
    const today = todayString();\r
    ['#actualCompletedDate', '#overdueActualCompletedDate'].forEach(selector => {\r
        const input = $(selector);\r
        if (input) input.max = today;\r
    });\r
}\r
\r
function assertActualCompletedDateNotFuture(value) {\r
    if (value && value > todayString()) {\r
        throw new Error('\\u5b9e\\u9645\\u5b8c\\u6210\\u65e5\\u671f\\u4e0d\\u80fd\\u665a\\u4e8e\\u4eca\\u5929');\r
    }\r
}\r
\r
function syncActualCompletedDateField() {\r
    const input = $('#actualCompletedDate');\r
    const field = input?.closest('label');\r
    const completed = normalizePlanStatus($('#planStatus').value) === 'COMPLETED';\r
    if (field) field.hidden = !completed;\r
    if (!completed && input) input.value = '';\r
    if (input) input.disabled = !completed || $('#planStatus').disabled;\r
}\r
\r
function contractOption(contract) {\r
    const title = contract.title || contract.contractNo || \`合同\${contract.contractId}\`;\r
    return \`<option value="\${contract.contractId}">\${escapeHtml(title)}</option>\`;\r
}\r
\r
function fillContractSelects() {\r
    const allOptions = '<option value="">全部合同</option>' + contracts.map(contractOption).join('');\r
    const requiredOptions = contracts.map(contractOption).join('') || '<option value="">暂无合同</option>';\r
    $('#filterContract').innerHTML = allOptions;\r
    $('#extractContract').innerHTML = requiredOptions;\r
    $('#initContract').innerHTML = requiredOptions;\r
    $('#planContract').innerHTML = requiredOptions;\r
    $('#paymentContract').innerHTML = requiredOptions;\r
}\r
\r
async function loadContracts() {\r
    contracts = await api('/api/contracts');\r
    fillContractSelects();\r
}\r
\r
function endpointWithContract(path) {\r
    const params = new URLSearchParams();\r
    const contractId = currentFilterContractId();\r
    if (contractId) params.set('contractId', String(contractId));\r
    return \`\${path}?\${params}\`;\r
}\r
\r
async function loadPlans() {\r
    const params = new URLSearchParams();\r
    const contractId = currentFilterContractId();\r
    if (contractId) params.set('contractId', String(contractId));\r
    if ($('#filterStatus').value) params.set('status', $('#filterStatus').value);\r
    if ($('#planKeyword').value.trim()) params.set('keyword', $('#planKeyword').value.trim());\r
    plans = await api(\`/api/fulfillment/plans?\${params}\`);\r
    renderPlans(plans);\r
}\r
\r
async function loadDeliverables() {\r
    deliverables = await api(endpointWithContract('/api/fulfillment/deliverables'));\r
    renderDeliverablesEnhanced(deliverables);\r
}\r
\r
async function loadPaymentPlans() {\r
    paymentPlans = await api(endpointWithContract('/api/fulfillment/payments/plans'));\r
    renderPaymentPlans(paymentPlans);\r
}\r
\r
async function loadPaymentRecords() {\r
    paymentRecords = await api(endpointWithContract('/api/fulfillment/payments/records'));\r
    renderPaymentRecords(paymentRecords);\r
}\r
\r
async function loadInvoices() {\r
    invoiceRecords = await api(endpointWithContract('/api/fulfillment/payments/invoices'));\r
    renderInvoices(invoiceRecords);\r
}\r
\r
async function loadReminders() {\r
    const list = await api(endpointWithContract('/api/fulfillment/reminders'));\r
    renderReminders(list);\r
    return list;\r
}\r
\r
async function refreshAll() {\r
    const statPromise = api(endpointWithContract('/api/fulfillment/stats'));\r
    const reminderPromise = loadReminders();\r
    await Promise.all([loadPlans(), loadDeliverables(), loadPaymentPlans(), loadPaymentRecords(), loadInvoices()]);\r
    const [stat, reminders] = await Promise.all([statPromise, reminderPromise]);\r
    renderStats(stat, reminders);\r
}\r
\r
function renderStats(stat, reminders = []) {\r
    const confirmedCount = deliverables.filter(item => item.confirmed).length;\r
    const overduePaymentCount = paymentPlans.filter(item => item.status === 'OVERDUE').length;\r
    $('#statTotal').textContent = stat.totalPlans || 0;\r
    $('#statWarning').textContent = stat.warningPlans || 0;\r
    $('#statOverdue').textContent = stat.overduePlans || 0;\r
    $('#statDeliverable').textContent = \`\${confirmedCount}/\${deliverables.length}\`;\r
    $('#statPaymentOverdue').textContent = overduePaymentCount;\r
    $('#statReminder').textContent = reminders.length;\r
}\r
\r
function normalizePlanStatus(status) {\r
    const value = String(status || 'NOT_STARTED').trim().toUpperCase();\r
    if (['TODO', 'ACTIVE', 'NOT_STARTED'].includes(value)) return 'NOT_STARTED';\r
    if (['HANDLED', 'CLOSE', 'CLOSED'].includes(value)) return 'CLOSED';\r
    if (value === 'PROCESSING') return 'IN_PROGRESS';\r
    return value;\r
}\r
\r
function formatConfidence(value) {\r
    if (value === null || value === undefined || value === '') {\r
        return '未返回';\r
    }\r
    const number = Number(value);\r
    if (Number.isNaN(number)) {\r
        return '未返回';\r
    }\r
    return \`\${Math.round(number * 100)}%\`;\r
}\r
\r
function planSourceText(plan) {\r
    if (plan.aiExtracted || plan.sourceType === 'AI') {\r
        return 'AI抽取';\r
    }\r
    if (plan.sourceType === 'AUTO') {\r
        return '系统生成';\r
    }\r
    return '人工维护';\r
}\r
\r
function planSourceClass(plan, pendingConfirm) {\r
    if (plan.aiExtracted || plan.sourceType === 'AI') {\r
        return pendingConfirm ? 'status-PENDING_CONFIRM' : 'status-COMPLETED';\r
    }\r
    return plan.sourceType === 'AUTO' ? 'status-IN_PROGRESS' : 'status-TODO';\r
}\r
\r
function deliverableConfirmText(status) {\r
    return status === 'PENDING_CONFIRM' ? '待人工确认' : '来源已确认';\r
}\r
\r
function deliverableSourceText(item) {\r
    if (item.aiExtracted) return 'AI生成';\r
    return item.planId ? '节点关联' : '人工维护';\r
}\r
\r
function ensureDeliverableUi() {\r
    const body = $('#deliverableTbody');\r
    if (!body) return;\r
    const panel = body.closest('.fulfillment-table-panel');\r
    const head = panel?.querySelector('.panel-head');\r
    if (head && !$('#newDeliverableBtn', head)) {\r
        head.insertAdjacentHTML('beforeend', \`\r
            <div class="panel-action-group">\r
                <button id="newDeliverableBtn" class="page-action-btn" type="button">\r
                    <i data-lucide="plus"></i>\r
                    新增交付物\r
                </button>\r
            </div>\r
        \`);\r
    }\r
    const headerRow = body.closest('table')?.querySelector('thead tr');\r
    if (headerRow && !headerRow.dataset.deliverableEnhanced) {\r
        headerRow.dataset.deliverableEnhanced = 'true';\r
        headerRow.innerHTML = \`\r
            <th>合同</th>\r
            <th>交付物</th>\r
            <th>对应节点</th>\r
            <th>AI信息</th>\r
            <th>流程状态</th>\r
            <th>提交/审核信息</th>\r
            <th>交付物文件</th>\r
            <th>备注</th>\r
            <th>操作</th>\r
        \`;\r
    }\r
    if (!$('#deliverableModal')) {\r
        document.body.insertAdjacentHTML('beforeend', \`\r
            <div id="deliverableModal" class="modal" hidden>\r
                <div class="modal-card">\r
                    <div class="modal-head">\r
                        <h3 id="deliverableModalTitle">新增交付物</h3>\r
                        <button type="button" id="closeDeliverableModal" class="secondary">关闭</button>\r
                    </div>\r
                    <form id="deliverableForm" class="form-grid">\r
                        <input type="hidden" id="deliverableId">\r
                        <label>\r
                            合同\r
                            <select id="deliverableContract" required></select>\r
                        </label>\r
                        <label>\r
                            对应节点\r
                            <select id="deliverablePlan"></select>\r
                        </label>\r
                        <label>\r
                            交付物类型\r
                            <input id="deliverableType" required placeholder="如 ACCEPTANCE_REPORT">\r
                        </label>\r
                        <label>\r
                            交付物名称\r
                            <input id="deliverableName" required placeholder="请输入交付物名称">\r
                        </label>\r
                        <label>\r
                            所属阶段\r
                            <input id="deliverableStage" placeholder="如 验收阶段">\r
                        </label>\r
                        <label>\r
                            AI确认状态\r
                            <select id="deliverableConfirmStatus">\r
                                <option value="CONFIRMED">来源已确认</option>\r
                                <option value="PENDING_CONFIRM">待人工确认</option>\r
                            </select>\r
                        </label>\r
                        <label class="wide">\r
                            备注\r
                            <textarea id="deliverableRemark" rows="2" placeholder="可填写人工确认意见、异常说明或交付范围"></textarea>\r
                        </label>\r
                        <div class="button-row wide">\r
                            <button type="submit">\r
                                <i data-lucide="circle-check"></i>\r
                                保存\r
                            </button>\r
                        </div>\r
                    </form>\r
                </div>\r
            </div>\r
            <div id="deliverableFileModal" class="modal" hidden>\r
                <div class="modal-card">\r
                    <div class="modal-head">\r
                        <h3 id="deliverableFileModalTitle">上传交付物文件</h3>\r
                        <button type="button" id="closeDeliverableFileModal" class="secondary">关闭</button>\r
                    </div>\r
                    <form id="deliverableFileForm" class="form-grid">\r
                        <input type="hidden" id="deliverableFileId">\r
                        <label>\r
                            文件\r
                            <input id="deliverableFile" type="file" accept=".pdf,.jpg,.jpeg,.png,.doc,.docx,.xls,.xlsx">\r
                        </label>\r
                        <label class="wide">\r
                            上传备注\r
                            <textarea id="deliverableFileRemark" rows="2" placeholder="可填写文件版本、交付说明或补充说明"></textarea>\r
                        </label>\r
                        <div class="button-row wide">\r
                            <button type="submit">\r
                                <i data-lucide="upload"></i>\r
                                上传\r
                            </button>\r
                        </div>\r
                    </form>\r
                </div>\r
            </div>\r
            <div id="deliverableTransitionModal" class="modal" hidden>\r
                <div class="modal-card">\r
                    <div class="modal-head">\r
                        <h3 id="deliverableTransitionTitle">交付物审核</h3>\r
                        <button type="button" id="closeDeliverableTransitionModal" class="secondary">关闭</button>\r
                    </div>\r
                    <form id="deliverableTransitionForm" class="form-grid">\r
                        <input type="hidden" id="deliverableTransitionId">\r
                        <input type="hidden" id="deliverableTransitionAction">\r
                        <label class="wide">\r
                            审核意见\r
                            <textarea id="deliverableTransitionRemark" rows="3" placeholder="补充、驳回时必须填写具体原因"></textarea>\r
                        </label>\r
                        <div class="button-row wide">\r
                            <button type="submit">\r
                                <i data-lucide="circle-check"></i>\r
                                确认操作\r
                            </button>\r
                        </div>\r
                    </form>\r
                </div>\r
            </div>\r
        \`);\r
    }\r
}\r
\r
function renderPlans(list) {\r
    const body = $('#planTbody');\r
    if (!list.length) {\r
        body.innerHTML = '<tr><td colspan="10" class="empty-cell">暂无履约节点</td></tr>';\r
        return;\r
    }\r
    body.innerHTML = list.map(plan => {\r
        const status = normalizePlanStatus(plan.status);\r
        const progress = Number(plan.progress || 0);\r
        const warningClass = \`warning-\${plan.warningLevel || 'NORMAL'}\`;\r
        const canHandle = plan.warningLevel === 'OVERDUE' || status === 'OVERDUE';\r
        const pendingConfirm = plan.confirmStatus === 'PENDING_CONFIRM' || status === 'PENDING_CONFIRM';\r
        const pendingDelay = plan.delayStatus === 'PENDING';\r
        const rejectedDelay = plan.delayStatus === 'REJECTED';\r
        const canConfirmDelay = pendingDelay && ['DEPT_LEADER', 'ADMIN'].includes(state.roleCode);
        return \`\r
            <tr>\r
                <td><strong>\${escapeHtml(plan.contractTitle || '-')}</strong><br><small>\${escapeHtml(plan.contractNo || '')}</small></td>\r
                <td><strong>\${escapeHtml(plan.nodeName || '-')}</strong><br><small>\${escapeHtml(planTypeMap[plan.planType] || plan.planType || '-')}</small>\${plan.sourceClause ? \`<br><small>来源：\${escapeHtml(plan.sourceClause)}</small>\` : ''}</td>\r
                <td>\r
                    \${escapeHtml(plan.dueDate || (pendingConfirm ? '待确认' : '-'))}<br><small>\${formatDays(plan.daysLeft)}</small>\r
                    \${Number(plan.overdueDays || 0) > 0 ? \`<br><small>逾期 \${Number(plan.overdueDays || 0)} 天</small>\` : ''}\r
                    \${pendingDelay ? \`<br><small>延期申请：\${escapeHtml(plan.delayRequestedDueDate || '-')} 待确认</small>\` : ''}\r
                    \${pendingDelay && plan.delayReason ? \`<br><small>原因：\${escapeHtml(plan.delayReason)}</small>\` : ''}\r
                </td>\r
                <td>\${escapeHtml(status === 'COMPLETED' ? (plan.actualCompletedDate || '-') : '-')}</td>\r
                <td>\r
                    <div class="progress-cell">\r
                        <span>\${progress}%</span>\r
                        <div class="progress-track"><i style="width:\${progress}%"></i></div>\r
                    </div>\r
                </td>\r
                <td><span class="tag status-\${status}">\${escapeHtml(statusTextMap[status] || status || '-')}</span>\${rejectedDelay ? \`<br><small class="danger-link">延期驳回：\${escapeHtml(plan.delayRejectReason || '-')}</small>\` : ''}</td>\r
                <td><span class="tag \${warningClass}">\${escapeHtml(warningTextMap[plan.warningLevel] || plan.warningLevel || '-')}</span></td>\r
                <td class="ai-meta">\r
                    <span class="tag \${planSourceClass(plan, pendingConfirm)}">\${planSourceText(plan)}</span>\r
                    <small>置信度：\${escapeHtml(formatConfidence(plan.aiConfidence))}</small>\r
                    \${pendingConfirm ? '<small class="ai-pending">待人工确认</small>' : ''}\r
                </td>\r
                <td>\${escapeHtml(plan.ownerName || '-')}</td>\r
                <td>\r
                    <div class="row-actions">\r
                        <button class="icon-btn edit-btn" data-id="\${plan.planId}" title="编辑"><i data-lucide="edit-3"></i></button>\r
                        <button class="icon-btn complete-btn" data-id="\${plan.planId}" title="完成"><i data-lucide="circle-check"></i></button>\r
                        <button class="icon-btn voucher-btn" data-id="\${plan.planId}" title="凭证与日志"><i data-lucide="paperclip"></i></button>\r
                        <button class="icon-btn delete-plan-btn" data-id="\${plan.planId}" title="删除"><i data-lucide="trash-2"></i></button>\r
                        \${canConfirmDelay ? \`<button class="link-btn confirm-delay-btn" data-id="\${plan.planId}" type="button">确认延期</button>\` : ''}\r
                        \${canHandle ? \`<button class="link-btn handle-btn" data-id="\${plan.planId}" type="button">处置</button>\` : ''}\r
                    </div>\r
                </td>\r
            </tr>\r
        \`;\r
    }).join('');\r
    body.querySelectorAll('.confirm-delay-btn').forEach(button => {\r
        if (!button.nextElementSibling?.classList.contains('reject-delay-btn')) {\r
            button.insertAdjacentHTML('afterend', \`<button class="link-btn reject-delay-btn danger-link" data-id="\${button.dataset.id}" type="button">驳回</button>\`);\r
        }\r
    });\r
    renderLucideIcons();\r
}\r
\r
function renderDeliverablesEnhanced(list) {\r
    ensureDeliverableUi();\r
    const body = $('#deliverableTbody');\r
    if (!list.length) {\r
        body.innerHTML = '<tr><td colspan="9" class="empty-cell">暂无交付物，请先生成标准台账或手工新增</td></tr>';\r
        return;\r
    }\r
    body.innerHTML = list.map(item => {\r
        const pendingConfirm = item.confirmStatus === 'PENDING_CONFIRM';\r
        const status = item.deliverableStatus || 'PENDING_SUBMIT';\r
        const editable = ['PENDING_SUBMIT', 'NEED_SUPPLEMENT', 'REJECTED'].includes(status);\r
        const reviewer = ['DEPT_LEADER', 'ADMIN'].includes(state.roleCode);
        const fileCell = item.downloadUrl\r
            ? \`<button class="link-btn download-deliverable-file-btn" data-url="\${escapeHtml(item.downloadUrl)}" data-name="\${escapeHtml(item.fileName || item.deliverableName || 'deliverable')}" type="button">\${escapeHtml(item.fileName || '下载文件')}</button>\`\r
            : '<span class="muted-text">未上传</span>';\r
        return \`\r
            <tr>\r
                <td>\${escapeHtml(item.contractTitle || '-')}</td>\r
                <td><strong>\${escapeHtml(item.deliverableName || '-')}</strong><br><small>\${escapeHtml(item.deliverableType || '')}</small></td>\r
                <td>\${escapeHtml(item.planName || item.stageName || '-')}<br><small>\${escapeHtml(planTypeMap[item.planType] || item.planType || '')}</small></td>\r
                <td class="ai-meta">\r
                    <span class="tag \${pendingConfirm ? 'status-PENDING_CONFIRM' : 'status-COMPLETED'}">\${deliverableConfirmText(item.confirmStatus)}</span>\r
                    <small>\${deliverableSourceText(item)}</small>\r
                    <small>置信度：\${escapeHtml(formatConfidence(item.aiConfidence))}</small>\r
                    \${item.sourceClause ? \`<small>来源：\${escapeHtml(item.sourceClause)}</small>\` : ''}\r
                </td>\r
                <td>\r
                    <span class="tag status-\${status}">\${escapeHtml(deliverableStatusMap[status] || status)}</span>\r
                    <br><small>提交版本：\${Number(item.submissionVersion || 0)}</small>\r
                </td>\r
                <td>\${escapeHtml(item.submittedBy || '-')}<br><small>\${item.submittedAt ? new Date(item.submittedAt).toLocaleString('zh-CN') : '尚未提交'}</small>\r
                    \${item.reviewerName ? \`<br><small>审核：\${escapeHtml(item.reviewerName)}</small>\` : ''}\r
                    \${item.reviewComment ? \`<br><small>\${escapeHtml(item.reviewComment)}</small>\` : ''}\r
                </td>\r
                <td>\${fileCell}</td>\r
                <td>\${escapeHtml(item.remark || '-')}</td>\r
                <td>\r
                    <div class="row-actions">\r
                        \${editable ? \`<button class="icon-btn edit-deliverable-btn" data-id="\${item.deliverableId}" title="编辑"><i data-lucide="edit-3"></i></button>\r
                        <button class="icon-btn upload-deliverable-file-btn" data-id="\${item.deliverableId}" title="上传文件"><i data-lucide="upload"></i></button>\r
                        <button class="link-btn deliverable-transition-btn" data-id="\${item.deliverableId}" data-action="SUBMIT" type="button">提交</button>\r
                        <button class="icon-btn delete-deliverable-btn" data-id="\${item.deliverableId}" title="删除"><i data-lucide="trash-2"></i></button>\` : ''}\r
                        \${status === 'SUBMITTED' && reviewer ? \`<button class="link-btn deliverable-transition-btn" data-id="\${item.deliverableId}" data-action="SUPPLEMENT" type="button">补充</button>\r
                        <button class="link-btn danger-link deliverable-transition-btn" data-id="\${item.deliverableId}" data-action="REJECT" type="button">驳回</button>\r
                        <button class="link-btn deliverable-transition-btn" data-id="\${item.deliverableId}" data-action="ACCEPT" type="button">确认</button>\` : ''}\r
                        \${status === 'ACCEPTED' && reviewer ? \`<button class="link-btn deliverable-transition-btn" data-id="\${item.deliverableId}" data-action="ACCEPTANCE" type="button">验收确认</button>\` : ''}\r
                    </div>\r
                </td>\r
            </tr>\r
        \`;\r
    }).join('');\r
    renderLucideIcons();\r
}\r
\r
function renderPaymentPlans(list) {\r
    renderPaymentRatioSummary(list);\r
    const body = $('#paymentPlanTbody');\r
    if (!list.length) {\r
        body.innerHTML = '<tr><td colspan="8" class="empty-cell">暂无付款计划，请先生成标准台账或新增付款计划</td></tr>';\r
        return;\r
    }\r
    body.innerHTML = list.map(item => {\r
        const prereqClass = item.prerequisiteCompleted ? 'status-COMPLETED' : 'warning-LEVEL2';\r
        const unpaidAmount = Number(item.unpaidAmount || 0);\r
        const canRecordPayment = !['PAID', 'WAIT_CONDITION', 'SUSPENDED'].includes(item.status) && unpaidAmount > 0;\r
        return \`\r
            <tr>\r
                <td><strong>\${escapeHtml(item.phaseName || '-')}</strong><br><small>\${escapeHtml(item.contractTitle || '')}</small><br><small>\${escapeHtml(item.fulfillmentNodeName || '')}</small></td>\r
                <td>\${formatPercent(item.percentage)}<br><small>\${formatMoney(item.plannedAmount)}</small></td>\r
                <td>\${escapeHtml(item.dueDate || '-')}<br><small>\${item.overdueDays > 0 ? \`逾期 \${item.overdueDays} 天\` : '未逾期'}</small></td>\r
                <td><span class="tag \${prereqClass}">\${escapeHtml(paymentConditionStatusMap[item.conditionStatus] || (item.prerequisiteCompleted ? '已满足' : '待满足'))}</span><br><small>\${escapeHtml(paymentConditionTypeMap[item.conditionType] || item.conditionType || '无前置条件')}</small><br><small>\${escapeHtml(item.paymentCondition || item.prerequisiteDelivery || '无')}</small></td>\r
                <td><span class="tag payment-\${item.status}">\${escapeHtml(paymentStatusMap[item.status] || item.status || '-')}</span><br><small>已收 \${formatMoney(item.paidAmount)} / 未收 \${formatMoney(item.unpaidAmount)}</small></td>\r
                <td>\${formatMoney(item.penaltyAmount)}<br><small>每日 \${formatPercent(item.penaltyRate)}</small></td>\r
                <td class="hint-cell">\${escapeHtml(item.responsibilityHint || '-')}</td>\r
                <td>\r
                    <div class="row-actions">\r
                        <button class="icon-btn edit-payment-btn" data-id="\${item.paymentPlanId}" title="编辑"><i data-lucide="edit-3"></i></button>\r
                        <button class="link-btn invoice-btn" data-id="\${item.paymentPlanId}" type="button">发票</button>\r
                        \${canRecordPayment\r
                            ? \`<button class="link-btn record-payment-btn" data-id="\${item.paymentPlanId}" type="button">到账</button>\`\r
                            : '<span class="tag payment-PAID">不可登记</span>'}\r
                        <button class="icon-btn delete-payment-plan-btn" data-id="\${item.paymentPlanId}" title="删除付款计划"><i data-lucide="trash-2"></i></button>\r
                    </div>\r
                </td>\r
            </tr>\r
        \`;\r
    }).join('');\r
    renderLucideIcons();\r
}\r
\r
function renderPaymentRecords(list) {\r
    const body = $('#paymentRecordTbody');\r
    if (!list.length) {\r
        body.innerHTML = '<tr><td colspan="8" class="empty-cell">暂无到账记录</td></tr>';\r
        return;\r
    }\r
    body.innerHTML = list.map(item => \`\r
        <tr>\r
            <td>\${escapeHtml(item.contractTitle || '-')}</td>\r
            <td>\${escapeHtml(item.phaseName || '-')}</td>\r
            <td>\${formatMoney(item.paidAmount)}</td>\r
            <td>\${escapeHtml(item.paidDate || '-')}</td>\r
            <td>\${escapeHtml(item.payer || '-')}<br><small>\${escapeHtml(item.bankSerialNo || '')}</small></td>\r
            <td>\${escapeHtml(item.receiver || '-')}<br><small>\${escapeHtml(item.handlerName || '')}</small></td>\r
            <td>\${escapeHtml(item.remark || '-')}<br>\${item.voucherDownloadUrl\r
                ? \`<button class="link-btn download-payment-voucher-btn" data-url="\${escapeHtml(item.voucherDownloadUrl)}" data-name="\${escapeHtml(item.voucherFileName || '付款凭证')}" type="button">\${escapeHtml(item.voucherFileName || '付款凭证')}</button>\`\r
                : '<small>未上传凭证</small>'}</td>\r
            <td>\r
                <button class="icon-btn delete-payment-record-btn" data-id="\${item.paymentRecordId}" title="删除到账记录">\r
                    <i data-lucide="trash-2"></i>\r
                </button>\r
            </td>\r
        </tr>\r
    \`).join('');\r
    renderLucideIcons();\r
}\r
\r
function renderInvoices(list) {\r
    const body = $('#invoiceTbody');\r
    if (!body) return;\r
    if (!list.length) {\r
        body.innerHTML = '<tr><td colspan="9" class="empty-cell">暂无发票记录</td></tr>';\r
        return;\r
    }\r
    body.innerHTML = list.map(item => \`\r
        <tr>\r
            <td>\${escapeHtml(item.contractTitle || '-')}</td>\r
            <td>\${escapeHtml(item.phaseName || '-')}</td>\r
            <td>\${escapeHtml(item.invoiceNo || '-')}</td>\r
            <td>\${formatMoney(item.invoiceAmount)}</td>\r
            <td>\${escapeHtml(item.invoiceDate || '-')}</td>\r
            <td><span class="tag">\${escapeHtml(invoiceStatusMap[item.invoiceStatus] || item.invoiceStatus || '-')}</span></td>\r
            <td>\${item.downloadUrl\r
                ? \`<button class="link-btn download-invoice-file-btn" data-url="\${escapeHtml(item.downloadUrl)}" data-name="\${escapeHtml(item.fileName || '发票附件')}" type="button">\${escapeHtml(item.fileName || '发票附件')}</button>\`\r
                : '<small>未上传</small>'}</td>\r
            <td>\${escapeHtml(item.remark || '-')}</td>\r
            <td><button class="link-btn upload-invoice-file-btn" data-id="\${item.invoiceId}" type="button">上传</button></td>\r
        </tr>\r
    \`).join('');\r
}\r
\r
function renderReminders(list) {\r
    const body = $('#reminderTbody');\r
    if (!list.length) {\r
        body.innerHTML = '<tr><td colspan="7" class="empty-cell">暂无推送记录</td></tr>';\r
        return;\r
    }\r
    body.innerHTML = list.map(item => \`\r
        <tr>\r
            <td>\${escapeHtml(item.contractTitle || '-')}</td>\r
            <td>\${escapeHtml(item.nodeName || '-')}</td>\r
            <td><span class="tag warning-\${item.reminderLevel}">\${escapeHtml(warningTextMap[item.reminderLevel] || item.reminderLevel || '-')}</span></td>\r
            <td>\${escapeHtml(item.receiver || '-')}</td>\r
            <td>\${escapeHtml(channelTextMap[item.channel] || item.channel || '-')}</td>\r
            <td>\${item.sentAt ? new Date(item.sentAt).toLocaleString('zh-CN') : '-'}</td>\r
            <td>\${escapeHtml(item.content || '-')}</td>\r
        </tr>\r
    \`).join('');\r
}\r
\r
function renderVouchers(list) {\r
    const body = $('#voucherTbody');\r
    if (!list.length) {\r
        body.innerHTML = '<tr><td colspan="5" class="empty-cell">暂无履约凭证</td></tr>';\r
        return;\r
    }\r
    body.innerHTML = list.map(item => {\r
        const canReview = ['FINANCE', 'DEPT_LEADER', 'ADMIN'].includes(state.roleCode)
            && item.reviewStatus === 'PENDING_REVIEW';\r
        return \`\r
            <tr>\r
                <td><strong>\${escapeHtml(item.fileName || '-')}</strong><br><small>\${escapeHtml(item.uploadedAt ? new Date(item.uploadedAt).toLocaleString('zh-CN') : '')}</small></td>\r
                <td>\${escapeHtml(voucherTypeMap[item.voucherType] || item.voucherType || '-')}</td>\r
                <td><span class="tag voucher-\${item.reviewStatus}">\${escapeHtml(voucherReviewMap[item.reviewStatus] || item.reviewStatus || '-')}</span><br><small>\${escapeHtml(item.reviewerName || '')}</small></td>\r
                <td>\${escapeHtml(item.uploadedByName || '-')}</td>\r
                <td>\r
                    <div class="row-actions">\r
                        <button class="icon-btn download-voucher-btn" data-url="\${escapeHtml(item.downloadUrl)}" data-name="\${escapeHtml(item.fileName || 'voucher')}" title="下载"><i data-lucide="download"></i></button>\r
                        \${canReview ? \`<button class="link-btn review-voucher-btn" data-id="\${item.voucherId}" data-approved="true" type="button">通过</button>\r
                        <button class="link-btn review-voucher-btn danger-link" data-id="\${item.voucherId}" data-approved="false" type="button">退回</button>\` : ''}\r
                    </div>\r
                </td>\r
            </tr>\r
        \`;\r
    }).join('');\r
    renderLucideIcons();\r
}\r
\r
function renderProgressLogs(list) {\r
    const body = $('#progressLogTbody');\r
    if (!list.length) {\r
        body.innerHTML = '<tr><td colspan="5" class="empty-cell">暂无进度变更日志</td></tr>';\r
        return;\r
    }\r
    body.innerHTML = list.map(item => \`\r
        <tr>\r
            <td>\${escapeHtml(progressOperationText(item.operation))}</td>\r
            <td>\${escapeHtml(statusTextMap[item.beforeStatus] || item.beforeStatus || '-')} → \${escapeHtml(statusTextMap[item.afterStatus] || item.afterStatus || '-')}</td>\r
            <td>\${escapeHtml(item.operatorName || '-')}<br><small>\${escapeHtml(item.clientIp || '')}</small></td>\r
            <td>\${item.operateTime ? new Date(item.operateTime).toLocaleString('zh-CN') : '-'}</td>\r
            <td>\${escapeHtml(item.remark || '-')}</td>\r
        </tr>\r
    \`).join('');\r
}\r
\r
function formatDays(days) {\r
    if (days === null || days === undefined) return '未设置';\r
    if (days < 0) return \`逾期 \${Math.abs(days)} 天\`;\r
    if (days === 0) return '今天到期';\r
    return \`剩余 \${days} 天\`;\r
}\r
\r
function formatMoney(value) {\r
    const number = Number(value || 0);\r
    return number.toLocaleString('zh-CN', {style: 'currency', currency: 'CNY'});\r
}\r
\r
function formatPercent(value) {\r
    const number = Number(value || 0);\r
    return \`\${number.toFixed(number % 1 === 0 ? 0 : 2)}%\`;\r
}\r
\r
function normalizedRatio(value) {\r
    return Math.round(Number(value || 0) * 100) / 100;\r
}\r
\r
function paymentRatioClass(total) {\r
    const rounded = normalizedRatio(total);\r
    if (rounded > PAYMENT_RATIO_TARGET) return 'is-danger';\r
    if (rounded === PAYMENT_RATIO_TARGET) return 'is-valid';\r
    return 'is-warning';\r
}\r
\r
function paymentRatioTotalFor(contractId, excludingPaymentPlanId = '') {\r
    return normalizedRatio(paymentPlans\r
        .filter(item => String(item.contractId) === String(contractId))\r
        .filter(item => !excludingPaymentPlanId || String(item.paymentPlanId) !== String(excludingPaymentPlanId))\r
        .reduce((sum, item) => sum + Number(item.percentage || 0), 0));\r
}\r
\r
function renderPaymentRatioSummary(list) {\r
    const summary = $('#paymentRatioSummary');\r
    if (!summary) return;\r
    if (!list.length) {\r
        summary.textContent = '付款比例合计：暂无计划';\r
        summary.className = 'payment-ratio-summary';\r
        return;\r
    }\r
    const groups = new Map();\r
    list.forEach(item => {\r
        const key = String(item.contractId || '');\r
        const group = groups.get(key) || {title: item.contractTitle || '未命名合同', total: 0};\r
        group.total = normalizedRatio(group.total + Number(item.percentage || 0));\r
        groups.set(key, group);\r
    });\r
    const values = Array.from(groups.values());\r
    if (values.length === 1) {\r
        const total = values[0].total;\r
        const matched = normalizedRatio(total) === PAYMENT_RATIO_TARGET;\r
        summary.textContent = matched\r
            ? \`付款比例合计：\${formatPercent(total)}，已满足 100%\`\r
            : \`付款比例合计：\${formatPercent(total)}，应为 100%\`;\r
        summary.className = \`payment-ratio-summary \${paymentRatioClass(total)}\`;\r
        return;\r
    }\r
    const validCount = values.filter(item => normalizedRatio(item.total) === PAYMENT_RATIO_TARGET).length;\r
    const overCount = values.filter(item => normalizedRatio(item.total) > PAYMENT_RATIO_TARGET).length;\r
    summary.textContent = overCount > 0\r
        ? \`付款比例校验：\${validCount}/\${values.length} 个合同合计为 100%，\${overCount} 个超过 100%\`\r
        : \`付款比例校验：\${validCount}/\${values.length} 个合同合计为 100%\`;\r
    summary.className = \`payment-ratio-summary \${overCount > 0 ? 'is-danger' : (validCount === values.length ? 'is-valid' : 'is-warning')}\`;\r
}\r
\r
function renderPaymentRatioCheck() {\r
    const check = $('#paymentRatioCheck');\r
    if (!check) return;\r
    const contractId = Number($('#paymentContract').value || 0);\r
    const current = normalizedRatio($('#percentage').value);\r
    const existing = paymentRatioTotalFor(contractId, $('#paymentPlanId').value);\r
    const total = normalizedRatio(existing + current);\r
    if (!contractId) {\r
        check.textContent = '请选择合同后校验付款比例';\r
        check.className = 'ratio-check wide';\r
        return;\r
    }\r
    if (total > PAYMENT_RATIO_TARGET) {\r
        check.textContent = \`保存后合计 \${formatPercent(total)}，超过 100%\`;\r
    } else if (total === PAYMENT_RATIO_TARGET) {\r
        check.textContent = '保存后合计 100%，满足分期配置';\r
    } else {\r
        check.textContent = \`保存后合计 \${formatPercent(total)}，距 100% 还差 \${formatPercent(PAYMENT_RATIO_TARGET - total)}\`;\r
    }\r
    check.className = \`ratio-check wide \${paymentRatioClass(total)}\`;\r
}\r
\r
function validatePaymentRatioBeforeSave(payload, paymentPlanId) {\r
    if (payload.percentage < 0 || payload.percentage > PAYMENT_RATIO_TARGET) {\r
        throw new Error('付款比例必须在 0% 到 100% 之间');\r
    }\r
    const total = normalizedRatio(paymentRatioTotalFor(payload.contractId, paymentPlanId) + payload.percentage);\r
    if (total > PAYMENT_RATIO_TARGET) {\r
        throw new Error(\`同一合同付款比例合计不能超过 100%，当前保存后合计为 \${formatPercent(total)}\`);\r
    }\r
}\r
\r
function prerequisiteTokens(value) {\r
    return String(value || '')\r
        .split(/[,，、]/)\r
        .map(item => item.trim())\r
        .filter(Boolean);\r
}\r
\r
function selectedPrerequisiteDelivery() {\r
    return Array.from($('#prerequisiteDelivery').selectedOptions)\r
        .map(option => option.value.trim())\r
        .filter(Boolean)\r
        .join('、');\r
}\r
\r
function addPrerequisiteOption(select, value, text, selected) {\r
    const option = document.createElement('option');\r
    option.value = value;\r
    option.textContent = text;\r
    option.selected = selected;\r
    select.appendChild(option);\r
}\r
\r
async function loadPrerequisiteDeliveryOptions(contractId, selectedValue = '') {\r
    const select = $('#prerequisiteDelivery');\r
    const selected = new Set(prerequisiteTokens(selectedValue));\r
    select.disabled = true;\r
    select.innerHTML = '';\r
    addPrerequisiteOption(select, '', '加载交付物中...', false);\r
    if (!contractId) {\r
        select.innerHTML = '';\r
        addPrerequisiteOption(select, '', '请先选择合同', false);\r
        select.disabled = true;\r
        return;\r
    }\r
    const items = await api(\`/api/fulfillment/deliverables?contractId=\${contractId}\`);\r
    select.innerHTML = '';\r
    const unmatched = new Set(selected);\r
    items.forEach(item => {\r
        const value = item.deliverableName || item.deliverableType || '';\r
        if (!value) return;\r
        const selectedByName = selected.has(item.deliverableName);\r
        const selectedByType = selected.has(item.deliverableType);\r
        const text = item.deliverableName || value;\r
        addPrerequisiteOption(select, value, text, selectedByName || selectedByType);\r
        unmatched.delete(item.deliverableName);\r
        unmatched.delete(item.deliverableType);\r
    });\r
    unmatched.forEach(value => addPrerequisiteOption(select, value, value, true));\r
    if (!select.options.length) {\r
        addPrerequisiteOption(select, '', '暂无交付物，请先生成标准台账', false);\r
        select.disabled = true;\r
        return;\r
    }\r
    select.disabled = false;\r
}\r
\r
function openPlanModal(plan = null) {\r
    $('#planModalTitle').textContent = plan ? '编辑履约节点' : '新增履约节点';\r
    $('#planId').value = plan?.planId || '';\r
    $('#planContract').value = plan?.contractId || currentFilterContractId() || contracts[0]?.contractId || '';\r
    $('#planContract').disabled = Boolean(plan);\r
    const isOverduePlan = Boolean(plan)\r
        && (normalizePlanStatus(plan.status) === 'OVERDUE' || plan.warningLevel === 'OVERDUE');\r
    setActualCompletedDateLimits();\r
    $('#planType').value = plan?.planType || 'OTHER';\r
    $('#nodeName').value = plan?.nodeName || '';\r
    $('#dueDate').value = plan ? (plan.dueDate || '') : new Date().toISOString().slice(0, 10);\r
    $('#dueDate').disabled = isOverduePlan;\r
    $('#planStatus').value = normalizePlanStatus(plan?.status || 'NOT_STARTED');\r
    $('#planStatus').disabled = isOverduePlan;\r
    $('#actualCompletedDate').value = plan?.actualCompletedDate || '';\r
    $('#actualCompletedDate').disabled = isOverduePlan;\r
    syncActualCompletedDateField();\r
    $('#ownerName').value = plan?.ownerName || state.username || '';\r
    $('#progressRange').value = plan?.progress ?? 0;\r
    $('#progressValue').value = plan?.progress ?? 0;\r
    $('#planRemark').value = plan?.remark || '';\r
    $('#planModal').hidden = false;\r
    renderLucideIcons();\r
}\r
\r
function closePlanModal() {\r
    $('#planModal').hidden = true;\r
    $('#planContract').disabled = false;\r
    $('#dueDate').disabled = false;\r
    $('#planStatus').disabled = false;\r
    $('#actualCompletedDate').disabled = false;\r
    $('#planForm').reset();\r
}\r
\r
async function openVoucherModal(planId) {\r
    const plan = plans.find(item => String(item.planId) === String(planId));\r
    if (!plan) return;\r
    currentVoucherPlan = plan;\r
    $('#voucherPlanId').value = plan.planId;\r
    $('#voucherModalTitle').textContent = \`履约凭证与进度日志 - \${plan.nodeName || ''}\`;\r
    $('#voucherType').value = plan.planType === 'PAYMENT' ? 'PAYMENT' : 'PROGRESS';\r
    $('#voucherRemark').value = '';\r
    $('#voucherFile').value = '';\r
    $('#voucherForm').hidden = ['LEGAL', 'FINANCE', 'EXECUTIVE'].includes(state.roleCode);
    $('#voucherModal').hidden = false;\r
    await reloadVoucherDetail();\r
    renderLucideIcons();\r
}\r
\r
function closeVoucherModal() {\r
    $('#voucherModal').hidden = true;\r
    $('#voucherForm').reset();\r
    currentVoucherPlan = null;\r
}\r
\r
function openOverdueModal(planId) {\r
    const plan = plans.find(item => String(item.planId) === String(planId));\r
    if (!plan) return;\r
    $('#overduePlanId').value = plan.planId;\r
    $('#overdueModalTitle').textContent = \`逾期处置 - \${plan.nodeName || ''}\`;\r
    $('#overdueAction').value = 'COMPLETE';\r
    setActualCompletedDateLimits();\r
    $('#overdueActualCompletedDate').value = todayString();\r
    $('#overdueNewPlannedDate').value = '';\r
    $('#overdueDelayReason').value = '';\r
    $('#overdueDisposalRemark').value = plan.remark || '';\r
    toggleOverdueFields();\r
    $('#overdueModal').hidden = false;\r
    renderLucideIcons();\r
}\r
\r
function closeOverdueModal() {\r
    $('#overdueModal').hidden = true;\r
    $('#overdueForm').reset();\r
}\r
\r
function toggleOverdueFields() {\r
    const isDelay = $('#overdueAction').value === 'DELAY';\r
    $$('.overdue-complete-field').forEach(item => item.hidden = isDelay);\r
    $$('.overdue-delay-field').forEach(item => item.hidden = !isDelay);\r
    $('#overdueHandleHint').textContent = isDelay\r
        ? '申请延期后会生成延期审批记录，并推送部门主管审批；审批通过前节点仍保持逾期。'\r
        : '标记完成前请先在“凭证/日志”中上传完成凭证；付款节点需上传付款凭证并复核通过。';\r
}\r
\r
function overdueHandlePayload() {\r
    const action = $('#overdueAction').value;\r
    const actualCompletedDate = action === 'COMPLETE' ? ($('#overdueActualCompletedDate').value || null) : null;\r
    assertActualCompletedDateNotFuture(actualCompletedDate);\r
    return {\r
        action,\r
        actualCompletedDate,\r
        newPlannedDate: action === 'DELAY' ? ($('#overdueNewPlannedDate').value || null) : null,\r
        delayReason: action === 'DELAY' ? $('#overdueDelayReason').value.trim() : '',\r
        disposalRemark: $('#overdueDisposalRemark').value.trim()\r
    };\r
}\r
\r
async function reloadVoucherDetail() {\r
    const planId = $('#voucherPlanId').value;\r
    if (!planId) return;\r
    const [vouchers, logs] = await Promise.all([\r
        api(\`/api/fulfillment/vouchers?planId=\${planId}\`),\r
        api(\`/api/fulfillment/progress-logs?planId=\${planId}\`)\r
    ]);\r
    renderVouchers(vouchers);\r
    renderProgressLogs(logs);\r
}\r
\r
function planPayload(plan = null, overrides = {}) {\r
    const actualCompletedDate = plan ? (plan.actualCompletedDate || null) : ($('#actualCompletedDate').value || null);\r
    const payload = {\r
        contractId: plan ? plan.contractId : Number($('#planContract').value),\r
        nodeName: plan ? plan.nodeName : $('#nodeName').value.trim(),\r
        planType: plan ? plan.planType : $('#planType').value,\r
        dueDate: plan ? plan.dueDate : ($('#dueDate').value || null),\r
        status: plan ? normalizePlanStatus(plan.status) : $('#planStatus').value,\r
        progress: plan ? plan.progress : Number($('#progressValue').value || 0),\r
        actualCompletedDate,\r
        ownerName: plan ? plan.ownerName : $('#ownerName').value.trim(),\r
        remark: plan ? plan.remark : $('#planRemark').value.trim(),\r
        ...overrides\r
    };\r
    payload.status = normalizePlanStatus(payload.status);\r
    if (payload.status !== 'COMPLETED') {\r
        payload.actualCompletedDate = null;\r
    }\r
    assertActualCompletedDateNotFuture(payload.actualCompletedDate);\r
    return payload;\r
}\r
\r
function fillDeliverableContractSelect(selectedValue = '') {\r
    const select = $('#deliverableContract');\r
    if (!select) return;\r
    select.innerHTML = contracts.map(contractOption).join('') || '<option value="">暂无合同</option>';\r
    select.value = selectedValue || currentFilterContractId() || contracts[0]?.contractId || '';\r
}\r
\r
function fillDeliverablePlanSelect(contractId, selectedValue = '') {\r
    const select = $('#deliverablePlan');\r
    if (!select) return;\r
    const filtered = plans.filter(plan => !contractId || String(plan.contractId) === String(contractId));\r
    select.innerHTML = '<option value="">不绑定节点</option>' + filtered.map(plan => {\r
        const text = \`\${plan.nodeName || \`节点\${plan.planId}\`} \${plan.planType ? \`(\${planTypeMap[plan.planType] || plan.planType})\` : ''}\`;\r
        return \`<option value="\${plan.planId}">\${escapeHtml(text)}</option>\`;\r
    }).join('');\r
    select.value = selectedValue || '';\r
}\r
\r
function openDeliverableModal(item = null) {\r
    ensureDeliverableUi();\r
    $('#deliverableModalTitle').textContent = item ? '编辑交付物' : '新增交付物';\r
    $('#deliverableId').value = item?.deliverableId || '';\r
    fillDeliverableContractSelect(item?.contractId || '');\r
    $('#deliverableContract').disabled = Boolean(item);\r
    fillDeliverablePlanSelect($('#deliverableContract').value, item?.planId || '');\r
    $('#deliverableType').value = item?.deliverableType || 'OTHER';\r
    $('#deliverableName').value = item?.deliverableName || '';\r
    $('#deliverableStage').value = item?.stageName || '';\r
    $('#deliverableConfirmStatus').value = item?.confirmStatus || 'CONFIRMED';\r
    $('#deliverableRemark').value = item?.remark || '';\r
    $('#deliverableModal').hidden = false;\r
    renderLucideIcons();\r
}\r
\r
function closeDeliverableModal() {\r
    $('#deliverableModal').hidden = true;\r
    $('#deliverableContract').disabled = false;\r
    $('#deliverableForm').reset();\r
}\r
\r
function deliverablePayload() {\r
    const planId = $('#deliverablePlan').value;\r
    return {\r
        contractId: Number($('#deliverableContract').value),\r
        planId: planId ? Number(planId) : null,\r
        deliverableType: $('#deliverableType').value.trim(),\r
        deliverableName: $('#deliverableName').value.trim(),\r
        stageName: $('#deliverableStage').value.trim(),\r
        confirmStatus: $('#deliverableConfirmStatus').value,\r
        confirmed: null,\r
        acceptancePassed: null,\r
        remark: $('#deliverableRemark').value.trim()\r
    };\r
}\r
\r
async function saveDeliverable(event) {\r
    event.preventDefault();\r
    const id = $('#deliverableId').value;\r
    const url = id ? \`/api/fulfillment/deliverables/\${id}\` : '/api/fulfillment/deliverables';\r
    await api(url, {method: id ? 'PUT' : 'POST', body: JSON.stringify(deliverablePayload())});\r
    closeDeliverableModal();\r
    await refreshAll();\r
    toast(id ? '交付物已更新' : '交付物已新增');\r
}\r
\r
function openDeliverableFileModal(id) {\r
    const item = deliverables.find(row => String(row.deliverableId) === String(id));\r
    if (!item) return;\r
    ensureDeliverableUi();\r
    $('#deliverableFileId').value = item.deliverableId;\r
    $('#deliverableFileModalTitle').textContent = \`上传交付物文件 - \${item.deliverableName || ''}\`;\r
    $('#deliverableFile').value = '';\r
    $('#deliverableFileRemark').value = item.remark || '';\r
    $('#deliverableFileModal').hidden = false;\r
    renderLucideIcons();\r
}\r
\r
function closeDeliverableFileModal() {\r
    $('#deliverableFileModal').hidden = true;\r
    $('#deliverableFileForm').reset();\r
}\r
\r
function openDeliverableTransitionModal(id, action) {\r
    const item = deliverables.find(row => String(row.deliverableId) === String(id));\r
    if (!item) return;\r
    $('#deliverableTransitionId').value = id;\r
    $('#deliverableTransitionAction').value = action;\r
    $('#deliverableTransitionRemark').value = '';\r
    $('#deliverableTransitionTitle').textContent =\r
        \`\${deliverableTransitionText[action] || '处理'} - \${item.deliverableName || ''}\`;\r
    $('#deliverableTransitionRemark').required = ['SUPPLEMENT', 'REJECT'].includes(action);\r
    $('#deliverableTransitionModal').hidden = false;\r
    renderLucideIcons();\r
}\r
\r
function closeDeliverableTransitionModal() {\r
    $('#deliverableTransitionModal').hidden = true;\r
    $('#deliverableTransitionForm').reset();\r
}\r
\r
async function saveDeliverableTransition(event) {\r
    event.preventDefault();\r
    const id = $('#deliverableTransitionId').value;\r
    const action = $('#deliverableTransitionAction').value;\r
    const remark = $('#deliverableTransitionRemark').value.trim();\r
    if (['SUPPLEMENT', 'REJECT'].includes(action) && !remark) {\r
        return toast(action === 'SUPPLEMENT' ? '请填写需要补充的内容' : '请填写驳回原因');\r
    }\r
    await api(\`/api/fulfillment/deliverables/\${id}/transition\`, {\r
        method: 'POST',\r
        body: JSON.stringify({action, remark})\r
    });\r
    closeDeliverableTransitionModal();\r
    await refreshAll();\r
    toast(\`\${deliverableTransitionText[action] || '操作'}已完成\`);\r
}\r
\r
async function uploadDeliverableFile(event) {\r
    event.preventDefault();\r
    const id = $('#deliverableFileId').value;\r
    const file = $('#deliverableFile').files[0];\r
    if (!id) return;\r
    if (!file) return toast('请先选择交付物文件');\r
    const formData = new FormData();\r
    formData.append('file', file);\r
    formData.append('remark', $('#deliverableFileRemark').value.trim());\r
    await uploadApi(\`/api/fulfillment/deliverables/\${id}/file\`, formData);\r
    closeDeliverableFileModal();\r
    await refreshAll();\r
    toast('交付物文件已上传');\r
}\r
\r
async function deleteDeliverable(id) {\r
    const item = deliverables.find(row => String(row.deliverableId) === String(id));\r
    const name = item?.deliverableName || '该交付物';\r
    if (!window.confirm(\`确认删除“\${name}”吗？\`)) return;\r
    await api(\`/api/fulfillment/deliverables/\${id}\`, {method: 'DELETE'});\r
    await refreshAll();\r
    toast('交付物已删除');\r
}\r
\r
function openPaymentPlanModal(plan = null) {\r
    $('#paymentPlanModalTitle').textContent = plan ? '编辑付款计划' : '新增付款计划';\r
    $('#paymentPlanId').value = plan?.paymentPlanId || '';\r
    $('#paymentContract').value = plan?.contractId || currentFilterContractId() || contracts[0]?.contractId || '';\r
    $('#paymentContract').disabled = Boolean(plan);\r
    $('#phaseName').value = plan?.phaseName || '';\r
    $('#percentage').value = plan?.percentage ?? '';\r
    $('#plannedAmount').value = plan?.plannedAmount ?? '';\r
    $('#paymentDueDate').value = plan?.dueDate || new Date().toISOString().slice(0, 10);\r
    $('#paymentPayee').value = plan?.payee || '';\r
    $('#paymentConditionType').value = plan?.conditionType || 'NONE';\r
    $('#paymentCondition').value = plan?.paymentCondition || '';\r
    $('#penaltyRate').value = plan?.penaltyRate ?? 0.05;\r
    $('#paymentRemark').value = plan?.remark || '';\r
    $('#paymentPlanModal').hidden = false;\r
    renderPaymentRatioCheck();\r
    loadPrerequisiteDeliveryOptions($('#paymentContract').value, plan?.prerequisiteDelivery || '')\r
        .catch(error => toast(error.message));\r
    renderLucideIcons();\r
}\r
\r
function closePaymentPlanModal() {\r
    $('#paymentPlanModal').hidden = true;\r
    $('#paymentContract').disabled = false;\r
    $('#paymentPlanForm').reset();\r
    $('#paymentRatioCheck').textContent = '';\r
    $('#paymentRatioCheck').className = 'ratio-check wide';\r
    $('#prerequisiteDelivery').innerHTML = '';\r
    $('#prerequisiteDelivery').disabled = false;\r
}\r
\r
function paymentPlanPayload() {\r
    return {\r
        contractId: Number($('#paymentContract').value),\r
        phaseName: $('#phaseName').value.trim(),\r
        percentage: Number($('#percentage').value || 0),\r
        plannedAmount: Number($('#plannedAmount').value || 0),\r
        dueDate: $('#paymentDueDate').value,\r
        payee: $('#paymentPayee').value.trim(),\r
        conditionType: $('#paymentConditionType').value,\r
        paymentCondition: $('#paymentCondition').value.trim(),\r
        prerequisiteDelivery: selectedPrerequisiteDelivery(),\r
        penaltyRate: Number($('#penaltyRate').value || 0),\r
        remark: $('#paymentRemark').value.trim()\r
    };\r
}\r
\r
function openPaymentRecordModal(planId) {\r
    const plan = paymentPlans.find(item => String(item.paymentPlanId) === String(planId));\r
    if (!plan || plan.status === 'PAID' || Number(plan.unpaidAmount || 0) <= 0) {\r
        toast('该付款计划已足额到账，无需重复登记');\r
        return;\r
    }\r
    $('#recordPaymentPlanId').value = planId;\r
    $('#paidAmount').value = plan?.unpaidAmount || plan?.plannedAmount || '';\r
    $('#paidDate').value = new Date().toISOString().slice(0, 10);\r
    $('#bankSerialNo').value = '';\r
    $('#paymentHandler').value = state.username || '';\r
    $('#payer').value = '甲方';\r
    $('#receiver').value = plan?.payee || '乙方';\r
    $('#paymentRecordRemark').value = '';\r
    $('#paymentVoucherFile').value = '';\r
    $('#paymentRecordModal').hidden = false;\r
}\r
\r
function closePaymentRecordModal() {\r
    $('#paymentRecordModal').hidden = true;\r
    $('#paymentRecordForm').reset();\r
}\r
\r
async function savePlan(event) {\r
    event.preventDefault();\r
    const id = $('#planId').value;\r
    const url = id ? \`/api/fulfillment/plans/\${id}\` : '/api/fulfillment/plans';\r
    const saved = await api(url, {method: id ? 'PUT' : 'POST', body: JSON.stringify(planPayload())});\r
    closePlanModal();\r
    await refreshAll();\r
    toast(saved?.delayStatus === 'PENDING' ? '延期申请已提交，待部门主管确认' : (id ? '履约节点已更新' : '履约节点已新增'));\r
}\r
\r
async function uploadVoucher(event) {\r
    event.preventDefault();\r
    const planId = $('#voucherPlanId').value;\r
    const file = $('#voucherFile').files[0];\r
    if (!planId) return;\r
    if (!file) return toast('请先选择履约凭证文件');\r
    const formData = new FormData();\r
    formData.append('file', file);\r
    formData.append('voucherType', $('#voucherType').value);\r
    formData.append('remark', $('#voucherRemark').value.trim());\r
    await uploadApi(\`/api/fulfillment/plans/\${planId}/vouchers\`, formData);\r
    $('#voucherFile').value = '';\r
    $('#voucherRemark').value = '';\r
    await Promise.all([reloadVoucherDetail(), refreshAll()]);\r
    toast('履约凭证已上传');\r
}\r
\r
async function reviewVoucher(id, approved) {\r
    const remark = $('#voucherRemark').value.trim();\r
    const params = new URLSearchParams({approved: String(approved)});\r
    if (remark) params.set('remark', remark);\r
    await api(\`/api/fulfillment/vouchers/\${id}/review?\${params}\`, {method: 'POST'});\r
    $('#voucherRemark').value = '';\r
    await reloadVoucherDetail();\r
    toast(approved ? '凭证复核通过' : '凭证已退回');\r
}\r
\r
async function savePaymentPlan(event) {\r
    event.preventDefault();\r
    const id = $('#paymentPlanId').value;\r
    const payload = paymentPlanPayload();\r
    validatePaymentRatioBeforeSave(payload, id);\r
    const url = id ? \`/api/fulfillment/payments/plans/\${id}\` : '/api/fulfillment/payments/plans';\r
    await api(url, {method: id ? 'PUT' : 'POST', body: JSON.stringify(payload)});\r
    closePaymentPlanModal();\r
    await refreshAll();\r
    toast(id ? '付款计划已更新' : '付款计划已新增');\r
}\r
\r
async function savePaymentRecord(event) {\r
    event.preventDefault();\r
    const id = $('#recordPaymentPlanId').value;\r
    const saved = await api(\`/api/fulfillment/payments/plans/\${id}/records\`, {\r
        method: 'POST',\r
        body: JSON.stringify({\r
            paidAmount: Number($('#paidAmount').value || 0),\r
            paidDate: $('#paidDate').value,\r
            bankSerialNo: $('#bankSerialNo').value.trim(),\r
            handlerName: $('#paymentHandler').value.trim(),\r
            payer: $('#payer').value.trim(),\r
            receiver: $('#receiver').value.trim(),\r
            remark: $('#paymentRecordRemark').value.trim()\r
        })\r
    });\r
    const voucherFile = $('#paymentVoucherFile').files[0];\r
    if (voucherFile && saved?.paymentRecordId) {\r
        const formData = new FormData();\r
        formData.append('file', voucherFile);\r
        formData.append('remark', $('#paymentRecordRemark').value.trim());\r
        await uploadApi(\`/api/fulfillment/payments/records/\${saved.paymentRecordId}/voucher\`, formData);\r
    }\r
    closePaymentRecordModal();\r
    await refreshAll();\r
    toast('到账记录已保存');\r
}\r
\r
async function createInvoiceForPlan(id) {\r
    const plan = paymentPlans.find(item => String(item.paymentPlanId) === String(id));\r
    const invoiceNo = window.prompt('请输入发票号码');\r
    if (!invoiceNo || !invoiceNo.trim()) return;\r
    const amountText = window.prompt('请输入发票金额', String(plan?.unpaidAmount || plan?.plannedAmount || ''));\r
    if (!amountText) return;\r
    await api(\`/api/fulfillment/payments/plans/\${id}/invoices\`, {\r
        method: 'POST',\r
        body: JSON.stringify({\r
            invoiceNo: invoiceNo.trim(),\r
            invoiceAmount: Number(amountText || 0),\r
            invoiceDate: todayString(),\r
            invoiceStatus: 'VERIFIED',\r
            remark: '前端快速登记'\r
        })\r
    });\r
    await refreshAll();\r
    toast('发票记录已保存');\r
}\r
\r
async function uploadInvoiceFile(id) {\r
    const input = document.createElement('input');\r
    input.type = 'file';\r
    input.accept = '.pdf,.jpg,.jpeg,.png,.doc,.docx,.xls,.xlsx';\r
    input.onchange = async () => {\r
        const file = input.files[0];\r
        if (!file) return;\r
        const formData = new FormData();\r
        formData.append('file', file);\r
        await uploadApi(\`/api/fulfillment/payments/invoices/\${id}/file\`, formData);\r
        await refreshAll();\r
        toast('发票附件已上传');\r
    };\r
    input.click();\r
}\r
\r
async function deletePaymentRecord(id) {\r
    if (!window.confirm('确认删除这条到账记录吗？删除后付款计划状态会重新计算。')) return;\r
    await api(\`/api/fulfillment/payments/records/\${id}\`, {method: 'DELETE'});\r
    await refreshAll();\r
    toast('到账记录已删除');\r
}\r
\r
async function deletePaymentPlan(id) {\r
    const plan = paymentPlans.find(item => String(item.paymentPlanId) === String(id));\r
    const phaseName = plan?.phaseName ? \`“\${plan.phaseName}”\` : '该';\r
    if (!window.confirm(\`确认删除\${phaseName}付款计划吗？已有到账记录或发票材料的计划不能删除。\`)) return;\r
    await api(\`/api/fulfillment/payments/plans/\${id}\`, {method: 'DELETE'});\r
    await refreshAll();\r
    toast('付款计划已删除');\r
}\r
\r
let fulfillmentGenerationBusy = false;\r
\r
async function runFulfillmentGeneration(buttonId, busyText, action) {\r
    if (fulfillmentGenerationBusy) {\r
        return toast('履约节点正在生成，请勿重复提交');\r
    }\r
    fulfillmentGenerationBusy = true;\r
    const buttons = [$('#initMemberEBtn'), $('#extractBtn')].filter(Boolean);\r
    const activeButton = $(buttonId);\r
    const originalHtml = activeButton?.innerHTML || '';\r
    buttons.forEach(button => {\r
        button.disabled = true;\r
    });\r
    if (activeButton) {\r
        activeButton.textContent = busyText;\r
    }\r
    try {\r
        return await action();\r
    } finally {\r
        fulfillmentGenerationBusy = false;\r
        buttons.forEach(button => {\r
            button.disabled = false;\r
        });\r
        if (activeButton) {\r
            activeButton.innerHTML = originalHtml;\r
        }\r
        renderLucideIcons();\r
    }\r
}\r
\r
async function initializeMemberE() {\r
    const contractId = selectedContractId('#initContract');\r
    if (!contractId) return toast('请先选择合同');\r
    return runFulfillmentGeneration('#initMemberEBtn', '生成中...', async () => {\r
        await api(\`/api/fulfillment/contracts/\${contractId}/member-e/init\`, {method: 'POST'});\r
        $('#filterContract').value = String(contractId);\r
        await refreshAll();\r
        toast('已生成交付物和付款台账');\r
    });\r
}\r
\r
async function extractPlans() {\r
    const contractId = selectedContractId('#extractContract');\r
    if (!contractId) return toast('请先选择合同');\r
    return runFulfillmentGeneration('#extractBtn', '抽取中...', async () => {\r
        await api(\`/api/fulfillment/plans/extract/\${contractId}\`, {method: 'POST'});\r
        $('#filterContract').value = String(contractId);\r
        await refreshAll();\r
        toast('履约节点已抽取');\r
    });\r
}\r
\r
async function dispatchReminders() {\r
    const contractId = currentFilterContractId();\r
    await api(endpointWithContract('/api/fulfillment/reminders/dispatch'), {method: 'POST'});\r
    await refreshAll();\r
    toast(contractId ? '当前合同预警提醒已生成' : '全部合同预警提醒已生成');\r
}\r
\r
async function completePlan(id) {\r
    const plan = plans.find(item => String(item.planId) === String(id));\r
    if (!plan) return;\r
    if (plan.warningLevel === 'OVERDUE' || normalizePlanStatus(plan.status) === 'OVERDUE') {\r
        openOverdueModal(id);\r
        return;\r
    }\r
    await api(\`/api/fulfillment/plans/\${id}\`, {\r
        method: 'PUT',\r
        body: JSON.stringify(planPayload(plan, {\r
            status: 'COMPLETED',\r
            progress: 100,\r
            actualCompletedDate: todayString()\r
        }))\r
    });\r
    await refreshAll();\r
    toast('节点已完成');\r
}\r
\r
async function handleOverdue(id) {\r
    openOverdueModal(id);\r
    return;\r
    await api(\`/api/fulfillment/plans/\${id}/handle-overdue\`, {method: 'POST'});\r
    await refreshAll();\r
    toast('逾期节点已处置');\r
}\r
\r
async function saveOverdueHandle(event) {\r
    event.preventDefault();\r
    const id = $('#overduePlanId').value;\r
    if (!id) return;\r
    const saved = await api(\`/api/fulfillment/plans/\${id}/handle-overdue\`, {\r
        method: 'POST',\r
        body: JSON.stringify(overdueHandlePayload())\r
    });\r
    closeOverdueModal();\r
    await refreshAll();\r
    toast(saved?.delayStatus === 'PENDING' ? '延期申请已提交，等待部门主管审批' : '逾期节点已标记完成');\r
}\r
\r
async function confirmDelay(id) {\r
    await api(\`/api/fulfillment/plans/\${id}/delay/confirm\`, {method: 'POST'});\r
    await refreshAll();\r
    toast('延期申请已确认');\r
}\r
\r
async function rejectDelay(id) {\r
    const reason = window.prompt('请输入延期驳回原因');\r
    if (!reason || !reason.trim()) return;\r
    const approvals = await api(\`/api/fulfillment/delay-approvals?planId=\${id}&status=PENDING\`);\r
    if (!approvals.length) {\r
        toast('没有待审批的延期申请');\r
        return;\r
    }\r
    await api(\`/api/fulfillment/delay-approvals/\${approvals[0].approvalId}/review\`, {\r
        method: 'POST',\r
        body: JSON.stringify({approved: false, remark: reason.trim()})\r
    });\r
    await refreshAll();\r
    toast('延期申请已驳回');\r
}\r
\r
async function deletePlan(id) {\r
    const plan = plans.find(item => String(item.planId) === String(id));\r
    const name = plan?.nodeName || '该履约节点';\r
    if (!window.confirm(\`确认删除“\${name}”吗？删除后该节点的提醒记录也会移除。\`)) return;\r
    await api(\`/api/fulfillment/plans/\${id}\`, {method: 'DELETE'});\r
    await refreshAll();\r
    toast('履约节点已删除');\r
}\r
\r
let keywordTimer;\r
ensureDeliverableUi();\r
$('#planKeyword').addEventListener('input', () => {\r
    clearTimeout(keywordTimer);\r
    keywordTimer = setTimeout(() => loadPlans().catch(error => toast(error.message)), 250);\r
});\r
$('#filterContract').addEventListener('change', () => refreshAll().catch(error => toast(error.message)));\r
$('#filterStatus').addEventListener('change', () => loadPlans().catch(error => toast(error.message)));\r
$('#resetBtn').addEventListener('click', () => {\r
    $('#planKeyword').value = '';\r
    $('#filterContract').value = '';\r
    $('#filterStatus').value = '';\r
    refreshAll().catch(error => toast(error.message));\r
});\r
\r
$('#newPlanBtn').addEventListener('click', () => openPlanModal());\r
$('#newDeliverableBtn').addEventListener('click', () => openDeliverableModal());\r
$('#newPaymentPlanBtn').addEventListener('click', () => openPaymentPlanModal());\r
$('#closePlanModal').addEventListener('click', closePlanModal);\r
$('#closeDeliverableModal').addEventListener('click', closeDeliverableModal);\r
$('#closeDeliverableFileModal').addEventListener('click', closeDeliverableFileModal);\r
$('#closeDeliverableTransitionModal').addEventListener('click', closeDeliverableTransitionModal);\r
$('#closeVoucherModal').addEventListener('click', closeVoucherModal);\r
$('#closeOverdueModal').addEventListener('click', closeOverdueModal);\r
$('#closePaymentPlanModal').addEventListener('click', closePaymentPlanModal);\r
$('#closePaymentRecordModal').addEventListener('click', closePaymentRecordModal);\r
$('#planForm').addEventListener('submit', event => savePlan(event).catch(error => toast(error.message)));\r
$('#deliverableForm').addEventListener('submit', event => saveDeliverable(event).catch(error => toast(error.message)));\r
$('#deliverableFileForm').addEventListener('submit', event => uploadDeliverableFile(event).catch(error => toast(error.message)));\r
$('#deliverableTransitionForm').addEventListener('submit', event => saveDeliverableTransition(event).catch(error => toast(error.message)));\r
$('#voucherForm').addEventListener('submit', event => uploadVoucher(event).catch(error => toast(error.message)));\r
$('#overdueForm').addEventListener('submit', event => saveOverdueHandle(event).catch(error => toast(error.message)));\r
$('#paymentPlanForm').addEventListener('submit', event => savePaymentPlan(event).catch(error => toast(error.message)));\r
$('#paymentRecordForm').addEventListener('submit', event => savePaymentRecord(event).catch(error => toast(error.message)));\r
$('#paymentContract').addEventListener('change', () => {\r
    renderPaymentRatioCheck();\r
    loadPrerequisiteDeliveryOptions($('#paymentContract').value)\r
        .catch(error => toast(error.message));\r
});\r
$('#deliverableContract').addEventListener('change', () => fillDeliverablePlanSelect($('#deliverableContract').value));\r
$('#percentage').addEventListener('input', renderPaymentRatioCheck);\r
$('#initMemberEBtn').addEventListener('click', () => initializeMemberE().catch(error => toast(error.message)));\r
$('#extractBtn').addEventListener('click', () => extractPlans().catch(error => toast(error.message)));\r
$('#dispatchBtn').addEventListener('click', () => dispatchReminders().catch(error => toast(error.message)));\r
$('#refreshReminderBtn').addEventListener('click', () => refreshAll().catch(error => toast(error.message)));\r
$('#overdueAction').addEventListener('change', toggleOverdueFields);\r
$('#planStatus').addEventListener('change', syncActualCompletedDateField);\r
$('#progressRange').addEventListener('input', () => $('#progressValue').value = $('#progressRange').value);\r
$('#progressValue').addEventListener('input', () => $('#progressRange').value = $('#progressValue').value);\r
\r
$('#planTbody').addEventListener('click', event => {\r
    const editBtn = event.target.closest('.edit-btn');\r
    const completeBtn = event.target.closest('.complete-btn');\r
    const voucherBtn = event.target.closest('.voucher-btn');\r
    const deleteBtn = event.target.closest('.delete-plan-btn');\r
    const confirmDelayBtn = event.target.closest('.confirm-delay-btn');\r
    const rejectDelayBtn = event.target.closest('.reject-delay-btn');\r
    const handleBtn = event.target.closest('.handle-btn');\r
    if (editBtn) {\r
        const plan = plans.find(item => String(item.planId) === String(editBtn.dataset.id));\r
        if (plan) openPlanModal(plan);\r
    }\r
    if (completeBtn) completePlan(completeBtn.dataset.id).catch(error => toast(error.message));\r
    if (voucherBtn) openVoucherModal(voucherBtn.dataset.id).catch(error => toast(error.message));\r
    if (deleteBtn) deletePlan(deleteBtn.dataset.id).catch(error => toast(error.message));\r
    if (confirmDelayBtn) confirmDelay(confirmDelayBtn.dataset.id).catch(error => toast(error.message));\r
    if (rejectDelayBtn) rejectDelay(rejectDelayBtn.dataset.id).catch(error => toast(error.message));\r
    if (handleBtn) handleOverdue(handleBtn.dataset.id).catch(error => toast(error.message));\r
});\r
\r
$('#voucherTbody').addEventListener('click', event => {\r
    const downloadBtn = event.target.closest('.download-voucher-btn');\r
    const reviewBtn = event.target.closest('.review-voucher-btn');\r
    if (downloadBtn) {\r
        downloadFile(downloadBtn.dataset.url, downloadBtn.dataset.name || 'voucher')\r
            .catch(error => toast(error.message));\r
    }\r
    if (reviewBtn) {\r
        reviewVoucher(reviewBtn.dataset.id, reviewBtn.dataset.approved === 'true')\r
            .catch(error => toast(error.message));\r
    }\r
});\r
\r
$('#deliverableTbody').addEventListener('click', event => {\r
    const editBtn = event.target.closest('.edit-deliverable-btn');\r
    const uploadBtn = event.target.closest('.upload-deliverable-file-btn');\r
    const deleteBtn = event.target.closest('.delete-deliverable-btn');\r
    const downloadBtn = event.target.closest('.download-deliverable-file-btn');\r
    const transitionBtn = event.target.closest('.deliverable-transition-btn');\r
    if (editBtn) {\r
        const item = deliverables.find(row => String(row.deliverableId) === String(editBtn.dataset.id));\r
        if (item) openDeliverableModal(item);\r
    }\r
    if (uploadBtn) openDeliverableFileModal(uploadBtn.dataset.id);\r
    if (deleteBtn) deleteDeliverable(deleteBtn.dataset.id).catch(error => toast(error.message));\r
    if (transitionBtn) openDeliverableTransitionModal(transitionBtn.dataset.id, transitionBtn.dataset.action);\r
    if (downloadBtn) {\r
        downloadFile(downloadBtn.dataset.url, downloadBtn.dataset.name || 'deliverable')\r
            .catch(error => toast(error.message));\r
    }\r
});\r
\r
$('#paymentPlanTbody').addEventListener('click', event => {\r
    const editBtn = event.target.closest('.edit-payment-btn');\r
    const recordBtn = event.target.closest('.record-payment-btn');\r
    const invoiceBtn = event.target.closest('.invoice-btn');\r
    const deleteBtn = event.target.closest('.delete-payment-plan-btn');\r
    if (editBtn) {\r
        const plan = paymentPlans.find(item => String(item.paymentPlanId) === String(editBtn.dataset.id));\r
        if (plan) openPaymentPlanModal(plan);\r
    }\r
    if (recordBtn) openPaymentRecordModal(recordBtn.dataset.id);\r
    if (invoiceBtn) createInvoiceForPlan(invoiceBtn.dataset.id).catch(error => toast(error.message));\r
    if (deleteBtn) deletePaymentPlan(deleteBtn.dataset.id).catch(error => toast(error.message));\r
});\r
\r
$('#paymentRecordTbody').addEventListener('click', event => {\r
    const deleteBtn = event.target.closest('.delete-payment-record-btn');\r
    const downloadBtn = event.target.closest('.download-payment-voucher-btn');\r
    if (deleteBtn) deletePaymentRecord(deleteBtn.dataset.id).catch(error => toast(error.message));\r
    if (downloadBtn) {\r
        downloadFile(downloadBtn.dataset.url, downloadBtn.dataset.name || '付款凭证')\r
            .catch(error => toast(error.message));\r
    }\r
});\r
\r
$('#invoiceTbody')?.addEventListener('click', event => {\r
    const uploadBtn = event.target.closest('.upload-invoice-file-btn');\r
    const downloadBtn = event.target.closest('.download-invoice-file-btn');\r
    if (uploadBtn) uploadInvoiceFile(uploadBtn.dataset.id).catch(error => toast(error.message));\r
    if (downloadBtn) {\r
        downloadFile(downloadBtn.dataset.url, downloadBtn.dataset.name || '发票附件')\r
            .catch(error => toast(error.message));\r
    }\r
});\r
\r
loadContracts()\r
    .then(refreshAll)\r
    .catch(error => toast(error.message));\r
`;export{e as default};

if (!initAppShell('dashboard', '工作台', '合同全生命周期概览，待办事项与数据统计')) throw new Error('auth required');

const EXPIRY_WARNING_DAYS = 60;
const TYPE_COLORS = ['#0f766e', '#2563eb', '#d97706', '#7c3aed', '#eab308', '#64748b'];
const STATUS_COLORS = {DRAFT:'#94a3b8', APPROVING:'#3b82f6', APPROVED:'#8b5cf6', SIGNING:'#f97316', EXECUTING:'#06b6d4', COMPLETED:'#059669', ARCHIVED:'#16a34a'};
const METRIC_IDS = ['totalContracts', 'approvingContracts', 'highRiskContracts', 'dueSoonPlans'];
const CHART_IDS = ['monthlyTrendChart', 'typeDistChart', 'statusDistChart'];

function fmt(value) {
    return value != null ? Number(value).toLocaleString('zh-CN') : '0';
}

function renderTrend(id, text, positive = true) {
    const element = document.getElementById(id);
    element.className = `metric-trend ${positive ? 'up' : 'down'}`;
    element.innerHTML = `<span class="trend-arrow">${positive ? '↑' : '↓'}</span><span>${escapeHtml(text)}</span>`;
}

function spinnerMarkup() {
    return '<div class="chart-loading" aria-label="数据加载中"><span class="loading-spinner"></span></div>';
}

function setDashboardLoading() {
    METRIC_IDS.forEach(id => {
        const element = document.getElementById(id);
        element.textContent = '';
        element.classList.add('skeleton', 'skeleton-pulse');
    });
    CHART_IDS.forEach(id => { document.getElementById(id).innerHTML = spinnerMarkup(); });
}

function clearMetricLoading() {
    METRIC_IDS.forEach(id => document.getElementById(id).classList.remove('skeleton', 'skeleton-pulse'));
}

function renderPanelError(containerIds, retryHandler) {
    containerIds.forEach(id => {
        const container = document.getElementById(id);
        container.innerHTML = '<button class="panel-error" type="button">数据加载失败，点击重试</button>';
        container.querySelector('.panel-error').addEventListener('click', retryHandler, {once: true});
    });
}

async function loadDashboard(dataPromise = api('/api/dashboard')) {
    METRIC_IDS.forEach(id => {
        const element = document.getElementById(id);
        element.textContent = '';
        element.classList.add('skeleton', 'skeleton-pulse');
    });
    try {
        const data = await dataPromise;
        clearMetricLoading();
        $('#totalContracts').textContent = fmt(data.totalContracts);
        $('#approvingContracts').textContent = fmt(data.approvingContracts);
        $('#highRiskContracts').textContent = fmt(data.highRiskContracts);
        $('#dueSoonPlans').textContent = fmt(data.dueSoonPlans);
        renderTrend('trendTotal', `合同总额 ¥${fmt(data.totalAmount)}`);
        renderTrend('trendApproving', '审批流程处理中');
        renderTrend('trendRisk', '需优先复核', false);
        renderTrend('trendDue', '履约节点提醒');
        renderStatusDist(toDistribution(data.statusDistribution));
    } catch {
        clearMetricLoading();
        const retry = () => {
            $('#statusDistChart').innerHTML = spinnerMarkup();
            loadDashboard();
        };
        METRIC_IDS.forEach(id => {
            const element = document.getElementById(id);
            element.innerHTML = '<button class="metric-error" type="button">加载失败，点击重试</button>';
            element.querySelector('.metric-error').addEventListener('click', event => {
                event.preventDefault();
                retry();
            }, {once: true});
        });
        renderPanelError(['statusDistChart'], retry);
    }
}

async function loadContractPanels(contractsPromise = api('/api/contracts')) {
    try {
        const contracts = await contractsPromise;
        const list = Array.isArray(contracts) ? contracts : [];
        renderMonthlyTrend(buildMonthlyTrend(list));
        renderTypeDist(buildTypeDistribution(list));
        renderExpiryWarnings(buildExpiryWarnings(list));
    } catch {
        renderPanelError(['monthlyTrendChart', 'typeDistChart', 'expiryWarnings'], () => {
            $('#monthlyTrendChart').innerHTML = spinnerMarkup();
            $('#typeDistChart').innerHTML = spinnerMarkup();
            loadContractPanels();
        });
    }
}

async function loadPendingTasks(tasksPromise = Promise.all([api('/api/approvals'), api('/api/fulfillment-plans')])) {
    try {
        const [approvalsValue, plansValue] = await tasksPromise;
        const approvals = Array.isArray(approvalsValue) ? approvalsValue : [];
        const plans = Array.isArray(plansValue) ? plansValue : [];
        renderPendingTasks([
            ...approvals.filter(item => item.status === 'RUNNING').slice(0, 4).map(item => ({
                taskType: 'APPROVAL', title: item.currentNode || '合同审批待处理',
                contractNo: `合同 #${item.contractId || '-'}`, contractId: item.contractId, priority: 'HIGH'
            })),
            ...plans.filter(item => !['FULFILLED', 'COMPLETED'].includes(item.status)).slice(0, 4).map(item => ({
                taskType: 'FULFILLMENT', title: item.planName || item.title || '履约计划待跟进',
                contractNo: `合同 #${item.contractId || '-'}`, contractId: item.contractId, priority: 'MEDIUM'
            }))
        ]);
    } catch {
        renderPanelError(['pendingTasks'], () => loadPendingTasks());
    }
}

function buildMonthlyTrend(contracts) {
    const now = new Date();
    const months = Array.from({length: 6}, (_, index) => {
        const date = new Date(now.getFullYear(), now.getMonth() - 5 + index, 1);
        return {key: `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`, label: `${date.getMonth() + 1}月`, count: 0};
    });
    contracts.forEach(contract => {
        const month = months.find(item => item.key === String(contract.createdAt || '').slice(0, 7));
        if (month) month.count++;
    });
    return months;
}

function buildTypeDistribution(contracts) {
    const counts = new Map();
    contracts.forEach(contract => {
        const type = contract.type || 'OTHER';
        counts.set(type, (counts.get(type) || 0) + 1);
    });
    const total = Math.max(contracts.length, 1);
    return [...counts.entries()].map(([type, count]) => ({type, typeName: typeLabel(type), count, percentage: Math.round(count / total * 100)}));
}

function buildExpiryWarnings(contracts) {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return contracts.map(contract => {
        const daysLeft = Math.ceil((new Date(contract.dueDate) - today) / 86400000);
        return {...contract, daysLeft};
    }).filter(item => Number.isFinite(item.daysLeft) && item.daysLeft >= 0 && item.daysLeft <= EXPIRY_WARNING_DAYS)
        .sort((a, b) => a.daysLeft - b.daysLeft).slice(0, 8);
}

function renderMonthlyTrend(items) {
    const max = Math.max(...items.map(item => item.count), 1);
    $('#monthlyTrendChart').innerHTML = items.map(item => `<div class="bar-col"><div class="bar-fill" style="height:${Math.max(item.count / max * 100, 3)}%"><span class="bar-tooltip">${item.count} 份</span></div><span class="bar-label">${item.label}</span></div>`).join('');
}

function renderTypeDist(items) {
    const container = $('#typeDistChart');
    if (!items.length) {
        container.innerHTML = '<div class="empty-state">暂无合同类型数据</div>';
        return;
    }
    const max = Math.max(...items.map(item => item.count), 1);
    container.innerHTML = items.map((item, index) => `<div class="hbar-row"><span class="hbar-label">${escapeHtml(item.typeName)}</span><div class="hbar-track"><div class="hbar-fill" style="width:${item.count / max * 100}%;background:${TYPE_COLORS[index % TYPE_COLORS.length]}">${item.percentage}%</div></div><span class="hbar-count">${item.count} 份</span></div>`).join('');
}

function renderStatusDist(items) {
    const container = $('#statusDistChart');
    if (!items.length) {
        container.innerHTML = '<div class="empty-state">暂无合同状态数据</div>';
        return;
    }
    const total = items.reduce((sum, item) => sum + item.count, 0);
    const circumference = 2 * Math.PI * 56;
    let offset = 0;
    const arcs = items.map((item, index) => {
        const length = total ? item.count / total * circumference : 0;
        const percentage = total ? Math.round(item.count / total * 100) : 0;
        const color = STATUS_COLORS[item.status] || '#64748b';
        const arc = `<circle class="donut-arc" data-arc-index="${index}" cx="70" cy="70" r="56" fill="none" stroke="${color}" stroke-width="20" stroke-dasharray="${length} ${circumference - length}" stroke-dashoffset="${-offset}"><title>${escapeHtml(`${item.statusName}：${item.count} 份（${percentage}%）`)}</title></circle>`;
        offset += length;
        return {item, color, arc, index};
    });
    container.innerHTML = `<div class="donut-visual"><svg viewBox="0 0 140 140">${arcs.map(entry => entry.arc).join('')}</svg><div class="donut-center"><strong>${total}</strong><small>合同总数</small></div></div><div class="donut-legend">${arcs.map(({item, color, index}) => `<button class="donut-legend-item" data-arc-index="${index}" type="button"><span class="donut-legend-dot" style="background:${color}"></span><span class="donut-legend-label">${escapeHtml(item.statusName)}</span><span class="donut-legend-count">${item.count}</span></button>`).join('')}</div>`;
    $$('.donut-legend-item', container).forEach(legend => {
        legend.addEventListener('click', () => {
            const isActive = legend.classList.contains('active');
            $$('.donut-legend-item, .donut-arc', container).forEach(element => element.classList.remove('active'));
            if (!isActive) {
                legend.classList.add('active');
                container.querySelector(`.donut-arc[data-arc-index="${legend.dataset.arcIndex}"]`)?.classList.add('active');
            }
        });
    });
}

function renderExpiryWarnings(items) {
    const footer = '<a class="panel-more-link" href="fulfillment.html">查看全部到期合同 →</a>';
    if (!items.length) {
        $('#expiryWarnings').innerHTML = `<div class="empty-state">暂无到期预警</div>${footer}`;
        return;
    }
    $('#expiryWarnings').innerHTML = items.map(item => {
        const urgency = item.daysLeft < 7 ? 'urgent' : item.daysLeft < 30 ? 'caution' : 'notice';
        const daysColor = item.daysLeft < 7 ? 'red' : item.daysLeft < 30 ? 'orange' : 'yellow';
        return `<div class="warn-item ${urgency}"><div class="warn-body"><div class="warn-title">${escapeHtml(item.title || item.contractNo || '未命名合同')}</div><div class="warn-meta"><span>${escapeHtml(item.counterparty || '未填写相对方')}</span><span>·</span><span>到期 ${escapeHtml(item.dueDate)}</span><span class="warn-days ${daysColor}">剩余 ${item.daysLeft} 天</span></div></div></div>`;
    }).join('') + footer;
}

function renderPendingTasks(items) {
    if (!items.length) {
        $('#pendingTasks').innerHTML = '<div class="empty-state">暂无待办任务</div>';
        return;
    }
    $('#pendingTasks').innerHTML = items.map(item => {
        const href = item.taskType === 'APPROVAL' ? `approval.html?id=${encodeURIComponent(item.contractId || '')}` : `fulfillment.html?id=${encodeURIComponent(item.contractId || '')}`;
        return `<div class="task-item"><div class="task-type-icon ${item.taskType}">${item.taskType === 'APPROVAL' ? '审批' : '履约'}</div><div class="task-body"><div class="task-title">${escapeHtml(item.title)}</div><div class="task-contract">${escapeHtml(item.contractNo)}</div></div><span class="task-priority ${item.priority}">${item.priority === 'HIGH' ? '高' : '中'}</span><a class="task-action" href="${href}">${item.taskType === 'APPROVAL' ? '去审批' : '查看'}</a></div>`;
    }).join('');
}

function toDistribution(items) {
    return (items || []).map(item => ({status: String(item.name || 'UNKNOWN'), statusName: STATUS_TEXT[item.name] || item.name || '未知', count: Number(item.value) || 0})).filter(item => item.count > 0);
}

function typeLabel(type) {
    return ({PURCHASE:'采购合同', SALES:'销售合同', TECH:'技术合同', LABOR:'劳务合同', CONFIDENTIAL:'保密合同', LOGISTICS:'物流合同', ENTERPRISE_SERVICE:'企业服务合同', INTELLECTUAL_PROPERTY:'知识产权合同', OTHER:'其他'})[type] || type;
}

function loadAllPanels() {
    setDashboardLoading();
    return Promise.allSettled([
        loadDashboard(api('/api/dashboard')),
        loadContractPanels(api('/api/contracts')),
        loadPendingTasks(Promise.all([api('/api/approvals'), api('/api/fulfillment-plans')]))
    ]).then(renderLucideIcons);
}

loadAllPanels();

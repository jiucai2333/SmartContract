if (!initAppShell('dashboard', '工作台', '合同全生命周期概览，待办事项与数据统计')) throw new Error('auth required');

const EXPIRY_WARNING_DAYS = 60;
const TYPE_COLORS = ['#0f766e', '#2563eb', '#7c3aed', '#d97706', '#059669', '#64748b'];
const STATUS_COLORS = {DRAFT:'#94a3b8', APPROVING:'#3b82f6', APPROVED:'#8b5cf6', SIGNING:'#f97316', EXECUTING:'#06b6d4', COMPLETED:'#059669', ARCHIVED:'#16a34a'};
const METRIC_IDS = ['totalContracts', 'approvingContracts', 'highRiskContracts', 'dueSoonPlans'];
const CHART_IDS = ['monthlyTrendChart', 'typeDistChart', 'statusDistChart'];
const STATE_ICON = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7.5h16M7 4.5h10a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-11a2 2 0 0 1 2-2Z"/><path d="M8 12h8M8 15h5"/></svg>';
const ERROR_ICON = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 8v5"/><path d="M12 17h.01"/><path d="M10.3 4.2 2.7 17.4A2 2 0 0 0 4.4 20h15.2a2 2 0 0 0 1.7-2.6L13.7 4.2a2 2 0 0 0-3.4 0Z"/></svg>';
const ALL_WORKFLOW_STAGES = {
    draft: '/edit',
    risk: '/risk',
    approval: '/approval',
    seal: '/seal',
    fulfillment: '/fulfillment'
};
const DASHBOARD_ROLE_CONFIG = {
    USER: {
        stages: ALL_WORKFLOW_STAGES,
        approvalMetricLabel: '我的审批中',
        approvalMetricHref: '/ledger?status=APPROVING',
        riskMetricHref: '/risk',
        dueMetricHref: '/fulfillment',
        actionableApprovalNodes: []
    },
    DEPT_LEADER: {
        stages: ALL_WORKFLOW_STAGES,
        approvalMetricLabel: '待办审批',
        approvalMetricHref: '/approval',
        riskMetricHref: '/risk',
        dueMetricHref: '/fulfillment',
        actionableApprovalNodes: ['部门主管审批']
    },
    LEGAL: {
        stages: ALL_WORKFLOW_STAGES,
        approvalMetricLabel: '待办审批',
        approvalMetricHref: '/approval',
        riskMetricHref: '/risk',
        dueMetricHref: '/fulfillment',
        actionableApprovalNodes: ['法务专员审批', '法务复核']
    },
    FINANCE: {
        stages: ALL_WORKFLOW_STAGES,
        approvalMetricLabel: '待办审批',
        approvalMetricHref: '/approval',
        riskMetricHref: '/ledger?riskLevel=HIGH',
        dueMetricHref: '/fulfillment',
        actionableApprovalNodes: ['财务专员审批', '财务审批']
    },
    EXECUTIVE: {
        stages: ALL_WORKFLOW_STAGES,
        approvalMetricLabel: '待办审批',
        approvalMetricHref: '/approval',
        riskMetricHref: '/risk',
        dueMetricHref: '/fulfillment',
        actionableApprovalNodes: ['企业高管审批', '高管审批']
    },
    ADMIN: {
        stages: ALL_WORKFLOW_STAGES,
        approvalMetricLabel: '审批中',
        approvalMetricHref: '/ledger?status=APPROVING',
        riskMetricHref: '/ledger?riskLevel=HIGH',
        dueMetricHref: '/ledger',
        actionableApprovalNodes: []
    }
};
const dashboardRoleConfig = DASHBOARD_ROLE_CONFIG[state.roleCode] || DASHBOARD_ROLE_CONFIG.USER;

function fmt(value) {
    return value != null ? Number(value).toLocaleString('zh-CN') : '0';
}

function emptyMarkup(title, message = '暂无可展示的数据。') {
    return `<div class="dashboard-state empty-state">${STATE_ICON}<strong>${escapeHtml(title)}</strong><span>${escapeHtml(message)}</span></div>`;
}

function errorMarkup(message = '数据加载失败') {
    return `<div class="dashboard-state dashboard-error">${ERROR_ICON}<strong>${escapeHtml(message)}</strong><button class="state-retry" type="button">重试</button></div>`;
}

function renderTrend(id, text, positive = true) {
    const element = document.getElementById(id);
    element.className = 'metric-trend';
    element.textContent = text;
}

function greetingText(taskCount = null) {
    const hour = new Date().getHours();
    const prefix = hour < 11 ? '早上好' : hour < 18 ? '下午好' : '晚上好';
    const username = state?.username || 'admin';
    const taskText = taskCount == null
        ? '今日待办加载中'
        : taskCount === 0
            ? '今日暂无待办~'
            : `今日有 ${taskCount} 项待办`;
    return `${prefix}，${username} · ${taskText}`;
}

function updateHeroGreeting(taskCount = null) {
    const element = document.getElementById('heroGreeting');
    if (element) element.textContent = greetingText(taskCount);
}

// Global stores — populated by loaders, read by updateFlowStage
let _stepTasks = { approval: [], fulfillment: [] };
let _dashboardData = {};

function configureDashboardForRole() {
    const metricLinks = document.querySelectorAll('.metric-grid .metric');
    const approvalMetric = metricLinks[1];
    const riskMetric = metricLinks[2];
    const dueMetric = metricLinks[3];
    if (approvalMetric) {
        const label = approvalMetric.querySelector(':scope > span:not(.metric-go)');
        if (label) label.textContent = dashboardRoleConfig.approvalMetricLabel;
        approvalMetric.href = dashboardRoleConfig.approvalMetricHref;
    }
    if (riskMetric) riskMetric.href = dashboardRoleConfig.riskMetricHref;
    if (dueMetric) dueMetric.href = dashboardRoleConfig.dueMetricHref;

    $$('.hero-step').forEach(step => {
        const href = dashboardRoleConfig.stages[step.dataset.flowStep];
        if (href) {
            step.href = href;
            step.classList.remove('is-disabled');
            step.removeAttribute('aria-disabled');
            step.removeAttribute('tabindex');
        } else {
            step.removeAttribute('href');
            step.classList.add('is-disabled');
            step.setAttribute('aria-disabled', 'true');
            step.setAttribute('tabindex', '-1');
        }
    });
}

function updateFlowStage(data = {}, tasksByStep = _stepTasks) {
    $$('.hero-step').forEach(step => {
        const stepKey = step.dataset.flowStep;
        const tasks = tasksByStep[stepKey] || [];
        const taskCount = tasks.length;

        // Two states only: has-tasks or nothing
        step.classList.toggle('has-tasks', taskCount > 0);

        // Dot — clear any old icon content (CSS handles the two visual states)
        const dot = step.querySelector('.step-dot');
        if (dot) dot.innerHTML = '';

        // Capsule
        let capEl = step.querySelector('.step-capsule');
        if (!capEl) {
            const oldInfo = step.querySelector('.step-info');
            if (oldInfo) oldInfo.remove();
            capEl = document.createElement('span');
            capEl.className = 'step-capsule';
            step.appendChild(capEl);
        }

        if (taskCount > 0) {
            capEl.className = 'step-capsule has-tasks';
            capEl.textContent = `${taskCount}个待办`;
        } else {
            capEl.className = 'step-capsule';
            capEl.textContent = '';
        }

    });

    renderLucideIcons();
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
        container.innerHTML = errorMarkup();
        container.querySelector('.state-retry').addEventListener('click', retryHandler, {once: true});
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
        _dashboardData = data;
        clearMetricLoading();
        $('#totalContracts').textContent = fmt(data.totalContracts);
        $('#approvingContracts').textContent = fmt(data.approvingContracts);
        $('#highRiskContracts').textContent = fmt(data.highRiskContracts);
        $('#dueSoonPlans').textContent = fmt(data.dueSoonPlans);
        renderTrend('trendTotal', `合同总额 ¥${fmt(data.totalAmount)}`);
        renderTrend('trendApproving', '审批流程处理中');
        renderTrend('trendRisk', '需优先复核', false);
        renderTrend('trendDue', '履约节点提醒');
        updateFlowStage(data);
        renderStatusDist(toDistribution(data.statusDistribution));
    } catch {
        clearMetricLoading();
        const retry = () => {
            $('#statusDistChart').innerHTML = spinnerMarkup();
            loadDashboard();
        };
        METRIC_IDS.forEach(id => {
            const element = document.getElementById(id);
            element.innerHTML = '<button class="metric-error state-retry" type="button">重试加载</button>';
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

async function loadPendingTasks(tasksPromise = Promise.all([api('/api/approvals'), api('/api/fulfillment/plans')])) {
    try {
        const [approvalsValue, plansValue] = await tasksPromise;
        const approvals = Array.isArray(approvalsValue) ? approvalsValue : [];
        const plans = Array.isArray(plansValue) ? plansValue : [];
        const runningApprovals = approvals.filter(item => item.status === 'RUNNING');
        const approvalTasks = runningApprovals
            .filter(item => dashboardRoleConfig.actionableApprovalNodes.includes(item.currentNode))
            .map(item => ({
            taskType: 'APPROVAL', title: item.currentNode || '合同审批待处理',
            contractNo: `合同 #${item.contractId || '-'}`, contractId: item.contractId, priority: 'HIGH'
        }));
        const canHandleFulfillment = ['DEPT_LEADER', 'FINANCE'].includes(state.roleCode);
        const fulfillmentTasks = (canHandleFulfillment ? plans : [])
            .filter(item => !['FULFILLED', 'COMPLETED'].includes(item.status)).map(item => ({
            taskType: 'FULFILLMENT', title: item.nodeName || item.contractTitle || '履约计划待跟进',
            contractNo: `合同 #${item.contractId || '-'}`, contractId: item.contractId, priority: 'MEDIUM'
        }));
        const approvalMetricCount = state.roleCode === 'USER' || state.roleCode === 'ADMIN'
            ? runningApprovals.length
            : approvalTasks.length;
        $('#approvingContracts').textContent = fmt(approvalMetricCount);
        const allTasks = [...approvalTasks, ...fulfillmentTasks];
        const totalCount = allTasks.length;
        updateHeroGreeting(totalCount);

        // Store tasks per step for flyout integration
        _stepTasks = {
            draft: [],
            risk: [],
            approval: approvalTasks,
            seal: [],
            fulfillment: fulfillmentTasks,
        };

        // Re-run flow stage update to show capsules with real task counts
        updateFlowStage(_dashboardData, _stepTasks);
    } catch {
        // Silently degrade — capsules just won't show task counts
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
    const all = contracts.map(contract => {
        const daysLeft = Math.ceil((new Date(contract.dueDate) - today) / 86400000);
        return {...contract, daysLeft};
    }).filter(item => Number.isFinite(item.daysLeft) && item.daysLeft >= 0 && item.daysLeft <= EXPIRY_WARNING_DAYS)
        .sort((a, b) => a.daysLeft - b.daysLeft);
    return { items: all.slice(0, 15), total: all.length, totalAmount: all.reduce((sum, item) => sum + (Number(item.amount) || 0), 0) };
}

function renderMonthlyTrend(items) {
    const max = Math.max(...items.map(item => item.count), 1);
    $('#monthlyTrendChart').innerHTML = items.map(item => `<div class="bar-col"><span class="bar-value">${item.count}</span><div class="bar-fill" style="height:${Math.max(item.count / max * 100, 3)}%"></div><span class="bar-label">${item.label}</span></div>`).join('');
}

function renderTypeDist(items) {
    const container = $('#typeDistChart');
    if (!items.length) {
        container.innerHTML = emptyMarkup('暂无合同类型数据', '新建合同后，这里会显示不同类型的占比。');
        return;
    }
    const max = Math.max(...items.map(item => item.count), 1);
    container.innerHTML = items.map((item, index) => `<div class="hbar-row"><span class="hbar-label">${escapeHtml(item.typeName)}</span><div class="hbar-track"><div class="hbar-fill" style="width:${item.count / max * 100}%;background:${TYPE_COLORS[index % TYPE_COLORS.length]}">${item.percentage}%</div></div><span class="hbar-count">${item.count} 份</span></div>`).join('');
}

function renderStatusDist(items) {
    const container = $('#statusDistChart');
    if (!items.length) {
        container.innerHTML = emptyMarkup('暂无合同状态数据', '合同进入审批、签章或履约后会形成状态分布。');
        return;
    }
    const total = items.reduce((sum, item) => sum + item.count, 0);
    const circumference = 2 * Math.PI * 68;
    const gap = 2;

    // Build arcs with angle ranges for hover hit-testing.
    const arcData = [];
    let offset = 0;
    items.forEach((item, index) => {
        const rawLength = total ? item.count / total * circumference : 0;
        const length = Math.max(rawLength - gap, 0);
        const percentage = total ? Math.round(item.count / total * 100) : 0;
        const color = STATUS_COLORS[item.status] || '#64748b';
        // start/end angles in SVG rotated space (0° = top, clockwise)
        const startAngle = (offset / circumference) * 2 * Math.PI;
        const sweepAngle = (rawLength / circumference) * 2 * Math.PI;
        const centerAngle = startAngle + sweepAngle / 2;
        const tx =  Math.cos(centerAngle) * 6;
        const ty =  Math.sin(centerAngle) * 6;
        const arcHTML = `<circle class="donut-arc" data-arc-index="${index}" cx="80" cy="80" r="68" fill="none" stroke="${color}" stroke-width="16" stroke-linecap="butt" stroke-dasharray="${length} ${circumference - length}" stroke-dashoffset="${-offset}" style="cursor:pointer"></circle>`;
        arcData.push({ item, color, tx, ty, index, startAngle, endAngle: startAngle + sweepAngle });
        offset += rawLength;
        arcData[index]._html = arcHTML;
    });

    container.innerHTML = `<div class="donut-visual"><svg viewBox="-10 -10 180 180">${arcData.map(d => d._html).join('')}</svg><div class="donut-center" id="donutCenterText"><strong>${total}</strong><small>合同总数</small></div></div><div class="donut-legend">${arcData.map(({item, color, index}) => `<button class="donut-legend-item" data-arc-index="${index}" type="button"><span class="donut-legend-dot" style="background:${color}"></span><span class="donut-legend-label">${escapeHtml(item.statusName)}</span><span class="donut-legend-count">${item.count}</span></button>`).join('')}</div>`;

    const centerStrong = container.querySelector('#donutCenterText strong');
    const centerSmall  = container.querySelector('#donutCenterText small');
    const defaultStrongText = String(total);
    const defaultSmallText  = '合同总数';
    const defaultColor      = 'var(--ink)';

    let hoveredIndex = -1;

    function applyVisual(index) {
        const d = arcData[index];
        container.querySelectorAll('.donut-arc').forEach(circle => {
            const ci = Number(circle.dataset.arcIndex);
            if (ci === index) {
                circle.setAttribute('transform', `translate(${d.tx}, ${d.ty})`);
                circle.classList.add('active');
                circle.classList.remove('inactive');
            } else {
                circle.setAttribute('transform', '');
                circle.classList.add('inactive');
                circle.classList.remove('active');
            }
        });
        container.querySelectorAll('.donut-legend-item').forEach(li => {
            li.classList.toggle('active', Number(li.dataset.arcIndex) === index);
            li.classList.toggle('inactive', Number(li.dataset.arcIndex) !== index);
        });
        centerStrong.style.opacity = '0';
        centerSmall.style.opacity = '0';
        setTimeout(() => {
            centerStrong.textContent = String(d.item.count);
            centerStrong.style.color = d.color;
            centerSmall.textContent = d.item.statusName;
            centerStrong.style.opacity = '1';
            centerSmall.style.opacity = '1';
        }, 120);
    }

    function resetVisual() {
        hoveredIndex = -1;
        container.classList.remove('has-active-arc');
        container.querySelectorAll('.donut-arc').forEach(circle => {
            circle.removeAttribute('transform');
            circle.classList.remove('active', 'inactive');
        });
        container.querySelectorAll('.donut-legend-item').forEach(li => {
            li.classList.remove('active', 'inactive');
        });
        centerStrong.style.opacity = '0';
        centerSmall.style.opacity = '0';
        setTimeout(() => {
            centerStrong.textContent = defaultStrongText;
            centerStrong.style.color = defaultColor;
            centerSmall.textContent = defaultSmallText;
            centerStrong.style.opacity = '1';
            centerSmall.style.opacity = '1';
        }, 120);
    }

    function onHoverEnter(index) {
        if (hoveredIndex === index) return;
        hoveredIndex = index;
        container.classList.add('has-active-arc');
        applyVisual(index);
    }

    function onHoverLeave() {
        resetVisual();
    }

    // Legend hover only
    $$('.donut-legend-item', container).forEach(legend => {
        legend.addEventListener('pointerenter', () => {
            onHoverEnter(Number(legend.dataset.arcIndex));
        });
        legend.addEventListener('pointerleave', () => {
            onHoverLeave();
        });
    });

    const visual = container.querySelector('.donut-visual');
    visual.addEventListener('pointermove', (event) => {
        const rect = visual.getBoundingClientRect();
        const cx = rect.left + rect.width / 2;
        const cy = rect.top + rect.height / 2;
        const x = event.clientX - cx;
        const y = event.clientY - cy;
        const radius = Math.sqrt(x * x + y * y);
        const scale = rect.width / 160;
        const inner = 58 * scale;
        const outer = 78 * scale;
        if (radius < inner || radius > outer) {
            onHoverLeave();
            return;
        }

        let angle = Math.atan2(y, x) + Math.PI / 2;
        if (angle < 0) angle += Math.PI * 2;
        if (angle >= Math.PI * 2) angle -= Math.PI * 2;
        const match = arcData.find(d => angle >= d.startAngle && angle <= d.endAngle);
        if (match) {
            onHoverEnter(match.index);
        } else {
            onHoverLeave();
        }
    });
    visual.addEventListener('pointerleave', () => {
        onHoverLeave();
    });
}

function renderExpiryWarnings(result) {
    const { items, total, totalAmount } = result;
    const footer = '<a class="panel-more-link" href="/fulfillment">查看全部到期合同 →</a>';
    if (!items.length) {
        $('#expiryWarnings').innerHTML = `${emptyMarkup('暂无到期预警', '未来 60 天内没有需要跟进的履约节点。')}${footer}`;
        upsertSummary(0, 0);
        return;
    }
    $('#expiryWarnings').innerHTML = items.map(item => {
        const urgency = item.daysLeft < 7 ? 'urgent' : item.daysLeft < 30 ? 'caution' : 'notice';
        return `<div class="warn-item ${urgency}"><div class="warn-body"><div class="warn-title">${escapeHtml(item.title || item.contractNo || '未命名合同')}</div><div class="warn-meta"><span>${escapeHtml(item.counterparty || '未填写相对方')}</span><span>到期 ${escapeHtml(item.dueDate)}</span></div></div><div class="warn-days">剩余 ${item.daysLeft} 天</div></div>`;
    }).join('') + footer;
    upsertSummary(total, totalAmount);
}

function upsertSummary(total, totalAmount) {
    const panel = document.querySelector('.warn-panel--full');
    if (!panel) return;
    let summaryEl = panel.querySelector('.warn-summary');
    if (!summaryEl) {
        summaryEl = document.createElement('div');
        summaryEl.className = 'warn-summary';
        panel.appendChild(summaryEl);
    }
    summaryEl.innerHTML = `60天内共 <strong>${total}</strong> 份合同到期 · 涉及金额 <strong>¥${fmt(totalAmount)}</strong>`;
}

function toDistribution(items) {
    return (items || []).map(item => ({status: String(item.name || 'UNKNOWN'), statusName: STATUS_TEXT[item.name] || item.name || '未知', count: Number(item.value) || 0})).filter(item => item.count > 0);
}

function typeLabel(type) {
    return ({PURCHASE:'采购合同', SALES:'销售合同', TECH:'技术合同', LABOR:'劳务合同', CONFIDENTIAL:'保密合同', LOGISTICS:'物流合同', ENTERPRISE_SERVICE:'企业服务合同', INTELLECTUAL_PROPERTY:'知识产权合同', OTHER:'其他'})[type] || type;
}

function startClock() {
    const el = document.getElementById('heroClock');
    if (!el) return;
    function tick() {
        const now = new Date();
        const y = now.getFullYear();
        const M = now.getMonth() + 1;
        const d = now.getDate();
        const weekdays = ['星期日','星期一','星期二','星期三','星期四','星期五','星期六'];
        const w = weekdays[now.getDay()];
        const hh = String(now.getHours()).padStart(2, '0');
        const mm = String(now.getMinutes()).padStart(2, '0');
        const ss = String(now.getSeconds()).padStart(2, '0');
        el.textContent = `${y}年${M}月${d}日 ${w} ${hh}:${mm}:${ss}`;
    }
    tick();
    return setInterval(tick, 1000);
}

function loadAllPanels() {
    configureDashboardForRole();
    setDashboardLoading();
    updateHeroGreeting(null);
    startClock();
    return loadDashboard(api('/api/dashboard'))
        .catch(() => {})
        .then(() => loadContractPanels(api('/api/contracts')).catch(() => {}))
        .then(() => loadPendingTasks(Promise.all([api('/api/approvals'), api('/api/fulfillment/plans')])).catch(() => {}))
        .then(renderLucideIcons);
}

loadAllPanels();

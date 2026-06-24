const t=`if (!initAppShell('audit', '审计日志', '查询操作留痕，登记并核查安全事件')) throw new Error('auth required');

let auditPage = 1;
const auditPageSize = 20;

const SECURITY_EVENT_LABELS = {
    CONTRACT_CREATE_OR_UPDATE: '合同创建/更新',
    VERSION_RESTORE: '版本恢复',
    AI_REVIEW: 'AI 审查',
    APPROVAL_ACTION: '审批动作',
    SIGN_FILE_UPLOAD: '签署文件上传',
    ARCHIVE: '归档',
    DELIVERY_PAYMENT_CHANGE: '履约/付款变更'
};

function toIsoLocal(value) {
    if (!value) return '';
    return value.length === 16 ? \`\${value}:00\` : value;
}

function formatTime(value) {
    if (!value) return '-';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value).replace('T', ' ');
    return date.toLocaleString('zh-CN', {hour12: false});
}

function readAuditFilters() {
    const params = new URLSearchParams({
        page: String(auditPage),
        size: String(auditPageSize)
    });
    const userId = $('#auditUserId').value.trim();
    const operation = $('#auditOperation').value.trim();
    const result = $('#auditResult').value;
    const start = toIsoLocal($('#auditStart').value);
    const end = toIsoLocal($('#auditEnd').value);
    if (userId) params.set('userId', userId);
    if (operation) params.set('operation', operation);
    if (result) params.set('result', result);
    if (start) params.set('start', start);
    if (end) params.set('end', end);
    return params;
}

function resultTag(result) {
    const value = String(result || '-').toUpperCase();
    const cls = value.includes('FAIL') || value.includes('ERROR') ? 'tag-red' : 'tag-green';
    return \`<span class="tag \${cls}">\${escapeHtml(value)}</span>\`;
}

function renderAuditRows(records) {
    const body = $('#auditTbody');
    if (!records.length) {
        renderTableState(body, 6, {
            title: '暂无审计日志',
            message: '换一组筛选条件，或执行一次受审计的业务操作后再查看。'
        });
        return;
    }
    body.innerHTML = records.map(row => \`
        <tr>
            <td>\${escapeHtml(formatTime(row.createdAt))}</td>
            <td>\${escapeHtml(row.userId ?? '-')}</td>
            <td><strong>\${escapeHtml(row.operation || '-')}</strong></td>
            <td>\${escapeHtml(row.targetType || '-')}\${row.targetId ? \` #\${escapeHtml(row.targetId)}\` : ''}</td>
            <td>\${escapeHtml(row.ip || '-')}</td>
            <td>\${resultTag(row.result)}</td>
        </tr>
    \`).join('');
}

function renderAuditPager(pageData) {
    const pager = $('#auditPager');
    const total = Number(pageData.total || 0);
    const pages = Math.max(1, Number(pageData.pages || Math.ceil(total / auditPageSize) || 1));
    auditPage = Math.min(Math.max(1, auditPage), pages);
    pager.innerHTML = \`
        <span class="pagination-info">共 \${total} 条，第 \${auditPage} / \${pages} 页</span>
        <div class="pagination-actions">
            <button type="button" class="secondary" data-audit-page="prev" \${auditPage <= 1 ? 'disabled' : ''}>上一页</button>
            <button type="button" class="secondary" data-audit-page="next" \${auditPage >= pages ? 'disabled' : ''}>下一页</button>
        </div>
    \`;
}

async function loadAuditLogs() {
    renderTableState($('#auditTbody'), 6, {type: 'loading', title: '正在加载审计日志'});
    const data = await api(\`/api/admin/audit-logs?\${readAuditFilters()}\`);
    const records = Array.isArray(data?.records) ? data.records : [];
    $('#auditTotal').textContent = String(data?.total ?? records.length);
    renderAuditRows(records);
    renderAuditPager(data || {});
}

function eventTypeLabel(type) {
    return SECURITY_EVENT_LABELS[type] || type || '-';
}

function renderSecurityEvents(events) {
    const container = $('#securityEventList');
    $('#securityEventTotal').textContent = String(events.length);
    if (!events.length) {
        renderListState(container, {
            title: '暂无安全事件',
            message: '登记一次 AI 审查、审批或归档类事件后，会在这里显示脱敏记录。'
        });
        return;
    }
    container.innerHTML = events.slice(0, 12).map(event => \`
        <article class="audit-event-item">
            <div class="audit-event-head">
                <strong>\${escapeHtml(eventTypeLabel(event.eventType))}</strong>
                <span>\${escapeHtml(formatTime(event.recordedAt))}</span>
            </div>
            <p>\${escapeHtml(event.summary || '未填写摘要')}</p>
            <dl>
                <div><dt>目标</dt><dd>\${escapeHtml(event.targetType || '-')}\${event.targetId ? \` #\${escapeHtml(event.targetId)}\` : ''}</dd></div>
                <div><dt>操作人</dt><dd>\${escapeHtml(event.operator || '-')}</dd></div>
            </dl>
            \${event.maskedPayload ? \`<pre>\${escapeHtml(event.maskedPayload)}</pre>\` : ''}
        </article>
    \`).join('');
}

async function loadSecurityEvents() {
    renderListState($('#securityEventList'), {type: 'loading', title: '正在加载安全事件'});
    const events = await api('/api/security-events');
    renderSecurityEvents(Array.isArray(events) ? events : []);
}

async function submitSecurityEvent(event) {
    event.preventDefault();
    const button = $('#securityEventSubmitBtn');
    setButtonBusy(button, true, '登记中...');
    try {
        await api('/api/security-events', {
            method: 'POST',
            body: JSON.stringify({
                eventType: $('#securityEventType').value,
                targetType: $('#securityTargetType').value.trim(),
                targetId: $('#securityTargetId').value.trim(),
                summary: $('#securitySummary').value.trim(),
                payload: $('#securityPayload').value.trim()
            })
        });
        $('#securityEventForm').reset();
        toast('安全事件已登记');
        await loadSecurityEvents();
    } finally {
        setButtonBusy(button, false);
    }
}

$('#auditSearchBtn').addEventListener('click', () => {
    auditPage = 1;
    loadAuditLogs().catch(error => toast(error.message));
});
$('#auditRefreshBtn').addEventListener('click', () => loadAuditLogs().catch(error => toast(error.message)));
$('#auditResetBtn').addEventListener('click', () => {
    ['auditUserId', 'auditOperation', 'auditStart', 'auditEnd'].forEach(id => { $(\`#\${id}\`).value = ''; });
    $('#auditResult').value = '';
    auditPage = 1;
    loadAuditLogs().catch(error => toast(error.message));
});
$('#auditPager').addEventListener('click', event => {
    const button = event.target.closest('[data-audit-page]');
    if (!button) return;
    auditPage += button.dataset.auditPage === 'next' ? 1 : -1;
    loadAuditLogs().catch(error => toast(error.message));
});
$('#securityEventRefreshBtn').addEventListener('click', () => loadSecurityEvents().catch(error => toast(error.message)));
$('#securityEventForm').addEventListener('submit', event => submitSecurityEvent(event).catch(error => toast(error.message)));

Promise.allSettled([loadAuditLogs(), loadSecurityEvents()]).then(results => {
    results.filter(result => result.status === 'rejected').forEach(result => toast(result.reason?.message || '审计数据加载失败'));
});
`;export{t as default};

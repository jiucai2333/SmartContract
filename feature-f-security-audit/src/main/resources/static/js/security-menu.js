const state = {
    accessToken: localStorage.getItem('accessToken') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null')
};

const $ = (selector) => document.querySelector(selector);

function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, (ch) => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
    }[ch]));
}

function authHeaders(options = {}) {
    const body = options.body;
    return {
        ...(body ? {'Content-Type': 'application/json'} : {}),
        ...(state.accessToken ? {Authorization: `Bearer ${state.accessToken}`} : {})
    };
}

async function api(url, options = {}) {
    const response = await fetch(url, {...options, headers: authHeaders(options)});
    const payload = await response.json();
    if (payload.code !== 200) {
        throw new Error(payload.msg || `HTTP ${response.status}`);
    }
    return payload.data;
}

async function login() {
    const data = await api('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({
            username: $('#username').value,
            password: $('#password').value
        })
    });
    state.user = data;
    state.accessToken = data.accessToken;
    localStorage.setItem('user', JSON.stringify(data));
    localStorage.setItem('accessToken', data.accessToken);
    renderIdentity();
    await loadMenus();
    await loadAuditLogs();
}

function logout() {
    state.user = null;
    state.accessToken = '';
    localStorage.removeItem('user');
    localStorage.removeItem('accessToken');
    renderIdentity();
    $('#mainNav').innerHTML = '';
    $('#auditTbody').innerHTML = '';
}

function renderIdentity() {
    $('#userChip').textContent = state.user
            ? `${state.user.username} / ${state.user.roleCode}`
            : 'anonymous';
}

async function loadMenus() {
    const menus = await api('/api/security/menus');
    $('#mainNav').innerHTML = menus
            .map((item) => `<a href="${escapeHtml(item.href)}">${escapeHtml(item.label)}</a>`)
            .join('');
}

async function recordSecurityEvent() {
    await api('/api/security-events', {
        method: 'POST',
        body: JSON.stringify({
            eventType: $('#eventType').value,
            targetType: $('#targetType').value,
            targetId: $('#targetId').value,
            summary: $('#eventSummary').value,
            payload: $('#eventPayload').value
        })
    });
    await loadAuditLogs();
}

function riskReviewPayload() {
    return {
        contractType: 'Demo Contract',
        partyA: 'Company A',
        partyB: 'Company B',
        businessScope: 'Delivery and payment',
        contractText: $('#contractText').value
    };
}

async function reviewRisk() {
    const result = await api('/api/ai/risk-review', {
        method: 'POST',
        body: JSON.stringify(riskReviewPayload())
    });
    $('#riskReviewOutput').textContent = JSON.stringify(result, null, 2);
    await loadAuditLogs();
}

async function exportReview() {
    const text = await api('/api/ai/risk-review/export', {
        method: 'POST',
        body: JSON.stringify(riskReviewPayload())
    });
    $('#riskReviewOutput').textContent = text;
    await loadAuditLogs();
}

async function loadAuditLogs() {
    const page = await api('/api/admin/audit-logs');
    $('#auditTbody').innerHTML = page.records.map((log) => `
        <tr>
            <td>${escapeHtml(log.createdAt)}</td>
            <td>${escapeHtml(log.username)}<br>${escapeHtml(log.roleCode)}</td>
            <td>${escapeHtml(log.operation)}<br>${escapeHtml(log.path)}</td>
            <td>${escapeHtml(log.result)}</td>
            <td><code>${escapeHtml(log.detail || '')}</code></td>
        </tr>
    `).join('');
}

$('#loginBtn').addEventListener('click', () => login().catch((error) => alert(error.message)));
$('#logoutBtn').addEventListener('click', logout);
$('#recordSecurityEventBtn').addEventListener('click', () => recordSecurityEvent().catch((error) => alert(error.message)));
$('#riskReviewBtn').addEventListener('click', () => reviewRisk().catch((error) => alert(error.message)));
$('#exportReviewBtn').addEventListener('click', () => exportReview().catch((error) => alert(error.message)));
$('#loadAuditBtn').addEventListener('click', () => loadAuditLogs().catch((error) => alert(error.message)));

renderIdentity();
if (state.accessToken) {
    loadMenus().catch(logout);
}

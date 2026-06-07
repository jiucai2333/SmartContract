const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

// ==================== 六角色常量 ====================
const ROLE_LABELS = {
    USER: '普通员工', DEPT_LEADER: '部门主管', LEGAL: '法务专员',
    FINANCE: '财务专员', EXECUTIVE: '企业高管', ADMIN: '系统管理员'
};

function readUser() {
    try {
        const raw = localStorage.getItem('user');
        return raw ? JSON.parse(raw) : null;
    } catch {
        localStorage.removeItem('user');
        return null;
    }
}

function roleFromToken() {
    try {
        const token = localStorage.getItem('accessToken') || state.accessToken || '';
        if (!token) return 'USER';
        const base64Url = token.split('.')[1];
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        const payload = JSON.parse(atob(base64));
        const role = payload.role;
        return typeof role === 'string' ? role.trim().toUpperCase() : 'USER';
    } catch {
        return 'USER';
    }
}

function scopeFromToken() {
    try {
        const token = localStorage.getItem('accessToken') || state.accessToken || '';
        if (!token) return 'SELF';
        const base64Url = token.split('.')[1];
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        const payload = JSON.parse(atob(base64));
        return payload.scope || 'SELF';
    } catch {
        return 'SELF';
    }
}

function uidFromToken() {
    try {
        const token = localStorage.getItem('accessToken') || state.accessToken || '';
        if (!token) return null;
        const base64Url = token.split('.')[1];
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        const payload = JSON.parse(atob(base64));
        return payload.uid || null;
    } catch {
        return null;
    }
}

function didFromToken() {
    try {
        const token = localStorage.getItem('accessToken') || state.accessToken || '';
        if (!token) return null;
        const base64Url = token.split('.')[1];
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        const payload = JSON.parse(atob(base64));
        return payload.did || null;
    } catch {
        return null;
    }
}

const state = {
    user: readUser(),
    username: readUser()?.username || '',
    userId: readUser()?.userId || uidFromToken() || null,
    deptId: readUser()?.deptId || didFromToken() || null,
    roleCode: (() => {
        const stored = localStorage.getItem('roleCode');
        if (stored) return stored.trim().toUpperCase();
        return roleFromToken() || 'USER';
    })(),
    dataScope: (() => {
        const stored = localStorage.getItem('dataScope');
        if (stored) return stored.trim().toUpperCase();
        return scopeFromToken() || 'SELF';
    })(),
    accessToken: readUser()?.accessToken || localStorage.getItem('accessToken') || '',
    refreshToken: readUser()?.refreshToken || localStorage.getItem('refreshToken') || '',
    lastDraft: localStorage.getItem('lastDraft') || ''
};

const statusText = {
    DRAFT: '草稿', APPROVING: '审批中', APPROVED: '已审批',
    SIGNING: '已签章', ARCHIVED: '已归档',
    EXECUTING: '履约中', COMPLETED: '已完成',
    EXPIRED: '已到期', TERMINATED: '已终止'
};
const statusTagClass = {
    DRAFT: 'tag-gray', APPROVING: 'tag-blue', APPROVED: 'tag-purple',
    SIGNING: 'tag-orange', ARCHIVED: 'tag-green',
    EXECUTING: 'tag-cyan', COMPLETED: 'tag-darkgreen',
    EXPIRED: 'tag-red', TERMINATED: 'tag-red'
};
const riskText = {LOW: '低', MEDIUM: '中', HIGH: '高'};

// ==================== 导航菜单（支持 children 子菜单） ====================
const NAV_ITEMS = [
    {id: 'dashboard',   href: '/html/dashboard.html',   label: '工作台',   menu: 'ALL'},
    {id: 'draft',       href: '/html/draft.html',       label: '合同编制', menu: 'USER,DEPT_LEADER,LEGAL,ADMIN'},
    {id: 'risk',        href: '/html/risk.html',        label: '风险审查', menu: 'ALL'},
    {id: 'approval',    href: '/html/approval.html',    label: '审批中心', menu: 'DEPT_LEADER,LEGAL,EXECUTIVE,ADMIN'},
    {id: 'ledger',      href: '/html/ledger.html',      label: '合同台账', menu: 'ALL', children: [
        {id: 'seal',    href: '/html/seal.html',        label: '签章登记', menu: 'LEGAL,DEPT_LEADER,ADMIN'},
        {id: 'archive', href: '/html/archive.html',     label: '归档确认', menu: 'LEGAL,DEPT_LEADER,ADMIN'},
    ]},
    {id: 'fulfillment', href: '/html/fulfillment.html', label: '履约预警', menu: 'ALL'},
    {id: 'templates',   href: '/html/templates.html',   label: '模板库',   menu: 'LEGAL,ADMIN'},
    {id: 'users',       href: '/html/users.html',       label: '用户管理', menu: 'ADMIN'}
];

function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, ch => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[ch]));
}

function toast(message) {
    let el = $('#toast');
    if (!el) {
        el = document.createElement('div');
        el.id = 'toast';
        el.className = 'toast';
        document.body.appendChild(el);
    }
    el.textContent = message;
    el.classList.add('show');
    setTimeout(() => el.classList.remove('show'), 2800);
}

function authHeaders(options = {}) {
    return {
        'Content-Type': 'application/json',
        ...(state.accessToken ? {Authorization: `Bearer ${state.accessToken}`} : {}),
        ...(options.headers || {})
    };
}

async function request(url, options = {}) {
    const response = await fetch(url, {
        ...options,
        headers: authHeaders(options)
    });
    let res;
    try {
        res = await response.json();
    } catch {
        throw new Error(`请求失败：${response.status}`);
    }
    if (typeof res === 'string') {
        res = JSON.parse(res);
    }
    if (res.code !== 200) {
        if (res.code === 401 && !url.includes('/login') && !url.includes('/register')) {
            logout('/html/login.html');
        }
        throw new Error(res.msg || '请求失败');
    }
    return res;
}

async function api(url, options = {}) {
    const response = await fetch(url, {
        ...options,
        headers: authHeaders(options)
    });
    let res;
    try {
        res = await response.json();
    } catch {
        throw new Error(`请求失败：${response.status}`);
    }
    if (typeof res === 'string') {
        res = JSON.parse(res);
    }
    if (res && typeof res.code === 'number') {
        if (res.code === 401) { logout('/html/login.html'); }
        if (res.code === 403) { toast('权限不足，请联系管理员'); throw new Error('权限不足'); }
        if (res.code !== 200) { throw new Error(res.msg || '请求失败'); }
        return res.data;
    }
    if (response.status === 401) { logout('/html/login.html'); throw new Error('请先登录'); }
    if (response.status === 403) { toast('权限不足，请联系管理员'); throw new Error('权限不足'); }
    if (!response.ok) { throw new Error(res.message || res.msg || `请求失败：${response.status}`); }
    return res;
}

async function uploadApi(url, formData) {
    const response = await fetch(url, { method: 'POST', headers: state.accessToken ? { Authorization: `Bearer ${state.accessToken}` } : {}, body: formData });
    let res;
    try { res = await response.json(); } catch { throw new Error(`上传失败：${response.status}`); }
    if (typeof res === 'string') res = JSON.parse(res);
    if (res && typeof res.code === 'number') {
        if (res.code === 401) { logout('/html/login.html'); } if (res.code === 403) { toast('权限不足'); throw new Error('权限不足'); }
        if (res.code !== 200) throw new Error(res.msg || '上传失败');
        return res.data;
    }
    if (response.status === 401) { logout('/html/login.html'); throw new Error('请先登录'); }
    if (response.status === 403) { toast('权限不足'); throw new Error('权限不足'); }
    if (!response.ok) throw new Error(res.message || `上传失败：${response.status}`);
    return res;
}

function persistUser(user) {
    state.user = user;
    state.username = user?.username || '';
    state.userId = user?.userId || null;
    state.deptId = user?.deptId || null;
    state.roleCode = (user?.roleCode || 'USER').trim().toUpperCase();
    state.dataScope = (user?.dataScope || 'SELF').trim().toUpperCase();
    state.accessToken = user?.accessToken || '';
    state.refreshToken = user?.refreshToken || '';
    localStorage.setItem('user', JSON.stringify({
        ...user,
        roleCode: state.roleCode,
        dataScope: state.dataScope
    }));
    localStorage.setItem('accessToken', state.accessToken);
    localStorage.setItem('refreshToken', state.refreshToken);
    localStorage.setItem('roleCode', state.roleCode);
    localStorage.setItem('dataScope', state.dataScope);
    if (state.userId) localStorage.setItem('userId', String(state.userId));
    if (state.deptId) localStorage.setItem('deptId', String(state.deptId));
    window.dispatchEvent(new Event('storage'));
}

async function login(username, password) {
    const res = await request('/api/user/login', {
        method: 'POST',
        body: JSON.stringify({username, password})
    });
    persistUser(res.data);
}

async function register(username, password) {
    const res = await request('/api/user/register', {
        method: 'POST',
        body: JSON.stringify({username, password})
    });
    persistUser(res.data);
}

async function logout(redirect = '/html/login.html') {
    try {
        await request('/api/user/logout', {method: 'POST'});
    } catch {
        // ignore
    }
    localStorage.removeItem('user');
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('roleCode');
    localStorage.removeItem('dataScope');
    localStorage.removeItem('userId');
    localStorage.removeItem('deptId');
    state.user = null;
    state.username = '';
    state.userId = null;
    state.deptId = null;
    state.roleCode = 'USER';
    state.dataScope = 'SELF';
    state.accessToken = '';
    state.refreshToken = '';
    if (redirect) {
        location.href = redirect;
    }
}

function requireAuth() {
    if (!state.accessToken) {
        location.href = '/html/login.html';
        return false;
    }
    return true;
}

function redirectIfAuthed(target = '/html/dashboard.html') {
    if (state.accessToken) {
        location.href = target;
    }
}

function hasMenuPermission(item) {
    const allowed = item.menu.split(',').map(r => r.trim().toUpperCase());
    return allowed.includes('ALL') || allowed.includes(state.roleCode);
}
function canOperateSealArchive() { return ['LEGAL', 'DEPT_LEADER', 'ADMIN'].includes(state.roleCode); }

function applyIdentity() {
    renderWatermark();
    const userChip = $('#userChip');
    if (userChip) {
        const roleLabel = ROLE_LABELS[state.roleCode] || state.roleCode;
        userChip.innerHTML = `<strong>${escapeHtml(state.username || '未登录')}</strong><small>${escapeHtml(roleLabel)}</small>`;
    }
    $$('[data-role-panel]').forEach(panel => {
        const panelRole = String(panel.dataset.rolePanel).trim().toUpperCase();
        panel.hidden = panelRole !== state.roleCode;
    });
}

let _wmTimer = null;
function renderWatermark() {
    const container = $('#wmContainer');
    if (!container) return;
    const username = state.username || 'anonymous';
    const now = new Date();
    const timeStr = now.toLocaleString('zh-CN', { hour12: false });
    container.innerHTML = `<div class="watermark-text">${escapeHtml(username)} · ${timeStr}</div>`;
    if (_wmTimer) clearInterval(_wmTimer);
    _wmTimer = setInterval(() => {
        const el = $('#wmContainer .watermark-text');
        if (!el) { clearInterval(_wmTimer); return; }
        const t = new Date().toLocaleString('zh-CN', { hour12: false });
        el.textContent = `${state.username || 'anonymous'} · ${t}`;
    }, 1000);
}

function buildNavLinks(activeId, items) {
    if (!items) items = NAV_ITEMS;
    const visibleItems = items.filter(hasMenuPermission);
    return visibleItems.map(item => {
        const hasChildren = item.children && item.children.length > 0;
        const visibleChildren = hasChildren ? item.children.filter(hasMenuPermission) : [];
        const childActive = visibleChildren.some(c => c.id === activeId);
        const selfActive = item.id === activeId || childActive;
        const parentClass = selfActive ? 'active' : '';
        let html = '';
        if (item.href && !hasChildren) {
            html += `<a href="${item.href}" class="${parentClass}">${item.label}</a>`;
        } else if (hasChildren && visibleChildren.length > 0) {
            html += `<a href="${item.href}" class="nav-parent ${parentClass}${childActive ? ' expanded' : ''}">${item.label}<span class="nav-toggle"></span></a>`;
            html += `<div class="nav-sub"${childActive ? '' : ' hidden'}>`;
            visibleChildren.forEach(child => { html += `<a href="${child.href}" class="${child.id === activeId ? 'active' : ''}">${child.label}</a>`; });
            html += `</div>`;
        } else if (item.href) { html += `<a href="${item.href}" class="${parentClass}">${item.label}</a>`; }
        return html;
    }).join('');
}

function renderSidebar(activeId) {
    const nav = $('#mainNav');
    if (!nav) return;
    nav.innerHTML = buildNavLinks(activeId);
    applyIdentity();
}

function renderNavInShell(activeId) { return buildNavLinks(activeId); }

// 子菜单展开/收起（事件委托）
document.addEventListener('click', function(event) {
    const toggle = event.target.closest('.nav-toggle');
    if (!toggle) return;
    event.preventDefault(); event.stopPropagation();
    const parent = toggle.closest('.nav-parent');
    const sub = parent ? parent.nextElementSibling : null;
    if (sub && sub.classList.contains('nav-sub')) { parent.classList.toggle('expanded'); sub.hidden = !sub.hidden; }
});

function initAppShell(activeId, title, eyebrow) {
    if (!requireAuth()) return false;
    renderSidebar(activeId);
    const titleEl = $('#pageTitle');
    const eyebrowEl = $('#pageEyebrow');
    if (titleEl) titleEl.textContent = title;
    if (eyebrowEl) eyebrowEl.textContent = eyebrow;
    const logoutBtn = $('#logoutBtn');
    if (logoutBtn) logoutBtn.addEventListener('click', () => logout());
    return true;
}

function appShellHtml(activeId) {
    const navLinks = renderNavInShell(activeId);
    return `
<div class="watermark-container" id="wmContainer"></div>
<aside class="sidebar">
    <div class="brand">
        <span class="brand-mark">合</span>
        <div><strong>智能合同管理</strong><small>Qwen ContractOps</small></div>
    </div>
    <nav id="mainNav">${navLinks}</nav>
    <div class="security-note">
        <strong>合规控制</strong>
        <span>Qwen 调用前脱敏，高风险未复核阻断提交，预览叠加登录人水印。</span>
    </div>
</aside>
<main class="app-shell">
    <header class="topbar">
        <div>
            <p class="eyebrow" id="pageEyebrow">基于通义千问 Qwen 的合同全生命周期管理</p>
            <h1 id="pageTitle">工作台</h1>
        </div>
        <div class="topbar-right">
            <div class="user-chip" id="userChip"></div>
            <button id="logoutBtn" class="secondary" type="button">退出登录</button>
        </div>
    </header>`;
}

function closeAppShell() {
    return `</main><div id="toast" class="toast"></div>`;
}

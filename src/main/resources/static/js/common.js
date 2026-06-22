const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

const LOCAL_LUCIDE_ICONS = {
    'circle-check': '<circle cx="12" cy="12" r="10"></circle><path d="m9 12 2 2 4-4"></path>',
    'clock': '<circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline>',
    'download': '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" x2="12" y1="15" y2="3"></line>',
    'edit-3': '<path d="M12 20h9"></path><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4Z"></path>',
    'file': '<path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5Z"></path><polyline points="14 2 14 8 20 8"></polyline>',
    'file-text': '<path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5Z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" x2="8" y1="13" y2="13"></line><line x1="16" x2="8" y1="17" y2="17"></line><line x1="10" x2="8" y1="9" y2="9"></line>',
    'more-vertical': '<circle cx="12" cy="12" r="1"></circle><circle cx="12" cy="5" r="1"></circle><circle cx="12" cy="19" r="1"></circle>',
    'paperclip': '<path d="m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"></path>',
    'plus': '<path d="M5 12h14"></path><path d="M12 5v14"></path>',
    'refresh-cw': '<path d="M21 12a9 9 0 0 1-15.219 6.492L3 16"></path><path d="M3 21v-5h5"></path><path d="M3 12a9 9 0 0 1 15.219-6.492L21 8"></path><path d="M21 3v5h-5"></path>',
    'search': '<circle cx="11" cy="11" r="8"></circle><path d="m21 21-4.3-4.3"></path>',
    'shield-alert': '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10"></path><path d="M12 8v4"></path><path d="M12 16h.01"></path>',
    'trash-2': '<path d="M3 6h18"></path><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"></path><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path><line x1="10" x2="10" y1="11" y2="17"></line><line x1="14" x2="14" y1="11" y2="17"></line>',
    'upload': '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" x2="12" y1="3" y2="15"></line>',
    'upload-cloud': '<path d="M16 16l-4-4-4 4"></path><path d="M12 12v9"></path><path d="M20.39 18.39A5 5 0 0 0 18 9h-1.26A8 8 0 1 0 3 16.3"></path><path d="M16 16l-4-4-4 4"></path>'
};

function renderLucideIcons() {
    if (window.lucide && typeof lucide.createIcons === 'function') {
        lucide.createIcons();
        return;
    }
    $$('i[data-lucide]').forEach((icon) => {
        const iconMarkup = LOCAL_LUCIDE_ICONS[icon.dataset.lucide];
        if (!iconMarkup) return;
        const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        Array.from(icon.attributes).forEach((attribute) => {
            if (attribute.name !== 'data-lucide') svg.setAttribute(attribute.name, attribute.value);
        });
        svg.setAttribute('class', `${icon.className} lucide lucide-${icon.dataset.lucide}`.trim());
        svg.setAttribute('viewBox', '0 0 24 24');
        svg.setAttribute('fill', 'none');
        svg.setAttribute('stroke', 'currentColor');
        svg.setAttribute('stroke-width', '2');
        svg.setAttribute('stroke-linecap', 'round');
        svg.setAttribute('stroke-linejoin', 'round');
        svg.innerHTML = iconMarkup;
        icon.replaceWith(svg);
    });
}

const ROLE_LABELS = {
    USER: '普通员工',
    DEPT_LEADER: '部门主管',
    LEGAL: '法务专员',
    FINANCE: '财务专员',
    EXECUTIVE: '企业高管',
    ADMIN: '系统管理员'
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

function tokenPayload() {
    try {
        const token = localStorage.getItem('accessToken') || state.accessToken || '';
        if (!token) return {};
        const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
        return JSON.parse(atob(base64));
    } catch {
        return {};
    }
}

const storedUser = readUser();
const state = {
    user: storedUser,
    username: storedUser?.username || '',
    userId: storedUser?.userId || tokenPayload().uid || null,
    deptId: storedUser?.deptId || tokenPayload().did || null,
    roleCode: (localStorage.getItem('roleCode') || storedUser?.roleCode || tokenPayload().role || 'USER').trim().toUpperCase(),
    dataScope: (localStorage.getItem('dataScope') || storedUser?.dataScope || tokenPayload().scope || 'SELF').trim().toUpperCase(),
    accessToken: storedUser?.accessToken || localStorage.getItem('accessToken') || '',
    refreshToken: storedUser?.refreshToken || localStorage.getItem('refreshToken') || '',
    lastDraft: localStorage.getItem('lastDraft') || ''
};

const STATUS_TEXT = {
    DRAFT: '草稿', APPROVING: '审批中', APPROVED: '已审批',
    SIGNING: '已签章', ARCHIVED: '已归档',
    EXECUTING: '履约中', COMPLETED: '已完成',
    EXPIRED: '已到期', TERMINATED: '已终止',
    REVIEWING: '审核中'
};
const statusTagClass = {
    DRAFT: 'tag-gray', APPROVING: 'tag-blue', APPROVED: 'tag-purple',
    SIGNING: 'tag-orange', ARCHIVED: 'tag-green',
    EXECUTING: 'tag-cyan', COMPLETED: 'tag-darkgreen',
    EXPIRED: 'tag-red', TERMINATED: 'tag-red',
    REVIEWING: 'tag-blue'
};
const RISK_TEXT = {LOW: '低', MEDIUM: '中', HIGH: '高'};
const APPROVAL_STATUS_TEXT = {RUNNING: '审批中', APPROVED: '已通过', REJECTED: '已驳回'};
const FLOW_TYPE_TEXT = {NORMAL: '普通合同', MAJOR: '重大合同', SUPER: '超阈值合同'};
const APPROVAL_NODE_ROLES = {
    '部门主管审批': ['DEPT_LEADER', 'ADMIN'],
    '法务专员审批': ['LEGAL', 'ADMIN'],
    '企业高管审批': ['EXECUTIVE', 'ADMIN']
};
const SEAL_STATUS_TEXT = {ELECTRONIC: '电子签章', SIGNED: '已签署', SEALED: '已盖章'};
const FULFILLMENT_STATUS_TEXT = {
    PENDING: '待履约', PROCESSING: '履约中', FULFILLED: '已完成', OVERDUE: '已逾期'
};

const GROUP_LABELS = {drafting: '合同编制', contract: '合同业务'};

const NAV_ITEMS = [
    {id: 'dashboard',   href: '/html/dashboard.html',   label: '工作台',     menu: 'ALL'},
    {id: 'draft',       href: '/html/draft.html',       label: '合同草稿',   menu: 'USER,DEPT_LEADER,LEGAL,ADMIN', group: 'drafting'},
    {id: 'templates',   href: '/html/templates.html',   label: '合同模板',   menu: 'ALL',                              group: 'drafting'},
    {id: 'edit',        href: '/html/edit.html',        label: '在线编辑',   menu: 'USER,DEPT_LEADER,LEGAL,ADMIN', group: 'drafting'},
    {id: 'risk',        href: '/html/risk.html',        label: '风险审查',   menu: 'ALL'},
    {id: 'approval',    href: '/html/approval.html',    label: '审批中心',   menu: 'DEPT_LEADER,LEGAL,EXECUTIVE,ADMIN'},
    {id: 'ledger',      href: '/html/ledger.html',      label: '合同台账',   menu: 'ALL',                              group: 'contract'},
    {id: 'seal',        href: '/html/seal.html',        label: '签章登记',   menu: 'LEGAL,DEPT_LEADER,ADMIN',          group: 'contract'},
    {id: 'archive',     href: '/html/archive.html',     label: '归档确认',   menu: 'LEGAL,DEPT_LEADER,ADMIN',          group: 'contract'},
    {id: 'blockchain',  href: '/html/blockchain.html',  label: '区块链存证',  menu: 'LEGAL,ADMIN',                      group: 'contract'},
    {id: 'fulfillment', href: '/html/fulfillment.html', label: '履约预警',   menu: 'ALL'},
    {id: 'users',       href: '/html/users.html',       label: '用户管理',   menu: 'ADMIN'}
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
    const isFormData = options.body instanceof FormData;
    return {
        ...(!isFormData ? {'Content-Type': 'application/json'} : {}),
        ...(state.accessToken ? {Authorization: `Bearer ${state.accessToken}`} : {}),
        ...(options.headers || {})
    };
}

async function request(url, options = {}) {
    const response = await fetch(url, {...options, headers: authHeaders(options)});
    const res = await readJson(response, '请求失败');
    if (res.code !== 200) {
        if (res.code === 401 && !url.includes('/login') && !url.includes('/register')) logout('/html/login.html');
        throw new Error(res.msg || '请求失败');
    }
    return res;
}

async function readJson(response, fallback) {
    try {
        const res = await response.json();
        return typeof res === 'string' ? JSON.parse(res) : res;
    } catch {
        throw new Error(`${fallback}：${response.status}`);
    }
}

async function uploadApi(url, formData) {
    const response = await fetch(url, {
        method: 'POST',
        headers: state.accessToken ? {Authorization: `Bearer ${state.accessToken}`} : {},
        body: formData
    });
    const res = await readJson(response, '上传失败');
    return handleApiResponse(response, res, '上传失败');
}

function downloadFilename(response, fallbackName) {
    const disposition = response.headers.get('Content-Disposition') || '';
    const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i);
    if (utf8Match) {
        try { return decodeURIComponent(utf8Match[1]); } catch { return utf8Match[1]; }
    }
    const plainMatch = disposition.match(/filename="?([^";]+)"?/i);
    return plainMatch ? plainMatch[1] : fallbackName;
}

async function downloadFile(url, fallbackName = 'download') {
    const response = await fetch(url, {
        headers: state.accessToken ? {Authorization: `Bearer ${state.accessToken}`} : {}
    });
    if (response.status === 401) {
        await logout('/html/login.html');
        throw new Error('请先登录');
    }
    if (response.status === 403) throw new Error('权限不足');
    if (!response.ok) throw new Error(`下载失败：${response.status}`);
    const blob = await response.blob();
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = downloadFilename(response, fallbackName);
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(objectUrl);
}

async function api(url, options = {}) {
    const response = await fetch(url, {...options, headers: authHeaders(options)});
    const res = await readJson(response, '请求失败');
    return handleApiResponse(response, res, '请求失败');
}

function handleApiResponse(response, res, fallback) {
    if (res && typeof res.code === 'number') {
        if (res.code === 401) logout('/html/login.html');
        if (res.code === 403) {
            toast('权限不足，请联系管理员');
            throw new Error('权限不足');
        }
        if (res.code !== 200) throw new Error(res.msg || fallback);
        return res.data;
    }
    if (response.status === 401) {
        logout('/html/login.html');
        throw new Error('请先登录');
    }
    if (response.status === 403) {
        toast('权限不足，请联系管理员');
        throw new Error('权限不足');
    }
    if (!response.ok) throw new Error(res.message || res.msg || `${fallback}：${response.status}`);
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
    localStorage.setItem('user', JSON.stringify({...user, roleCode: state.roleCode, dataScope: state.dataScope}));
    localStorage.setItem('accessToken', state.accessToken);
    localStorage.setItem('refreshToken', state.refreshToken);
    localStorage.setItem('roleCode', state.roleCode);
    localStorage.setItem('dataScope', state.dataScope);
    if (state.userId) localStorage.setItem('userId', String(state.userId));
    if (state.deptId) localStorage.setItem('deptId', String(state.deptId));
}

async function login(username, password) {
    const res = await request('/api/users/login', {method: 'POST', body: JSON.stringify({username, password})});
    persistUser(res.data);
}

async function register(username, password) {
    const res = await request('/api/users/register', {method: 'POST', body: JSON.stringify({username, password})});
    persistUser(res.data);
}

async function logout(redirect = '/html/login.html') {
    try { await request('/api/users/logout', {method: 'POST'}); } catch {}
    ['user', 'accessToken', 'refreshToken', 'roleCode', 'dataScope', 'userId', 'deptId'].forEach(key => localStorage.removeItem(key));
    Object.assign(state, {user: null, username: '', userId: null, deptId: null, roleCode: 'USER', dataScope: 'SELF', accessToken: '', refreshToken: ''});
    if (redirect) location.href = redirect;
}

function requireAuth() {
    if (!state.accessToken) {
        location.href = '/html/login.html';
        return false;
    }
    return true;
}

function redirectIfAuthed(target = '/html/dashboard.html') {
    if (state.accessToken) location.href = target;
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
    const updateText = () => {
        const text = `${username} · ${new Date().toLocaleString('zh-CN', {hour12: false})}`;
        container.querySelectorAll('.watermark-text').forEach(item => {
            item.textContent = text;
        });
    };
    if (!container.children.length) {
        const fragment = document.createDocumentFragment();
        for (let index = 0; index < 24; index++) {
            const item = document.createElement('div');
            item.className = 'watermark-text';
            fragment.appendChild(item);
        }
        container.appendChild(fragment);
    }
    updateText();
    if (_wmTimer) clearInterval(_wmTimer);
    _wmTimer = setInterval(updateText, 60000);
}

function renderNavItems(items, activeId) {
    const allItems = items.filter(hasMenuPermission);
    const rendered = {};
    let html = '';
    let i = 0;
    while (i < allItems.length) {
        const item = allItems[i];
        if (item.group) {
            const g = item.group;
            if (!rendered[g]) {
                rendered[g] = true;
                const groupItems = allItems.filter(it => it.group === g);
                const hasActiveChild = groupItems.some(it => it.id === activeId);
                const stateClass = hasActiveChild ? 'expanded' : 'collapsed';
                const label = GROUP_LABELS[g] || g;
                html += `<div class="nav-parent ${stateClass}${hasActiveChild ? ' has-active' : ''}">${escapeHtml(label)}<span class="arrow">›</span></div>`;
                html += `<div class="nav-group ${stateClass}">`;
            }
            html += `<a href="${item.href}" class="nav-child${item.id === activeId ? ' active' : ''}">${escapeHtml(item.label)}</a>`;
            const next = allItems[i + 1];
            if (!next || next.group !== item.group) html += '</div>';
        } else {
            html += `<a href="${item.href}" class="${item.id === activeId ? 'active' : ''}">${escapeHtml(item.label)}</a>`;
        }
        i++;
    }
    return html;
}

function initNavGroups(nav) {
    nav.querySelectorAll('.nav-group').forEach(group => {
        group.style.maxHeight = group.classList.contains('collapsed') ? '0px' : `${group.scrollHeight}px`;
    });
}

function toggleNavParent(parent) {
    const group = parent.nextElementSibling;
    if (!group || !group.classList.contains('nav-group')) return;
    const expanded = parent.classList.contains('expanded');
    parent.classList.toggle('expanded', !expanded);
    parent.classList.toggle('collapsed', expanded);
    group.classList.toggle('expanded', !expanded);
    group.classList.toggle('collapsed', expanded);
    group.style.maxHeight = expanded ? '0px' : `${group.scrollHeight}px`;
}

function renderSidebar(activeId) {
    const nav = $('#mainNav');
    if (!nav) return;
    nav.innerHTML = renderNavItems(NAV_ITEMS, activeId);
    initNavGroups(nav);
    nav.addEventListener('click', (e) => {
        const parent = e.target.closest('.nav-parent');
        if (parent) toggleNavParent(parent);
        // 导航链接点击时加淡出动画
        const link = e.target.closest('a[href]');
        if (link) {
            e.preventDefault();
            const href = link.getAttribute('href');
            const shell = document.querySelector('.app-shell');
            if (shell) {
                shell.style.transition = 'opacity .15s ease, transform .15s ease';
                shell.style.opacity = '0';
                shell.style.transform = 'translateY(-4px)';
                setTimeout(() => { location.href = href; }, 150);
            } else {
                location.href = href;
            }
        }
    });
    applyIdentity();
    renderLucideIcons();
}

function renderNavInShell(activeId) {
    return renderNavItems(NAV_ITEMS, activeId);
}

function initAppShell(activeId, title, subtitle) {
    if (!requireAuth()) return false;
    renderSidebar(activeId);
    const titleEl = $('#pageTitle');
    const subtitleEl = $('#pageSubtitle');
    if (titleEl) titleEl.textContent = title;
    if (subtitleEl) subtitleEl.textContent = subtitle;
    const logoutBtn = $('#logoutBtn');
    if (logoutBtn) logoutBtn.addEventListener('click', () => logout());
    renderLucideIcons();
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
        <div class="topbar-heading">
            <h1 class="topbar-title" id="pageTitle">工作台</h1>
            <p class="topbar-subtitle" id="pageSubtitle">合同全生命周期概览，待办事项与数据统计</p>
        </div>
        <div class="topbar-right">
            <div class="user-chip" id="userChip"></div>
            <button id="logoutBtn" class="secondary" type="button">退出登录</button>
        </div>
    </header>`;
}

function closeAppShell() {
    return '</main><div id="toast" class="toast"></div>';
}

if (!initAppShell('users', '用户管理', '管理系统用户账号，配置角色权限与访问控制')) {
    throw new Error('auth required');
}

// 检查ADMIN权限
if (state.roleCode !== 'ADMIN') {
    toast('您没有管理员权限，无法访问此页面');
    setTimeout(() => { location.href = '/html/dashboard.html'; }, 2000);
    throw new Error('admin required');
}

let allRoles = [];
let selectedUserId = null;

async function loadUsers() {
    const users = await api('/api/admin/users');
    const tbody = $('#userTbody');
    if (!users.length) {
        tbody.innerHTML = '<tr><td colspan="6" class="empty-cell">暂无用户</td></tr>';
        return;
    }
    tbody.innerHTML = users.map(u => `
        <tr>
            <td>${u.userId}</td>
            <td><strong>${escapeHtml(u.username)}</strong></td>
            <td><span class="tag LOW">${escapeHtml(u.roleName || u.roleCode || 'USER')} (${escapeHtml(u.roleCode || 'USER')})</span></td>
            <td>${escapeHtml(u.deptName || `部门 #${u.deptId || '-'}`)}</td>
            <td>${u.status === 1 ? '<span class="tag LOW">启用</span>' : '<span class="tag HIGH">停用</span>'}</td>
            <td><button class="secondary edit-role-btn" data-id="${u.userId}" data-username="${escapeHtml(u.username)}" data-role="${u.roleCode || 'USER'}">编辑角色</button></td>
        </tr>
    `).join('');

    $$('.edit-role-btn', tbody).forEach(btn => {
        btn.addEventListener('click', () => openRoleModal(btn.dataset.id, btn.dataset.username, btn.dataset.role));
    });
}

async function loadRoles() {
    allRoles = await api('/api/admin/roles');
}

function openRoleModal(userId, username, currentRole) {
    selectedUserId = Number(userId);
    $('#roleModalTitle').textContent = `编辑角色 - ${username}`;

    const container = $('#roleSelectContainer');
    container.innerHTML = `
        <select id="roleSelect" class="form-select">
            ${allRoles.map(r => `
                <option value="${r.roleCode}" ${r.roleCode === currentRole ? 'selected' : ''}>
                    ${escapeHtml(r.roleName)} (${r.roleCode})
                </option>
            `).join('')}
        </select>
    `;
    $('#roleModal').hidden = false;
}

async function saveRole() {
    const roleCode = $('#roleSelect').value;
    await api(`/api/admin/users/${selectedUserId}/role`, {
        method: 'PUT',
        body: JSON.stringify({ roleCode })
    });
    $('#roleModal').hidden = true;
    await loadUsers();
    toast('角色已更新');
}

$('#closeRoleModal').addEventListener('click', () => { $('#roleModal').hidden = true; });
$('#roleModal').addEventListener('click', e => { if (e.target.id === 'roleModal') $('#roleModal').hidden = true; });
$('#saveRoleBtn').addEventListener('click', saveRole);

loadRoles().then(() => loadUsers()).catch(e => toast(e.message));

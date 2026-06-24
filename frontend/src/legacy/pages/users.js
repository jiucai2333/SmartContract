if (!initAppShell('users', '用户管理', '管理系统用户账号，配置角色权限与访问控制')) {
    throw new Error('auth required');
}

if (state.roleCode !== 'ADMIN') {
    toast('您没有管理员权限，无法访问此页面');
    setTimeout(() => { location.href = '/html/dashboard.html'; }, 2000);
    throw new Error('admin required');
}

let allRoles = [];

async function loadUsers() {
    const tbody = $('#userTbody');
    renderTableState(tbody, 5, { type: 'loading', title: '正在加载用户列表' });
    let users = [];
    try {
        users = await api('/api/admin/users');
    } catch (error) {
        renderTableState(tbody, 5, {
            type: 'error',
            title: '用户列表加载失败',
            message: error.message || '请稍后重试。',
            actionHtml: '<button type="button" class="secondary" data-retry-users>重新加载</button>'
        });
        throw error;
    }
    if (!users.length) {
        renderTableState(tbody, 5, {
            title: '暂无用户',
            message: '新注册账号或导入用户后，会显示在这里。'
        });
        return;
    }
    tbody.innerHTML = users.map(u => {
        const roleLabel = u.roleName || u.roleCode || 'USER';
        const roleCode = u.roleCode || 'USER';
        const statusText = u.status === 1 ? '启用' : '停用';
        const statusCls = u.status === 1 ? 'enabled' : 'disabled';
        return `
        <tr>
            <td>${u.userId}</td>
            <td><strong>${escapeHtml(u.username)}</strong></td>
            <td><span class="tag editable-tag role-tag-${escapeHtml(roleCode)}" data-editable="role" data-id="${u.userId}" data-current="${escapeHtml(roleCode)}" title="点击切换角色">${escapeHtml(roleLabel)}</span></td>
            <td>${escapeHtml(u.deptName || `部门 #${u.deptId || '-'}`)}</td>
            <td><span class="tag editable-tag ${statusCls}" data-editable="status" data-id="${u.userId}" data-current="${u.status}" title="点击切换状态">${statusText}</span></td>
        </tr>
    `}).join('');
}

async function loadRoles() {
    allRoles = await api('/api/admin/roles');
}

// ==================== Dropdown ====================

function showDropdown(tagEl) {
    const type = tagEl.dataset.editable;
    const id = tagEl.dataset.id;
    const current = tagEl.dataset.current;

    // 移除已有 dropdown
    $$('.dropdown-overlay').forEach(d => d.remove());

    let options;
    if (type === 'role') {
        options = allRoles.map(r => ({
            value: r.roleCode,
            label: r.roleName,
            code: r.roleCode,
            current: r.roleCode === current
        }));
    } else {
        options = [
            { value: '1', label: '启用', current: current === '1' },
            { value: '0', label: '停用', current: current === '0' }
        ];
    }

    const dd = document.createElement('div');
    dd.className = 'dropdown-overlay';
    dd.innerHTML = options.map(o => {
        const dot = o.code ? '<span class="role-dot role-dot-' + escapeHtml(o.code) + '"></span>' : '';
        return '<div class="dropdown-option' + (o.current ? ' current' : '') + '" data-value="' + o.value + '">'
            + dot + escapeHtml(o.label)
            + '</div>';
    }).join('');

    dd.querySelectorAll('.dropdown-option').forEach(opt => {
        opt.addEventListener('click', async ev => {
            ev.stopPropagation();
            const value = opt.dataset.value;
            if ((type === 'role' && value === current) || (type === 'status' && value === current)) {
                dd.remove();
                return;
            }
            try {
                opt.textContent = '更新中...';
                if (type === 'role') {
                    await api('/api/admin/users/' + id + '/role', {
                        method: 'PUT',
                        body: JSON.stringify({ roleCode: value })
                    });
                } else {
                    await api('/api/admin/users/' + id + '/status', {
                        method: 'PUT',
                        body: JSON.stringify({ status: Number(value) })
                    });
                }
                dd.remove();
                await loadUsers();
                toast(type === 'role' ? '角色已更新' : (value === '1' ? '已启用' : '已停用'));
            } catch (err) {
                toast(err.message);
                dd.remove();
            }
        });
    });

    const rect = tagEl.getBoundingClientRect();
    dd.style.top = (rect.bottom + 4) + 'px';
    dd.style.left = rect.left + 'px';
    document.body.appendChild(dd);

    setTimeout(() => {
        function closeDD(ev) {
            if (!dd.contains(ev.target) && ev.target !== tagEl) {
                dd.remove();
                document.removeEventListener('click', closeDD);
                window.removeEventListener('scroll', closeDD, true);
            }
        }
        document.addEventListener('click', closeDD);
        window.addEventListener('scroll', closeDD, true);
    }, 0);
}

// ==================== 事件委托 ====================

$('#userTbody').addEventListener('click', event => {
    if (event.target.closest('[data-retry-users]')) {
        loadUsers().catch(e => toast(e.message));
        return;
    }
    const tag = event.target.closest('[data-editable]');
    if (tag) {
        event.stopPropagation();
        showDropdown(tag);
    }
});

loadRoles().then(() => loadUsers()).catch(e => toast(e.message));

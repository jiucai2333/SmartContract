const e=`if (!initAppShell('templates', '合同模板', '按类型查找、下载和使用合同模板')) {
    throw new Error('auth required');
}

const TYPE_MAP = {
    CONFIDENTIAL: '保密合同',
    LABOR: '劳务合同',
    PURCHASE: '采购合同',
    SALES: '销售合同',
    TECH: '技术合同',
    LOGISTICS: '物流合同',
    ENTERPRISE_SERVICE: '企业服务合同',
    INTELLECTUAL_PROPERTY: '知识产权合同'
};

const canManage = ['LEGAL', 'ADMIN'].includes(state.roleCode);
const PAGE_SIZE = 5;
let currentFile = null;
let templates = [];
let templatePage = 1;

$('#newTemplateBtn').hidden = !canManage;

function displayTemplateName(item) {
    return item.templateName || \`\${TYPE_MAP[item.templateType] || '合同'}模板\`;
}

function displayFileName(item) {
    if (!item.fileName) return '';
    return item.fileName;
}

async function loadTemplates(resetPage = false) {
    const params = new URLSearchParams();
    if ($('#filterType').value) params.set('type', $('#filterType').value);
    if ($('#templateKeyword').value) params.set('keyword', $('#templateKeyword').value);
    const body = $('#templateTbody');
    const pager = $('#templatePager');
    renderTableState(body, 5, { type: 'loading', title: '正在加载合同模板' });
    if (pager) pager.innerHTML = '';
    try {
        templates = await api(\`/api/templates?\${params}\`);
        if (resetPage) templatePage = 1;
        renderTable();
    } catch (error) {
        renderTableState(body, 5, {
            type: 'error',
            title: '合同模板加载失败',
            message: error.message || '请稍后重试。',
            actionHtml: '<button type="button" class="secondary" data-retry-templates>重新加载</button>'
        });
        throw error;
    }
}

async function loadTypes() {
    const types = await api('/api/templates/types');
    $('#filterType').innerHTML = '<option value="">全部类型</option>' +
        types.map(type => \`<option value="\${escapeHtml(type)}">\${escapeHtml(TYPE_MAP[type] || type)}</option>\`).join('');
}

function clampTemplatePage(total) {
    const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
    templatePage = Math.min(Math.max(1, templatePage), totalPages);
    return totalPages;
}

function renderTemplatePager(total) {
    const pager = $('#templatePager');
    const totalPages = clampTemplatePage(total);
    const start = total ? (templatePage - 1) * PAGE_SIZE + 1 : 0;
    const end = Math.min(total, templatePage * PAGE_SIZE);
    pager.innerHTML = \`
        <div class="pagination-info">共 \${total} 条，每页 \${PAGE_SIZE} 条，显示 \${start}-\${end}</div>
        <div class="pagination-actions">
            <button class="secondary" type="button" data-template-page="prev" \${templatePage <= 1 ? 'disabled' : ''}>上一页</button>
            <span class="pagination-current">第 \${templatePage} / \${totalPages} 页</span>
            <button class="secondary" type="button" data-template-page="next" \${templatePage >= totalPages ? 'disabled' : ''}>下一页</button>
        </div>
    \`;
}

function renderTable() {
    const body = $('#templateTbody');
    const total = templates.length;
    clampTemplatePage(total);
    const list = templates.slice((templatePage - 1) * PAGE_SIZE, templatePage * PAGE_SIZE);
    renderTemplatePager(total);
    if (!list.length) {
        renderTableState(body, 5, {
            title: '暂无合同模板',
            message: canManage ? '上传标准模板后，起草合同时可直接套用。' : '当前没有可使用的模板，请联系法务或管理员维护模板库。'
        });
        return;
    }
    body.innerHTML = list.map(item => {
        const templateName = displayTemplateName(item);
        const fileName = displayFileName(item);
        return \`
            <tr>
                <td><strong>\${escapeHtml(templateName)}</strong><br><small>\${escapeHtml(item.description || '')}</small></td>
                <td><span class="tag">\${escapeHtml(TYPE_MAP[item.templateType] || item.templateType)}</span></td>
                <td>
                    \${item.fileName
                        ? \`<a class="file-link" href="\${item.downloadUrl}" data-download-url="\${item.downloadUrl}" data-file-name="\${escapeHtml(fileName)}"><i data-lucide="download"></i>\${escapeHtml(fileName)}</a>\`
                        : '<span class="muted-text">无文件</span>'}
                </td>
                <td>\${item.updatedAt ? new Date(item.updatedAt).toLocaleString('zh-CN') : '-'}</td>
                <td>
                    <div class="row-actions">
                        <a href="/html/edit.html?templateId=\${item.templateId}">使用此模板</a>
                        \${canManage ? \`
                            <button class="icon-btn edit-btn" data-id="\${item.templateId}" title="编辑" aria-label="编辑模板 \${escapeHtml(templateName)}"><i data-lucide="edit-3"></i></button>
                            <button class="icon-btn danger-icon delete-btn" data-id="\${item.templateId}" title="删除" aria-label="删除模板 \${escapeHtml(templateName)}"><i data-lucide="trash-2"></i></button>
                        \` : ''}
                    </div>
                </td>
            </tr>
        \`;
    }).join('');
    renderLucideIcons();
    $$('.edit-btn', body).forEach(btn => btn.addEventListener('click', () => editTemplate(btn.dataset.id)));
    $$('.delete-btn', body).forEach(btn => btn.addEventListener('click', () => deleteTemplate(btn.dataset.id)));
}

$('#templateTbody').addEventListener('click', event => {
    const retry = event.target.closest('[data-retry-templates]');
    if (retry) {
        loadTemplates().catch(error => toast(error.message));
        return;
    }
    const link = event.target.closest('[data-download-url]');
    if (!link) return;
    event.preventDefault();
    downloadFile(link.dataset.downloadUrl, link.dataset.fileName).catch(error => toast(error.message));
});

$('#templatePager').addEventListener('click', event => {
    const btn = event.target.closest('[data-template-page]');
    if (!btn || btn.disabled) return;
    templatePage += btn.dataset.templatePage === 'next' ? 1 : -1;
    renderTable();
});

async function editTemplate(id) {
    const item = await api(\`/api/templates/\${id}\`);
    $('#templateModalTitle').textContent = '编辑模板';
    $('#templateId').value = item.templateId;
    $('#templateType').value = item.templateType;
    $('#templateName').value = displayTemplateName(item);
    $('#templateDesc').value = item.description || '';
    currentFile = null;
    $('#templateFileName').textContent = displayFileName(item) || '未选择文件';
    $('#templateModal').hidden = false;
}

function resetForm() {
    $('#templateModalTitle').textContent = '上传模板';
    $('#templateId').value = '';
    $('#templateForm').reset();
    currentFile = null;
    $('#templateFileName').textContent = '未选择文件';
}

async function saveTemplate(event) {
    event.preventDefault();
    const submitButton = event.submitter || $('#templateForm button[type="submit"]');
    const id = $('#templateId').value;
    if (!id && !currentFile) {
        showFieldError($('#templateFileInput'), '请选择 PDF、DOC 或 DOCX 模板文件');
        toast('请选择 PDF、DOC …DOCX 模板文件');
        return;
    }
    clearFieldError($('#templateFileInput'));
    const data = new FormData();
    data.append('templateType', $('#templateType').value);
    data.append('templateName', $('#templateName').value);
    data.append('description', $('#templateDesc').value);
    if (currentFile) data.append('file', currentFile);
    setButtonBusy(submitButton, true, '保存中...');
    try {
        await saveTemplateRequest(id ? \`/api/templates/\${id}\` : '/api/templates', id ? 'PUT' : 'POST', data);
        $('#templateModal').hidden = true;
        resetForm();
        await loadTemplates();
        toast(id ? '模板已更新' : '模板已上传');
    } finally {
        setButtonBusy(submitButton, false);
    }
}

async function saveTemplateRequest(url, method, data) {
    const response = await fetch(url, {
        method,
        headers: state.accessToken ? {Authorization: \`Bearer \${state.accessToken}\`} : {},
        body: data
    });
    const result = await response.json().catch(() => {
        throw new Error(\`请求失败：\${response.status}\`);
    });
    return handleApiResponse(response, result, '请求失败');
}

async function deleteTemplate(id) {
    if (!await confirmDialog('确认删除此模板？', {title: '删除确认', confirmText: '删除', type: 'danger'})) return;
    await api(\`/api/templates/\${id}\`, {method: 'DELETE'});
    await loadTemplates();
    toast('模板已删除');
}

let timer;
$('#templateKeyword').addEventListener('input', () => {
    clearTimeout(timer);
    timer = setTimeout(() => loadTemplates(true).catch(error => toast(error.message)), 250);
});
$('#filterType').addEventListener('change', () => loadTemplates(true).catch(error => toast(error.message)));
$('#refreshBtn').addEventListener('click', () => {
    $('#templateKeyword').value = '';
    $('#filterType').value = '';
    loadTemplates(true).catch(error => toast(error.message));
});
$('#newTemplateBtn').addEventListener('click', () => {
    resetForm();
    $('#templateModal').hidden = false;
});
$('#closeTemplateModal').addEventListener('click', () => {
    $('#templateModal').hidden = true;
});
$('#templateForm').addEventListener('submit', event => saveTemplate(event).catch(error => toast(error.message)));
$('#pickTemplateFile').addEventListener('click', () => $('#templateFileInput').click());
$('#templateFileInput').addEventListener('change', event => {
    currentFile = event.target.files[0] || null;
    $('#templateFileName').textContent = currentFile ? currentFile.name : '未选择文件';
    clearFieldError($('#templateFileInput'));
    if (currentFile) $('#templateName').value = currentFile.name.replace(/\\.[^.]+$/, '');
});

loadTypes().then(() => loadTemplates()).catch(error => toast(error.message));
`;export{e as default};

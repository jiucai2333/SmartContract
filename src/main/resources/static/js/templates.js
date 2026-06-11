if (!initAppShell('templates', '合同模板', '按类型查找、下载和使用合同模板')) {
    throw new Error('auth required');
}

const typeMap = {
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
let currentFile = null;

$('#newTemplateBtn').hidden = !canManage;

function displayTemplateName(item) {
    return item.templateName || `${typeMap[item.templateType] || '合同'}模板`;
}

function displayFileName(item) {
    if (!item.fileName) return '';
    return item.fileName;
}

async function loadTemplates() {
    const params = new URLSearchParams();
    if ($('#filterType').value) params.set('type', $('#filterType').value);
    if ($('#templateKeyword').value) params.set('keyword', $('#templateKeyword').value);
    const list = await api(`/api/templates?${params}`);
    renderTable(list);
}

async function loadTypes() {
    const types = await api('/api/templates/types');
    $('#filterType').innerHTML = '<option value="">全部类型</option>' +
        types.map(type => `<option value="${escapeHtml(type)}">${escapeHtml(typeMap[type] || type)}</option>`).join('');
}

function renderTable(list) {
    const body = $('#templateTbody');
    if (!list.length) {
        body.innerHTML = '<tr><td colspan="5" class="empty-cell">暂无模板</td></tr>';
        return;
    }
    body.innerHTML = list.map(item => {
        const templateName = displayTemplateName(item);
        const fileName = displayFileName(item);
        return `
            <tr>
                <td><strong>${escapeHtml(templateName)}</strong><br><small>${escapeHtml(item.description || '')}</small></td>
                <td><span class="tag">${escapeHtml(typeMap[item.templateType] || item.templateType)}</span></td>
                <td>
                    ${item.fileName
                        ? `<a class="file-link" href="${item.downloadUrl}" data-download-url="${item.downloadUrl}" data-file-name="${escapeHtml(fileName)}"><i data-lucide="download"></i>${escapeHtml(fileName)}</a>`
                        : '<span class="muted-text">无文件</span>'}
                </td>
                <td>${item.updatedAt ? new Date(item.updatedAt).toLocaleString('zh-CN') : '-'}</td>
                <td>
                    <div class="row-actions">
                        <a href="/html/edit.html?templateId=${item.templateId}">使用此模板</a>
                        ${canManage ? `
                            <button class="icon-btn edit-btn" data-id="${item.templateId}" title="编辑"><i data-lucide="edit-3"></i></button>
                            <button class="icon-btn danger-icon delete-btn" data-id="${item.templateId}" title="删除"><i data-lucide="trash-2"></i></button>
                        ` : ''}
                    </div>
                </td>
            </tr>
        `;
    }).join('');
    renderLucideIcons();
    $$('.edit-btn', body).forEach(btn => btn.addEventListener('click', () => editTemplate(btn.dataset.id)));
    $$('.delete-btn', body).forEach(btn => btn.addEventListener('click', () => deleteTemplate(btn.dataset.id)));
}

$('#templateTbody').addEventListener('click', event => {
    const link = event.target.closest('[data-download-url]');
    if (!link) return;
    event.preventDefault();
    downloadFile(link.dataset.downloadUrl, link.dataset.fileName).catch(error => toast(error.message));
});

async function editTemplate(id) {
    const item = await api(`/api/templates/${id}`);
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
    const id = $('#templateId').value;
    if (!id && !currentFile) {
        toast('请选择 PDF、DOC 或 DOCX 模板文件');
        return;
    }
    const data = new FormData();
    data.append('templateType', $('#templateType').value);
    data.append('templateName', $('#templateName').value);
    data.append('description', $('#templateDesc').value);
    if (currentFile) data.append('file', currentFile);
    await saveTemplateRequest(id ? `/api/templates/${id}` : '/api/templates', id ? 'PUT' : 'POST', data);
    $('#templateModal').hidden = true;
    resetForm();
    await loadTemplates();
    toast(id ? '模板已更新' : '模板已上传');
}

async function saveTemplateRequest(url, method, data) {
    const response = await fetch(url, {
        method,
        headers: state.accessToken ? {Authorization: `Bearer ${state.accessToken}`} : {},
        body: data
    });
    const result = await response.json().catch(() => {
        throw new Error(`请求失败：${response.status}`);
    });
    return handleApiResponse(response, result, '请求失败');
}

async function deleteTemplate(id) {
    if (!confirm('确认删除此模板？')) return;
    await api(`/api/templates/${id}`, {method: 'DELETE'});
    await loadTemplates();
    toast('模板已删除');
}

let timer;
$('#templateKeyword').addEventListener('input', () => {
    clearTimeout(timer);
    timer = setTimeout(() => loadTemplates().catch(error => toast(error.message)), 250);
});
$('#filterType').addEventListener('change', () => loadTemplates().catch(error => toast(error.message)));
$('#refreshBtn').addEventListener('click', () => {
    $('#templateKeyword').value = '';
    $('#filterType').value = '';
    loadTemplates().catch(error => toast(error.message));
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
    if (currentFile) $('#templateName').value = currentFile.name.replace(/\.[^.]+$/, '');
});

loadTypes().then(loadTemplates).catch(error => toast(error.message));

if (!initAppShell('edit', '在线编辑', '支持合同内容编辑、模板解析、草稿保存与 Word 下载')) throw new Error('auth required');

const searchParams = new URLSearchParams(location.search);
const draftState = {
    attachmentId: null,
    lastFile: null,
    contractId: Number(searchParams.get('contractId')) || null,
    templateId: Number(searchParams.get('templateId')) || null
};
const versionBox = document.createElement('div');
versionBox.id = 'savedVersion';
versionBox.className = 'hint';
versionBox.hidden = true;
$('#draftStatus').after(versionBox);
const touchedMetaFields = new Set();
['contractTitle', 'contractCounterparty', 'contractType', 'contractAmount'].forEach(id => {
    const field = document.getElementById(id);
    field?.addEventListener('input', event => { if (event.isTrusted) touchedMetaFields.add(id); });
    field?.addEventListener('change', event => { if (event.isTrusted) touchedMetaFields.add(id); });
});

function updateStatus(message) { $('#draftStatus').textContent = message; }
function initMode() {
    if (draftState.contractId) {
        $('#contractMetaPanel').hidden = true;
        return;
    }
    if (draftState.templateId) {
        updateStatus('正在准备模板...');
        return;
    }
}
function setStep(step) {
    $$('[data-step]').forEach(item => {
        const current = Number(item.dataset.step);
        item.classList.toggle('active', current === step);
        item.classList.toggle('done', current < step);
        item.classList.remove('failed');
    });
}
function setFailedStep(step) {
    setStep(step);
    document.querySelector(`[data-step="${step}"]`)?.classList.add('failed');
}
function updateCharCount() { $('#draftCharCount').textContent = `${$('#draftEditor').innerText.length} 字符`; }
function setDraftContent(html) { $('#draftEditor').innerHTML = html || ''; updateCharCount(); }
function draftContent() { return $('#draftEditor').innerHTML; }
function hasEditorContent() { return $('#draftEditor').innerText.trim().length > 0; }
function setMetaValue(selector, value, {force = false} = {}) {
    const input = $(selector);
    if (!input || value === null || value === undefined || value === '') return false;
    if (!force && touchedMetaFields.has(input.id)) return false;
    if (!force && input.tagName !== 'SELECT' && input.value.trim()) return false;
    input.value = value;
    input.dispatchEvent(new Event('input', {bubbles: true}));
    input.dispatchEvent(new Event('change', {bubbles: true}));
    return true;
}
function applyExtractedMeta(result) {
    if (draftState.contractId || !result?.extract) return;
    const extract = result.extract;
    const filled = [];
    if (setMetaValue('#contractTitle', extract.title)) filled.push('合同名称');
    if (setMetaValue('#contractCounterparty', extract.counterparty || extract.partyB || extract.partyA)) filled.push('合同相对方');
    if (setMetaValue('#contractAmount', extract.amount)) filled.push('合同金额');
    const typeOptions = Array.from($('#contractType')?.options || []).map(option => option.value);
    if (extract.contractType && typeOptions.includes(extract.contractType) && setMetaValue('#contractType', extract.contractType)) {
        filled.push('合同类型');
    }
    if (filled.length) toast(`已自动填充${filled.join('、')}`);
}
function showSavedVersion(version) {
    versionBox.hidden = false;
    versionBox.innerHTML = `最新草稿：<strong>${escapeHtml(version.versionNo)}</strong> · <a href="${version.downloadUrl}" data-download-url="${version.downloadUrl}" data-file-name="contract-${version.contractId}-${version.versionNo}.docx">下载 Word 草稿</a>`;
}
async function restoreLatestDraft() {
    if (!draftState.contractId) return;
    const [contract, version] = await Promise.all([
        api(`/api/contracts/${draftState.contractId}`),
        api(`/api/contracts/${draftState.contractId}/versions/latest`)
    ]);
    if (contract) {
        $('#contractTitle').value = contract.title || '';
        $('#contractCounterparty').value = contract.counterparty || '';
        $('#contractType').value = contract.type || 'OTHER';
        $('#contractAmount').value = contract.amount ?? '';
    }
    if (!version?.content) {
        updateStatus('该合同暂无草稿正文，可上传材料或直接编辑后保存草稿。');
        return;
    }
    setDraftContent(version.content);
    showSavedVersion(version);
    updateStatus(`已加载最新草稿 ${version.versionNo}`);
}
async function loadTemplateForEditing() {
    if (!draftState.templateId) return;
    updateStatus('正在识别并载入合同模板...');
    setStep(2);
    try {
        const [template, parsed] = await Promise.all([
            api(`/api/templates/${draftState.templateId}`),
            api(`/api/templates/${draftState.templateId}/parse?preserveFormat=true`)
        ]);
        $('#contractTitle').value = template.templateName || '';
        $('#contractType').value = ['PURCHASE', 'SALES', 'TECH', 'LABOR', 'CONFIDENTIAL', 'LOGISTICS', 'ENTERPRISE_SERVICE', 'INTELLECTUAL_PROPERTY', 'OTHER']
            .includes(template.templateType) ? template.templateType : 'OTHER';
        showParsedResult({editorHtml: parsed.html || ''});
        updateStatus(`模板"${template.templateName || template.fileName || ''}"已识别并载入，可继续编制。`);
    } catch (error) {
        setFailedStep(2);
        updateStatus('模板识别失败，请返回模板页重试。');
        toast(error.message);
    }
}
async function saveDraft() {
    const saveButton = $('#saveDraftBtn');
    if (saveButton.disabled) return;
    const content = draftContent();
    if (!hasEditorContent()) return toast('请先上传或编辑合同草稿');
    saveButton.disabled = true;
    const originalText = saveButton.textContent;
    saveButton.textContent = '保存中...';
    try {
    if (!draftState.contractId) {
        const title = $('#contractTitle').value.trim();
        const counterparty = $('#contractCounterparty').value.trim();
        const amount = Number($('#contractAmount').value);
        if (!title || !counterparty || !Number.isFinite(amount)) return toast('请先填写合同名称、相对方和金额');
        const contract = await api('/api/contracts', {
            method: 'POST',
            body: JSON.stringify({
                title,
                counterparty,
                amount,
                type: $('#contractType').value,
                deptId: state.deptId || 1,
                ownerId: state.userId || 1,
                dueDate: new Date(Date.now() + 90 * 86400000).toISOString().slice(0, 10)
            })
        });
        draftState.contractId = contract.contractId;
        history.replaceState(null, '', `/html/edit.html?contractId=${contract.contractId}`);
        $('#contractMetaPanel').hidden = true;
    }
    const version = await api(`/api/contracts/${draftState.contractId}/versions`, {
        method: 'POST', body: JSON.stringify({contractId: draftState.contractId, content, saveType: 'SAVE'})
    });
    showSavedVersion(version);
    updateStatus(`草稿已生成 Word 留档 · ${new Date().toLocaleString('zh-CN')}`);
    toast(`草稿 ${version.versionNo} 已保存`);
    } finally {
        saveButton.disabled = false;
        saveButton.textContent = originalText;
    }
}

/**
 * 更新 OCR 面板状态。
 * @param {ContractImportResultVO} result OCR 导入结果 VO
 */
function updateOcrPanel(result) {
    draftState.attachmentId = result.attachmentId;
    $('#ocrPanel').hidden = false;
    $('#ocrStatusText').textContent = `${result.ocrStatus} · ${result.fileName || ''} · ${result.pageCount || '?'} 页`;
    $('#retryOcrBtn').hidden = result.ocrStatus !== 'FAILED';
    $('#reuploadBtn').hidden = result.ocrStatus !== 'FAILED';
    if (result.ocrStatus === 'SUCCESS') {
        const states = ['OCR 已完成'];
        if (result.ocrRawJsonExist) states.push('已保存 OCR 原始结果');
        if (result.ocrBlocksJsonExist) states.push('已生成结构化解析结果');
        if (result.previewHtml) states.push('已按 OCR 坐标还原版式');
        $('#ocrHint').textContent = states.join(' · ');
    } else {
        $('#ocrHint').textContent = result.ocrError || '正在解析合同内容...';
    }
    setStep(result.ocrStatus === 'SUCCESS' ? 3 : 2);
}

/**
 * 将 OCR 导入结果载入编辑器。
 * editorHtml → contentEditable（唯一允许）
 * previewHtml → 只读 OCR 预览面板（不进入编辑器）
 * @param {ContractImportResultVO} result OCR 导入结果 VO
 */
function showParsedResult(result) {
    let content = result.editorHtml;
    // 严格禁止 previewHtml 进入编辑器
    if (!content && result.plainText) {
        content = result.plainText.split('\n')
            .map(line => line.trim())
            .filter(line => line.length > 0)
            .map(line => `<p>${escapeHtml(line)}</p>`)
            .join('');
        updateStatus('编辑器排版生成失败，已使用纯文本降级展示，请校对格式后保存。');
    }
    if (!content) {
        updateStatus('OCR 已完成，但编辑器排版生成失败，请点击"重新解析"重试。');
        setStep(2);
        return;
    }
    setDraftContent(content);
    applyExtractedMeta(result);
    updateStatus('解析完成，可以在线编辑并保存 Word 草稿。');
    setStep(3);
    const editor = $('#draftEditor');
    editor.scrollTop = 0;
    editor.classList.remove('import-ready');
    void editor.offsetWidth;
    editor.classList.add('import-ready');
    setTimeout(() => editor.classList.remove('import-ready'), 1200);
    // previewHtml 只在只读预览区展示
    if (result.previewHtml) showOcrPreview(result.previewHtml);
}

/**
 * 在 OCR 面板中展示坐标版式预览（只读）。
 */
let ocrPreviewVisible = false;
function showOcrPreview(html) {
    const area = $('#ocrPreviewArea');
    const toggle = $('#ocrPreviewToggle');
    if (!area || !toggle) return;
    area.innerHTML = html;
    toggle.hidden = false;
    toggle.textContent = '显示版式预览';
    ocrPreviewVisible = false;
    area.hidden = true;
    toggle.addEventListener('click', function handler() {
        ocrPreviewVisible = !ocrPreviewVisible;
        area.hidden = !ocrPreviewVisible;
        toggle.textContent = ocrPreviewVisible ? '隐藏版式预览' : '显示版式预览';
    }, {once: false});
}

/**
 * 上传文件 → 创建附件 → 触发 OCR → 载入编辑器。
 * 流程：POST /api/attachments/upload → POST /api/attachments/{id}/ocr
 */
async function uploadTemplate(file) {
    if (!file) return;
    if (!/\.(pdf|doc|docx|jpg|jpeg|png|webp)$/i.test(file.name)) {
        return toast('仅支持 PDF、DOC、DOCX、JPG、JPEG、PNG 或 WEBP 格式');
    }
    if (hasEditorContent() && !confirm('当前内容将被替换，是否继续？')) return;
    draftState.lastFile = file;
    $('#uploadFileName').textContent = file.name;
    $('#pickFileBtn').disabled = true;
    $('#ocrPanel').hidden = false;
    $('#ocrStatusText').textContent = `正在上传 · ${file.name}`;
    $('#ocrHint').textContent = '正在上传文件，请稍候。';
    $('#retryOcrBtn').hidden = true;
    $('#reuploadBtn').hidden = true;
    updateStatus('正在上传合同模板...');
    setStep(1);
    try {
        // Step 1: 上传附件（仅存储，不触发 OCR）
        const formData = new FormData();
        formData.append('file', file);
        if (draftState.contractId) formData.append('contractId', String(draftState.contractId));
        const attachment = await api('/api/attachments/upload', {method: 'POST', body: formData});
        draftState.attachmentId = attachment.attachmentId;
        setStep(2);
        $('#ocrStatusText').textContent = `正在解析格式 · ${file.name}`;
        $('#ocrHint').textContent = '正在进行 OCR、结构化解析和版式判断。';
        // Step 2: 触发 OCR
        const result = await api(`/api/attachments/${attachment.attachmentId}/ocr?preserveFormat=true`, {method: 'POST'});
        updateOcrPanel(result);
        if (result.ocrStatus === 'SUCCESS') showParsedResult(result);
    } catch (error) {
        setFailedStep(2);
        $('#ocrPanel').hidden = false;
        $('#ocrStatusText').textContent = `解析失败 · ${file.name}`;
        $('#ocrHint').textContent = error.message || '文件损坏、解析超时或格式不受支持';
        $('#retryOcrBtn').hidden = !draftState.attachmentId;
        $('#reuploadBtn').hidden = false;
        updateStatus('模板导入失败，请检查错误原因后重试。');
        toast(error.message || '模板导入失败');
    } finally {
        $('#pickFileBtn').disabled = false;
        $('#fileInput').value = '';
    }
}
async function retryOcr() {
    setStep(2);
    const result = await api(`/api/attachments/${draftState.attachmentId}/ocr?preserveFormat=true`, {method: 'POST'});
    updateOcrPanel(result);
    if (result.ocrStatus === 'SUCCESS') showParsedResult(result);
}
const zone = $('#uploadZone');
const input = $('#fileInput');
$('#pickFileBtn').addEventListener('click', event => { event.preventDefault(); event.stopPropagation(); input.click(); });
input.addEventListener('change', event => uploadTemplate(event.target.files[0]));
zone.addEventListener('dragover', event => { event.preventDefault(); zone.classList.add('dragover'); });
zone.addEventListener('dragleave', () => zone.classList.remove('dragover'));
zone.addEventListener('drop', event => { event.preventDefault(); zone.classList.remove('dragover'); uploadTemplate(event.dataTransfer?.files?.[0]); });

// 模板选择器
async function loadTemplateOptions() {
    try {
        const templates = await api('/api/templates');
        const select = $('#templateSelect');
        templates.forEach(t => {
            const opt = document.createElement('option');
            opt.value = t.templateId;
            opt.textContent = t.templateName || t.fileName || `模板 #${t.templateId}`;
            select.appendChild(opt);
        });
    } catch (e) { /* 静默失败，不影响主流程 */ }
}
$('#templateSelect').addEventListener('change', event => {
    $('#useTemplateBtn').disabled = !event.target.value;
});
$('#useTemplateBtn').addEventListener('click', async () => {
    const templateId = Number($('#templateSelect').value);
    if (!templateId) return;
    if (hasEditorContent() && !confirm('当前内容将被替换，是否继续？')) return;
    $('#useTemplateBtn').disabled = true;
    updateStatus('正在载入模板...');
    setStep(2);
    try {
        const [template, parsed] = await Promise.all([
            api(`/api/templates/${templateId}`),
            api(`/api/templates/${templateId}/parse?preserveFormat=true`)
        ]);
        $('#contractTitle').value = template.templateName || '';
        $('#contractType').value = ['PURCHASE', 'SALES', 'TECH', 'LABOR', 'CONFIDENTIAL', 'LOGISTICS', 'ENTERPRISE_SERVICE', 'INTELLECTUAL_PROPERTY', 'OTHER']
            .includes(template.templateType) ? template.templateType : 'OTHER';
        showParsedResult({editorHtml: parsed.html || ''});
        updateStatus(`模板"${template.templateName || template.fileName || ''}"已载入，可继续编制。`);
    } catch (error) {
        setFailedStep(2);
        updateStatus('模板载入失败，请重试。');
        toast(error.message);
    } finally {
        $('#useTemplateBtn').disabled = false;
    }
});
loadTemplateOptions();
$('#retryOcrBtn').addEventListener('click', () => retryOcr().catch(error => toast(error.message)));
$('#reuploadBtn').addEventListener('click', () => input.click());
$('#saveDraftBtn').addEventListener('click', () => saveDraft().catch(error => toast(error.message)));
versionBox.addEventListener('click', event => {
    const link = event.target.closest('[data-download-url]');
    if (!link) return;
    event.preventDefault();
    downloadFile(link.dataset.downloadUrl, link.dataset.fileName).catch(error => toast(error.message));
});
$('#downloadDraftBtn').addEventListener('click', async () => {
    if (!hasEditorContent()) return toast('请先上传或编辑合同内容');
    try {
        const link = $('#savedVersion a');
        if (link) {
            await downloadFile(link.dataset.downloadUrl, link.dataset.fileName);
            return;
        }
        await saveDraft();
        const newLink = $('#savedVersion a');
        if (newLink) await downloadFile(newLink.dataset.downloadUrl, newLink.dataset.fileName);
        else toast('保存成功但下载链接未生成，请重试');
    } catch (error) {
        toast(error.message);
    }
});
$('#draftEditor').addEventListener('input', updateCharCount);

// 编辑器工具栏按钮
const editorEl = $('#draftEditor');

$$('.toolbar-btns button').forEach(btn => {
    btn.addEventListener('mousedown', (e) => {
        e.preventDefault();
    });

    btn.addEventListener('click', () => {
        const cmd = btn.dataset.cmd;
        editorEl.focus();
        document.execCommand(cmd, false, null);
        refreshToolbarState();
    });
});

function refreshToolbarState() {
    $$('.toolbar-btns button').forEach(btn => {
        const cmd = btn.dataset.cmd;
        if (cmd === 'undo' || cmd === 'redo') return;
        const active = document.queryCommandState(cmd);
        btn.classList.toggle('active', active);
    });
}

document.addEventListener('selectionchange', () => {
    if (document.activeElement === editorEl || editorEl.contains(document.activeElement)) {
        refreshToolbarState();
    }
});

editorEl.addEventListener('click', refreshToolbarState);
editorEl.addEventListener('keyup', refreshToolbarState);

initMode();
if (draftState.contractId) restoreLatestDraft().catch(error => toast(error.message));
else if (draftState.templateId) loadTemplateForEditing();

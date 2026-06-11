if (!initAppShell('edit', '在线编辑', '支持合同内容编辑、模板解析、草稿保存与 Word 下载')) throw new Error('auth required');

const searchParams = new URLSearchParams(location.search);
const draftState = {
    attachmentId: null,
    contractId: Number(searchParams.get('contractId')) || null,
    templateId: Number(searchParams.get('templateId')) || null
};
const versionBox = document.createElement('div');
versionBox.id = 'savedVersion';
versionBox.className = 'hint';
versionBox.hidden = true;
$('#draftStatus').after(versionBox);

function updateStatus(message) { $('#draftStatus').textContent = message; }
function initMode() {
    if (draftState.contractId) {
        $('#contractMetaPanel').hidden = true;
        return;
    }
    if (draftState.templateId) {
        updateStatus('模板解析接口待接入，可先手动编辑。');
        return;
    }
}
function setStep(step) {
    $$('[data-step]').forEach(item => {
        const current = Number(item.dataset.step);
        item.classList.toggle('active', current === step);
        item.classList.toggle('done', current < step);
    });
}
function updateCharCount() { $('#draftCharCount').textContent = `${$('#draftEditor').innerText.length} 字符`; }
function setDraftContent(html) { $('#draftEditor').innerHTML = html || ''; updateCharCount(); }
function draftContent() { return $('#draftEditor').innerHTML.trim(); }
function showSavedVersion(version) {
    versionBox.hidden = false;
    versionBox.innerHTML = `最新草稿：<strong>${escapeHtml(version.versionNo)}</strong> · <a href="${version.downloadUrl}" data-download-url="${version.downloadUrl}" data-file-name="contract-${version.contractId}-${version.versionNo}.docx">下载 Word 草稿</a>`;
}
async function restoreLatestDraft() {
    if (!draftState.contractId) return;
    const version = await api(`/api/contracts/${draftState.contractId}/versions/latest`);
    if (!version?.content) return;
    setDraftContent(version.content);
    showSavedVersion(version);
    updateStatus(`已加载最新草稿 ${version.versionNo}`);
}
async function saveDraft() {
    const content = draftContent();
    if (!content) return toast('请先上传或编辑合同草稿');
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
    }
    const version = await api(`/api/contracts/${draftState.contractId}/versions`, {
        method: 'POST', body: JSON.stringify({contractId: draftState.contractId, content, saveType: 'SAVE'})
    });
    showSavedVersion(version);
    updateStatus(`草稿已生成 Word 留档 · ${new Date().toLocaleString('zh-CN')}`);
    toast(`草稿 ${version.versionNo} 已保存`);
    return version;
}
async function launchAiReview() {
    const content = draftContent();
    if (!content) return toast('请先上传或编辑合同草稿');
    $('#aiReviewBtn').disabled = true;
    $('#aiReviewBtn').textContent = '准备审核...';
    try {
        const version = await saveDraft();
        if (!draftState.contractId || !version?.versionId) return;
        const params = new URLSearchParams({
            contractId: String(draftState.contractId),
            versionId: String(version.versionId),
            auto: '1'
        });
        location.href = `/html/risk.html?${params.toString()}`;
    } finally {
        $('#aiReviewBtn').disabled = false;
        $('#aiReviewBtn').textContent = 'AI 风险审查';
    }
}
function updateOcrPanel(vo) {
    draftState.attachmentId = vo.attachmentId;
    $('#ocrPanel').hidden = false;
    $('#ocrStatusText').textContent = `${vo.ocrStatus} · ${vo.fileName || ''} · ${vo.pageCount || '?'} 页`;
    $('#retryOcrBtn').hidden = vo.ocrStatus !== 'FAILED';
    $('#ocrHint').textContent = vo.ocrStatus === 'SUCCESS' ? '解析完成，请在右侧编辑格式化内容。' : (vo.ocrError || '正在解析合同内容...');
    setStep(vo.ocrStatus === 'SUCCESS' ? 3 : 2);
}
function showParsedResult(vo) {
    setDraftContent(vo.ocrFullText || '');
    updateStatus('解析完成，可以在线编辑并保存 Word 草稿。');
    setStep(3);
}
async function uploadTemplate(file) {
    if (!file) return;
    if (!/\.(pdf|docx)$/i.test(file.name)) return toast('仅支持 PDF 或 DOCX 格式');
    $('#uploadFileName').textContent = file.name;
    $('#pickFileBtn').disabled = true;
    updateStatus('正在上传并解析合同模板...');
    setStep(2);
    try {
        const formData = new FormData();
        formData.append('file', file);
        formData.append('runOcr', 'true');
        if (draftState.contractId) formData.append('contractId', String(draftState.contractId));
        const vo = await uploadApi('/api/attachments/upload', formData);
        updateOcrPanel(vo);
        if (vo.ocrStatus === 'SUCCESS') showParsedResult(vo);
    } finally {
        $('#pickFileBtn').disabled = false;
        $('#fileInput').value = '';
    }
}
async function retryOcr() {
    const vo = await api(`/api/attachments/${draftState.attachmentId}/ocr`, {method: 'POST'});
    updateOcrPanel(vo);
    if (vo.ocrStatus === 'SUCCESS') showParsedResult(vo);
}
const zone = $('#uploadZone');
const input = $('#fileInput');
$('#pickFileBtn').addEventListener('click', event => { event.preventDefault(); event.stopPropagation(); input.click(); });
input.addEventListener('change', event => uploadTemplate(event.target.files[0]).catch(error => toast(error.message)));
zone.addEventListener('dragover', event => { event.preventDefault(); zone.classList.add('dragover'); });
zone.addEventListener('dragleave', () => zone.classList.remove('dragover'));
zone.addEventListener('drop', event => { event.preventDefault(); zone.classList.remove('dragover'); uploadTemplate(event.dataTransfer?.files?.[0]).catch(error => toast(error.message)); });
$('#retryOcrBtn').addEventListener('click', () => retryOcr().catch(error => toast(error.message)));
$('#saveDraftBtn').addEventListener('click', () => saveDraft().catch(error => toast(error.message)));
$('#aiReviewBtn').addEventListener('click', () => launchAiReview().catch(error => toast(error.message)));
versionBox.addEventListener('click', event => {
    const link = event.target.closest('[data-download-url]');
    if (!link) return;
    event.preventDefault();
    downloadFile(link.dataset.downloadUrl, link.dataset.fileName).catch(error => toast(error.message));
});
$('#downloadDraftBtn').addEventListener('click', () => {
    const link = $('#savedVersion a');
    if (link) downloadFile(link.dataset.downloadUrl, link.dataset.fileName).catch(error => toast(error.message));
    else toast('草稿下载接口待接入');
});
$('#draftEditor').addEventListener('input', updateCharCount);

// 编辑器工具栏按钮
const editorEl = $('#draftEditor');

$$('.toolbar-btns button').forEach(btn => {
    // 阻止 mousedown 默认行为，防止点击按钮时编辑器失焦导致选区丢失
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

// 根据当前选区状态高亮工具栏按钮（加粗/斜体/列表等）
function refreshToolbarState() {
    $$('.toolbar-btns button').forEach(btn => {
        const cmd = btn.dataset.cmd;
        if (cmd === 'undo' || cmd === 'redo') return;
        const active = document.queryCommandState(cmd);
        btn.classList.toggle('active', active);
    });
}

// 光标移动或选区变化时刷新按钮高亮状态
document.addEventListener('selectionchange', () => {
    if (document.activeElement === editorEl || editorEl.contains(document.activeElement)) {
        refreshToolbarState();
    }
});

// 编辑器获得焦点时也刷新一次（处理点击编辑器不同位置的情况）
editorEl.addEventListener('click', refreshToolbarState);
editorEl.addEventListener('keyup', refreshToolbarState);

initMode();
restoreLatestDraft().catch(error => toast(error.message));

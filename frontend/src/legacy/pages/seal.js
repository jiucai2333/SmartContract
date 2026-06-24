if (!initAppShell('seal', '签章登记', '登记合同签章信息，管理盖章流程与印章记录')) throw new Error('auth required');

const P = new URLSearchParams(location.search);
let contractId = P.get('contractId');
let versionId = P.get('versionId');
let uploadedFileId = null;
let uploadedFileUrl = null;
let uploadedFileName = null;
const sealReadOnly = !canOperate('SEAL_ARCHIVE');

(async function init() {
    if (!contractId) {
        await showPicker();
        return;
    }
    await loadContract();
})();

async function showPicker() {
    updateSteps(1);
    try {
        const list = await api('/api/contracts');
        const approved = list.filter(c => c.status === 'APPROVED');
        $('#contractPicker').innerHTML = approved.length === 0
            ? '<div class="list-item"><span>暂无可签章的合同。</span></div>'
            : '<h3 style="margin-bottom:12px">选择要签章的合同（已审批状态）</h3>' + approved.map(c => {
                const label = STATUS_TEXT[c.status] || c.status;
                return `<div class="list-item"><div class="list-item-info"><strong>${escapeHtml(c.contractNo)} - ${escapeHtml(c.title)}</strong><span>${escapeHtml(c.counterparty)} · ¥${Number(c.amount || 0).toLocaleString()} · <span class="tag tag-purple">${escapeHtml(label)}</span></span></div><a class="secondary" href="/html/seal.html?contractId=${c.contractId}" onclick="return pick(${c.contractId})">${sealReadOnly ? '查看' : '签章登记'}</a></div>`;
            }).join('');
        $('#contractPicker').style.display = 'block';
    } catch (e) {
        showErr('加载合同失败：' + e.message);
    }
}

window.pick = function(cid) {
    contractId = String(cid);
    versionId = null;
    loadContract();
    return false;
};

async function resolveVersionId(c) {
    if (versionId) return versionId;
    const versions = await api(`/api/contracts/${c.contractId}/versions`).catch(() => []);
    if (versions && versions.length) {
        versionId = versions[0].versionId;
        return versionId;
    }
    return null;
}

async function loadContract() {
    updateSteps(2);
    try {
        const list = await api('/api/contracts');
        const c = list.find(x => x.contractId === Number(contractId));
        if (!c) { showErr('未找到合同'); return; }
        if (c.status !== 'APPROVED') {
            showErr(`当前状态为“${STATUS_TEXT[c.status] || c.status}”，仅“已审批”合同可签章`, true);
            return;
        }
        if (!await resolveVersionId(c)) {
            showErr(`合同“${c.title || c.contractNo}”暂无可签章版本，请先进入草稿编辑页保存一个版本。`, true, c.contractId);
            return;
        }
        $('#contractCard').innerHTML = `<h3>${escapeHtml(c.title)}</h3><div class="info-grid">` + [
            '合同编号', c.contractNo,
            '相对方', c.counterparty,
            '金额', '¥' + Number(c.amount || 0).toLocaleString(),
            '类型', c.type,
            '状态', `<span class="tag tag-purple">${escapeHtml(STATUS_TEXT[c.status] || c.status)}</span>`,
            '签章版本', String(versionId || '未选择'),
            '签署日期', c.signDate || '-',
            '经办部门', 'ID: ' + (c.deptId || '-'),
            '风险等级', `<span class="tag ${c.riskLevel}">${escapeHtml(RISK_TEXT[c.riskLevel] || c.riskLevel || '-')}</span>`
        ].reduce((a, v, i) => i % 2 === 0 ? a + `<div class="info-item"><span class="info-label">${v}</span><span class="info-value">` : a + `${v}</span></div>`, '') + '</div>';
        $('#contractCard').style.display = 'block';
        $('#contractPicker').style.display = 'none';
        $('#sealFormArea').style.display = sealReadOnly ? 'none' : 'block';
        const now = new Date();
        $('#sealTime').value = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}T${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
        $('#operatorName').value = state.username || '';
    } catch (e) {
        showErr('加载合同失败：' + e.message);
    }
}

function updateSteps(n) {
    $$('#stepsBar .step').forEach((s, i) => {
        s.classList.remove('done', 'current');
        if (i + 1 < n) s.classList.add('done');
        else if (i + 1 === n) s.classList.add('current');
    });
}

function showErr(msg, showPickerToo, editContractId) {
    $('#contractCard').style.display = 'none';
    $('#sealFormArea').style.display = 'none';
    $('#statusMsg').style.display = 'block';
    $('#statusMsg').innerHTML = `<div class="notice">${escapeHtml(msg)}</div><div class="button-row" style="margin-top:8px">${editContractId ? `<a href="/html/draft.html?contractId=${editContractId}" class="primary-btn">去保存草稿版本</a>` : ''}<a href="/html/ledger.html" class="link-btn">返回台账</a></div>`;
    if (showPickerToo) showPicker();
}

(function bindUpload() {
    const zone = $('#uploadZone');
    const inp = $('#fileInput');
    const prev = $('#filePreview');
    if (!zone) return;
    if (sealReadOnly) return;
    zone.addEventListener('click', () => inp.click());
    zone.addEventListener('dragover', e => { e.preventDefault(); zone.classList.add('dragover'); });
    zone.addEventListener('dragleave', () => zone.classList.remove('dragover'));
    zone.addEventListener('drop', e => {
        e.preventDefault();
        zone.classList.remove('dragover');
        if (e.dataTransfer.files[0]) doUpload(e.dataTransfer.files[0]);
    });
    inp.addEventListener('change', () => { if (inp.files[0]) doUpload(inp.files[0]); });

    async function doUpload(file) {
        if (!file.type.match(/pdf|jpe?g|png/) && !file.name.match(/\.(pdf|jpg|jpeg|png)$/i)) {
            toast('仅支持 PDF、JPG、PNG 文件');
            return;
        }
        prev.style.display = 'block';
        prev.innerHTML = `<p>上传中：${escapeHtml(file.name)}...</p>`;
        const fd = new FormData();
        fd.append('file', file);
        fd.append('attachType', 'SIGNED_FILE');
        fd.append('runOcr', 'false');
        try {
            const r = await uploadApi('/api/attachments/upload', fd);
            const attachmentId = r.attachmentId || r.id;
            uploadedFileId = r.fileId;
            uploadedFileUrl = r.fileUrl || (attachmentId ? `/api/attachments/${attachmentId}/download` : null);
            uploadedFileName = file.name;
            if (!uploadedFileId) throw new Error('未返回文件 ID');
            prev.innerHTML = `<span>${escapeHtml(file.name)} 上传成功</span><button type="button" class="secondary" id="clearFile">移除</button>`;
            $('#clearFile').addEventListener('click', () => {
                uploadedFileId = uploadedFileUrl = uploadedFileName = null;
                prev.style.display = 'none';
                prev.innerHTML = '';
                inp.value = '';
            });
            updateSteps(3);
            // 显示签章方式选择
            showSealTypeChoice();
            if (contractId && attachmentId) {
                try {
                    await api(`/api/attachments/${attachmentId}/link`, { method: 'POST', body: JSON.stringify({ contractId: Number(contractId) }) });
                } catch {}
            }
        } catch (e) {
            prev.innerHTML = `<p style="color:var(--danger)">上传失败：${escapeHtml(e.message)}</p>`;
        }
    }
})();

function showSealTypeChoice() {
    $('#sealTypeChoice').style.display = 'block';
    $('#physicalSealSection').style.display = 'none';
}

$('#confirmSealTypeBtn').addEventListener('click', () => {
    const selected = document.querySelector('input[name="sealType"]:checked');
    if (!selected) { toast('请选择签章方式'); return; }
    if (selected.value === 'electronic') {
        // 跳转到电子签章页面
        const params = new URLSearchParams({
            contractId: contractId,
            versionId: versionId || '',
            fileId: uploadedFileId || '',
            fileName: uploadedFileName || ''
        });
        location.href = '/html/signature.html?' + params.toString();
    } else {
        // 物理盖章：显示签章信息表单
        $('#sealTypeChoice').style.display = 'none';
        $('#physicalSealSection').style.display = 'block';
        $('#sealStatus').value = 'SEALED';
        updateSteps(4);
    }
});

$('#submitSealBtn').addEventListener('click', async () => {
    if (!contractId) { toast('请先选择合同'); return; }
    if (!versionId) { toast('该合同暂无可签章版本，请先保存草稿版本'); return; }
    if (!uploadedFileId) { toast('请先上传签章文件'); return; }
    const st = $('#sealStatus').value || 'ELECTRONIC';
    if (!st) { toast('请选择签章状态'); return; }
    try {
        $('#submitSealBtn').disabled = true;
        $('#submitSealBtn').textContent = '登记中...';
        await api(`/api/contracts/${contractId}/seal`, {
            method: 'POST',
            body: JSON.stringify({
                contractId: Number(contractId),
                versionId: Number(versionId),
                fileId: uploadedFileId,
                fileUrl: uploadedFileUrl,
                fileName: uploadedFileName,
                sealStatus: st,
                sealTime: $('#sealTime').value || new Date().toISOString(),
                remark: $('#remark').value || ''
            })
        });
        updateSteps(4);
        toast('签章登记成功，合同状态已更新为已签章');
        setTimeout(() => { location.href = '/html/ledger.html'; }, 1500);
    } catch (e) {
        toast(e.message);
        $('#submitSealBtn').disabled = false;
        $('#submitSealBtn').textContent = '确认签章登记';
    }
});

$('#cancelBtn').addEventListener('click', () => {
    if (uploadedFileId && !confirm('已上传文件将保留为附件，确定返回？')) return;
    location.href = '/html/ledger.html';
});

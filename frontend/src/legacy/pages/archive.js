if (!initAppShell('archive', '归档确认', '确认合同归档入库，支持电子文件管理与检索')) throw new Error('auth required');

const P = new URLSearchParams(location.search);
let contractId = P.get('contractId');
let versionId = P.get('versionId');
let isElectronicSign = false;   // 是否为电子签章合同
let signedFileUploaded = false;  // 是否已上传已签章文件
const archiveReadOnly = !canOperate('SEAL_ARCHIVE');

(async function init() {
    if (!contractId) {
        await showPicker();
        return;
    }
    await loadData();
})();

async function showPicker() {
    updateSteps(1);
    try {
        const list = await api('/api/contracts');
        const signing = list.filter(c => c.status === 'SIGNING');
        $('#contractPicker').innerHTML = signing.length === 0
            ? '<div class="list-item"><span>暂无待归档的合同。</span></div>'
            : '<h3 style="margin-bottom:12px">选择要归档的合同（已签章状态）</h3>' + signing.map(c => {
                const label = STATUS_TEXT[c.status] || c.status;
                return `<div class="list-item"><div class="list-item-info"><strong>${escapeHtml(c.contractNo)} - ${escapeHtml(c.title)}</strong><span>${escapeHtml(c.counterparty)} · ¥${Number(c.amount || 0).toLocaleString()} · <span class="tag tag-orange">${escapeHtml(label)}</span></span></div><a class="secondary" href="/html/archive.html?contractId=${c.contractId}" onclick="return pick(${c.contractId})">${archiveReadOnly ? '查看' : '归档确认'}</a></div>`;
            }).join('');
        $('#contractPicker').style.display = 'block';
    } catch (e) {
        showErr('加载合同失败：' + e.message);
    }
}

window.pick = function(cid) {
    contractId = String(cid);
    loadData();
    return false;
};

async function loadData() {
    updateSteps(2);
    try {
        const list = await api('/api/contracts');
        const c = list.find(x => x.contractId === Number(contractId));
        if (!c) {
            showErr('未找到合同');
            return;
        }
        if (c.status !== 'SIGNING') {
            showErr(`当前状态为“${STATUS_TEXT[c.status] || c.status}”，仅“已签章”合同可归档`, true);
            return;
        }
        if (!versionId) {
            try {
                const vs = await api(`/api/contracts/${c.contractId}/versions`);
                if (vs && vs.length) versionId = vs[0].versionId;
            } catch {}
        }
        const [sealRecords, attachments, versions] = await Promise.all([
            api(`/api/contracts/${c.contractId}/seal-records`).catch(() => []),
            api(`/api/contracts/${c.contractId}/attachments`).catch(() => []),
            api(`/api/contracts/${c.contractId}/versions`).catch(() => [])
        ]);
        $('#contractCard').innerHTML = `<h3>${escapeHtml(c.title)}</h3><div class="info-grid">` + [
            '合同编号', c.contractNo,
            '相对方', c.counterparty,
            '金额', '¥' + Number(c.amount || 0).toLocaleString(),
            '类型', c.type,
            '状态', `<span class="tag tag-orange">${escapeHtml(STATUS_TEXT[c.status] || c.status)}</span>`,
            '归档版本', String(versionId || '未选择'),
            '签署日期', c.signDate || '-',
            '经办部门', 'ID: ' + (c.deptId || '-'),
            '风险等级', `<span class="tag ${c.riskLevel}">${escapeHtml(RISK_TEXT[c.riskLevel] || c.riskLevel || '-')}</span>`
        ].reduce((a, v, i) => i % 2 === 0 ? a + `<div class="info-item"><span class="info-label">${v}</span><span class="info-value">` : a + `${v}</span></div>`, '') + '</div>';
        $('#contractCard').style.display = 'block';

        const signed = attachments.filter(a => a.attachType === 'SIGNED_FILE' || a.attachType === 'SIGNED');
        const sealFileNames = new Set(sealRecords.map(r => r.fileName).filter(Boolean));
        const supplementalSigned = signed.filter(a => !sealFileNames.has(a.fileName));
        const sealFiles = [
            ...sealRecords.map(r => ({
                fileName: r.fileName || '签章文件',
                downloadUrl: r.fileUrl,
                attachmentId: null,
                fileSize: null
            })),
            ...supplementalSigned
        ];
        $('#sealFilesList').innerHTML = sealFiles.length === 0
            ? '<p style="color:var(--muted)">暂无签章文件</p>'
            : sealFiles.map(f => `<div class="list-row"><span><strong>${escapeHtml(f.fileName)}</strong>${f.fileSize != null ? ` · ${(f.fileSize / 1024).toFixed(1)}KB` : ''}</span>${f.downloadUrl ? `<a class="secondary" href="${escapeHtml(f.downloadUrl)}" data-direct-dl="${escapeHtml(f.downloadUrl)}" data-fn="${escapeHtml(f.fileName)}">下载</a>` : f.attachmentId ? `<button class="secondary" data-dl="${f.attachmentId}" data-fn="${escapeHtml(f.fileName)}">下载</button>` : ''}</div>`).join('');
        $('#filesCard').style.display = 'block';

        $('#sealRecordsList').innerHTML = sealRecords.length === 0
            ? '<p style="color:var(--muted)">暂无签章记录</p>'
            : sealRecords.map(r => `<div class="list-row"><span><strong>${escapeHtml(r.fileName || '签章文件')}</strong> · ${escapeHtml(SEAL_STATUS_TEXT[r.sealStatus] || r.sealStatus || '-')} · ${fmtTime(r.sealTime)}</span></div>`).join('');
        $('#recordsCard').style.display = 'block';

        // 检测是否为电子签章 → 需要上传已签章文件
        // ELECTRONIC: 发起但未完成 / SIGNED: 法大大回调后完成 / fadada provider: 电子签章
        isElectronicSign = sealRecords.some(r => r.sealStatus === 'ELECTRONIC' || r.sealStatus === 'SIGNED' || r.signatureProvider === 'fadada');
        signedFileUploaded = false;
        if (isElectronicSign && !archiveReadOnly) {
            $('#esignUploadCard').style.display = 'block';
            $('#uploadedSignedFiles').innerHTML = '';
            $('#signedFileName').textContent = '未选择文件';
            $('#uploadSignedFileBtn').disabled = true;
        } else {
            $('#esignUploadCard').style.display = 'none';
        }

        const ver = versions.find(v => v.versionId === Number(versionId));
        const vNo = ver ? ver.versionNo : (versionId || 'unknown');
        const now = new Date();
        $('#pvNo').textContent = `AR-${now.getFullYear()}-${contractId}-${String(vNo).replace(/\./g, '-')}`;
        $('#pvTime').textContent = now.toLocaleString('zh-CN', { hour12: false });
        $('#pvVer').textContent = vNo;
        $('#previewCard').style.display = 'block';
        $('#actionBar').style.display = archiveReadOnly ? 'none' : 'block';
        $('#contractPicker').style.display = 'none';
        updateSteps(3);
        $$('[data-dl]').forEach(b => b.addEventListener('click', async e => {
            e.preventDefault();
            const r = await fetch(`/api/attachments/${b.dataset.dl}/download`, { headers: state.accessToken ? { Authorization: `Bearer ${state.accessToken}` } : {} });
            if (r.status === 401) { logout(); return; }
            if (!r.ok) { toast('下载失败'); return; }
            const blob = await r.blob();
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = b.dataset.fn || 'file';
            a.click();
            URL.revokeObjectURL(url);
        }));
        $$('[data-direct-dl]').forEach(link => link.addEventListener('click', async e => {
            e.preventDefault();
            const r = await fetch(link.dataset.directDl, { headers: state.accessToken ? { Authorization: `Bearer ${state.accessToken}` } : {} });
            if (r.status === 401) { logout(); return; }
            if (!r.ok) { toast('下载失败'); return; }
            const blob = await r.blob();
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = link.dataset.fn || 'file';
            a.click();
            URL.revokeObjectURL(url);
        }));
    } catch (e) {
        showErr('加载归档数据失败：' + e.message);
    }
}

function updateSteps(n) {
    $$('#stepsBar .step').forEach((s, i) => {
        s.classList.remove('done', 'current');
        if (i + 1 < n) s.classList.add('done');
        else if (i + 1 === n) s.classList.add('current');
    });
}

function fmtTime(v) {
    if (!v) return '-';
    if (Array.isArray(v)) {
        const [y, m, d] = v;
        return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
    }
    const s = String(v);
    return s.includes('T') ? s.replace('T', ' ').substring(0, 19) : s;
}

function showErr(msg, showPickerToo) {
    ['contractCard', 'filesCard', 'recordsCard', 'previewCard'].forEach(id => {
        const el = $('#' + id);
        if (el) el.style.display = 'none';
    });
    $('#actionBar').style.display = 'none';
    $('#statusMsg').style.display = 'block';
    $('#statusMsg').innerHTML = `<div class="notice">${escapeHtml(msg)}</div><a href="/html/ledger.html" class="link-btn" style="margin-top:8px">返回台账</a>`;
    if (showPickerToo) showPicker();
}

$('#confirmBtn').addEventListener('click', async () => {
    if (!contractId || !versionId) { toast('缺少参数'); return; }
    if (isElectronicSign && !signedFileUploaded) {
        toast('该合同为电子签章，请先上传已签章文件再归档');
        return;
    }
    try {
        $('#confirmBtn').disabled = true;
        $('#confirmBtn').textContent = '归档中...';
        const r = await api(`/api/contracts/${contractId}/archive`, {
            method: 'POST',
            body: JSON.stringify({ contractId: Number(contractId), versionId: Number(versionId) })
        });
        toast(`归档成功！编号：${r.archiveNo}`);
        setTimeout(() => { location.href = '/html/ledger.html'; }, 1500);
    } catch (e) {
        toast(e.message);
        $('#confirmBtn').disabled = false;
        $('#confirmBtn').textContent = '确认归档';
    }
});

// ==================== 电子签章：已签章文件上传 ====================
$('#pickSignedFileBtn').addEventListener('click', () => {
    $('#signedFileInput').click();
});

$('#signedFileInput').addEventListener('change', function () {
    var f = this.files[0];
    if (f) {
        $('#signedFileName').textContent = f.name;
        $('#uploadSignedFileBtn').disabled = false;
    }
});

$('#uploadSignedFileBtn').addEventListener('click', async function () {
    var file = $('#signedFileInput').files[0];
    if (!file) { toast('请先选择文件'); return; }
    var btn = this;
    btn.disabled = true;
    btn.textContent = '上传中...';
    try {
        var fd = new FormData();
        fd.append('file', file);
        fd.append('contractId', contractId);
        fd.append('attachType', 'SIGNED_FILE');
        var att = await api('/api/attachments/upload', { method: 'POST', body: fd });
        signedFileUploaded = true;
        $('#uploadedSignedFiles').innerHTML = '<div class="list-row" style="background:#f0fdf4;border:1px solid #86efac;border-radius:8px;padding:8px 14px"><span style="color:#166534;font-size:13px">已上传：<strong>' + escapeHtml(att.fileName || file.name) + '</strong></span></div>';
        $('#signedFileName').textContent = '上传完成';
        btn.textContent = '已上传';
        toast('已签章文件上传成功');
    } catch (e) {
        toast('上传失败：' + e.message);
        btn.disabled = false;
        btn.textContent = '上传已签章文件';
    }
});

$('#cancelBtn').addEventListener('click', () => { location.href = '/html/ledger.html'; });

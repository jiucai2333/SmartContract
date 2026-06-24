if (!initAppShell('draft', '合同草稿', '管理合同草稿，支持在线编辑、保存与下载')) throw new Error('auth required');

const TYPE_TEXT = {
    PURCHASE: '采购合同', SALES: '销售合同', TECH: '技术合同',
    LABOR: '劳务合同', LEASE: '租赁合同', OTHER: '其他'
};

let drafts = [];

async function loadDrafts() {
    const params = new URLSearchParams();
    params.set('status', 'DRAFT');
    if ($('#draftKeyword').value) params.set('keyword', $('#draftKeyword').value);
    if ($('#draftType').value) params.set('type', $('#draftType').value);
    drafts = await api(`/api/contracts?${params}`);
    renderDrafts(drafts);
    enrichVersions().catch(() => {});
}

function renderDrafts(rows) {
    const body = $('#draftRows');
    if (!rows.length) {
        body.innerHTML = '<tr><td colspan="9" class="empty-cell"><i data-lucide="file"></i><p>暂无合同草稿</p></td></tr>';
        renderLucideIcons();
        return;
    }
    body.innerHTML = rows.map(x => `
    <tr>
      <td>${escapeHtml(x.contractNo || '-')}</td>
      <td><strong>${escapeHtml(x.title || '无标题')}</strong></td>
      <td>${escapeHtml(x.counterparty || '-')}</td>
      <td>${escapeHtml(TYPE_TEXT[x.type] || x.type || '-')}</td>
      <td class="amount">¥${Number(x.amount || 0).toLocaleString()}</td>
      <td><span class="draft-badge">草稿</span></td>
      <td>${x.updatedAt ? new Date(x.updatedAt).toLocaleString('zh-CN') : '-'}</td>
      <td>${escapeHtml(x.ownerId || '-')}</td>
      <td><div class="row-actions">
        <a href="/html/edit.html?contractId=${x.contractId}">编辑</a>
        <button class="icon-btn" data-download="${x.contractId}" title="下载 Word"><i data-lucide="download"></i></button>
        <button class="icon-btn danger-icon" data-delete="${x.contractId}" title="删除"><i data-lucide="trash-2"></i></button>
      </div></td>
    </tr>`).join('');
    renderLucideIcons();
}

let timer;
$('#draftKeyword').addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(() => loadDrafts().catch(e => toast(e.message)), 250); });
$('#draftType').addEventListener('change', () => loadDrafts().catch(e => toast(e.message)));
$('#draftResetBtn').addEventListener('click', () => { $('#draftKeyword').value = ''; $('#draftType').value = ''; loadDrafts().catch(e => toast(e.message)); });

$('#draftRows').addEventListener('click', async e => {
    const downloadBtn = e.target.closest('[data-download]');
    if (downloadBtn) {
        const contractId = downloadBtn.dataset.download;
        downloadBtn.disabled = true;
        try {
            const version = await api(`/api/contracts/${contractId}/versions/latest`);
            if (!version || !version.downloadUrl) {
                toast('暂无草稿版本可下载，请先编辑并保存草稿');
            } else {
                await downloadFile(version.downloadUrl, `contract-${contractId}-${version.versionNo}.docx`);
            }
        } catch (err) { toast(err.message); }
        downloadBtn.disabled = false;
        return;
    }
    const deleteBtn = e.target.closest('[data-delete]');
    if (deleteBtn) {
        if (!confirm('确定要删除此草稿吗？删除后不可恢复。')) return;
        deleteBtn.disabled = true;
        try { await api(`/api/contracts/${deleteBtn.dataset.delete}`, {method: 'DELETE'}); toast('草稿已删除'); await loadDrafts(); }
        catch (err) { toast(err.message); deleteBtn.disabled = false; }
    }
});

async function enrichVersions() {
    for (const row of $$('#draftRows tr')) {
        const downloadBtn = row.querySelector('[data-download]');
        if (!downloadBtn) continue;
        try {
            const version = await api(`/api/contracts/${downloadBtn.dataset.download}/versions/latest`);
            if (version && version.versionNo && row.cells[6]) row.cells[6].innerHTML += `<br><small class="muted-text">${escapeHtml(version.versionNo)}</small>`;
        } catch {}
    }
}

loadDrafts().catch(e => toast(e.message));

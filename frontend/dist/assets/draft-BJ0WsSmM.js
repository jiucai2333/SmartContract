const t=`if (!initAppShell('draft', '合同草稿', '管理合同草稿，支持在线编辑、保存与下载')) throw new Error('auth required');

const TYPE_TEXT = {
    PURCHASE: '采购合同', SALES: '销售合同', TECH: '技术合同',
    LABOR: '劳务合同', CONFIDENTIAL: '保密合同', LOGISTICS: '物流合同',
    ENTERPRISE_SERVICE: '企业服务合同',
    INTELLECTUAL_PROPERTY: '知识产权合同',
    OTHER: '其他'
};

const PAGE_SIZE = 5;
let drafts = [];
let draftPage = 1;
let draftRenderSequence = 0;

async function loadDrafts(resetPage = false) {
    const params = new URLSearchParams();
    params.set('status', 'DRAFT');
    if ($('#draftKeyword').value) params.set('keyword', $('#draftKeyword').value);
    if ($('#draftType').value) params.set('type', $('#draftType').value);
    const body = $('#draftRows');
    const pager = $('#draftPager');
    renderTableState(body, 9, { type: 'loading', title: '正在加载合同草稿' });
    if (pager) pager.innerHTML = '';
    try {
        drafts = await api(\`/api/contracts?\${params}\`);
        if (resetPage) draftPage = 1;
        await renderDrafts();
    } catch (error) {
        renderTableState(body, 9, {
            type: 'error',
            title: '合同草稿加载失败',
            message: error.message || '请稍后重试。',
            actionHtml: '<button type="button" class="secondary" data-retry-drafts>重新加载</button>'
        });
        throw error;
    }
}

function clampDraftPage(total) {
    const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
    draftPage = Math.min(Math.max(1, draftPage), totalPages);
    return totalPages;
}

function renderDraftPager(total) {
    const pager = $('#draftPager');
    const totalPages = clampDraftPage(total);
    const start = total ? (draftPage - 1) * PAGE_SIZE + 1 : 0;
    const end = Math.min(total, draftPage * PAGE_SIZE);
    pager.innerHTML = \`
        <div class="pagination-info">共 \${total} 条，每页 \${PAGE_SIZE} 条，显示 \${start}-\${end}</div>
        <div class="pagination-actions">
            <button class="secondary" type="button" data-draft-page="prev" \${draftPage <= 1 ? 'disabled' : ''}>上一页</button>
            <span class="pagination-current">第 \${draftPage} / \${totalPages} 页</span>
            <button class="secondary" type="button" data-draft-page="next" \${draftPage >= totalPages ? 'disabled' : ''}>下一页</button>
        </div>
    \`;
}

async function renderDrafts() {
    const renderSequence = ++draftRenderSequence;
    const body = $('#draftRows');
    const total = drafts.length;
    clampDraftPage(total);
    const rows = drafts.slice((draftPage - 1) * PAGE_SIZE, draftPage * PAGE_SIZE);
    renderDraftPager(total);
    if (!rows.length) {
        renderTableState(body, 9, {
            title: '暂无合同草稿',
            message: '新建编制后，未提交审批的合同会显示在这里。',
            actionHtml: '<a class="link-btn" href="/edit">新建编制</a>'
        });
        return;
    }
    const versionLists = await Promise.all(rows.map(x =>
        api(\`/api/contracts/\${x.contractId}/versions\`).catch(() => [])
    ));
    if (renderSequence !== draftRenderSequence) return;

    body.innerHTML = rows.map((x, index) => {
    const versions = versionLists[index] || [];
    const versionOptions = versions.map(version =>
        \`<option value="\${escapeHtml(version.versionId)}" data-version-no="\${escapeHtml(version.versionNo || '')}">\${escapeHtml(version.versionNo || '未命名版本')}</option>\`
    ).join('');
    const versionSelect = versions.length
        ? \`<select class="draft-version-select" data-version-select="\${x.contractId}" aria-label="选择 \${escapeHtml(x.title || x.contractNo || '合同草稿')} 的版本">\${versionOptions}</select>\`
        : '<span class="hint">暂无版本</span>';
    return \`
    <tr>
      <td>\${escapeHtml(x.contractNo || '-')}</td>
      <td><strong>\${escapeHtml(x.title || '无标题')}</strong></td>
      <td>\${escapeHtml(x.counterparty || '-')}</td>
      <td>\${escapeHtml(TYPE_TEXT[x.type] || x.type || '-')}</td>
      <td class="amount">¥\${Number(x.amount || 0).toLocaleString()}</td>
      <td>\${versionSelect}</td>
      <td>\${x.updatedAt ? new Date(x.updatedAt).toLocaleString('zh-CN') : '-'}</td>
      <td>\${escapeHtml(x.ownerId || '-')}</td>
      <td><div class="row-actions">
        <a href="/html/edit.html?contractId=\${x.contractId}" title="编辑合同信息和正文">编辑</a>
        <button class="icon-btn" data-download="\${x.contractId}"
                \${versions.length ? '' : 'disabled'}
                title="下载 Word" aria-label="下载 \${escapeHtml(x.title || x.contractNo || '合同草稿')} Word"><i data-lucide="download"></i></button>
        <button class="icon-btn danger-icon" data-delete="\${x.contractId}" title="删除" aria-label="删除 \${escapeHtml(x.title || x.contractNo || '合同草稿')}"><i data-lucide="trash-2"></i></button>
      </div></td>
    </tr>\`;
    }).join('');
    renderLucideIcons();
}

let timer;
$('#draftKeyword').addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(() => loadDrafts(true).catch(e => toast(e.message)), 250); });
$('#draftType').addEventListener('change', () => loadDrafts(true).catch(e => toast(e.message)));
$('#draftResetBtn').addEventListener('click', () => { $('#draftKeyword').value = ''; $('#draftType').value = ''; loadDrafts(true).catch(e => toast(e.message)); });

$('#draftPager').addEventListener('click', e => {
    const btn = e.target.closest('[data-draft-page]');
    if (!btn || btn.disabled) return;
    draftPage += btn.dataset.draftPage === 'next' ? 1 : -1;
    renderDrafts().catch(err => toast(err.message));
});

$('#draftRows').addEventListener('click', e => {
    if (e.target.closest('[data-retry-drafts]')) {
        loadDrafts().catch(err => toast(err.message));
    }
});

$('#draftRows').addEventListener('click', async e => {
    const downloadBtn = e.target.closest('[data-download]');
    if (downloadBtn) {
        const contractId = downloadBtn.dataset.download;
        const versionSelect = $(\`[data-version-select="\${contractId}"]\`);
        const selectedOption = versionSelect?.selectedOptions?.[0];
        downloadBtn.disabled = true;
        try {
            if (!versionSelect || !selectedOption?.value) {
                toast('暂无草稿版本可下载，请先编辑并保存草稿');
            } else {
                const versionNo = selectedOption.dataset.versionNo || selectedOption.textContent;
                await downloadFile(
                    \`/api/contracts/\${contractId}/versions/\${selectedOption.value}/download\`,
                    \`contract-\${contractId}-\${versionNo}.docx\`
                );
            }
        } catch (err) { toast(err.message); }
        downloadBtn.disabled = !versionSelect?.options?.length;
        return;
    }
    const deleteBtn = e.target.closest('[data-delete]');
    if (deleteBtn) {
        if (!await confirmDialog('确定要删除此草稿吗？删除后不可恢复。', {title: '删除确认', confirmText: '删除', type: 'danger'})) return;
        deleteBtn.disabled = true;
        try { await api(\`/api/contracts/\${deleteBtn.dataset.delete}\`, {method: 'DELETE'}); toast('草稿已删除'); await loadDrafts(); }
        catch (err) { toast(err.message); deleteBtn.disabled = false; }
    }
});

loadDrafts().catch(e => toast(e.message));
`;export{t as default};

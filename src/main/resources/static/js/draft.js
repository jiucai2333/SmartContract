if (!initAppShell('draft', '合同草稿', '管理合同草稿，支持在线编辑、保存与下载')) throw new Error('auth required');

// ─── 图标定义 ──────────────────────────────────────────────────────────────────
// ─── 常量 & 状态 ───────────────────────────────────────────────────────────────
const typeText = {
    PURCHASE: '采购合同',
    SALES:    '销售合同',
    TECH:     '技术合同',
    LABOR:    '劳务合同',
    LEASE:    '租赁合同',
    OTHER:    '其他',
};

let drafts = [];

// ─── 数据加载 ──────────────────────────────────────────────────────────────────
async function loadDrafts() {
    const params = new URLSearchParams();
    params.set('status', 'DRAFT');
    if ($('#draftKeyword').value) params.set('keyword', $('#draftKeyword').value);
    if ($('#draftType').value)    params.set('type',    $('#draftType').value);
    drafts = await api(`/api/contracts?${params}`);
    renderDrafts(drafts);
}

// ─── 渲染 ──────────────────────────────────────────────────────────────────────
function renderDrafts(rows) {
    const body = $('#draftRows');

    if (!rows.length) {
        body.innerHTML = '<tr><td colspan="9" class="empty-cell"><i data-lucide="file"></i><p>暂无合同草稿</p></td></tr>';
        renderLucideIcons();
        return;
    }

    body.innerHTML = rows.map((x) => `
    <tr>
      <td>${escapeHtml(x.contractNo || '-')}</td>
      <td><strong>${escapeHtml(x.title || '无标题')}</strong></td>
      <td>${escapeHtml(x.counterparty || '-')}</td>
      <td>${escapeHtml(typeText[x.type] || x.type || '-')}</td>
      <td class="amount">¥${Number(x.amount || 0).toLocaleString()}</td>
      <td><span class="draft-badge">草稿</span></td>
      <td>${x.updatedAt ? new Date(x.updatedAt).toLocaleString('zh-CN') : '-'}</td>
      <td>${escapeHtml(x.ownerName || x.ownerId || '-')}</td>
      <td>
        <div class="row-actions">
          <a href="/html/edit.html?contractId=${x.contractId}">编辑</a>
          <button class="icon-btn"              data-download="${x.contractId}" title="下载 Word"><i data-lucide="download"></i></button>
          <button class="icon-btn danger-icon"  data-delete="${x.contractId}"  title="删除"><i data-lucide="trash-2"></i></button>
        </div>
      </td>
    </tr>
    `).join('');
    renderLucideIcons();
}

// ─── 事件绑定 ──────────────────────────────────────────────────────────────────
let timer;

$('#draftKeyword').addEventListener('input', () => {
    clearTimeout(timer);
    timer = setTimeout(() => loadDrafts().catch((e) => toast(e.message)), 250);
});

$('#draftType').addEventListener('change', () =>
    loadDrafts().catch((e) => toast(e.message))
);

$('#draftResetBtn').addEventListener('click', () => {
    $('#draftKeyword').value = '';
    $('#draftType').value    = '';
    loadDrafts().catch((e) => toast(e.message));
});

$('#draftRows').addEventListener('click', (e) => {
    if (e.target.closest('[data-download]')) toast('草稿下载接口待接入');
    if (e.target.closest('[data-delete]'))   toast('草稿删除接口待接入');
});

// ─── 初始化 ────────────────────────────────────────────────────────────────────
loadDrafts().catch((e) => toast(e.message));

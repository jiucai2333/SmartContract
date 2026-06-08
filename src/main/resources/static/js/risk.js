if (!initAppShell('risk', 'AI 风险审查', 'Risk Audit')) throw new Error('auth required');

const searchParams = new URLSearchParams(location.search);
const pageState = {
    contractId: Number(searchParams.get('contractId')) || null,
    versionId: Number(searchParams.get('versionId')) || null,
    autoReview: searchParams.get('auto') === '1',
    currentReportId: Number(searchParams.get('reportId')) || null
};

const reviewBtn = $('#reviewBtn');
const clearBtn = $('#clearBtn');
const contractTextInput = $('#contractText');
const contractBody = $('#contractBody');
const riskListEl = $('#riskList');
const reportSummaryEl = $('#reportSummary');
const reportListEl = $('#reportList');

reviewBtn.addEventListener('click', async () => {
    const contractText = contractTextInput.value.trim();
    if (!contractText) {
        toast('请输入合同正文');
        return;
    }
    reviewBtn.disabled = true;
    reviewBtn.textContent = 'AI 审查中...';
    riskListEl.innerHTML = '<div class="list-item"><span>正在调用 Qwen 进行风险审查，请稍候...</span></div>';
    reportSummaryEl.className = 'report-summary empty';
    reportSummaryEl.textContent = 'AI 审核中，完成后会自动生成并保存报告。';
    try {
        const body = {
            contractText,
            contractId: pageState.contractId || undefined,
            versionId: pageState.versionId || undefined,
            contractType: $('#contractType').value || undefined,
            partyA: $('#partyA').value.trim() || undefined,
            partyB: $('#partyB').value.trim() || undefined,
            businessScope: $('#businessScope').value.trim() || undefined,
            specialTerms: $('#specialTerms').value.trim() || undefined
        };
        const result = await api('/api/ai/risk-review', {method: 'POST', body: JSON.stringify(body)});
        const risks = Array.isArray(result) ? result : (result.risks || []);
        pageState.currentReportId = result.reportId || null;
        renderContractBody(contractText);
        renderRisks(risks);
        renderReportSummary(result);
        await loadReports();
        toast(result.reportId ? `风险报告 ${result.reportNo} 已保存` : 'AI 风险审查完成');
    } catch (e) {
        toast(e.message || '风险审查失败');
        riskListEl.innerHTML = `<div class="list-item"><span>审查失败：${escapeHtml(e.message)}</span></div>`;
        reportSummaryEl.className = 'report-summary empty';
        reportSummaryEl.textContent = '风险报告生成失败，请检查后端服务、数据库和 AI 配置。';
    } finally {
        reviewBtn.disabled = false;
        reviewBtn.textContent = 'AI 风险审查';
    }
});

clearBtn.addEventListener('click', () => {
    pageState.contractId = null;
    pageState.versionId = null;
    pageState.autoReview = false;
    pageState.currentReportId = null;
    contractTextInput.value = '';
    $('#contractType').value = '';
    $('#partyA').value = '';
    $('#partyB').value = '';
    $('#businessScope').value = '';
    $('#specialTerms').value = '';
    contractBody.innerHTML = '<p class="hint">提交合同文本或打开历史报告后，这里会展示合同条款。点击右侧风险卡片可定位到对应条款。</p>';
    riskListEl.innerHTML = '<div class="list-item"><span>请提交合同文本，AI 将进行风险审查。</span></div>';
    reportSummaryEl.className = 'report-summary empty';
    reportSummaryEl.textContent = '完成 AI 审核后，报告会自动存库并显示在这里。';
    loadReports().catch(error => toast(error.message));
});

function renderReportSummary(report) {
    if (!report || !report.reportId) {
        reportSummaryEl.className = 'report-summary empty';
        reportSummaryEl.textContent = '本次审查未生成报告。';
        return;
    }
    const level = (report.highestRiskLevel || 'LOW').toUpperCase();
    reportSummaryEl.className = `report-summary ${level}`;
    reportSummaryEl.innerHTML = `
        <div>
            <strong>${escapeHtml(report.reportNo || `报告 #${report.reportId}`)}</strong>
            <span>${escapeHtml(formatRiskLevel(level))} · ${Number(report.riskCount || 0)} 项风险</span>
        </div>
        <button class="secondary compact" type="button" data-report-id="${report.reportId}">查看报告</button>
    `;
}

function renderReportDetails(report) {
    pageState.currentReportId = report.reportId;
    pageState.contractId = report.contractId || pageState.contractId;
    pageState.versionId = report.versionId || pageState.versionId;
    if (report.contractType) $('#contractType').value = report.contractType;
    if (report.partyA) $('#partyA').value = report.partyA;
    if (report.partyB) $('#partyB').value = report.partyB;
    if (report.businessScope) $('#businessScope').value = report.businessScope;
    contractTextInput.value = report.contractText || '';
    renderContractBody(report.contractText || '');
    renderRisks(report.risks || []);
    renderReportSummary(report);
    renderReportsHighlight();
}

async function loadReports() {
    const params = new URLSearchParams();
    if (pageState.contractId) params.set('contractId', String(pageState.contractId));
    const reports = await api(`/api/risk-reports?${params.toString()}`);
    renderReports(Array.isArray(reports) ? reports : []);
}

function renderReports(reports) {
    if (!reports.length) {
        reportListEl.innerHTML = '<div class="list-item"><span>暂无风险报告</span></div>';
        return;
    }
    reportListEl.innerHTML = reports.map(report => {
        const level = (report.highestRiskLevel || 'LOW').toUpperCase();
        return `<button class="report-item ${report.reportId === pageState.currentReportId ? 'active' : ''}" type="button" data-report-id="${report.reportId}">
            <span class="tag ${escapeHtml(level)}">${escapeHtml(formatRiskLevel(level))}</span>
            <strong>${escapeHtml(report.reportNo || `报告 #${report.reportId}`)}</strong>
            <small>${escapeHtml(report.summary || '')}</small>
            <em>${report.createdAt ? new Date(report.createdAt).toLocaleString('zh-CN') : ''}</em>
        </button>`;
    }).join('');
}

function renderReportsHighlight() {
    $$('.report-item', reportListEl).forEach(item => {
        item.classList.toggle('active', Number(item.dataset.reportId) === pageState.currentReportId);
    });
}

reportSummaryEl.addEventListener('click', event => {
    const button = event.target.closest('[data-report-id]');
    if (!button) return;
    openReport(button.dataset.reportId).catch(error => toast(error.message));
});

reportListEl.addEventListener('click', event => {
    const item = event.target.closest('[data-report-id]');
    if (!item) return;
    openReport(item.dataset.reportId).catch(error => toast(error.message));
});

async function openReport(reportId) {
    const report = await api(`/api/risk-reports/${reportId}`);
    renderReportDetails(report);
    history.replaceState(null, '', buildReportUrl(report));
}

function buildReportUrl(report) {
    const params = new URLSearchParams();
    if (report.contractId) params.set('contractId', String(report.contractId));
    if (report.versionId) params.set('versionId', String(report.versionId));
    params.set('reportId', String(report.reportId));
    return `/html/risk.html?${params.toString()}`;
}

function renderContractBody(text) {
    const paragraphs = htmlToPlainText(text).split(/\n{2,}/).filter(p => p.trim());
    if (!paragraphs.length) {
        contractBody.innerHTML = '<p class="hint">暂无合同正文。</p>';
        return;
    }
    contractBody.innerHTML = paragraphs.map((p, i) => {
        const firstLine = p.trim().split(/[。；;\n]/)[0].substring(0, 20);
        const clauseName = firstLine.length > 2 ? firstLine : `条款${i + 1}`;
        return `<p data-clause="${escapeHtml(clauseName)}">${escapeHtml(p.trim())}</p>`;
    }).join('');
}

function renderRisks(risks) {
    if (!risks.length) {
        riskListEl.innerHTML = '<div class="list-item"><span>AI 审查完成，未发现明显风险。</span></div>';
        return;
    }
    riskListEl.innerHTML = risks.map(risk => {
        const level = (risk.level || 'LOW').toUpperCase();
        const levelLabel = formatRiskLevel(level);
        return `<button class="risk-card ${escapeHtml(level)}" data-clause="${escapeHtml(risk.clause || '')}">
            <strong><span class="tag ${escapeHtml(level)}">${escapeHtml(levelLabel)}</span> ${escapeHtml(risk.clause || '未指明条款')}</strong>
            <span>${escapeHtml(risk.reason || '')}</span>
            <small>建议：${escapeHtml(risk.suggestion || '')}</small>
        </button>`;
    }).join('');
}

function formatRiskLevel(level) {
    return {LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险'}[level] || RISK_TEXT[level] || level;
}

riskListEl.addEventListener('click', event => {
    const card = event.target.closest('[data-clause]');
    if (!card) return;
    const clause = card.dataset.clause;
    $$('[data-clause]', contractBody).forEach(el => el.classList.remove('active-clause'));
    const target = $(`#contractBody [data-clause="${CSS.escape(clause)}"]`);
    if (target) {
        target.classList.add('active-clause');
        target.scrollIntoView({behavior: 'smooth', block: 'center'});
    }
});

async function hydrateFromVersion() {
    if (!pageState.contractId) return;
    try {
        const [contract, version] = await Promise.all([
            api('/api/contracts').then(rows => rows.find(row => row.contractId === pageState.contractId)).catch(() => null),
            pageState.versionId
                ? api(`/api/contracts/${pageState.contractId}/versions/${pageState.versionId}`)
                : api(`/api/contracts/${pageState.contractId}/versions/latest`)
        ]);
        if (contract) {
            $('#contractType').value = contract.type || '';
            $('#partyB').value = contract.counterparty || '';
            $('#businessScope').value = contract.title || '';
        }
        pageState.versionId = version?.versionId || pageState.versionId;
        const text = htmlToPlainText(version?.content || '');
        if (!text) {
            toast('未读取到合同正文，请手动粘贴后审核');
            return;
        }
        contractTextInput.value = text;
        renderContractBody(text);
        if (pageState.autoReview) reviewBtn.click();
    } catch (error) {
        toast(error.message || '合同版本加载失败，请手动粘贴后审核');
    }
}

function htmlToPlainText(value) {
    const text = String(value || '');
    if (!/<[a-z][\s\S]*>/i.test(text)) return text;
    const box = document.createElement('div');
    box.innerHTML = text
        .replace(/<br\s*\/?>/gi, '\n')
        .replace(/<\/(p|div|li|tr|h[1-6])>/gi, '\n\n');
    return (box.textContent || '').replace(/\n{3,}/g, '\n\n').trim();
}

async function initPage() {
    await loadReports();
    if (pageState.currentReportId) {
        await openReport(pageState.currentReportId);
        return;
    }
    await hydrateFromVersion();
}

initPage().catch(error => toast(error.message));

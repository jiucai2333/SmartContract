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
const reportEls = ensureReportPanels();
const reportSummaryEl = reportEls.summary;
const reportListEl = reportEls.list;
let reviewProgressTimer = null;

const REVIEW_PROGRESS_STEPS = [
    '识别合同结构',
    '检查关键条款',
    '评估风险等级',
    '整理审查报告'
];

function ensureReportPanels() {
    let summary = $('#reportSummary');
    let list = $('#reportList');
    if (summary && list) {
        return {summary, list};
    }
    const reviewLayout = $('.review-layout');
    const section = document.createElement('section');
    section.className = 'section report-layout';
    section.innerHTML = `
        <div class="panel">
            <div class="panel-head"><div><p class="eyebrow">Saved Report</p><h2>本次风险报告</h2></div></div>
            <div id="reportSummary" class="report-summary empty">完成 AI 审核后，报告会自动存库并显示在这里。</div>
        </div>
        <div class="panel">
            <div class="panel-head"><div><p class="eyebrow">History</p><h2>历史风险报告</h2></div></div>
            <div id="reportList" class="report-list">
                <div class="list-item"><span>暂无风险报告</span></div>
            </div>
        </div>
    `;
    if (reviewLayout?.parentElement) {
        reviewLayout.parentElement.insertBefore(section, reviewLayout);
    } else {
        document.querySelector('.app-shell')?.appendChild(section);
    }
    summary = $('#reportSummary');
    list = $('#reportList');
    return {summary, list};
}

reviewBtn.addEventListener('click', async () => {
    const contractText = contractTextInput.value.trim();
    if (!contractText) {
        toast('请输入合同正文');
        return;
    }
    reviewBtn.disabled = true;
    reviewBtn.textContent = '正在核对...';
    startReviewProgress();
    reportSummaryEl.className = 'report-summary empty';
    reportSummaryEl.textContent = '正在核对合同条款，完成后会自动保存报告。';
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
        const reportId = getReportId(result);
        if (reportId) {
            pageState.currentReportId = reportId;
            await openReport(reportId);
            await loadReports();
            renderReportsHighlight();
            toast(`风险报告 ${result.reportNo || `#${reportId}`} 已保存`);
            return;
        }
        const risks = riskItemsOf(result);
        renderContractBody(contractText);
        renderRisks(risks);
        renderReportSummary(result);
        await loadReports();
        toast('风险审查完成');
    } catch (e) {
        toast(e.message || '风险审查失败');
        riskListEl.innerHTML = `<div class="list-item"><span>审查失败：${escapeHtml(e.message)}</span></div>`;
        reportSummaryEl.className = 'report-summary empty';
        reportSummaryEl.textContent = '风险报告生成失败，请检查后端服务、数据库和 AI 配置。';
    } finally {
        stopReviewProgress();
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
    riskListEl.innerHTML = '<div class="list-item"><span>请提交合同文本，系统将核对主要风险条款。</span></div>';
    reportSummaryEl.className = 'report-summary empty';
    reportSummaryEl.textContent = '完成审查后，报告会自动存库并显示在这里。';
    loadReports().catch(error => toast(error.message));
});

function renderReportSummary(report) {
    const reportId = getReportId(report);
    if (!report || !reportId) {
        reportSummaryEl.className = 'report-summary empty';
        reportSummaryEl.textContent = '本次审查未生成报告。';
        return;
    }
    const risks = riskItemsOf(report);
    const level = normalizeRiskLevel(report.highestRiskLevel || report.riskLevel || highestRiskLevelOf(risks));
    const riskCount = Number(report.riskCount ?? risks.length ?? 0);
    reportSummaryEl.className = `report-summary ${level}`;
    reportSummaryEl.innerHTML = `
        <div>
            <strong>${escapeHtml(report.reportNo || `报告 #${reportId}`)}</strong>
            <span>${escapeHtml(formatRiskLevel(level))} · ${riskCount} 项风险</span>
        </div>
        <button class="secondary compact" type="button" data-download-report-id="${reportId}">下载报告</button>
    `;
}

function renderReportDetails(report) {
    pageState.currentReportId = getReportId(report);
    pageState.contractId = positiveId(report.contractId);
    pageState.versionId = positiveId(report.versionId);
    if (report.contractType) $('#contractType').value = report.contractType;
    if (report.partyA) $('#partyA').value = report.partyA;
    if (report.partyB) $('#partyB').value = report.partyB;
    if (report.businessScope) $('#businessScope').value = report.businessScope;
    contractTextInput.value = report.contractText || '';
    renderContractBody(report.contractText || '');
    renderRisks(riskItemsOf(report));
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
        const reportId = getReportId(report);
        const level = normalizeRiskLevel(report.highestRiskLevel || report.riskLevel);
        return `<button class="report-item ${reportId === pageState.currentReportId ? 'active' : ''}" type="button" data-report-id="${reportId}">
            <span class="tag ${escapeHtml(level)}">${escapeHtml(formatRiskLevel(level))}</span>
            <strong>${escapeHtml(report.reportNo || `报告 #${reportId}`)}</strong>
            <small>${escapeHtml(formatReportSummary(report.summary || ''))}</small>
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
    const downloadButton = event.target.closest('[data-download-report-id]');
    if (downloadButton) {
        downloadRiskReport(downloadButton.dataset.downloadReportId).catch(error => toast(error.message));
        return;
    }
    const openButton = event.target.closest('[data-report-id]');
    if (!openButton) return;
    openReport(openButton.dataset.reportId).catch(error => toast(error.message));
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

async function downloadRiskReport(reportId) {
    await downloadFile(`/api/risk-reports/${reportId}/export`, `风险报告-${reportId}.docx`);
}

function buildReportUrl(report) {
    const params = new URLSearchParams();
    if (report.contractId) params.set('contractId', String(report.contractId));
    if (report.versionId) params.set('versionId', String(report.versionId));
    params.set('reportId', String(getReportId(report)));
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
        riskListEl.innerHTML = '<div class="list-item"><span>本次核对未发现明显风险。</span></div>';
        return;
    }
    riskListEl.innerHTML = risks.map(risk => {
        const level = normalizeRiskLevel(risk.level || risk.riskLevel);
        const levelLabel = formatRiskLevel(level);
        const clause = risk.clause || risk.clauseRef || '未指明条款';
        return `<article class="risk-card ${escapeHtml(level)}" data-clause="${escapeHtml(clause)}" tabindex="0">
            <strong><span class="tag ${escapeHtml(level)}">${escapeHtml(levelLabel)}</span> ${escapeHtml(clause)}</strong>
            <span>${escapeHtml(risk.reason || risk.riskType || '')}</span>
            <small>建议：${escapeHtml(risk.suggestion || '')}</small>
        </article>`;
    }).join('');
}

function startReviewProgress() {
    let stepIndex = 0;
    const startedAt = Date.now();
    const render = () => {
        const step = REVIEW_PROGRESS_STEPS[Math.min(stepIndex, REVIEW_PROGRESS_STEPS.length - 1)];
        const elapsedSeconds = Math.max(1, Math.round((Date.now() - startedAt) / 1000));
        riskListEl.innerHTML = `
            <div class="review-progress">
                <div class="progress-orbit" aria-hidden="true"></div>
                <div class="progress-copy">
                    <strong>正在核对合同条款</strong>
                    <span>${escapeHtml(step)} · 已用时 ${elapsedSeconds} 秒</span>
                </div>
            </div>
        `;
    };
    stopReviewProgress();
    render();
    reviewProgressTimer = setInterval(() => {
        stepIndex = Math.min(stepIndex + 1, REVIEW_PROGRESS_STEPS.length - 1);
        render();
    }, 12000);
}

function stopReviewProgress() {
    if (reviewProgressTimer) {
        clearInterval(reviewProgressTimer);
        reviewProgressTimer = null;
    }
}

function formatRiskLevel(level) {
    const normalized = normalizeRiskLevel(level);
    return {LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险'}[normalized] || RISK_TEXT[normalized] || normalized;
}

function formatReportSummary(summary) {
    return String(summary || '').replaceAll('AI 审核完成', '风险审查完成');
}

function normalizeRiskLevel(level) {
    const normalized = String(level || 'LOW').trim().toUpperCase();
    if (normalized === 'HIGH' || normalized.includes('高')) return 'HIGH';
    if (normalized === 'MEDIUM' || normalized.includes('中')) return 'MEDIUM';
    return 'LOW';
}

function riskItemsOf(value) {
    if (Array.isArray(value)) return value;
    if (!value || typeof value !== 'object') return [];
    return [value.risks, value.riskItems, value.items, value.records]
        .find(Array.isArray) || [];
}

function getReportId(report) {
    const id = Number(report?.reportId ?? report?.id);
    return Number.isFinite(id) && id > 0 ? id : null;
}

function positiveId(value) {
    const id = Number(value);
    return Number.isFinite(id) && id > 0 ? id : null;
}

function highestRiskLevelOf(risks) {
    if (risks.some(risk => normalizeRiskLevel(risk.level || risk.riskLevel) === 'HIGH')) return 'HIGH';
    if (risks.some(risk => normalizeRiskLevel(risk.level || risk.riskLevel) === 'MEDIUM')) return 'MEDIUM';
    return 'LOW';
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

riskListEl.addEventListener('keydown', event => {
    if (event.key !== 'Enter' && event.key !== ' ') return;
    const card = event.target.closest('[data-clause]');
    if (!card) return;
    event.preventDefault();
    card.click();
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

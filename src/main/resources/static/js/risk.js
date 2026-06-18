if (!initAppShell('risk', 'AI 风险审查', '上传 PDF 合同，自动提取正文并输出风险评估报告')) throw new Error('auth required');

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
const contractPdfInput = $('#contractPdf');
const pdfStatusEl = $('#pdfStatus');
const contractBody = $('#contractBody');
const riskListEl = $('#riskList');
const reportSummaryEl = $('#reportSummary');
const reportListEl = $('#reportList');
let reviewProgressTimer = null;

contractTextInput?.closest('label')?.classList.add('hidden-text-input');

const REVIEW_PROGRESS_STEPS = ['上传解析 PDF', '识别合同结构', '检查关键条款', '整理审查报告'];
const RISK_CATEGORY_TEXT = {
    LEGAL_COMPLIANCE: '法律合规风险',
    PERFORMANCE_DELIVERY: '履约交付风险',
    PAYMENT_SETTLEMENT: '付款结算风险',
    IP_CONFIDENTIALITY: '知识产权与保密风险',
    LIABILITY_APPROVAL: '违约责任与审批风险'
};

contractPdfInput?.addEventListener('change', () => {
    contractTextInput.value = '';
    pageState.currentReportId = null;
    const file = selectedPdfFile();
    setPdfStatus(file ? `${file.name} · ${(file.size / 1024 / 1024).toFixed(2)} MB` : '请选择 PDF 合同文件');
});

reviewBtn.addEventListener('click', async () => {
    let contractText = contractTextInput.value.trim();
    if (!contractText) {
        contractText = await uploadPdfAndExtractText();
        if (!contractText) return;
        contractTextInput.value = contractText;
    }

    reviewBtn.disabled = true;
    reviewBtn.textContent = '正在审查...';
    startReviewProgress(1);
    reportSummaryEl.className = 'report-summary empty';
    reportSummaryEl.textContent = '正在审查合同条款，完成后会自动保存报告。';

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
            await openReport(reportId, false);
            await loadReports();
            renderReportsHighlight();
            toast(`风险报告 ${result.reportNo || `#${reportId}`} 已保存`);
            return;
        }
        renderContractBody(contractText);
        renderRisks(riskItemsOf(result));
        renderReportSummary(result);
        await loadReports();
        toast('风险审查完成');
    } catch (error) {
        toast(error.message || '风险审查失败');
        riskListEl.innerHTML = `<div class="list-item"><span>审查失败：${escapeHtml(error.message || '')}</span></div>`;
        reportSummaryEl.className = 'report-summary empty';
        reportSummaryEl.textContent = '风险报告生成失败，请检查后端服务、数据库和 AI 配置。';
    } finally {
        stopReviewProgress();
        reviewBtn.disabled = false;
        reviewBtn.textContent = '开始风险审查';
    }
});

clearBtn.addEventListener('click', () => {
    pageState.currentReportId = null;
    contractTextInput.value = '';
    if (contractPdfInput) contractPdfInput.value = '';
    setPdfStatus('选择 PDF 后点击开始审查，系统会自动提取合同正文。');
    $('#contractType').value = '';
    $('#partyA').value = '';
    $('#partyB').value = '';
    $('#businessScope').value = '';
    $('#specialTerms').value = '';
    contractBody.innerHTML = '<p class="hint">上传 PDF 并完成解析后，这里会展示合同正文。</p>';
    riskListEl.innerHTML = '<div class="list-item"><span>请上传 PDF 合同文件后开始审查。</span></div>';
    reportSummaryEl.className = 'report-summary empty';
    reportSummaryEl.textContent = '完成审查后，报告会自动保存并显示在这里。';
    loadReports().catch(error => toast(error.message));
});

function selectedPdfFile() {
    const file = contractPdfInput?.files?.[0];
    if (!file) return null;
    return file.name.toLowerCase().endsWith('.pdf') ? file : null;
}

function setPdfStatus(message) {
    if (pdfStatusEl) pdfStatusEl.textContent = message;
}

async function uploadPdfAndExtractText() {
    const file = selectedPdfFile();
    if (!file) {
        toast('请先选择 PDF 合同文件');
        return '';
    }
    reviewBtn.disabled = true;
    reviewBtn.textContent = '正在解析 PDF...';
    startReviewProgress(0);
    setPdfStatus('正在上传并解析 PDF，请稍候...');
    try {
        const formData = new FormData();
        formData.append('file', file);
        formData.append('runOcr', 'true');
        formData.append('attachType', 'CONTRACT_FILE');
        if (pageState.contractId) formData.append('contractId', String(pageState.contractId));
        const attachment = await uploadApi('/api/attachments/upload', formData);
        const text = htmlToPlainText(attachment?.ocrFullText || attachment?.ocrTextPreview || '');
        if (!text || text.replace(/\s+/g, '').length < 30) {
            throw new Error('PDF 解析结果为空，请换一个可识别的合同 PDF');
        }
        setPdfStatus(`已解析：${attachment.fileName || file.name}${attachment.pageCount ? `，${attachment.pageCount} 页` : ''}`);
        renderContractBody(text);
        return text;
    } catch (error) {
        setPdfStatus(error.message || 'PDF 解析失败');
        toast(error.message || 'PDF 解析失败');
        return '';
    } finally {
        stopReviewProgress();
        reviewBtn.disabled = false;
        reviewBtn.textContent = '开始风险审查';
    }
}

async function loadReports() {
    const params = new URLSearchParams();
    if (pageState.contractId) params.set('contractId', String(pageState.contractId));
    const query = params.toString();
    const reports = await api(`/api/risk-reports${query ? `?${query}` : ''}`);
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
            <span>${escapeHtml(formatReportSummary(report.summary || ''))}</span>
        </div>
        <button class="secondary compact" type="button" data-download-report-id="${reportId}">下载报告</button>`;
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

function renderReportsHighlight() {
    $$('.report-item', reportListEl).forEach(item => {
        item.classList.toggle('active', Number(item.dataset.reportId) === pageState.currentReportId);
    });
}

reportSummaryEl.addEventListener('click', event => {
    const downloadButton = event.target.closest('[data-download-report-id]');
    if (downloadButton) downloadRiskReport(downloadButton.dataset.downloadReportId).catch(error => toast(error.message));
});

reportListEl.addEventListener('click', event => {
    const item = event.target.closest('[data-report-id]');
    if (item) openReport(item.dataset.reportId).catch(error => toast(error.message));
});

async function openReport(reportId, updateUrl = true) {
    const report = await api(`/api/risk-reports/${reportId}`);
    renderReportDetails(report);
    if (updateUrl) history.replaceState(null, '', buildReportUrl(report));
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
        riskListEl.innerHTML = '<div class="list-item"><span>本次审查未发现明显风险。</span></div>';
        return;
    }
    riskListEl.innerHTML = risks.map(risk => {
        const level = normalizeRiskLevel(risk.level || risk.riskLevel);
        const category = normalizeRiskCategory(risk.category || risk.riskType);
        const clause = risk.clause || risk.clauseRef || '未指明条款';
        return `<article class="risk-card ${escapeHtml(level)}" data-clause="${escapeHtml(clause)}" tabindex="0">
            <strong><span class="tag ${escapeHtml(level)}">${escapeHtml(formatRiskLevel(level))}</span> <span class="tag">${escapeHtml(formatRiskCategory(category))}</span> ${escapeHtml(clause)}</strong>
            <span>${escapeHtml(risk.reason || '')}</span>
            <small>建议：${escapeHtml(risk.suggestion || '')}</small>
        </article>`;
    }).join('');
}

function startReviewProgress(startIndex = 0) {
    let stepIndex = startIndex;
    const startedAt = Date.now();
    const render = () => {
        const step = REVIEW_PROGRESS_STEPS[Math.min(stepIndex, REVIEW_PROGRESS_STEPS.length - 1)];
        const elapsedSeconds = Math.max(1, Math.round((Date.now() - startedAt) / 1000));
        riskListEl.innerHTML = `
            <div class="review-progress">
                <div class="progress-orbit" aria-hidden="true"></div>
                <div class="progress-copy">
                    <strong>正在处理合同</strong>
                    <span>${escapeHtml(step)} · 已用时 ${elapsedSeconds} 秒</span>
                </div>
            </div>`;
    };
    stopReviewProgress();
    render();
    reviewProgressTimer = setInterval(() => {
        stepIndex = Math.min(stepIndex + 1, REVIEW_PROGRESS_STEPS.length - 1);
        render();
    }, 10000);
}

function stopReviewProgress() {
    if (reviewProgressTimer) {
        clearInterval(reviewProgressTimer);
        reviewProgressTimer = null;
    }
}

function formatRiskLevel(level) {
    const normalized = normalizeRiskLevel(level);
    return {LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险'}[normalized] || normalized;
}

function normalizeRiskLevel(level) {
    const normalized = String(level || 'LOW').trim().toUpperCase();
    if (normalized === 'HIGH' || normalized.includes('高')) return 'HIGH';
    if (normalized === 'MEDIUM' || normalized.includes('中')) return 'MEDIUM';
    return 'LOW';
}

function formatRiskCategory(category) {
    return RISK_CATEGORY_TEXT[normalizeRiskCategory(category)] || RISK_CATEGORY_TEXT.LEGAL_COMPLIANCE;
}

function normalizeRiskCategory(category) {
    const value = String(category || '').trim().toUpperCase();
    if (RISK_CATEGORY_TEXT[value]) return value;
    if (value.includes('PAYMENT') || value.includes('付款') || value.includes('结算')) return 'PAYMENT_SETTLEMENT';
    if (value.includes('PERFORMANCE') || value.includes('DELIVERY') || value.includes('履约') || value.includes('交付') || value.includes('验收')) return 'PERFORMANCE_DELIVERY';
    if (value.includes('IP') || value.includes('CONFIDENTIAL') || value.includes('知识产权') || value.includes('保密')) return 'IP_CONFIDENTIALITY';
    if (value.includes('LIABILITY') || value.includes('APPROVAL') || value.includes('违约') || value.includes('审批')) return 'LIABILITY_APPROVAL';
    return 'LEGAL_COMPLIANCE';
}

function formatReportSummary(summary) {
    return String(summary || '').replaceAll('AI 审核完成', '风险审查完成');
}

function riskItemsOf(value) {
    if (Array.isArray(value)) return value;
    if (!value || typeof value !== 'object') return [];
    return [value.risks, value.riskItems, value.items, value.records].find(Array.isArray) || [];
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
        if (!text) return;
        contractTextInput.value = text;
        setPdfStatus('已从合同版本读取正文，也可以重新上传 PDF 审查。');
        renderContractBody(text);
        if (pageState.autoReview) reviewBtn.click();
    } catch (error) {
        toast(error.message || '合同版本加载失败，请上传 PDF 后审查');
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

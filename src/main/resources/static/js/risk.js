if (!initAppShell('risk', 'AI 风险审查', '上传 PDF 合同，自动提取正文并输出风险评估报告')) throw new Error('auth required');

const reviewBtn = $('#reviewBtn');
const clearBtn = $('#clearBtn');
const contractTextInput = $('#contractText');
const contractPdfInput = $('#contractPdf');
const pdfStatusEl = $('#pdfStatus');
const contractBody = $('#contractBody');
const riskListEl = $('#riskList');
const reportSummaryEl = $('#reportSummary');
const reportListEl = $('#reportList');
const searchParams = new URLSearchParams(location.search);
const pageState = {
    contractId: Number(searchParams.get('contractId')) || null,
    currentReportId: Number(searchParams.get('reportId')) || null
};

contractTextInput?.closest('label')?.classList.add('hidden-text-input');

contractPdfInput?.addEventListener('change', () => {
    contractTextInput.value = '';
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
    riskListEl.innerHTML = '<div class="list-item"><span>正在进行风险审查，请稍候...</span></div>';
    try {
        const body = {
            contractText,
            contractId: pageState.contractId || undefined,
            contractType: $('#contractType').value || undefined,
            partyA: $('#partyA').value.trim() || undefined,
            partyB: $('#partyB').value.trim() || undefined,
            businessScope: $('#businessScope').value.trim() || undefined,
            specialTerms: $('#specialTerms').value.trim() || undefined
        };
        const result = await api('/api/ai/risk-review', {method: 'POST', body: JSON.stringify(body)});
        pageState.contractId = result.contractId || pageState.contractId;
        const risks = riskItemsOf(result);
        renderContractBody(contractText);
        renderRisks(risks);
        const reportId = getReportId(result);
        if (reportId) {
            await openReport(reportId);
            await loadRiskReports();
            toast(`风险报告 ${result.reportNo || `#${reportId}`} 已保存`);
        } else {
            renderReportSummary(result);
            toast('风险审查完成');
        }
    } catch (error) {
        toast(error.message || '风险审查失败');
        riskListEl.innerHTML = `<div class="list-item"><span>审查失败：${escapeHtml(error.message || '')}</span></div>`;
    } finally {
        reviewBtn.disabled = false;
        reviewBtn.textContent = '开始风险审查';
    }
});

clearBtn.addEventListener('click', () => {
    contractTextInput.value = '';
    if (contractPdfInput) contractPdfInput.value = '';
    setPdfStatus('选择 PDF 后点击开始审查，系统会自动提取合同正文。');
    $('#contractType').value = '';
    $('#partyA').value = '';
    $('#partyB').value = '';
    $('#businessScope').value = '';
    $('#specialTerms').value = '';
    contractBody.innerHTML = '<p class="hint">上传 PDF 合同后，此处将展示解析出的合同条款，点击右侧风险卡片可定位到对应条款。</p>';
    riskListEl.innerHTML = '<div class="list-item"><span>请上传 PDF 合同文件，系统将自动解析并进行风险审查。</span></div>';
    renderReportSummary(null);
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
    riskListEl.innerHTML = '<div class="list-item"><span>正在上传并解析 PDF，请稍候...</span></div>';
    setPdfStatus('正在上传并解析 PDF，请稍候...');
    try {
        const formData = new FormData();
        formData.append('file', file);
        formData.append('runOcr', 'true');
        formData.append('attachType', 'CONTRACT_FILE');
        if (pageState.contractId) formData.append('contractId', String(pageState.contractId));
        const result = await uploadApi('/api/contracts/import/upload', formData);
        const text = htmlToPlainText(result?.plainText || result?.editorHtml || result?.previewHtml || result?.plainTextPreview || '');
        if (!text || text.replace(/\s+/g, '').length < 30) {
            const status = result?.ocrStatus ? `（状态：${result.ocrStatus}）` : '';
            throw new Error(`${result?.ocrError || 'PDF 解析结果为空，请换一个可识别的合同 PDF'}${status}`);
        }
        setPdfStatus(`已解析：${result.fileName || file.name}${result.pageCount ? `，${result.pageCount} 页` : ''}`);
        renderContractBody(text);
        autofillContractFields(text, result?.fileName || file.name);
        return text;
    } catch (error) {
        setPdfStatus(error.message || 'PDF 解析失败');
        toast(error.message || 'PDF 解析失败');
        return '';
    } finally {
        reviewBtn.disabled = false;
        reviewBtn.textContent = '开始风险审查';
    }
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
        const level = normalizeRiskLevel(risk.level || risk.riskLevel);
        const levelLabel = RISK_TEXT[level] || formatRiskLevel(level);
        const category = formatRiskCategory(risk.category || risk.riskType);
        return `<button class="risk-card ${escapeHtml(level)}" data-clause="${escapeHtml(risk.clause || risk.clauseRef || '')}">
            <strong class="risk-card-title">
                <span class="tag ${escapeHtml(level)}">${escapeHtml(levelLabel)}</span>
                <span class="tag">${escapeHtml(category)}</span>
                <span class="risk-clause">${escapeHtml(risk.clause || risk.clauseRef || '未指明条款')}</span>
            </strong>
            <span class="risk-reason">${escapeHtml(risk.reason || '')}</span>
            <small class="risk-suggestion">建议：${escapeHtml(risk.suggestion || '')}</small>
        </button>`;
    }).join('');
}

function autofillContractFields(text, fileName = '') {
    const normalized = htmlToPlainText(text).replace(/\s+/g, ' ').trim();
    if (!normalized) return;
    const meta = {
        contractType: detectContractType(captureContractValue(normalized, ['合同类型'], ['合同金额', '甲方', '乙方', '业务范围', '一、', '二、']) || fileName),
        partyA: captureContractValue(normalized, ['甲方', '采购方', '委托方'], ['统一社会信用代码', '乙方', '合同类型', '合同金额', '业务范围', '地址', '联系人', '一、']),
        partyB: captureContractValue(normalized, ['乙方', '供应商', '服务方', '受托方'], ['统一社会信用代码', '甲方', '合同类型', '合同金额', '业务范围', '地址', '联系人', '一、']),
        businessScope: captureContractValue(normalized, ['业务范围', '项目内容', '服务内容'], ['合同金额', '甲方', '乙方', '二、', '三、', '四、', '付款', '交付'])
    };
    setSelectIfEmpty($('#contractType'), meta.contractType);
    setInputIfEmpty($('#partyA'), meta.partyA);
    setInputIfEmpty($('#partyB'), meta.partyB);
    setInputIfEmpty($('#businessScope'), meta.businessScope);
}

function captureContractValue(text, labels, stops) {
    for (const label of labels) {
        const index = text.indexOf(label);
        if (index < 0) continue;
        let value = text.slice(index + label.length).replace(/^[:：\s]+/, '').trim();
        let end = value.length;
        for (const stop of stops) {
            const stopIndex = value.indexOf(stop);
            if (stopIndex > 0) end = Math.min(end, stopIndex);
        }
        value = value.slice(0, end).replace(/[，,。；;：:]+$/g, '').trim();
        if (value.length > 80) value = value.slice(0, 80).trim();
        if (value.length >= 2) return value;
    }
    return '';
}

function detectContractType(value) {
    if (!value) return '';
    if (value.includes('采购')) return '采购合同';
    if (value.includes('销售')) return '销售合同';
    if (value.includes('服务')) return '服务合同';
    if (value.includes('技术') || value.includes('开发')) return '技术开发合同';
    if (value.includes('租赁')) return '租赁合同';
    if (value.includes('劳动') || value.includes('劳务')) return '劳动合同';
    if (value.includes('合作') || value.includes('协议')) return '合作协议';
    return '';
}

function setInputIfEmpty(input, value) {
    if (input && !input.value.trim() && value) input.value = value;
}

function setSelectIfEmpty(select, value) {
    if (!select || select.value || !value) return;
    const option = Array.from(select.options).find(item => item.value === value || item.textContent.trim() === value);
    if (option) select.value = option.value;
}

riskListEl.addEventListener('click', event => {
    const card = event.target.closest('[data-clause]');
    if (!card) return;
    const clause = card.dataset.clause;
    $$('[data-clause]').forEach(el => el.classList.remove('active-clause'));
    const target = $(`#contractBody [data-clause="${CSS.escape(clause)}"]`);
    if (target) {
        target.classList.add('active-clause');
        target.scrollIntoView({behavior: 'smooth', block: 'center'});
    }
});

reportSummaryEl.addEventListener('click', event => {
    const button = event.target.closest('[data-download-report-id]');
    if (button) downloadRiskReport(button.dataset.downloadReportId).catch(error => toast(error.message));
});

reportListEl.addEventListener('click', event => {
    const item = event.target.closest('[data-report-id]');
    if (item) openReport(item.dataset.reportId).catch(error => toast(error.message));
});

async function loadRiskReports() {
    const params = new URLSearchParams();
    if (pageState.contractId) params.set('contractId', pageState.contractId);
    const query = params.toString();
    const reports = await api(`/api/risk-reports${query ? `?${query}` : ''}`);
    renderReportList(Array.isArray(reports) ? reports : []);
}

function renderReportList(reports) {
    if (!reports.length) {
        reportListEl.innerHTML = '<div class="list-item"><span>暂无风险报告</span></div>';
        return;
    }
    reportListEl.innerHTML = reports.map(report => {
        const reportId = getReportId(report);
        const level = normalizeRiskLevel(report.highestRiskLevel);
        return `<button class="report-item ${reportId === pageState.currentReportId ? 'active' : ''}"
                type="button" data-report-id="${reportId}">
            <span class="tag ${level}">${escapeHtml(RISK_TEXT[level] || formatRiskLevel(level))}</span>
            <strong>${escapeHtml(report.reportNo || `报告 #${reportId}`)}</strong>
            <em>${escapeHtml(formatReportTime(report.createdAt))}</em>
            <small>${escapeHtml(formatReportSummary(report.summary || `共发现 ${report.riskCount || 0} 项风险`))}</small>
        </button>`;
    }).join('');
}

async function openReport(reportId) {
    const report = await api(`/api/risk-reports/${reportId}`);
    pageState.currentReportId = getReportId(report);
    renderReportSummary(report);
    renderContractBody(report.contractText || '');
    renderRisks(riskItemsOf(report));
    $$('.report-item').forEach(item => {
        item.classList.toggle('active', Number(item.dataset.reportId) === pageState.currentReportId);
    });
}

function renderReportSummary(report) {
    if (!report || !getReportId(report)) {
        reportSummaryEl.className = 'report-summary empty';
        reportSummaryEl.textContent = '完成审查后，报告会自动保存并显示在这里。';
        return;
    }
    const reportId = getReportId(report);
    const level = normalizeRiskLevel(report.highestRiskLevel || report.riskLevel);
    reportSummaryEl.className = `report-summary ${level}`;
    reportSummaryEl.innerHTML = `<div>
        <strong>${escapeHtml(report.reportNo || `报告 #${reportId}`)}</strong>
        <span>最高风险：${escapeHtml(RISK_TEXT[level] || formatRiskLevel(level))}，共 ${Number(report.riskCount) || riskItemsOf(report).length || 0} 项</span>
        <span>${escapeHtml(formatReportSummary(report.summary || ''))}</span>
    </div>
    <button class="secondary compact" type="button" data-download-report-id="${reportId}">导出 DOCX</button>`;
}

async function downloadRiskReport(reportId) {
    await downloadFile(`/api/risk-reports/${reportId}/export`, `风险报告-${reportId}.docx`);
}

function riskItemsOf(value) {
    if (Array.isArray(value)) return value;
    if (!value || typeof value !== 'object') return [];
    return [value.risks, value.riskItems, value.items, value.records].find(Array.isArray) || [];
}

function getReportId(value) {
    const reportId = Number(value?.reportId ?? value?.id);
    return Number.isFinite(reportId) && reportId > 0 ? reportId : null;
}

function normalizeRiskLevel(level) {
    const normalized = String(level || 'LOW').toUpperCase();
    return ['HIGH', 'MEDIUM', 'LOW'].includes(normalized) ? normalized : 'LOW';
}

function formatRiskLevel(level) {
    return {LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险'}[normalizeRiskLevel(level)] || level;
}

function formatRiskCategory(category) {
    const value = String(category || '').trim().toUpperCase();
    const map = {
        LEGAL_COMPLIANCE: '法律合规风险',
        PERFORMANCE_DELIVERY: '履约交付风险',
        PAYMENT_SETTLEMENT: '付款结算风险',
        IP_CONFIDENTIALITY: '知识产权与保密风险',
        LIABILITY_APPROVAL: '违约责任与审批风险'
    };
    return map[value] || category || '法律合规风险';
}

function formatReportSummary(summary) {
    return String(summary || '').replaceAll('AI 审核完成', '风险审查完成');
}

function formatReportTime(value) {
    if (!value) return '';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN');
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

loadRiskReports()
    .then(() => pageState.currentReportId ? openReport(pageState.currentReportId) : null)
    .catch(error => {
        reportListEl.innerHTML = `<div class="list-item"><span>报告加载失败：${escapeHtml(error.message)}</span></div>`;
    });

if (!initAppShell('edit', '在线编辑', '支持合同内容编辑、模板解析、草稿保存与 Word 下载')) throw new Error('auth required');

const searchParams = new URLSearchParams(location.search);
const draftState = {
    attachmentId: null,
    lastFile: null,
    contractId: Number(searchParams.get('contractId')) || null,
    templateId: Number(searchParams.get('templateId')) || null,
    dirty: false,
    analyzedFields: [],
    analyzedRisks: [],
    latestRiskReport: null,
    currentContract: null
};
let editor = null;
let settingEditorContent = false;
const versionBox = document.createElement('div');
versionBox.id = 'savedVersion';
versionBox.className = 'hint';
versionBox.hidden = true;
$('#draftStatus').after(versionBox);
const touchedMetaFields = new Set();
['contractTitle', 'contractCounterparty', 'contractType', 'contractAmount'].forEach(id => {
    const field = document.getElementById(id);
    field?.addEventListener('input', event => { if (event.isTrusted) touchedMetaFields.add(id); });
    field?.addEventListener('change', event => { if (event.isTrusted) touchedMetaFields.add(id); });
});

function updateStatus(message) { $('#draftStatus').textContent = message; }
function initMode() {
    if (draftState.contractId) {
        return;
    }
    if (draftState.templateId) {
        updateStatus('正在准备模板...');
        return;
    }
}
function setStep(step) {
    $$('[data-step]').forEach(item => {
        const current = Number(item.dataset.step);
        item.classList.toggle('active', current === step);
        item.classList.toggle('done', current < step);
        item.classList.remove('failed');
    });
}
function setFailedStep(step) {
    setStep(step);
    document.querySelector(`[data-step="${step}"]`)?.classList.add('failed');
}
function editorText() {
    if (!editor) return '';
    const text = editor.getText();
    if (text) return text;
    return ($('#draftEditor [data-slate-editor]')?.innerText || '').replace(/\uFEFF/g, '').trimEnd();
}
function updateCharCount() { $('#draftCharCount').textContent = `${editorText().length} 字符`; }
function isPartyFieldLineText(text) {
    if (/签名|签字|签章|盖章|法定代表人|授权代表|签署日期/.test(text)) return false;
    return /^\s*(?:甲方|乙方|丙方|买方|卖方|委托方|受托方|服务方)(?:[（(].{0,30}[）)])?\s*[:：].{0,40}$/.test(text)
        || /^\s*(?:甲方|乙方|丙方|住所地|联系地址|地址|联系人|联系电话|电话|身份证号)\s*[：:].*/.test(text);
}
function normalizePartyFieldLines(html) {
    if (!html) return html;
    const template = document.createElement('template');
    template.innerHTML = html;
    template.content.querySelectorAll('p, h2, h3, div').forEach(element => {
        const text = (element.textContent || '').replace(/\s+/g, '').trim();
        if (!text || !isPartyFieldLineText(text)) return;
        element.classList.add('party-info');
        element.dataset.docStyle = 'FIELD_LINE';
        element.dataset.indentChars = '0';
        element.style.textIndent = '0';
        if (!element.style.textAlign) element.style.textAlign = 'left';
    });
    return template.innerHTML;
}
function createPlaceholderUnderline(token) {
    const span = createUnderlineFill(token, token);
    span.dataset.placeholderText = token;
    span.dataset.placeholderEmpty = 'true';
    return span;
}
function isEmptyPlaceholderUnderline(node) {
    return node?.nodeType === Node.ELEMENT_NODE
        && node.classList.contains('contract-fill-underline')
        && node.dataset.placeholderEmpty === 'true';
}
function normalizePlaceholderUnderlines(html) {
    if (!html) return html;
    const template = document.createElement('template');
    template.innerHTML = html;
    const textNodes = [];
    const walker = document.createTreeWalker(template.content, NodeFilter.SHOW_TEXT, {
        acceptNode(node) {
            if (!underlineTokenRegex().test(node.nodeValue || '')) return NodeFilter.FILTER_REJECT;
            if (node.parentElement?.closest('.contract-fill-underline')) return NodeFilter.FILTER_REJECT;
            return NodeFilter.FILTER_ACCEPT;
        }
    });
    let node;
    while ((node = walker.nextNode())) textNodes.push(node);
    textNodes.forEach(textNode => {
        const text = textNode.nodeValue || '';
        const fragment = document.createDocumentFragment();
        let lastIndex = 0;
        let match;
        const pattern = underlineTokenGlobalRegex();
        while ((match = pattern.exec(text))) {
            fragment.append(document.createTextNode(text.slice(lastIndex, match.index)));
            fragment.append(createPlaceholderUnderline(match[0]));
            lastIndex = match.index + match[0].length;
        }
        fragment.append(document.createTextNode(text.slice(lastIndex)));
        textNode.replaceWith(fragment);
    });
    return template.innerHTML;
}
function setDraftContent(html) {
    settingEditorContent = true;
    editor.setHtml(normalizePlaceholderUnderlines(normalizePartyFieldLines(html)) || '<p><br></p>');
    draftState.dirty = false;
    updateCharCount();
    setTimeout(() => {
        settingEditorContent = false;
        draftState.dirty = false;
        updateCharCount();
    }, 0);
}
function draftContent() { return editor.getHtml(); }
function hasEditorContent() { return editorText().trim().length > 0; }
function setMetaValue(selector, value, {force = false} = {}) {
    const input = $(selector);
    if (!input || value === null || value === undefined || value === '') return false;
    if (!force && touchedMetaFields.has(input.id)) return false;
    if (!force && input.tagName !== 'SELECT' && input.value.trim()) return false;
    input.value = value;
    input.dispatchEvent(new Event('input', {bubbles: true}));
    input.dispatchEvent(new Event('change', {bubbles: true}));
    return true;
}
function applyExtractedMeta(result) {
    if (draftState.contractId || !result?.extract) return;
    const extract = result.extract;
    const filled = [];
    if (setMetaValue('#contractTitle', extract.title)) filled.push('合同名称');
    if (setMetaValue('#contractCounterparty', extract.counterparty || extract.partyB || extract.partyA)) filled.push('合同相对方');
    if (setMetaValue('#contractAmount', extract.amount)) filled.push('合同金额');
    const typeOptions = Array.from($('#contractType')?.options || []).map(option => option.value);
    if (extract.contractType && typeOptions.includes(extract.contractType) && setMetaValue('#contractType', extract.contractType)) {
        filled.push('合同类型');
    }
    if (filled.length) toast(`已自动填充${filled.join('、')}`);
}
const FIELD_LEVEL_TEXT = {required: '基础必填', before_submit: '审批前提示', optional: '可选'};
const RISK_LEVEL_TEXT = {HIGH: '高风险', MEDIUM: '中风险', LOW: '低风险'};
const RISK_CATEGORY_TEXT = {
    SUBJECT_INFO: '主体信息风险',
    PAYMENT: '付款风险',
    LIABILITY: '违约风险',
    TERM: '期限风险',
    DISPUTE_RESOLUTION: '争议解决风险',
    LEGAL_COMPLIANCE: '法律合规风险',
    PERFORMANCE_DELIVERY: '履约交付风险',
    PAYMENT_SETTLEMENT: '付款结算风险',
    IP_CONFIDENTIALITY: '知识产权与保密风险',
    LIABILITY_APPROVAL: '违约责任与审批风险',
    AI_REVIEW: 'AI 风险识别'
};

function fieldInputValue(fieldKey) {
    return document.querySelector(`[data-field-input="${fieldKey}"]`)?.value.trim() || '';
}

function renderPlaceholderPreview(text) {
    const value = text || '正文中未定位到明确片段';
    const pattern = /_{2,}|＿{2,}|-{3,}|【\s*】|\[\s*]|\(\s*(?:填写)?\s*\)|（\s*(?:填写)?\s*）/g;
    let html = '';
    let lastIndex = 0;
    let match;
    while ((match = pattern.exec(value))) {
        html += escapeHtml(value.slice(lastIndex, match.index));
        html += '<span class="contract-placeholder-preview"></span>';
        lastIndex = match.index + match[0].length;
    }
    html += escapeHtml(value.slice(lastIndex));
    return html;
}

function renderContractFields(result) {
    draftState.analyzedFields = result.fields || [];
    renderFieldPanel();
    $('#fieldAnalysisSummary').textContent =
        `基础必填缺失 ${result.requiredMissingCount || 0} 项，审批前提示 ${result.beforeSubmitMissingCount || 0} 项，风险修改点 ${draftState.analyzedRisks.length} 项`;
    $('#fieldPanel').hidden = false;
    $('.editor-workspace')?.classList.add('has-field-panel');
}

function riskLevelText(level) {
    return RISK_LEVEL_TEXT[String(level || '').toUpperCase()] || level || '风险';
}

function riskCategoryText(category) {
    return RISK_CATEGORY_TEXT[String(category || '').toUpperCase()] || category || 'AI 风险识别';
}

function renderRiskReportVersion(report) {
    if (!report) return '';
    const version = report.versionId ? `草稿版本 #${report.versionId}` : '未绑定草稿版本';
    return `${report.reportNo || '最新风险报告'} · ${version}`;
}

function renderContractRisks() {
    if (!draftState.analyzedRisks.length) {
        const hint = draftState.contractId ? '当前合同暂无风险报告中的修改点。' : '保存草稿并生成风险报告后，这里会显示需修改点。';
        return `<section class="contract-field-section"><h4>风险修改点</h4><p class="hint">${hint}</p></section>`;
    }
    return `<section class="contract-field-section">
        <h4>风险修改点</h4>
        <p class="contract-risk-report-ref">${escapeHtml(renderRiskReportVersion(draftState.latestRiskReport))}</p>
        ${draftState.analyzedRisks.map(risk => {
            const level = String(risk.level || risk.riskLevel || 'LOW').toUpperCase();
            return `<div class="contract-field-card contract-risk-card ${escapeHtml(level)}">
                <div class="contract-field-title"><strong>${escapeHtml(risk.clause || risk.clauseRef || '未定位原文')}</strong>
                    <span class="contract-field-level">${escapeHtml(riskLevelText(level))} · ${escapeHtml(riskCategoryText(risk.category || risk.riskType))}</span></div>
                <p class="contract-field-source"><strong>风险原因：</strong>${escapeHtml(risk.reason || '风险报告未返回详细原因')}</p>
                <p class="contract-field-suggestion"><strong>建议修改：</strong>${escapeHtml(risk.suggestion || '请法务复核该风险项')}</p>
            </div>`;
        }).join('')}
    </section>`;
}

function renderFieldPanel() {
    const fieldHtml = draftState.analyzedFields.map(field => {
        const suggestion = field.suggestedValue
            ? `<p class="contract-field-suggestion"><strong>可采用 AI 建议</strong>：${escapeHtml(field.suggestedValue)}
                <br><button type="button" class="secondary" data-use-suggestion="${escapeHtml(field.fieldKey)}">采用建议</button></p>`
            : '<p class="contract-field-suggestion"><strong>需手动填写或确认</strong></p>';
        const input = field.fieldType === 'textarea'
            ? `<textarea data-field-input="${escapeHtml(field.fieldKey)}"></textarea>`
            : `<input type="${['number', 'date'].includes(field.fieldType) ? field.fieldType : 'text'}"
                data-field-input="${escapeHtml(field.fieldKey)}">`;
        return `<div class="contract-field-card">
            <div class="contract-field-title"><strong>${escapeHtml(field.fieldName)}</strong>
                <span class="contract-field-level">${FIELD_LEVEL_TEXT[field.requiredLevel] || field.requiredLevel} · ${field.status === 'filled' ? '已识别值' : '待填写'}</span></div>
            ${input}${suggestion}
            <p class="contract-field-source">来源：${renderPlaceholderPreview(field.sourceText)}</p>
        </div>`;
    }).join('') || '<p class="hint">未识别到待填写字段。</p>';
    $('#contractFieldList').innerHTML = `<section class="contract-field-section"><h4>待填写字段</h4>${fieldHtml}</section>${renderContractRisks()}`;
}

async function analyzeContractFields() {
    if (!hasEditorContent()) return toast('请先将模板、OCR 或草稿内容载入编辑器');
    const button = $('#analyzeFieldsBtn');
    button.disabled = true;
    const originalText = button.textContent;
    button.textContent = '识别中...';
    try {
        const [result, latestRiskReport] = await Promise.all([
            api('/api/contracts/field-analysis', {
                method: 'POST',
                body: JSON.stringify({
                    contractId: draftState.contractId,
                    versionId: null,
                    html: draftContent(),
                    plainText: editorText(),
                    contractType: $('#contractType').value
                })
            }),
            loadLatestRiskReport()
        ]);
        draftState.latestRiskReport = latestRiskReport;
        draftState.analyzedRisks = Array.isArray(latestRiskReport?.risks) ? latestRiskReport.risks : [];
        renderContractFields(result);
        const riskNotice = draftState.analyzedRisks.length ? `，风险修改点 ${draftState.analyzedRisks.length} 项` : '';
        toast((result.notice || '字段识别完成') + riskNotice);
    } finally {
        button.disabled = false;
        button.textContent = originalText;
    }
}

async function loadLatestRiskReport() {
    if (!draftState.contractId) return null;
    const reports = await api(`/api/risk-reports?contractId=${encodeURIComponent(draftState.contractId)}`).catch(() => []);
    const latest = Array.isArray(reports) ? reports[0] : null;
    const reportId = latest?.reportId || latest?.id;
    if (!reportId) return null;
    return api(`/api/risk-reports/${reportId}`).catch(() => null);
}

function replaceUniqueText(html, placeholder, value) {
    if (!placeholder || !value) return {html, replaced: false, reason: '字段值为空或缺少占位符'};
    const tokenMatch = placeholder.match(/_{2,}|＿{2,}|-{3,}|【\s*】|\[\s*]|\(\s*(?:填写)?\s*\)|（\s*(?:填写)?\s*）/);
    const dateMatch = placeholder.match(dateSequenceRegex());
    const container = document.createElement('div');
    container.innerHTML = html;
    const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT);
    const matches = [];
    const matchedPlaceholderElements = new Set();
    let node;
    while ((node = walker.nextNode())) {
        const placeholderElement = node.parentElement?.closest('.contract-fill-underline[data-placeholder-empty="true"]');
        if (placeholderElement && (placeholderElement.dataset.placeholderText || placeholderElement.textContent) === placeholder) {
            if (!matchedPlaceholderElements.has(placeholderElement)) {
                matchedPlaceholderElements.add(placeholderElement);
                matches.push({element: placeholderElement, token: placeholder});
            }
            continue;
        }
        let offset = node.nodeValue.indexOf(placeholder);
        while (offset >= 0) {
            matches.push({node, offset});
            offset = node.nodeValue.indexOf(placeholder, offset + placeholder.length);
        }
    }
    if (matches.length !== 1) {
        return {html, replaced: false, reason: matches.length ? `占位文本重复 ${matches.length} 次` : '未找到占位文本'};
    }
    const match = matches[0];
    if (match.element) {
        match.element.replaceWith(createUnderlineFill(value, match.token));
        return {html: container.innerHTML, replaced: true};
    }
    if (dateMatch) {
        const range = document.createRange();
        range.setStart(match.node, match.offset + dateMatch.index);
        range.setEnd(match.node, match.offset + dateMatch.index + dateMatch[0].length);
        range.deleteContents();
        range.insertNode(createUnderlineFill(formatDateFillValue(value, dateMatch[0]), dateMatch[0]));
    } else if (tokenMatch) {
        const range = document.createRange();
        range.setStart(match.node, match.offset + tokenMatch.index);
        range.setEnd(match.node, match.offset + tokenMatch.index + tokenMatch[0].length);
        range.deleteContents();
        range.insertNode(createUnderlineFill(value, tokenMatch[0]));
    } else {
        match.node.nodeValue = match.node.nodeValue.slice(0, match.offset)
            + value + match.node.nodeValue.slice(match.offset + placeholder.length);
    }
    return {html: container.innerHTML, replaced: true};
}

function createUnderlineFill(value, token) {
    const span = document.createElement('span');
    span.className = 'contract-fill-underline';
    span.textContent = value;
    span.style.display = 'inline-block';
    span.style.minWidth = `${placeholderWidthEm(token)}em`;
    span.style.padding = '0 0.35em';
    span.style.textAlign = 'center';
    span.style.borderBottom = '1px solid currentColor';
    span.style.lineHeight = '1.2';
    return span;
}

function placeholderWidthEm(token) {
    const length = (token || '').length;
    return Math.min(14, Math.max(4, Math.ceil(length * 0.42)));
}

function normalizeFieldText(value) {
    return (value || '').replace(/\s+/g, '').replace(/[：:]/g, ':').trim();
}

function underlineTokenRegex() {
    return /_{2,}|＿{2,}|-{3,}|【\s*】|\[\s*]|\(\s*(?:填写)?\s*\)|（\s*(?:填写)?\s*）/;
}

function underlineTokenGlobalRegex() {
    return /_{2,}|＿{2,}|-{3,}|【\s*】|\[\s*]|\(\s*(?:填写)?\s*\)|（\s*(?:填写)?\s*）/g;
}

function dateSequenceRegex() {
    return /(?:_{2,}|＿{2,}|-{3,}|【\s*】|\[\s*]|\(\s*(?:填写)?\s*\)|（\s*(?:填写)?\s*）)年(?:_{2,}|＿{2,}|-{3,}|【\s*】|\[\s*]|\(\s*(?:填写)?\s*\)|（\s*(?:填写)?\s*）)月(?:_{2,}|＿{2,}|-{3,}|【\s*】|\[\s*]|\(\s*(?:填写)?\s*\)|（\s*(?:填写)?\s*）)日/;
}

function dateSequenceGlobalRegex() {
    return /(?:_{2,}|＿{2,}|-{3,}|【\s*】|\[\s*]|\(\s*(?:填写)?\s*\)|（\s*(?:填写)?\s*）)年(?:_{2,}|＿{2,}|-{3,}|【\s*】|\[\s*]|\(\s*(?:填写)?\s*\)|（\s*(?:填写)?\s*）)月(?:_{2,}|＿{2,}|-{3,}|【\s*】|\[\s*]|\(\s*(?:填写)?\s*\)|（\s*(?:填写)?\s*）)日/g;
}

function formatDateFillValue(value, source) {
    const trimmed = (value || '').trim();
    const match = trimmed.match(/^(\d{4})-(\d{1,2})-(\d{1,2})$/);
    if (match && /年.*月.*日/.test(source || '')) {
        return `${match[1]}年${match[2].padStart(2, '0')}月${match[3].padStart(2, '0')}日`;
    }
    return trimmed;
}

function countUnderlineTokensInText(text) {
    return [...(text || '').matchAll(underlineTokenGlobalRegex())].length;
}

function replaceFirstUnderlineToken(element, value) {
    return replaceNthUnderlineToken(element, value, 1);
}

function replaceDateSequenceStartingAtToken(element, value, ordinal) {
    const targets = collectUnderlineTargets(element);
    const first = targets[ordinal - 1];
    const third = targets[ordinal + 1];
    if (!first || !third) return false;
    const range = document.createRange();
    setRangeStartForUnderlineTarget(range, first);
    setRangeEndForUnderlineTarget(range, third);
    const source = range.toString();
    if (!dateSequenceRegex().test(source)) return false;
    range.deleteContents();
    range.insertNode(createUnderlineFill(formatDateFillValue(value, source), source));
    return true;
}

function replaceNthUnderlineToken(element, value, ordinal) {
    const target = collectUnderlineTargets(element)[ordinal - 1];
    if (!target) return false;
    const range = document.createRange();
    setRangeStartForUnderlineTarget(range, target);
    setRangeEndForUnderlineTarget(range, target);
    range.deleteContents();
    range.insertNode(createUnderlineFill(value, target.token));
    return true;
}

function countUnderlineTokens(element) {
    return collectUnderlineTargets(element).length;
}

function collectUnderlineTargets(root) {
    const targets = [];
    const visit = node => {
        if (isEmptyPlaceholderUnderline(node)) {
            targets.push({type: 'element', element: node, token: node.dataset.placeholderText || node.textContent || '____'});
            return;
        }
        if (node.nodeType === Node.TEXT_NODE) {
            const text = node.nodeValue || '';
            let match;
            const pattern = underlineTokenGlobalRegex();
            while ((match = pattern.exec(text))) {
                targets.push({type: 'text', node, start: match.index, end: match.index + match[0].length, token: match[0]});
            }
            return;
        }
        node.childNodes?.forEach(visit);
    };
    visit(root);
    return targets;
}

function setRangeStartForUnderlineTarget(range, target) {
    if (target.type === 'element') range.setStartBefore(target.element);
    else range.setStart(target.node, target.start);
}

function setRangeEndForUnderlineTarget(range, target) {
    if (target.type === 'element') range.setEndAfter(target.element);
    else range.setEnd(target.node, target.end);
}

function hasChildFillBlock(element) {
    return [...element.children].some(child => ['TR', 'P', 'LI', 'DIV'].includes(child.tagName));
}

function fillBlocks(container) {
    return [...container.querySelectorAll('tr, p, li, div')]
        .filter(element => !hasChildFillBlock(element))
        .filter(element => normalizeFieldText(element.textContent));
}

function fillByLocator(html, field, value, adjustedPlaceholderIndex) {
    if (field.blockIndex == null || field.placeholderIndex == null) {
        return {html, replaced: false, reason: '缺少结构化定位'};
    }
    const placeholderIndex = Number(adjustedPlaceholderIndex ?? field.placeholderIndex);
    if (placeholderIndex < 1) return {html, replaced: false, reason: '目标占位符已被前序字段替换'};
    const container = document.createElement('div');
    container.innerHTML = html;
    const blocks = fillBlocks(container);
    const target = blocks[Number(field.blockIndex)];
    if (!target) return {html, replaced: false, reason: `目标段落 #${Number(field.blockIndex) + 1} 不存在`};
    const tokenCount = countUnderlineTokens(target);
    if (tokenCount < placeholderIndex) {
        return {
            html,
            replaced: false,
            reason: `目标段落仅剩 ${tokenCount} 个占位符，无法替换第 ${field.placeholderIndex} 个`
        };
    }
    const hasDateSequence = field.placeholderText && dateSequenceRegex().test(field.placeholderText);
    const replaced = hasDateSequence
        ? replaceDateSequenceStartingAtToken(target, value, placeholderIndex)
        : replaceNthUnderlineToken(target, value, placeholderIndex);
    if (!replaced) {
        return {html, replaced: false, reason: '目标占位符已被填写或不存在'};
    }
    return {html: container.innerHTML, replaced: true};
}

function fillBySourceText(html, field, value) {
    const sourceText = normalizeFieldText(field.sourceText);
    if (!sourceText) return {html, replaced: false};
    const container = document.createElement('div');
    container.innerHTML = html;
    const candidates = [...container.querySelectorAll('tr, p, li, div')]
        .filter(element => underlineTokenRegex().test(element.textContent || ''))
        .filter(element => {
            const text = normalizeFieldText(element.textContent);
            return text && (sourceText.includes(text) || text.includes(sourceText));
        })
        .filter(element => ![...element.children].some(child => {
            const childText = normalizeFieldText(child.textContent);
            return childText && underlineTokenRegex().test(child.textContent || '')
                && (sourceText.includes(childText) || childText.includes(sourceText));
        }));
    if (candidates.length !== 1) {
        return {html, replaced: false, reason: candidates.length ? '来源片段匹配到多个候选段落' : '来源片段未匹配到段落'};
    }
    if (!replaceFirstUnderlineToken(candidates[0], value)) return {html, replaced: false, reason: '候选段落没有可替换占位符'};
    return {html: container.innerHTML, replaced: true};
}

function fillByDocumentStructure(html, field, value) {
    const container = document.createElement('div');
    container.innerHTML = html;
    const fieldName = normalizeFieldText(field.fieldName);
    const sourceText = normalizeFieldText(field.sourceText);
    const candidates = [...container.querySelectorAll('tr, p, li, div')]
        .filter(element => {
            const text = normalizeFieldText(element.textContent);
            if (!text || !fieldName || !text.includes(fieldName)) return false;
            return !sourceText || sourceText.includes(text) || text.includes(sourceText);
        })
        .filter(element => ![...element.children].some(child => {
            const childText = normalizeFieldText(child.textContent);
            return childText.includes(fieldName)
                && (!sourceText || sourceText.includes(childText) || childText.includes(sourceText));
        }));
    if (candidates.length !== 1) {
        return {html, replaced: false, reason: candidates.length ? '字段名匹配到多个候选段落' : '字段名未匹配到段落'};
    }

    const target = candidates[0];
    if (target.tagName === 'TR') {
        const cells = [...target.querySelectorAll(':scope > th, :scope > td')];
        const labelIndex = cells.findIndex(cell => normalizeFieldText(cell.textContent).includes(fieldName));
        if (labelIndex >= 0) {
            const emptyCells = cells.slice(labelIndex + 1)
                .filter(cell => !normalizeFieldText(cell.textContent));
            if (emptyCells.length === 1) {
                emptyCells[0].textContent = value;
                return {html: container.innerHTML, replaced: true};
            }
        }
    }

    const text = target.textContent || '';
    const token = text.match(underlineTokenRegex());
    if (token) {
        replaceFirstUnderlineToken(target, value);
        return {html: container.innerHTML, replaced: true};
    }
    const labelPattern = new RegExp(`(${field.fieldName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*[：:]\\s*)$`);
    if (labelPattern.test(text.trim())) {
        target.textContent = text.replace(labelPattern, `$1${value}`);
        return {html: container.innerHTML, replaced: true};
    }
    return {html, replaced: false, reason: '未找到可安全替换的位置'};
}

function fillContractFields() {
    let html = draftContent();
    let replacedCount = 0;
    const failedFields = [];
    const locatorReplacementCounts = new Map();
    draftState.analyzedFields.forEach(field => {
        const value = fieldInputValue(field.fieldKey);
        if (!value) {
            failedFields.push(`${field.fieldName}：字段值为空`);
            return;
        }
        const locatorKey = field.blockIndex == null ? null : String(field.blockIndex);
        const adjustedIndex = field.placeholderIndex == null || locatorKey == null
            ? field.placeholderIndex
            : Number(field.placeholderIndex) - (locatorReplacementCounts.get(locatorKey) || 0);
        let result = fillByLocator(html, field, value, adjustedIndex);
        if (!result.replaced) result = replaceUniqueText(html, field.placeholderText, value);
        if (!result.replaced) result = fillBySourceText(html, field, value);
        if (!result.replaced) result = fillByDocumentStructure(html, field, value);
        html = result.html;
        if (result.replaced) {
            replacedCount++;
            if (locatorKey != null) locatorReplacementCounts.set(locatorKey, (locatorReplacementCounts.get(locatorKey) || 0) + 1);
        } else {
            failedFields.push(`${field.fieldName}：${result.reason || '未找到唯一可靠占位符'}`);
        }
    });
    if (replacedCount) {
        settingEditorContent = true;
        editor.setHtml(html);
        settingEditorContent = false;
        draftState.dirty = true;
        updateCharCount();
    }
    if (failedFields.length) {
        toast(`已回填 ${replacedCount} 项；未回填：${failedFields.slice(0, 5).join('；')}${failedFields.length > 5 ? ' 等' : ''}`);
    } else {
        toast(replacedCount ? `已回填 ${replacedCount} 项字段` : '请先填写字段值');
    }
}

function currentMissingRequiredFields() {
    return draftState.analyzedFields
        .filter(field => field.requiredLevel === 'required' && field.status === 'unfilled')
        .filter(field => !fieldInputValue(field.fieldKey))
        .map(field => field.fieldName);
}
function showSavedVersion(version) {
    versionBox.hidden = false;
    versionBox.innerHTML = `最新草稿：<strong>${escapeHtml(version.versionNo)}</strong> · <a href="${version.downloadUrl}" data-download-url="${version.downloadUrl}" data-file-name="contract-${version.contractId}-${version.versionNo}.docx">下载 Word 草稿</a>`;
}
function readContractMetaPayload(existing = {}) {
    const title = $('#contractTitle').value.trim();
    const counterparty = $('#contractCounterparty').value.trim();
    const amount = Number($('#contractAmount').value);
    if (!title || !counterparty || !Number.isFinite(amount)) {
        throw new Error('请先填写合同名称、相对方和金额');
    }
    return {
        title,
        counterparty,
        amount,
        type: $('#contractType').value,
        deptId: existing.deptId || state.deptId || 1,
        ownerId: existing.ownerId || state.userId || 1,
        templateId: existing.templateId || draftState.templateId || null,
        signDate: existing.signDate || null,
        dueDate: existing.dueDate || new Date(Date.now() + 90 * 86400000).toISOString().slice(0, 10)
    };
}
async function restoreLatestDraft() {
    if (!draftState.contractId) return;
    const [contract, version] = await Promise.all([
        api(`/api/contracts/${draftState.contractId}`),
        api(`/api/contracts/${draftState.contractId}/versions/latest`)
    ]);
    if (contract) {
        draftState.currentContract = contract;
        $('#contractTitle').value = contract.title || '';
        $('#contractCounterparty').value = contract.counterparty || '';
        $('#contractType').value = contract.type || 'OTHER';
        $('#contractAmount').value = contract.amount ?? '';
    }
    if (!version?.content) return;
    setDraftContent(version.content);
    showSavedVersion(version);
    updateStatus(`已加载最新草稿 ${version.versionNo}`);
}
async function loadTemplateForEditing() {
    if (!draftState.templateId) return;
    updateStatus('正在识别并载入合同模板...');
    setStep(2);
    try {
        const [template, parsed] = await Promise.all([
            api(`/api/templates/${draftState.templateId}`),
            api(`/api/templates/${draftState.templateId}/parse?preserveFormat=true`)
        ]);
        $('#contractTitle').value = template.templateName || '';
        $('#contractType').value = ['PURCHASE', 'SALES', 'TECH', 'LABOR', 'CONFIDENTIAL', 'LOGISTICS', 'ENTERPRISE_SERVICE', 'INTELLECTUAL_PROPERTY', 'OTHER']
            .includes(template.templateType) ? template.templateType : 'OTHER';
        showParsedResult({editorHtml: parsed.html || ''});
        updateStatus(`模板"${template.templateName || template.fileName || ''}"已识别并载入，可继续编制。`);
    } catch (error) {
        setFailedStep(2);
        updateStatus('模板识别失败，请返回模板页重试。');
        toast(error.message);
    }
}
async function saveDraft() {
    const saveButton = $('#saveDraftBtn');
    if (saveButton.disabled) return;
    const content = draftContent();
    if (!hasEditorContent()) return toast('请先上传或编辑合同草稿');
    const missingRequired = currentMissingRequiredFields();
    if (missingRequired.length) {
        toast(`仍有基础必填字段未补齐：${missingRequired.join('、')}；本次仍可保存草稿`);
    }
    saveButton.disabled = true;
    const originalText = saveButton.textContent;
    saveButton.textContent = '保存中...';
    try {
    if (draftState.contractId && !draftState.currentContract) {
        draftState.currentContract = await api(`/api/contracts/${draftState.contractId}`);
    }
    const metaPayload = readContractMetaPayload(draftState.currentContract || {});
    if (!draftState.contractId) {
        const contract = await api('/api/contracts', {
            method: 'POST',
            body: JSON.stringify(metaPayload)
        });
        draftState.contractId = contract.contractId;
        draftState.currentContract = contract;
        history.replaceState(null, '', `/html/edit.html?contractId=${contract.contractId}`);
    } else {
        draftState.currentContract = await api(`/api/contracts/${draftState.contractId}`, {
            method: 'PUT',
            body: JSON.stringify(metaPayload)
        });
    }
    const version = await api(`/api/contracts/${draftState.contractId}/versions`, {
        method: 'POST', body: JSON.stringify({contractId: draftState.contractId, content, saveType: 'SAVE'})
    });
    draftState.dirty = false;
    showSavedVersion(version);
    updateStatus(`草稿已生成 Word 留档 · ${new Date().toLocaleString('zh-CN')}`);
    toast(`草稿 ${version.versionNo} 已保存`);
    } finally {
        saveButton.disabled = false;
        saveButton.textContent = originalText;
    }
}

/**
 * 更新 OCR 面板状态。
 * @param {ContractImportResultVO} result OCR 导入结果 VO
 */
function updateOcrPanel(result) {
    draftState.attachmentId = result.attachmentId;
    $('#ocrPanel').hidden = false;
    $('#ocrStatusText').textContent = `${result.ocrStatus} · ${result.fileName || ''} · ${result.pageCount || '?'} 页`;
    $('#retryOcrBtn').hidden = result.ocrStatus !== 'FAILED';
    $('#reuploadBtn').hidden = result.ocrStatus !== 'FAILED';
    if (result.ocrStatus === 'SUCCESS') {
        const states = ['OCR 已完成'];
        if (result.ocrRawJsonExist) states.push('已保存 OCR 原始结果');
        if (result.ocrBlocksJsonExist) states.push('已生成结构化解析结果');
        if (result.editorHtml) states.push('已生成可编辑合同富文本');
        $('#ocrHint').textContent = states.join(' · ');
    } else {
        $('#ocrHint').textContent = result.ocrError || '正在解析合同内容...';
    }
    setStep(result.ocrStatus === 'SUCCESS' ? 3 : 2);
}

/**
 * 将 OCR 导入结果载入编辑器。
 * editorHtml 是 OCR 导入、在线编辑、版本保存和 DOCX 导出的唯一内容来源。
 * @param {ContractImportResultVO} result OCR 导入结果 VO
 */
function showParsedResult(result) {
    const content = result.editorHtml;
    if (!content) {
        updateStatus('OCR 已完成，但编辑器排版生成失败，请点击"重新解析"重试。');
        setStep(2);
        return;
    }
    setDraftContent(content);
    applyExtractedMeta(result);
    draftState.dirty = true;
    updateStatus('解析完成，可以在线编辑并保存 Word 草稿。');
    setStep(3);
    const editorContainer = $('#draftEditor');
    editorContainer.scrollTop = 0;
    editorContainer.classList.remove('import-ready');
    void editorContainer.offsetWidth;
    editorContainer.classList.add('import-ready');
    setTimeout(() => editorContainer.classList.remove('import-ready'), 1200);
}

/**
 * 上传文件 → 创建附件 → 触发 OCR → 载入编辑器。
 * 流程：POST /api/attachments/upload → POST /api/attachments/{id}/ocr
 */
async function uploadTemplate(file) {
    if (!file) return;
    if (!/\.(pdf|doc|docx|jpg|jpeg|png|webp)$/i.test(file.name)) {
        return toast('仅支持 PDF、DOC、DOCX、JPG、JPEG、PNG 或 WEBP 格式');
    }
    if (hasEditorContent() && !await confirmDialog('当前内容将被替换，是否继续？', {title: '替换确认', confirmText: '替换', type: 'warn'})) return;
    draftState.lastFile = file;
    $('#uploadFileName').textContent = file.name;
    $('#pickFileBtn').disabled = true;
    $('#ocrPanel').hidden = false;
    $('#ocrStatusText').textContent = `正在上传 · ${file.name}`;
    $('#ocrHint').textContent = '正在上传文件，请稍候。';
    $('#retryOcrBtn').hidden = true;
    $('#reuploadBtn').hidden = true;
    updateStatus('正在上传合同模板...');
    setStep(1);
    try {
        // Step 1: 上传附件（仅存储，不触发 OCR）
        const formData = new FormData();
        formData.append('file', file);
        if (draftState.contractId) formData.append('contractId', String(draftState.contractId));
        const attachment = await api('/api/attachments/upload', {method: 'POST', body: formData});
        draftState.attachmentId = attachment.attachmentId;
        setStep(2);
        $('#ocrStatusText').textContent = `正在解析格式 · ${file.name}`;
        $('#ocrHint').textContent = '正在进行 OCR、结构化解析和版式判断。';
        // Step 2: 触发 OCR
        const result = await api(`/api/attachments/${attachment.attachmentId}/ocr?preserveFormat=true`, {method: 'POST'});
        updateOcrPanel(result);
        if (result.ocrStatus === 'SUCCESS') showParsedResult(result);
    } catch (error) {
        setFailedStep(2);
        $('#ocrPanel').hidden = false;
        $('#ocrStatusText').textContent = `解析失败 · ${file.name}`;
        $('#ocrHint').textContent = error.message || '文件损坏、解析超时或格式不受支持';
        $('#retryOcrBtn').hidden = !draftState.attachmentId;
        $('#reuploadBtn').hidden = false;
        updateStatus('模板导入失败，请检查错误原因后重试。');
        toast(error.message || '模板导入失败');
    } finally {
        $('#pickFileBtn').disabled = false;
        $('#fileInput').value = '';
    }
}
async function retryOcr() {
    setStep(2);
    const result = await api(`/api/attachments/${draftState.attachmentId}/ocr?preserveFormat=true`, {method: 'POST'});
    updateOcrPanel(result);
    if (result.ocrStatus === 'SUCCESS') showParsedResult(result);
}
const zone = $('#uploadZone');
const input = $('#fileInput');
$('#pickFileBtn').addEventListener('click', event => { event.preventDefault(); event.stopPropagation(); input.click(); });
input.addEventListener('change', event => uploadTemplate(event.target.files[0]));
zone.addEventListener('dragover', event => { event.preventDefault(); zone.classList.add('dragover'); });
zone.addEventListener('dragleave', () => zone.classList.remove('dragover'));
zone.addEventListener('drop', event => { event.preventDefault(); zone.classList.remove('dragover'); uploadTemplate(event.dataTransfer?.files?.[0]); });

// 模板选择器
async function loadTemplateOptions() {
    try {
        const templates = await api('/api/templates');
        const select = $('#templateSelect');
        templates.forEach(t => {
            const opt = document.createElement('option');
            opt.value = t.templateId;
            opt.textContent = t.templateName || t.fileName || `模板 #${t.templateId}`;
            select.appendChild(opt);
        });
    } catch (e) { /* 静默失败，不影响主流程 */ }
}
$('#templateSelect').addEventListener('change', event => {
    $('#useTemplateBtn').disabled = !event.target.value;
});
$('#useTemplateBtn').addEventListener('click', async () => {
    const templateId = Number($('#templateSelect').value);
    if (!templateId) return;
    if (hasEditorContent() && !await confirmDialog('当前内容将被替换，是否继续？', {title: '替换确认', confirmText: '替换', type: 'warn'})) return;
    $('#useTemplateBtn').disabled = true;
    updateStatus('正在载入模板...');
    setStep(2);
    try {
        const [template, parsed] = await Promise.all([
            api(`/api/templates/${templateId}`),
            api(`/api/templates/${templateId}/parse?preserveFormat=true`)
        ]);
        $('#contractTitle').value = template.templateName || '';
        $('#contractType').value = ['PURCHASE', 'SALES', 'TECH', 'LABOR', 'CONFIDENTIAL', 'LOGISTICS', 'ENTERPRISE_SERVICE', 'INTELLECTUAL_PROPERTY', 'OTHER']
            .includes(template.templateType) ? template.templateType : 'OTHER';
        showParsedResult({editorHtml: parsed.html || ''});
        updateStatus(`模板"${template.templateName || template.fileName || ''}"已载入，可继续编制。`);
    } catch (error) {
        setFailedStep(2);
        updateStatus('模板载入失败，请重试。');
        toast(error.message);
    } finally {
        $('#useTemplateBtn').disabled = false;
    }
});
loadTemplateOptions();
$('#retryOcrBtn').addEventListener('click', () => retryOcr().catch(error => toast(error.message)));
$('#reuploadBtn').addEventListener('click', () => input.click());
$('#saveDraftBtn').addEventListener('click', () => saveDraft().catch(error => toast(error.message)));
$('#analyzeFieldsBtn').addEventListener('click', () => analyzeContractFields().catch(error => toast(error.message)));
$('#closeFieldPanelBtn').addEventListener('click', () => {
    $('#fieldPanel').hidden = true;
    $('.editor-workspace')?.classList.remove('has-field-panel');
});
$('#fillContractFieldsBtn').addEventListener('click', fillContractFields);
$('#contractFieldList').addEventListener('click', event => {
    const button = event.target.closest('[data-use-suggestion]');
    if (!button) return;
    const field = draftState.analyzedFields.find(item => item.fieldKey === button.dataset.useSuggestion);
    const input = document.querySelector(`[data-field-input="${button.dataset.useSuggestion}"]`);
    if (field && input) input.value = field.suggestedValue || '';
});
versionBox.addEventListener('click', event => {
    const link = event.target.closest('[data-download-url]');
    if (!link) return;
    event.preventDefault();
    downloadFile(link.dataset.downloadUrl, link.dataset.fileName).catch(error => toast(error.message));
});
$('#downloadDraftBtn').addEventListener('click', async () => {
    if (!hasEditorContent()) return toast('请先上传或编辑合同内容');
    try {
        const link = $('#savedVersion a');
        if (link && !draftState.dirty) {
            await downloadFile(link.dataset.downloadUrl, link.dataset.fileName);
            return;
        }
        await saveDraft();
        const newLink = $('#savedVersion a');
        if (newLink) await downloadFile(newLink.dataset.downloadUrl, newLink.dataset.fileName);
        else toast('保存成功但下载链接未生成，请重试');
    } catch (error) {
        toast(error.message);
    }
});
function initEditor() {
    if (!window.wangEditor) throw new Error('wangEditor 加载失败');
    const {createEditor, createToolbar} = window.wangEditor;
    editor = createEditor({
        selector: '#draftEditor',
        html: '<p><br></p>',
        config: {
            scroll: false,
            onChange() {
                if (!settingEditorContent) draftState.dirty = true;
                updateCharCount();
            }
        },
        mode: 'default'
    });
    createToolbar({
        editor,
        selector: '#editorToolbar',
        config: {
            excludeKeys: [
                'emotion',
                'insertLink',
                'group-image',
                'insertImage',
                'uploadImage',
                'group-video',
                'insertVideo',
                'uploadVideo'
            ]
        },
        mode: 'default'
    });
    $('#draftEditor').addEventListener('input', () => {
        if (!settingEditorContent) draftState.dirty = true;
        updateCharCount();
    });
    updateCharCount();
}

window.addEventListener('beforeunload', () => {
    if (editor) editor.destroy();
});

initEditor();
initMode();
if (draftState.contractId) restoreLatestDraft().catch(error => toast(error.message));
else if (draftState.templateId) loadTemplateForEditing();


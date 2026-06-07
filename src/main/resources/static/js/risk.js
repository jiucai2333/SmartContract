if (!initAppShell('risk', 'AI 风险审查', 'Risk Audit')) throw new Error('auth required');

const searchParams = new URLSearchParams(location.search);
const pageState = {
    contractId: Number(searchParams.get('contractId')) || null,
    versionId: Number(searchParams.get('versionId')) || null,
    autoReview: searchParams.get('auto') === '1'
};
const reviewBtn = $('#reviewBtn');
const clearBtn = $('#clearBtn');
const contractTextInput = $('#contractText');
const contractBody = $('#contractBody');
const riskListEl = $('#riskList');

reviewBtn.addEventListener('click', async () => {
    const contractText = contractTextInput.value.trim();
    if (!contractText) { toast('请输入合同正文'); return; }
    reviewBtn.disabled = true;
    reviewBtn.textContent = 'AI 审查中...';
    riskListEl.innerHTML = '<div class="list-item"><span>正在调用 Qwen 进行风险审查，请稍候...</span></div>';
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
        const risks = await api('/api/ai/risk-review', {method: 'POST', body: JSON.stringify(body)});
        renderContractBody(contractText);
        renderRisks(Array.isArray(risks) ? risks : []);
    } catch (e) {
        toast(e.message || '风险审查失败');
        riskListEl.innerHTML = `<div class="list-item"><span>审查失败：${escapeHtml(e.message)}</span></div>`;
    } finally {
        reviewBtn.disabled = false;
        reviewBtn.textContent = 'AI 风险审查';
    }
});

clearBtn.addEventListener('click', () => {
    pageState.contractId = null;
    pageState.versionId = null;
    pageState.autoReview = false;
    contractTextInput.value = '';
    $('#contractType').value = '';
    $('#partyA').value = '';
    $('#partyB').value = '';
    $('#businessScope').value = '';
    $('#specialTerms').value = '';
    contractBody.innerHTML = '<p class="hint">提交合同文本后，此处将展示合同条款，点击右侧风险卡片可定位到对应条款。</p>';
    riskListEl.innerHTML = '<div class="list-item"><span>请提交合同文本，AI 将实时进行风险审查。</span></div>';
});

function renderContractBody(text) {
    const paragraphs = htmlToPlainText(text).split(/\n{2,}/).filter(p => p.trim());
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
        const levelLabel = RISK_TEXT[level] || level;
        return `<button class="risk-card ${escapeHtml(level)}" data-clause="${escapeHtml(risk.clause || '')}">
            <strong><span class="tag ${escapeHtml(level)}">${escapeHtml(levelLabel)}</span> ${escapeHtml(risk.clause || '未指明条款')}</strong>
            <span>${escapeHtml(risk.reason || '')}</span>
            <small>建议：${escapeHtml(risk.suggestion || '')}</small>
        </button>`;
    }).join('');
}

riskListEl.addEventListener('click', event => {
    const card = event.target.closest('[data-clause]');
    if (!card) return;
    const clause = card.dataset.clause;
    $$('[data-clause]').forEach(el => el.classList.remove('active-clause'));
    const target = $(`#contractBody [data-clause="${CSS.escape(clause)}"]`);
    if (target) { target.classList.add('active-clause'); target.scrollIntoView({behavior: 'smooth', block: 'center'}); }
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

hydrateFromVersion();

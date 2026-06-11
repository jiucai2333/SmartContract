if (!initAppShell('ledger', '合同台账', 'Contract Ledger')) throw new Error('auth required');

async function loadAttachmentCount(contractId) {
    try { const data = await api(`/api/contracts/${contractId}/attachment-count`); return data.count ?? data; } catch { return 0; }
}
async function getLatestVersionId(contractId) {
    try { const versions = await api(`/api/contracts/${contractId}/versions`); if (versions && versions.length > 0) return versions[0].versionId; } catch {}
    return null;
}

function renderActionButtons(row) {
    const buttons = [], canOp = canOperateSealArchive(), cid = row.contractId, title = escapeHtml(row.title);
    if (row.status === 'DRAFT' || row.status === 'APPROVING') {
        buttons.push(`<button class="secondary" data-detail="${cid}" data-title="${title}">查看</button>`);
        buttons.push(`<a class="secondary" href="/html/draft.html?contractId=${cid}">编辑</a>`);
        if (row.status === 'DRAFT') {
            const blocked = row.riskLevel === 'HIGH';
            buttons.push(`<button class="secondary" data-submit="${cid}" ${blocked?'disabled':''} title="${blocked?'存在未经复核的高风险问题，请先处理':'提交审批'}">提交审批</button>`);
        }
    } else if (row.status === 'APPROVED') {
        buttons.push(`<button class="secondary" data-detail="${cid}" data-title="${title}">查看</button>`);
        if (canOp) buttons.push(`<a class="primary-btn" href="/html/seal.html?contractId=${cid}&versionId=${row._latestVersionId||''}">签章登记</a>`);
    } else if (row.status === 'SIGNING') {
        buttons.push(`<button class="secondary" data-detail="${cid}" data-title="${title}">查看</button>`);
        if (canOp) buttons.push(`<a class="primary-btn" href="/html/archive.html?contractId=${cid}&versionId=${row._latestVersionId||''}">归档确认</a>`);
    } else if (row.status === 'ARCHIVED') {
        buttons.push(`<button class="secondary" data-detail="${cid}" data-title="${title}">查看归档</button>`);
        buttons.push(`<a class="link-btn" href="/html/fulfillment.html?contractId=${cid}">创建履约计划</a>`);
    } else {
        buttons.push(`<button class="secondary" data-detail="${cid}" data-title="${title}">查看</button>`);
    }
    return buttons.join(' ');
}

async function loadContracts() {
    const params = new URLSearchParams();
    if ($('#keyword').value) params.set('keyword', $('#keyword').value);
    if ($('#statusFilter').value) params.set('status', $('#statusFilter').value);
    if ($('#riskFilter').value) params.set('riskLevel', $('#riskFilter').value);
    const rows = await api(`/api/contracts?${params.toString()}`);
    const counts = await Promise.all(rows.map(r => loadAttachmentCount(r.contractId)));
    const versionIds = await Promise.all(rows.map(r => getLatestVersionId(r.contractId)));
    rows.forEach((r, i) => { r._latestVersionId = versionIds[i]; });
    $('#contractRows').innerHTML = rows.map((row, index) => {
        const attachCount = counts[index] || 0;
        const statusLabel = statusText[row.status] || row.status;
        const tagCls = statusTagClass[row.status] || 'tag-gray';
        return `<tr><td>${escapeHtml(row.contractNo)}</td><td><strong>${escapeHtml(row.title)}</strong></td><td>${escapeHtml(row.counterparty)}</td><td>¥${Number(row.amount||0).toLocaleString()}</td>`
            + `<td>${state.roleCode==='ADMIN'?`<span class="tag ${tagCls} status-editable" data-status-edit="${row.contractId}" data-current="${escapeHtml(row.status)}" title="点击修改状态">${escapeHtml(statusLabel)} ▾</span>`:`<span class="tag ${tagCls}">${escapeHtml(statusLabel)}</span>`}</td>`
            + `<td><span class="tag ${escapeHtml(row.riskLevel)}">${escapeHtml(riskText[row.riskLevel]||row.riskLevel||'未评估')}</span></td>`
            + `<td><button type="button" class="secondary" data-attachments="${row.contractId}" data-title="${escapeHtml(row.title)}">${attachCount} 个</button></td>`
            + `<td>${escapeHtml(row.dueDate||'-')}</td><td class="action-cell">${renderActionButtons(row)}</td></tr>`;
    }).join('');
}

function ocrStatusLabel(s) { return {PENDING:'待识别',PROCESSING:'识别中',SUCCESS:'成功',FAILED:'失败'}[s]||s; }

async function openAttachmentModal(contractId, title) {
    const modal=$('#attachmentModal'); $('#attachmentModalTitle').textContent = `${title} · 附件列表`;
    const body=$('#attachmentModalBody'); body.innerHTML='<p class="hint">加载中…</p>'; modal.hidden=false;
    const list=await api(`/api/contracts/${contractId}/attachments`);
    if(!list.length){body.innerHTML='<p class="hint">暂无附件。</p>';return;}
    body.innerHTML=list.map(item=>`<article class="attachment-item"><div class="attachment-meta"><strong>${escapeHtml(item.fileName)}</strong><span>${escapeHtml(item.fileType)} · ${(item.fileSize/1024).toFixed(1)} KB</span><span class="tag">${escapeHtml(ocrStatusLabel(item.ocrStatus))}</span></div><pre class="ocr-preview">${escapeHtml(item.ocrTextPreview||item.ocrError||'无预览')}</pre><div class="button-row"><a class="secondary" href="${item.downloadUrl}" data-download="${item.attachmentId}">下载原件</a></div></article>`).join('');
    body.querySelectorAll('[data-download]').forEach(link=>{link.addEventListener('click',event=>{event.preventDefault();downloadAttachment(link.dataset.download,link.closest('.attachment-item')?.querySelector('strong')?.textContent);});});
}

async function openDetailModal(contractId, title) {
    const modal=$('#detailModal');$('#detailModalTitle').textContent=`${title} · 合同详情`;const body=$('#detailModalBody');body.innerHTML='<p class="hint">加载中…</p>';modal.hidden=false;
    try {
        const [contract, sealRecords, archiveRecords, versions, attachments, approvals] = await Promise.all([
            (async()=>{const list=await api('/api/contracts');return list.find(c=>c.contractId===Number(contractId))||null;})(),
            api(`/api/contracts/${contractId}/seal-records`).catch(()=>[]),
            api(`/api/contracts/${contractId}/archive-records`).catch(()=>[]),
            api(`/api/contracts/${contractId}/versions`).catch(()=>[]),
            api(`/api/contracts/${contractId}/attachments`).catch(()=>[]),
            api('/api/approvals').catch(()=>[])
        ]);
        if(!contract){body.innerHTML='<p class="hint">合同不存在或无权限查看。</p>';return;}
        const tagCls=statusTagClass[contract.status]||'tag-gray';
        const lockedIds=new Set(); archiveRecords.forEach(r=>{if(r.isLocked&&r.versionId)lockedIds.add(r.versionId);});
        body.innerHTML=`
        <div class="detail-section"><h4>基本信息</h4><div class="detail-grid">
        <div class="detail-item"><label>合同编号</label><span>${escapeHtml(contract.contractNo)}</span></div>
        <div class="detail-item"><label>合同标题</label><span>${escapeHtml(contract.title)}</span></div>
        <div class="detail-item"><label>相对方</label><span>${escapeHtml(contract.counterparty)}</span></div>
        <div class="detail-item"><label>金额</label><span>¥${Number(contract.amount||0).toLocaleString()}</span></div>
        <div class="detail-item"><label>类型</label><span>${escapeHtml(contract.type)}</span></div>
        <div class="detail-item"><label>状态</label><span class="tag ${tagCls}">${escapeHtml(statusText[contract.status]||contract.status)}</span></div>
        <div class="detail-item"><label>风险等级</label><span class="tag ${escapeHtml(contract.riskLevel)}">${escapeHtml(riskText[contract.riskLevel]||contract.riskLevel||'未评估')}</span></div>
        <div class="detail-item"><label>到期日期</label><span>${escapeHtml(contract.dueDate||'-')}</span></div></div></div>
        <div class="detail-section"><h4>签章记录</h4>${sealRecords.length===0?'<p class="hint">暂无签章记录</p>':sealRecords.map(r=>`<div class="record-item"><span><strong>${escapeHtml(r.fileName||'签章文件')}</strong></span><span>状态：${escapeHtml(r.sealStatus||'-')}</span><span>时间：${escapeHtml(r.sealTime||'-')}</span>${r.remark?`<span class="hint">备注：${escapeHtml(r.remark)}</span>`:''}</div>`).join('')}</div>
        <div class="detail-section"><h4>归档信息</h4>${archiveRecords.length===0?'<p class="hint">暂无归档记录</p>':archiveRecords.map(r=>`<div class="record-item"><span><strong>归档编号：${escapeHtml(r.archiveNo)}</strong></span><span>归档时间：${escapeHtml(r.archiveTime||'-')}</span><span>状态：${r.isLocked?'<span class="tag tag-green">已锁定</span>':'<span class="tag tag-gray">未锁定</span>'}</span></div>`).join('')}</div>
        <div class="detail-section"><h4>签章文件</h4>${attachments.filter(a=>a.attachType==='SIGNED_FILE').length===0?'<p class="hint">暂无签章文件</p>':attachments.filter(a=>a.attachType==='SIGNED_FILE').map(a=>`<div class="record-item"><strong>${escapeHtml(a.fileName)}</strong><span>${escapeHtml(a.fileType||'')} · ${(a.fileSize/1024).toFixed(1)} KB</span><button class="secondary" data-download="${a.attachmentId}">下载</button></div>`).join('')}</div>
        <div class="detail-section"><h4>历史版本 (${versions.length})</h4>${versions.length===0?'<p class="hint">暂无版本记录</p>':versions.map(v=>`<div class="record-item"><span><strong>${escapeHtml(v.versionNo)}</strong>${lockedIds.has(v.versionId)?' <span class="tag tag-green">已锁定</span>':''}</span><span>创建时间：${escapeHtml(v.createdAt||'-')}</span><span>创建人：${escapeHtml(v.createdBy||'-')}</span></div>`).join('')}<p class="hint" style="margin-top:8px"><a href="/html/draft.html?contractId=${contractId}">前往编辑页查看完整版本历史 →</a></p></div>
        <div class="detail-section"><h4>审批记录</h4>${approvals.filter(a=>a.contractId===Number(contractId)).length===0?'<p class="hint">暂无审批记录</p>':approvals.filter(a=>a.contractId===Number(contractId)).map(a=>`<div class="record-item"><span><strong>流程类型：${escapeHtml(a.flowType)}</strong></span><span>当前节点：${escapeHtml(a.currentNode||'-')}</span><span>状态：${escapeHtml(a.status)}</span><span>启动时间：${escapeHtml(a.startedAt||'-')}</span></div>`).join('')}</div>
        <div class="button-row" style="margin-top:16px"><a class="secondary" href="/html/draft.html?contractId=${contractId}">编辑合同</a><button type="button" class="secondary" id="closeDetailFromBody">关闭</button></div>`;
        body.querySelectorAll('[data-download]').forEach(link=>{link.addEventListener('click',event=>{event.preventDefault();downloadAttachment(link.dataset.download,link.closest('.record-item')?.querySelector('strong')?.textContent);});});
        $('#closeDetailFromBody').addEventListener('click',()=>{$('#detailModal').hidden=true;});
    }catch(error){body.innerHTML=`<p class="hint">加载失败：${escapeHtml(error.message)}</p>`;}
}

async function downloadAttachment(attachmentId, filename) {
    const response=await fetch(`/api/attachments/${attachmentId}/download`,{headers:state.accessToken?{Authorization:`Bearer ${state.accessToken}`}:{}});
    if(response.status===401){logout();return;} if(!response.ok)throw new Error('下载失败');
    const blob=await response.blob(),url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=filename||'attachment';a.click();URL.revokeObjectURL(url);
}

$('#createForm').addEventListener('submit',async event=>{
    event.preventDefault();const form=new FormData(event.currentTarget);const payload=Object.fromEntries(form.entries());
    payload.amount=Number(payload.amount);payload.deptId=1;payload.ownerId=1;
    payload.dueDate=new Date(Date.now()+90*86400000).toISOString().slice(0,10);
    await api('/api/contracts',{method:'POST',body:JSON.stringify(payload)});event.currentTarget.reset();
    toast('合同已创建');await loadContracts();
});

$('#contractRows').addEventListener('click',async event=>{
    const attachBtn=event.target.closest('[data-attachments]');
    if(attachBtn){try{await openAttachmentModal(attachBtn.dataset.attachments,attachBtn.dataset.title);}catch(e){toast(e.message);}return;}
    const submitBtn=event.target.closest('[data-submit]');
    if(submitBtn){try{await api(`/api/contracts/${submitBtn.dataset.submit}/submit`,{method:'POST'});toast('已提交审批');await loadContracts();}catch(e){toast(e.message);}return;}
    const detailBtn=event.target.closest('[data-detail]');
    if(detailBtn){try{await openDetailModal(detailBtn.dataset.detail,detailBtn.dataset.title);}catch(e){toast(e.message);}return;}
    const statusTag=event.target.closest('[data-status-edit]');
    if(statusTag){event.stopPropagation();showStatusDropdown(statusTag);return;}
});

$('#closeAttachmentModal').addEventListener('click',()=>{$('#attachmentModal').hidden=true;});
$('#attachmentModal').addEventListener('click',event=>{if(event.target.id==='attachmentModal')$('#attachmentModal').hidden=true;});
$('#closeDetailModal').addEventListener('click',()=>{$('#detailModal').hidden=true;});
$('#detailModal').addEventListener('click',event=>{if(event.target.id==='detailModal')$('#detailModal').hidden=true;});

function showStatusDropdown(tagEl) {
    const contractId=tagEl.dataset.statusEdit,currentStatus=tagEl.dataset.current;
    const allStatuses=['DRAFT','APPROVING','APPROVED','SIGNING','ARCHIVED','EXECUTING','COMPLETED','EXPIRED','TERMINATED'];
    $$('.status-dropdown').forEach(d=>d.remove());
    const dd=document.createElement('div');dd.className='status-dropdown';
    dd.innerHTML=allStatuses.map(s=>{const cur=s===currentStatus;return`<div class="status-option${cur?' current':''}" data-status="${s}">${cur?'✓ ':''}${statusText[s]||s}</div>`;}).join('');
    dd.querySelectorAll('.status-option').forEach(opt=>{opt.addEventListener('click',async e=>{e.stopPropagation();const target=opt.dataset.status;if(target===currentStatus){dd.remove();return;}try{opt.textContent='更新中…';await api(`/api/admin/contracts/${contractId}/fields`,{method:'PUT',body:JSON.stringify({status:target})});toast(`状态已更新为：${statusText[target]||target}`);dd.remove();await loadContracts();}catch(err){toast(err.message);dd.remove();}});});
    const rect=tagEl.getBoundingClientRect();dd.style.top=(rect.bottom+4)+'px';dd.style.left=rect.left+'px';document.body.appendChild(dd);
    setTimeout(()=>{document.addEventListener('click',function closeDD(e){if(!dd.contains(e.target)&&e.target!==tagEl){dd.remove();document.removeEventListener('click',closeDD);}});},0);
}

['keyword','statusFilter','riskFilter'].forEach(id=>$(`#${id}`).addEventListener('input',()=>loadContracts().catch(e=>toast(e.message))));
loadContracts().catch(e=>toast(e.message));

// ==================== 知识库搜索 ====================
$('#btnKnowledgeSearch').addEventListener('click', async () => {
    const keyword = $('#ksKeyword').value.trim();
    const contractType = $('#ksType').value.trim();
    if (!keyword && !contractType) { toast('请输入搜索关键词或合同类型'); return; }
    try {
        const results = await api('/api/knowledge/search', {
            method: 'POST',
            body: JSON.stringify({ keyword, contractType, partyName: keyword, minAmount: null, maxAmount: null, startDate: null, endDate: null })
        });
        const $r = $('#knowledgeResults');
        if (!results || results.length === 0) {
            $r.style.display = 'block';
            $r.innerHTML = '<div class="hint" style="padding:12px;background:#fff3cd;border-radius:8px;">未找到匹配的知识库条目。请确认合同已归档并完成 AI 元数据提取。</div>';
            return;
        }
        $r.style.display = 'block';
        $r.innerHTML = `<h4 style="margin:0 0 8px;">知识库结果 (${results.length} 条)</h4>` + results.map(k => `
            <div style="padding:12px;background:#f8f9ff;border-radius:8px;margin-bottom:8px;font-size:13px;">
                <strong>${escapeHtml(k.title || k.contractNo)}</strong>
                <span class="tag">${escapeHtml(k.contractType || '-')}</span>
                <span>¥${Number(k.amount||0).toLocaleString()}</span>
                <div style="color:#888;margin-top:4px;">甲方：${escapeHtml(k.partyA||'-')} | 乙方：${escapeHtml(k.partyB||'-')}</div>
                <div style="color:#888;">签约：${escapeHtml(k.signDate||'-')} | 到期：${escapeHtml(k.expiryDate||'-')}</div>
                ${k.keyClauses ? `<div style="color:#666;margin-top:4px;font-size:12px;">关键条款：${escapeHtml(k.keyClauses).substring(0,200)}</div>` : ''}
                ${k.keywords ? `<div style="color:#4361ee;font-size:12px;">🏷 ${escapeHtml(k.keywords)}</div>` : ''}
                <a href="/html/ledger.html" onclick="document.getElementById('keyword').value='${escapeHtml(k.contractNo)}';document.getElementById('keyword').dispatchEvent(new Event('input'));return false;" style="font-size:12px;">→ 查看合同</a>
            </div>`).join('');
    } catch (e) { toast('知识库搜索失败：' + e.message); }
});
$('#btnKnowledgeClear').addEventListener('click', () => {
    $('#ksKeyword').value = '';
    $('#ksType').value = '';
    $('#knowledgeResults').style.display = 'none';
});

// ==================== 区块链校验 ====================
async function verifyBlockchain(contractId, title) {
    try {
        const result = await api(`/api/blockchain/verify/${contractId}`);
        const lines = [];
        lines.push(`<h4>🔗 区块链完整性校验 — ${escapeHtml(title)}</h4>`);
        lines.push(`<p>校验结果：<span class="tag ${result.valid ? 'tag-green' : 'tag-red'}">${result.valid ? '✅ 完整' : '❌ 异常'}</span> | 存证节点：${result.totalNodes} 个</p>`);
        if (result.brokenNodes && result.brokenNodes.length > 0) {
            lines.push('<div style="background:#ffeaea;padding:12px;border-radius:8px;margin:8px 0;"><strong>⚠️ 发现异常节点：</strong>');
            result.brokenNodes.forEach(b => {
                lines.push(`<p style="margin:4px 0;font-size:13px;">节点 #${b.index} (${escapeHtml(b.versionNo)}): ${escapeHtml(b.reason)}</p>`);
            });
            lines.push('</div>');
        }
        if (result.chain && result.chain.length > 0) {
            lines.push('<table class="rec-table" style="margin-top:8px;"><thead><tr><th>#</th><th>类型</th><th>摘要</th><th>哈希</th><th>时间</th></tr></thead><tbody>');
            result.chain.forEach(c => {
                lines.push(`<tr><td>${c.index}</td><td><span class="tag">${escapeHtml(c.recordType)}</span></td><td>${escapeHtml(c.versionNo)}</td><td style="font-size:11px;font-family:monospace;">${escapeHtml(c.nodeHash||'').substring(0,20)}...</td><td>${escapeHtml(c.recordedAt||'')}</td></tr>`);
            });
            lines.push('</tbody></table>');
        }
        $('#detailModalBody').innerHTML = lines.join('');
        $('#detailModalTitle').textContent = '区块链校验';
        $('#detailModal').hidden = false;
    } catch (e) { toast('区块链校验失败：' + e.message); }
}

// 扩展操作按钮，增加校验入口
const origRenderActionButtons = renderActionButtons;
renderActionButtons = function(row) {
    let html = origRenderActionButtons(row);
    html += ` <button class="secondary" data-verify="${row.contractId}" data-vtitle="${escapeHtml(row.title)}" title="区块链校验">🔗</button>`;
    return html;
};

// 在 contractRows 委托中增加 verify 和 knowledge 按钮
const origRowsClick = $('#contractRows').onclick;
$('#contractRows').addEventListener('click', async (event) => {
    const verifyBtn = event.target.closest('[data-verify]');
    if (verifyBtn) {
        event.stopPropagation();
        await verifyBlockchain(verifyBtn.dataset.verify, verifyBtn.dataset.vtitle);
    }
});

// ==================== 锚点自动聚焦（从仪表盘快捷入口跳转） ====================
(function handleHashAnchor() {
    const hash = location.hash;
    if (hash === '#knowledge') {
        setTimeout(() => {
            const el = $('#knowledge');
            if (el) { el.scrollIntoView({ behavior: 'smooth', block: 'start' }); el.style.boxShadow = '0 0 0 3px #4361ee'; setTimeout(() => { el.style.boxShadow = ''; }, 2000); }
            const input = $('#ksKeyword');
            if (input) setTimeout(() => input.focus(), 500);
        }, 300);
    } else if (hash === '#blockchain') {
        setTimeout(() => {
            const el = $('#blockchain');
            if (el) { el.scrollIntoView({ behavior: 'smooth', block: 'start' }); el.style.boxShadow = '0 0 0 3px #4361ee'; setTimeout(() => { el.style.boxShadow = ''; }, 2000); }
        }, 300);
    }
})();

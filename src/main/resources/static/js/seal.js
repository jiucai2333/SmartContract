if (!initAppShell('seal', '签章登记', 'Seal Registration')) throw new Error('auth required');
const P = new URLSearchParams(location.search);
let contractId = P.get('contractId'), versionId = P.get('versionId');
let uploadedFileId = null, uploadedFileUrl = null, uploadedFileName = null;

(async function init() { if (!contractId) { await showPicker(); return; } await loadContract(); })();

async function showPicker() {
    updateSteps(1);
    try {
        const list = await api('/api/contracts');
        const approved = list.filter(c => c.status === 'APPROVED');
        $('#contractPicker').innerHTML = approved.length === 0
            ? '<div class="list-item"><span>暂无可签章的合同。请先在台账页完成合同编制和审批。</span></div>'
            : '<h3 style="margin-bottom:12px">选择要签章的合同（已审批状态）</h3>' + approved.map(c => {
                const label = statusText[c.status] || c.status;
                return `<div class="list-item"><div class="list-item-info"><strong>${escapeHtml(c.contractNo)} — ${escapeHtml(c.title)}</strong><span>${escapeHtml(c.counterparty)} · ¥${Number(c.amount||0).toLocaleString()} · <span class="tag tag-purple">${escapeHtml(label)}</span></span></div><a class="primary-btn" href="/html/seal.html?contractId=${c.contractId}" onclick="return pick(${c.contractId})">签章登记 →</a></div>`;
            }).join('');
        $('#contractPicker').style.display = 'block';
    } catch (e) { showErr('加载失败：' + e.message); }
}
window.pick = function(cid) { contractId = String(cid); loadContract(); return false; };

async function loadContract() {
    updateSteps(2);
    try {
        const list = await api('/api/contracts');
        const c = list.find(x => x.contractId === Number(contractId));
        if (!c) { showErr('未找到合同'); return; }
        if (c.status !== 'APPROVED') { showErr(`合同状态为「${statusText[c.status]||c.status}」，仅「已审批」可签章`, true); return; }
        if (!versionId) { try { const vs = await api(`/api/contracts/${c.contractId}/versions`); if (vs && vs.length) versionId = vs[0].versionId; } catch {} }
        $('#contractCard').innerHTML = `<h3>${escapeHtml(c.title)}</h3><div class="info-grid">`
            + ['合同编号',c.contractNo,'相对方',c.counterparty,'金额','¥'+Number(c.amount||0).toLocaleString(),'类型',c.type,'当前状态',`<span class="tag tag-purple">${escapeHtml(statusText[c.status]||c.status)}</span>`,'签章版本',String(versionId||'未找到'),'经办部门','ID: '+(c.deptId||'-'),'风险等级',`<span class="tag ${c.riskLevel}">${escapeHtml(riskText[c.riskLevel]||c.riskLevel||'-')}</span>`]
            .reduce((a,v,i)=>i%2===0?a+`<div class="info-item"><span class="info-label">${v}</span><span class="info-value">`:a+`${v}</span></div>`,'')+'</div>';
        $('#contractCard').style.display='block'; $('#contractPicker').style.display='none'; $('#sealFormArea').style.display='block';
        const now = new Date(); $('#sealTime').value = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}T${String(now.getHours()).padStart(2,'0')}:${String(now.getMinutes()).padStart(2,'0')}`;
        $('#operatorName').value = state.username || '';
    } catch (e) { showErr('加载失败：' + e.message); }
}

function updateSteps(n) { $$('#stepsBar .step').forEach((s,i)=>{ s.classList.remove('done','current'); if(i+1<n)s.classList.add('done'); else if(i+1===n)s.classList.add('current'); }); }
function showErr(msg, showPickerToo) { $('#contractCard').style.display='none'; $('#sealFormArea').style.display='none'; $('#statusMsg').style.display='block'; $('#statusMsg').innerHTML=`<div class="notice">${escapeHtml(msg)}</div><a href="/html/ledger.html" class="link-btn" style="margin-top:8px">返回台账</a>`; if(showPickerToo)showPicker(); }

(function bindUpload() {
    const zone=$('#uploadZone'), inp=$('#fileInput'), prev=$('#filePreview');
    if(!zone)return;
    zone.addEventListener('click',()=>inp.click());
    zone.addEventListener('dragover',e=>{e.preventDefault();zone.classList.add('dragover');});
    zone.addEventListener('dragleave',()=>zone.classList.remove('dragover'));
    zone.addEventListener('drop',e=>{e.preventDefault();zone.classList.remove('dragover');if(e.dataTransfer.files[0])doUpload(e.dataTransfer.files[0]);});
    inp.addEventListener('change',()=>{if(inp.files[0])doUpload(inp.files[0]);});
    async function doUpload(file) {
        if(!file.type.match(/pdf|jpe?g|png/)&&!file.name.match(/\.(pdf|jpg|jpeg|png)$/i)){toast('仅支持 PDF、JPG、PNG');return;}
        prev.style.display='block'; prev.innerHTML=`<p>上传中：${escapeHtml(file.name)}…</p>`;
        const fd=new FormData(); fd.append('file',file); fd.append('attachType','SIGNED_FILE');
        try {
            const r=await uploadApi('/api/attachments/upload',fd);
            uploadedFileId=r.fileId||r.id||r.attachmentId;
            uploadedFileUrl=r.fileUrl||(uploadedFileId?`/api/attachments/${uploadedFileId}/download`:null);
            uploadedFileName=file.name;
            if(!uploadedFileId)throw new Error('未返回文件ID');
            prev.innerHTML=`<span>✓ ${escapeHtml(file.name)} 上传成功</span><button type="button" class="secondary" id="clearFile">移除</button>`;
            $('#clearFile').addEventListener('click',()=>{uploadedFileId=uploadedFileUrl=uploadedFileName=null;prev.style.display='none';prev.innerHTML='';inp.value='';});
            updateSteps(3);
            if(contractId&&uploadedFileId){try{await api(`/api/attachments/${uploadedFileId}/link`,{method:'POST',body:JSON.stringify({contractId:Number(contractId)})});}catch{}}
        }catch(e){prev.innerHTML=`<p style="color:var(--danger)">上传失败：${escapeHtml(e.message)}</p>`;}
    }
})();

$('#submitSealBtn').addEventListener('click',async()=>{
    if(!contractId||!versionId){toast('缺少合同/版本参数');return;}
    if(!uploadedFileId){toast('请先上传签章文件');return;}
    const st=$('#sealStatus').value; if(!st){toast('请选择盖章状态');return;}
    try {
        $('#submitSealBtn').disabled=true;$('#submitSealBtn').textContent='提交中…';
        await api(`/api/contracts/${contractId}/seal`,{method:'POST',body:JSON.stringify({contractId:Number(contractId),versionId:Number(versionId),fileId:uploadedFileId,fileUrl:uploadedFileUrl,fileName:uploadedFileName,sealStatus:st,sealTime:$('#sealTime').value||new Date().toISOString(),remark:$('#remark').value||''})});
        updateSteps(4); toast('签章登记成功！状态已推进至 SIGNING。');
        setTimeout(()=>{location.href='/html/ledger.html';},1500);
    }catch(e){toast(e.message);$('#submitSealBtn').disabled=false;$('#submitSealBtn').textContent='确认签章登记';}
});
$('#cancelBtn').addEventListener('click',()=>{if(uploadedFileId&&!confirm('已上传文件将丢失，确定返回？'))return;location.href='/html/ledger.html';});

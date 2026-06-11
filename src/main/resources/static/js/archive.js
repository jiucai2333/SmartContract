if (!initAppShell('archive', '归档确认', 'Archive Confirmation')) throw new Error('auth required');
const P = new URLSearchParams(location.search);
let contractId = P.get('contractId'), versionId = P.get('versionId');

(async function init() { if (!contractId) { await showPicker(); return; } await loadData(); })();

async function showPicker() {
    updateSteps(1);
    try {
        const list = await api('/api/contracts');
        const signing = list.filter(c => c.status === 'SIGNING');
        $('#contractPicker').innerHTML = signing.length === 0
            ? '<div class="list-item"><span>暂无待归档的合同。请先完成签章登记。</span></div>'
            : '<h3 style="margin-bottom:12px">选择要归档的合同（已签章状态）</h3>' + signing.map(c => {
                const label = statusText[c.status] || c.status;
                return `<div class="list-item"><div class="list-item-info"><strong>${escapeHtml(c.contractNo)} — ${escapeHtml(c.title)}</strong><span>${escapeHtml(c.counterparty)} · ¥${Number(c.amount||0).toLocaleString()} · <span class="tag tag-orange">${escapeHtml(label)}</span></span></div><a class="primary-btn" href="/html/archive.html?contractId=${c.contractId}" onclick="return pick(${c.contractId})">归档确认 →</a></div>`;
            }).join('');
        $('#contractPicker').style.display = 'block';
    } catch (e) { showErr('加载失败：' + e.message); }
}
window.pick = function(cid) { contractId = String(cid); loadData(); return false; };

async function loadData() {
    updateSteps(2);
    try {
        const list = await api('/api/contracts');
        const c = list.find(x => x.contractId === Number(contractId));
        if (!c) { showErr('未找到合同'); return; }
        if (c.status !== 'SIGNING') { showErr(`合同状态为「${statusText[c.status]||c.status}」，仅「已签章」可归档`, true); return; }
        if (!versionId) { try { const vs = await api(`/api/contracts/${c.contractId}/versions`); if (vs && vs.length) versionId = vs[0].versionId; } catch {} }
        const [sealRecords, attachments, versions] = await Promise.all([
            api(`/api/contracts/${c.contractId}/seal-records`).catch(()=>[]),
            api(`/api/contracts/${c.contractId}/attachments`).catch(()=>[]),
            api(`/api/contracts/${c.contractId}/versions`).catch(()=>[])
        ]);
        $('#contractCard').innerHTML = `<h3>${escapeHtml(c.title)}</h3><div class="info-grid">`
            + ['合同编号',c.contractNo,'相对方',c.counterparty,'金额','¥'+Number(c.amount||0).toLocaleString(),'类型',c.type,'当前状态',`<span class="tag tag-orange">${escapeHtml(statusText[c.status]||c.status)}</span>`,'归档版本',String(versionId||'未找到'),'经办部门','ID: '+(c.deptId||'-'),'风险等级',`<span class="tag ${c.riskLevel}">${escapeHtml(riskText[c.riskLevel]||c.riskLevel||'-')}</span>`]
            .reduce((a,v,i)=>i%2===0?a+`<div class="info-item"><span class="info-label">${v}</span><span class="info-value">`:a+`${v}</span></div>`,'')+'</div>';
        $('#contractCard').style.display='block';
        const signed = attachments.filter(a => a.attachType === 'SIGNED_FILE' || a.attachType === 'SIGNED');
        $('#sealFilesList').innerHTML = signed.length === 0 ? '<p style="color:var(--muted)">暂无签章文件</p>' : signed.map(f => `<div class="list-row"><span><strong>${escapeHtml(f.fileName)}</strong> · ${((f.fileSize||0)/1024).toFixed(1)}KB</span><button class="secondary" data-dl="${f.attachmentId}" data-fn="${escapeHtml(f.fileName)}">下载</button></div>`).join('');
        $('#filesCard').style.display='block';
        $('#sealRecordsList').innerHTML = sealRecords.length === 0 ? '<p style="color:var(--muted)">暂无签章记录</p>' : sealRecords.map(r => `<div class="list-row"><span><strong>${escapeHtml(r.fileName||'签章文件')}</strong> · ${escapeHtml(r.sealStatus||'-')} · ${fmtTime(r.sealTime)}</span></div>`).join('');
        $('#recordsCard').style.display='block';
        const ver = versions.find(v => v.versionId === Number(versionId));
        const vNo = ver ? ver.versionNo : (versionId || 'unknown');
        const now = new Date();
        $('#pvNo').textContent = `AR-${now.getFullYear()}-${contractId}-${vNo.replace(/\./g,'-')}`;
        $('#pvTime').textContent = now.toLocaleString('zh-CN',{hour12:false});
        $('#pvVer').textContent = vNo;
        $('#previewCard').style.display='block'; $('#actionBar').style.display='block'; $('#contractPicker').style.display='none';
        updateSteps(3);
        $$('[data-dl]').forEach(b => b.addEventListener('click', async e => {
            e.preventDefault();
            const r = await fetch(`/api/attachments/${b.dataset.dl}/download`,{headers:state.accessToken?{Authorization:`Bearer ${state.accessToken}`}:{}});
            if(r.status===401){logout();return;} if(!r.ok){toast('下载失败');return;}
            const blob=await r.blob(),url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=b.dataset.fn||'file';a.click();URL.revokeObjectURL(url);
        }));
    } catch (e) { showErr('加载失败：' + e.message); }
}

function updateSteps(n) { $$('#stepsBar .step').forEach((s,i)=>{ s.classList.remove('done','current'); if(i+1<n)s.classList.add('done'); else if(i+1===n)s.classList.add('current'); }); }
function fmtTime(v) { if(!v)return'-';if(Array.isArray(v)){const[y,m,d,h=0,min=0]=v;return`${y}-${String(m).padStart(2,'0')}-${String(d).padStart(2,'0')}`;}const s=String(v);return s.includes('T')?s.replace('T',' ').substring(0,19):s; }
function showErr(msg, showPickerToo) { ['contractCard','filesCard','recordsCard','previewCard'].forEach(id=>{const el=$('#'+id);if(el)el.style.display='none';}); $('#actionBar').style.display='none'; $('#statusMsg').style.display='block'; $('#statusMsg').innerHTML=`<div class="notice">${escapeHtml(msg)}</div><a href="/html/ledger.html" class="link-btn" style="margin-top:8px">返回台账</a>`; if(showPickerToo)showPicker(); }

$('#confirmBtn').addEventListener('click', async () => {
    if (!contractId || !versionId) { toast('缺少参数'); return; }
    try { $('#confirmBtn').disabled=true;$('#confirmBtn').textContent='归档中…'; const r = await api(`/api/contracts/${contractId}/archive`,{method:'POST',body:JSON.stringify({contractId:Number(contractId),versionId:Number(versionId)})}); toast(`归档成功！编号：${r.archiveNo}`); setTimeout(()=>{location.href='/html/ledger.html';},1500); }
    catch(e){toast(e.message);$('#confirmBtn').disabled=false;$('#confirmBtn').textContent='确认归档';}
});
$('#cancelBtn').addEventListener('click', () => { location.href='/html/ledger.html'; });

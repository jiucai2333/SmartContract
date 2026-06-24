if (!initAppShell('seal', '电子签章', '通过第三方平台完成电子印章签署')) throw new Error('auth required');

var P = new URLSearchParams(location.search);
var contractId = P.get('contractId');
var versionId = P.get('versionId');
var fileId = P.get('fileId');
var fileName = P.get('fileName') || '';
var pollTimer = null;

if (!contractId || !fileId) {
    document.body.innerHTML = '<div class="section panel"><div class="form-card" style="text-align:center;padding:40px"><h3 style="color:var(--danger)">参数错误</h3><p style="margin:12px 0">缺少合同 ID 或文件 ID，请从签章登记页面进入。</p><a href="/html/seal.html" class="primary-btn">返回签章登记</a></div></div>';
    throw new Error('missing params');
}

(function init() {
    updateSteps(1);
    loadContractInfo().then(function () { updateSteps(2); });
})();

function updateSteps(n) {
    var steps = document.querySelectorAll('#stepsBar .step');
    steps.forEach(function (s, i) {
        s.classList.remove('done', 'current');
        if (i + 1 < n) s.classList.add('done');
        else if (i + 1 === n) s.classList.add('current');
    });
}

function loadContractInfo() {
    return api('/api/contracts').then(function (list) {
        var c = list.find(function (x) { return x.contractId === Number(contractId); });
        if (!c) { showStatus('error', '未找到合同'); return; }
        document.getElementById('contractCard').innerHTML =
            '<h3>' + escapeHtml(c.title) + '</h3>' +
            '<div class="info-grid">' +
            '<div class="info-item"><span class="info-label">合同编号</span><span class="info-value">' + escapeHtml(c.contractNo) + '</span></div>' +
            '<div class="info-item"><span class="info-label">相对方</span><span class="info-value">' + escapeHtml(c.counterparty) + '</span></div>' +
            '<div class="info-item"><span class="info-label">金额</span><span class="info-value">¥' + Number(c.amount || 0).toLocaleString() + '</span></div>' +
            '<div class="info-item"><span class="info-label">类型</span><span class="info-value">' + escapeHtml(c.type) + '</span></div>' +
            '<div class="info-item"><span class="info-label">状态</span><span class="info-value"><span class="tag tag-purple">' + escapeHtml(STATUS_TEXT[c.status] || c.status) + '</span></span></div>' +
            '<div class="info-item"><span class="info-label">签章文件</span><span class="info-value">' + escapeHtml(fileName) + '</span></div>' +
            '<div class="info-item"><span class="info-label">文件 ID</span><span class="info-value">' + fileId + '</span></div>' +
            '<div class="info-item"><span class="info-label">合同版本</span><span class="info-value">' + (versionId || '-') + '</span></div>' +
            '</div>';
    }).catch(function (e) {
        showStatus('error', '加载合同信息失败：' + e.message);
    });
}

document.getElementById('submitSignBtn').addEventListener('click', function () {
    var signerName = document.getElementById('signerName').value.trim();
    var signerMobile = document.getElementById('signerMobile').value.trim();
    if (!signerName) { toast('请输入签章人姓名'); return; }
    if (!signerMobile) { toast('请输入签章人手机号'); return; }
    if (!/^\d{11}$/.test(signerMobile)) { toast('请输入正确的手机号'); return; }

    var btn = document.getElementById('submitSignBtn');
    btn.disabled = true;
    btn.textContent = '发起中...';

    api('/api/signature/request', {
        method: 'POST',
        body: JSON.stringify({
            contractId: Number(contractId),
            versionId: versionId ? Number(versionId) : null,
            fileId: Number(fileId),
            signerName: signerName,
            signerMobile: signerMobile,
            signType: 'SINGLE'
        })
    }).then(function (res) {
        document.getElementById('signFormArea').style.display = 'none';
        updateSteps(3);
        if (res.status === 'ERROR' || res.status === 'FAILED') {
            showStatus('error', res.errorMessage || '签章请求失败');
        } else if (res.isSuccess) {
            showSuccess(res);
        } else if (res.status === 'PENDING' && res.signedFileUrl) {
            showSignUrl(res);
        } else if (res.transactionId) {
            startPolling(res.transactionId);
        } else {
            showStatus('error', '签章请求未返回事务ID，请重试');
        }
    }).catch(function (e) {
        toast('签章请求失败：' + e.message);
        btn.disabled = false;
        btn.textContent = '发起电子签章';
    });
});

function startPolling(txnId) {
    updateSteps(3);
    document.getElementById('statusArea').style.display = 'block';
    document.getElementById('statusCard').innerHTML =
        '<div style="text-align:center;padding:32px 24px">' +
        '<div style="font-size:48px;margin-bottom:12px">&#9203;</div>' +
        '<h3>签名处理中</h3>' +
        '<p style="color:var(--muted);margin:8px 0">事务 ID: <code>' + escapeHtml(txnId) + '</code></p>' +
        '<p style="color:var(--accent);font-size:13px"><span class="pulse-dot"></span>等待第三方平台签署...</p>' +
        '<p style="color:var(--muted);font-size:12px;margin-top:12px">在法大大完成签署后，点击下方按钮刷新状态</p>' +
        '<div class="button-row" style="justify-content:center;margin-top:12px">' +
        '<button type="button" id="refreshStatusBtn" class="primary-btn">刷新签署状态</button>' +
        '<a href="/html/ledger.html" class="secondary">返回台账</a>' +
        '</div></div>';
    document.getElementById('refreshStatusBtn').addEventListener('click', function () {
        document.getElementById('refreshStatusBtn').disabled = true;
        document.getElementById('refreshStatusBtn').textContent = '查询中...';
        api('/api/signature/status/' + encodeURIComponent(txnId)).then(function (r) {
            document.getElementById('refreshStatusBtn').disabled = false;
            document.getElementById('refreshStatusBtn').textContent = '刷新签署状态';
            if (r.status === 'SIGNED') { clearInterval(pollTimer); showSuccess(r); }
            else { toast('当前状态: ' + (r.status || 'PENDING') + '，请在法大大完成签署后再试'); }
        }).catch(function () { toast('查询失败'); document.getElementById('refreshStatusBtn').disabled = false; document.getElementById('refreshStatusBtn').textContent = '刷新签署状态'; });
    });
    var attempts = 0;
    pollTimer = setInterval(function () {
        attempts++;
        api('/api/signature/status/' + encodeURIComponent(txnId)).then(function (r) {
            if (r.status === 'SIGNED') { clearInterval(pollTimer); showSuccess(r); return; }
            if (r.status === 'FAILED' || r.status === 'ERROR') { clearInterval(pollTimer); showStatus('error', '签章失败：' + (r.errorMessage || '平台返回失败')); return; }
            if (attempts >= 60) { clearInterval(pollTimer); showStatus('timeout', '签章超时，事务 ID: ' + escapeHtml(txnId)); }
        }).catch(function () {
            if (attempts >= 60) { clearInterval(pollTimer); showStatus('timeout', '查询状态失败'); }
        });
    }, 5000);
}

function showSuccess(r) {
    document.getElementById('statusArea').style.display = 'block';
    document.getElementById('statusCard').innerHTML =
        '<div style="text-align:center;padding:32px 24px">' +
        '<div style="font-size:48px;color:var(--success);margin-bottom:12px">&#10003;</div>' +
        '<h3 style="color:var(--success)">签章成功</h3>' +
        '<p style="color:var(--muted);margin:8px 0;line-height:1.5">事务 ID: ' + escapeHtml(r.transactionId) + '<br>' +
        '签署时间: ' + (r.signedAt ? new Date(r.signedAt).toLocaleString() : new Date().toLocaleString()) + '</p>' +
        '<div class="button-row" style="justify-content:center;margin-top:16px">' +
        '<a href="/html/ledger.html" class="primary-btn">返回台账</a>' +
        '<a href="/html/seal.html" class="secondary">继续签章</a>' +
        '</div></div>';
}

function showSignUrl(res) {
    document.getElementById('statusArea').style.display = 'block';
    document.getElementById('statusCard').innerHTML =
        '<div style="text-align:center;padding:32px 24px">' +
        '<div style="font-size:48px;margin-bottom:12px">&#128221;</div>' +
        '<h3>签署任务已发起</h3>' +
        '<p style="color:var(--muted);margin:8px 0">事务 ID: <code>' + escapeHtml(res.transactionId) + '</code></p>' +
        '<div class="button-row" style="justify-content:center;margin-top:16px">' +
        '<a href="' + escapeHtml(res.signedFileUrl) + '" target="_blank" class="primary-btn" style="font-size:14px;padding:12px 28px">前往法大大签署 &rarr;</a>' +
        '</div>' +
        '<p style="color:var(--warning);font-size:13px;margin-top:20px;font-weight:600">在法大大完成签署后，点击下方按钮更新状态</p>' +
        '<div class="button-row" style="justify-content:center;margin-top:8px">' +
        '<button type="button" id="refreshAfterSignBtn" class="primary-btn">已完成签署，刷新状态</button>' +
        '<a href="/html/ledger.html" class="secondary">返回台账</a>' +
        '</div></div>';
    document.getElementById('refreshAfterSignBtn').addEventListener('click', function () {
        var btn = document.getElementById('refreshAfterSignBtn');
        btn.disabled = true; btn.textContent = '查询中...';
        api('/api/signature/status/' + encodeURIComponent(res.transactionId)).then(function (r) {
            if (r.status === 'SIGNED') { showSuccess(r); return; }
            btn.disabled = false; btn.textContent = '已完成签署，刷新状态';
            toast('状态: ' + (r.status || 'PENDING') + '，请确保在法大大已完成签署');
        }).catch(function () { btn.disabled = false; btn.textContent = '已完成签署，刷新状态'; toast('查询失败'); });
    });
}

function showStatus(type, msg) {
    document.getElementById('statusArea').style.display = 'block';
    var icon = type === 'error' ? '&#10007;' : '&#9200;';
    var title = type === 'error' ? '签章失败' : '签章等待中';
    var color = type === 'error' ? 'var(--danger)' : 'var(--warning)';
    document.getElementById('statusCard').innerHTML =
        '<div style="text-align:center;padding:32px 24px">' +
        '<div style="font-size:48px;color:' + color + ';margin-bottom:12px">' + icon + '</div>' +
        '<h3>' + title + '</h3>' +
        '<p style="color:var(--muted);margin:8px 0;line-height:1.5">' + escapeHtml(msg) + '</p>' +
        '<div class="button-row" style="justify-content:center;margin-top:16px">' +
        '<button type="button" id="retryRefreshBtn" class="primary-btn">刷新状态</button>' +
        '<a href="/html/ledger.html" class="secondary">返回台账</a>' +
        '</div></div>';
    if (document.getElementById('retryRefreshBtn')) {
        document.getElementById('retryRefreshBtn').addEventListener('click', function () {
            document.getElementById('retryRefreshBtn').disabled = true;
            document.getElementById('retryRefreshBtn').textContent = '查询中...';
            var txnId = document.querySelector('code').textContent;
            api('/api/signature/status/' + encodeURIComponent(txnId)).then(function (r) {
                if (r.status === 'SIGNED') showSuccess(r);
                else { toast('当前状态: ' + (r.status || 'UNKNOWN')); document.getElementById('retryRefreshBtn').disabled = false; document.getElementById('retryRefreshBtn').textContent = '刷新状态'; }
            }).catch(function () { toast('查询失败'); document.getElementById('retryRefreshBtn').disabled = false; document.getElementById('retryRefreshBtn').textContent = '刷新状态'; });
        });
    }
}

document.getElementById('cancelBtn').addEventListener('click', function () {
    if (pollTimer) clearInterval(pollTimer);
    location.href = '/html/seal.html?contractId=' + encodeURIComponent(contractId);
});
window.addEventListener('beforeunload', function () {
    if (pollTimer) clearInterval(pollTimer);
});

if (!initAppShell('blockchain', '区块链存证', '验证合同数据完整性，查看哈希链存证记录')) throw new Error('auth required');

api('/api/contracts').then(function (list) {
    var opts = '<option value="">-- 请选择合同 --</option>';
    list.forEach(function (c) {
        opts += '<option value="' + c.contractId + '">' + escapeHtml(c.contractNo) + ' - ' + escapeHtml(c.title) + ' (' + escapeHtml(STATUS_TEXT[c.status] || c.status) + ')</option>';
    });
    document.getElementById('contractPicker').innerHTML = opts;
}).catch(function () { toast('加载合同列表失败'); });

document.getElementById('verifyBtn').addEventListener('click', function () {
    var cid = document.getElementById('contractPicker').value;
    if (!cid) { toast('请先选择合同'); return; }
    loadBlockchainData(cid);
});

function loadBlockchainData(cid) {
    var btn = document.getElementById('verifyBtn');
    btn.disabled = true; btn.textContent = '查询中...';
    // 先清空上次结果
    document.getElementById('verifySummary').innerHTML = '';
    document.getElementById('recordsTable').innerHTML = '';

    Promise.all([
        api('/api/blockchain/verify/' + cid).catch(function () { return null; }),
        api('/api/blockchain/records/' + cid).catch(function () { return []; })
    ]).then(function (results) {
        var verify = results[0]; var records = results[1];
        btn.disabled = false; btn.textContent = '查询验证';
        document.getElementById('resultArea').style.display = 'block';

        // 无记录：显示提示
        if ((!verify || verify.total === 0) && records.length === 0) {
            document.getElementById('verifySummary').innerHTML =
                '<div style="background:#fffbeb;border:1px solid #fcd34d;border-radius:10px;padding:14px 18px;color:#92400e;font-size:13px">' +
                '该合同暂无区块链存证记录。完成<strong>签章登记</strong>后系统会自动将合同数据锚定到哈希链。</div>';
            return;
        }

        // 有记录：显示验证结果
        if (verify && verify.total > 0) {
            var summaryHtml;
            if (verify.allValid) {
                summaryHtml = '<div style="background:#f0fdf4;border:1px solid #86efac;border-radius:10px;padding:14px 18px">' +
                    '<span style="font-weight:700;color:#166534;font-size:14px">完整性验证通过</span>' +
                    '<span style="color:#15803d;font-size:13px;margin-left:12px">共 ' + verify.total + ' 条记录，未检测到篡改</span></div>';
            } else {
                summaryHtml = '<div style="background:#fef2f2;border:1px solid #fca5a5;border-radius:10px;padding:14px 18px">' +
                    '<span style="font-weight:700;color:#991b1b;font-size:14px">完整性验证异常</span>' +
                    '<span style="color:#dc2626;font-size:13px;margin-left:12px">' + verify.failCount + ' / ' + verify.total + ' 条记录哈希不匹配，可能已被篡改</span></div>';
            }
            document.getElementById('verifySummary').innerHTML = summaryHtml;
        }

        // 存证记录表
        if (records.length > 0) {
            var rows = '';
            records.forEach(function (r, i) {
                rows += '<tr><td>' + (i + 1) + '</td><td>' + escapeHtml(r.recordType || '-') + '</td>' +
                    '<td><code style="font-size:11px">' + escapeHtml((r.nodeHash || '').substring(0, 24)) + '...</code></td>' +
                    '<td><code style="font-size:11px">' + escapeHtml((r.previousHash || '').substring(0, 24)) + '...</code></td>' +
                    '<td>' + (r.recordedAt ? new Date(r.recordedAt).toLocaleString() : '-') + '</td></tr>';
            });
            document.getElementById('recordsTable').innerHTML =
                '<table class="table-wrap"><thead><tr><th>#</th><th>类型</th><th>当前哈希</th><th>前驱哈希</th><th>记录时间</th></tr></thead><tbody>' + rows + '</tbody></table>';
        } else {
            document.getElementById('recordsTable').innerHTML =
                '<div style="color:var(--muted);font-size:13px;padding:12px 0">暂未查询到存证记录条目。</div>';
        }
    }).catch(function () { btn.disabled = false; btn.textContent = '查询验证'; toast('查询失败'); });
}

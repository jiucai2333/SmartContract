<template>
  <div class="legacy-view">
<section class="section panel">
    <div class="panel-head">
        <div><p class="eyebrow">合同归档</p><h2>归档确认</h2></div>
        <a href="/html/ledger.html" class="secondary">← 返回台账</a>
    </div>
    <div class="steps" id="stepsBar">
        <div class="step done"><span class="step-num">1</span> 选择合同</div>
        <div class="step-line"></div>
        <div class="step" id="step2"><span class="step-num">2</span> 确认签章文件</div>
        <div class="step-line"></div>
        <div class="step" id="step3"><span class="step-num">3</span> 生成归档编号</div>
    </div>
    <div id="contractPicker" class="picker" style="display:none"></div>
    <div id="contractCard" class="info-card" style="display:none"></div>
    <div id="filesCard" class="info-card" style="display:none"><h3>签章文件</h3><div id="sealFilesList"></div></div>
    <div id="recordsCard" class="info-card" style="display:none"><h3>签章登记记录</h3><div id="sealRecordsList"></div></div>
    <div id="esignUploadCard" class="info-card" style="display:none">
        <h3>电子签章 · 上传已签章文件</h3>
        <div class="archive-notice" style="margin-bottom:14px">
            <p style="color:#92400e;font-size:13px;margin:0">该合同使用<strong>电子签章</strong>，签章登记时上传的是空白待签文件。请在此上传法大大返回的<strong>已签章文件</strong>后再归档。</p>
        </div>
        <div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap">
            <input type="file" id="signedFileInput" accept=".pdf,.doc,.docx" style="display:none">
            <button type="button" id="pickSignedFileBtn" class="secondary">选择文件</button>
            <span id="signedFileName" style="font-size:13px;color:var(--muted)">未选择文件</span>
            <button type="button" id="uploadSignedFileBtn" class="primary-btn" disabled>上传已签章文件</button>
        </div>
        <div id="uploadedSignedFiles" style="margin-top:12px"></div>
    </div>
    <div id="previewCard" class="info-card highlight" style="display:none">
        <h3>归档预览</h3>
        <div class="info-grid">
            <div class="info-item"><span class="info-label">归档编号</span><span class="info-value" id="pvNo">-</span></div>
            <div class="info-item"><span class="info-label">归档时间</span><span class="info-value" id="pvTime">-</span></div>
            <div class="info-item"><span class="info-label">归档版本</span><span class="info-value" id="pvVer">-</span></div>
            <div class="info-item"><span class="info-label">归档后状态</span><span class="info-value"><span class="tag tag-green">已归档（锁定）</span></span></div>
        </div>
    </div>
    <div id="actionBar" class="action-bar" style="display:none">
        <div class="archive-notice">
            <p><strong>归档确认后将执行以下操作：</strong></p>
            <ol>
                <li>合同状态从 <strong>已签章</strong> 推进至 <strong>已归档</strong></li>
                <li>当前版本标记为<strong>已锁定</strong>，禁止后续编辑与恢复</li>
                <li>自动生成归档编号（格式：AR-年份-合同ID-版本号）</li>
            </ol>
        </div>
        <div class="button-row"><button type="button" id="confirmBtn">确认归档</button><button type="button" class="secondary" id="cancelBtn">取消</button></div>
    </div>
    <div id="statusMsg" style="display:none"></div>
</section>
  </div>
</template>

<script setup>
import { nextTick, onMounted } from 'vue';
import { runLegacyPage } from '../legacy/pageLoader';

onMounted(async () => {
  await nextTick();
  await runLegacyPage('archive');
});
</script>

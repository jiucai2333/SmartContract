<template>
  <div class="legacy-view">
<section class="section panel">
    <div class="panel-head">
        <div><p class="eyebrow">安全存证</p><h2>区块链防篡改验证</h2></div>
    </div>

    <div class="form-card" style="background:#f8fafc;border:1.5px solid #d1d5db;border-radius:14px;padding:24px 28px;margin-bottom:24px">
        <h3 style="font-size:15px;margin-bottom:6px">什么是区块链存证？</h3>
        <p style="color:var(--ink-secondary);font-size:13.5px;line-height:2;margin:0">
            合同在<strong style="color:var(--accent)">签章登记</strong>和<strong style="color:var(--accent)">归档确认</strong>两个关键节点，系统会自动提取合同编号、金额、签章状态等核心数据，生成唯一的 SHA-256 哈希指纹并存入哈希链。每条记录包含前一条的哈希值，环环相扣形成<strong style="color:var(--accent)">防篡改证据链</strong>——任何人修改历史数据都会导致哈希不匹配而被检测出来。
        </p>
    </div>

    <div class="form-card" style="margin-top:24px">
        <h3>选择合同</h3>
        <div class="form-row" style="max-width:520px">
            <label>合同</label>
            <select id="contractPicker" style="height:44px;font-size:13px">
                <option value="">-- 请选择合同 --</option>
            </select>
        </div>
        <button type="button" id="verifyBtn" style="margin-top:8px">查询验证</button>
    </div>

    <div id="resultArea" style="display:none">
        <div class="form-card">
            <h3>完整性验证</h3>
            <div id="verifySummary"></div>
        </div>
        <div class="form-card">
            <h3>存证记录</h3>
            <div id="recordsTable"></div>
        </div>
    </div>
</section>
  </div>
</template>

<script setup>
import { nextTick, onMounted } from 'vue';
import { runLegacyPage } from '../legacy/pageLoader';

onMounted(async () => {
  await nextTick();
  await runLegacyPage('blockchain');
});
</script>

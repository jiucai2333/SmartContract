<template>
  <div class="legacy-view">
<section class="section panel">
    <div class="panel-head">
        <div><p class="eyebrow">电子签章</p><h2>第三方平台电子签章</h2></div>
        <a href="/html/ledger.html" class="secondary">&larr; 返回台账</a>
    </div>

    <div class="steps" id="stepsBar">
        <div class="step done"><span class="step-num">1</span> 确认合同</div>
        <div class="step-line"></div>
        <div class="step" id="step2"><span class="step-num">2</span> 填写信息</div>
        <div class="step-line"></div>
        <div class="step" id="step3"><span class="step-num">3</span> 等待结果</div>
    </div>

    <div class="sig-layout">
        <!-- 左栏：合同信息 -->
        <div class="sig-left">
            <div class="info-card" id="contractCard"></div>
        </div>

        <!-- 右栏：签章表单 -->
        <div class="sig-right">
            <div id="signFormArea">
                <div class="form-card">
                    <h3>签章人信息</h3>
                    <p class="card-desc">填写签章人的姓名和手机号，用于第三方平台实名认证</p>
                    <div class="form-row">
                        <label>签章人姓名</label>
                        <input type="text" id="signerName" placeholder="请输入签章人姓名">
                    </div>
                    <div class="form-row">
                        <label>签章人手机号</label>
                        <input type="text" id="signerMobile" placeholder="请输入签章人手机号">
                    </div>
                </div>

                <div class="button-row">
                    <button type="button" id="submitSignBtn">发起电子签章</button>
                    <button type="button" class="secondary" id="cancelBtn">取消</button>
                </div>
            </div>

            <!-- 状态区域 -->
            <div id="statusArea" style="display:none">
                <div class="form-card" id="statusCard"></div>
            </div>
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
  await runLegacyPage('signature');
});
</script>

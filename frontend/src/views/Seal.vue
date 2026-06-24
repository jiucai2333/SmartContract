<template>
  <div class="legacy-view">
<section class="section panel">
    <div class="panel-head">
        <div><p class="eyebrow">合同签章</p><h2>签章登记</h2></div>
        <a href="/html/ledger.html" class="secondary">← 返回台账</a>
    </div>
    <div class="steps" id="stepsBar">
        <div class="step done"><span class="step-num">1</span> 选择合同</div>
        <div class="step-line"></div>
        <div class="step" id="step2"><span class="step-num">2</span> 上传签章文件</div>
        <div class="step-line"></div>
        <div class="step" id="step3"><span class="step-num">3</span> 填写签章信息</div>
        <div class="step-line"></div>
        <div class="step" id="step4"><span class="step-num">4</span> 确认提交</div>
    </div>
    <div id="contractPicker" class="picker" style="display:none"></div>
    <div id="contractCard" class="info-card" style="display:none"></div>
    <div id="sealFormArea" style="display:none">
        <div class="form-card" id="uploadCard">
            <h3>上传已签文件</h3>
            <p class="card-desc">上传盖章后的 PDF 扫描件或电子签章文件</p>
            <div class="upload-zone" id="uploadZone">
                <div class="upload-icon">+</div>
                <p>拖放文件到此处，或<span class="link">点击选择文件</span></p>
                <small>支持 PDF、JPG、PNG 格式</small>
                <input type="file" id="fileInput" accept=".pdf,.jpg,.jpeg,.png" hidden>
            </div>
            <div id="filePreview" class="file-preview" style="display:none"></div>
        </div>
        <!-- 签章方式选择 -->
        <div class="form-card" id="sealTypeChoice" style="display:none">
            <h3>选择签章方式</h3>
            <p class="card-desc">请选择本合同的签章方式：物理盖章直接登记，电子签章将通过第三方平台发起</p>
            <div class="seal-type-options">
                <label class="seal-type-card">
                    <input type="radio" name="sealType" value="physical" checked>
                    <div class="seal-type-body">
                        <strong>🔵 物理盖章</strong>
                        <small>使用实体印章盖章后，上传扫描件并直接登记。签章状态默认为"已盖章"。</small>
                    </div>
                </label>
                <label class="seal-type-card">
                    <input type="radio" name="sealType" value="electronic">
                    <div class="seal-type-body">
                        <strong>🟢 电子签章</strong>
                        <small>通过第三方电子签章平台（法大大）在线签署，无需上传实体扫描件。</small>
                    </div>
                </label>
            </div>
            <div class="button-row" style="margin-top:16px">
                <button type="button" id="confirmSealTypeBtn">确认签章方式</button>
            </div>
        </div>
        <div id="physicalSealSection" style="display:none">
            <div class="form-card">
                <h3>签章信息</h3>
                <div class="form-row"><label>签章状态</label><select id="sealStatus" required><option value="SEALED" selected>已签章</option><option value="ELECTRONIC">电子签章</option></select></div>
                <div class="form-row"><label>盖章时间</label><input type="datetime-local" id="sealTime"></div>
                <div class="form-row"><label>经办人</label><input type="text" id="operatorName" placeholder="请输入经办人姓名"></div>
                <div class="form-row"><label>备注</label><textarea id="remark" rows="2" placeholder="签章备注（选填）"></textarea></div>
            </div>
            <div class="action-bar">
                <p class="action-hint">提交后合同状态将从 <strong>已审批</strong> → <strong>已签章</strong>，并自动写入操作日志。盖章状态仅作为签章记录字段保存。</p>
                <div class="button-row"><button type="button" id="submitSealBtn">确认签章登记</button><button type="button" class="secondary" id="cancelBtn">取消</button></div>
            </div>
        </div>
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
  await runLegacyPage('seal');
});
</script>

<template>
  <div class="legacy-view">
    <section class="section edit-layout">
        <!-- 上方：元信息 + 上传 + 进度条 -->
        <div class="panel edit-top">
            <div class="edit-top-inner">
                <!-- 第一行：元信息字段 + 上传/选模板区 -->
                <div class="meta-upload-row">
                    <div id="contractMetaPanel" class="field-panel">
                        <div class="panel-mini-head">
                            <strong>合同信息</strong>
                            <span>用于生成编号、台账和审批上下文</span>
                        </div>
                        <div class="field-list">
                            <label class="field-item"><span>合同名称</span><input id="contractTitle" placeholder="请输入合同名称"></label>
                            <label class="field-item"><span>合同相对方</span><input id="contractCounterparty" placeholder="请输入合同相对方"></label>
                            <label class="field-item"><span>合同类型</span>
                                <select id="contractType">
                                    <option value="PURCHASE">采购合同</option>
                                    <option value="SALES">销售合同</option>
                                    <option value="TECH">技术合同</option>
                                    <option value="LABOR">劳务合同</option>
                                    <option value="CONFIDENTIAL">保密合同</option>
                                    <option value="LOGISTICS">物流合同</option>
                                    <option value="ENTERPRISE_SERVICE">企业服务合同</option>
                                    <option value="INTELLECTUAL_PROPERTY">知识产权合同</option>
                                    <option value="OTHER">其他</option>
                                </select>
                            </label>
                            <label class="field-item"><span>合同金额</span><input id="contractAmount" type="number" min="0" step="0.01" placeholder="请输入合同金额"></label>
                        </div>
                    </div>
                    <div class="source-cards">
                        <div class="upload-zone" id="uploadZone">
                            <input id="fileInput" type="file" accept=".pdf,.doc,.docx,.jpg,.jpeg,.png,.webp" hidden>
                            <div class="upload-zone-icon"><i data-lucide="upload-cloud"></i></div>
                            <div class="upload-zone-text">上传合同材料</div>
                            <div class="upload-zone-sub">拖拽 Word / PDF / 图片到此处</div>
                            <button type="button" id="pickFileBtn" class="secondary">选择文件</button>
                            <span id="uploadFileName" class="upload-name">未选择文件</span>
                        </div>
                        <div class="draft-picker" id="draftPicker">
                            <div class="upload-zone-icon"><i data-lucide="edit-3"></i></div>
                            <div class="upload-zone-text">选择现有草稿</div>
                            <div class="upload-zone-sub">继续编辑已保存合同</div>
                            <select id="draftSelect">
                                <option value="">选择草稿...</option>
                            </select>
                            <button type="button" id="useDraftBtn" class="secondary" disabled>打开草稿</button>
                        </div>
                        <div class="template-picker" id="templatePicker">
                            <div class="upload-zone-icon"><i data-lucide="file-text"></i></div>
                            <div class="upload-zone-text">从模板库选择</div>
                            <div class="upload-zone-sub">使用已有合同模板</div>
                            <select id="templateSelect">
                                <option value="">选择模板...</option>
                            </select>
                            <button type="button" id="useTemplateBtn" class="secondary" disabled>使用此模板</button>
                        </div>
                    </div>
                </div>
                <!-- 第二行：OCR 状态 + 进度条 + 草稿状态 -->
                <div class="status-row">
                    <div id="ocrPanel" class="ocr-panel" hidden>
                        <div class="ocr-head">
                            <strong id="ocrStatusText">OCR 状态</strong>
                            <div class="ocr-head-actions">
                                <button type="button" id="retryOcrBtn" class="secondary" hidden>重新解析</button>
                                <button type="button" id="reuploadBtn" class="secondary" hidden>重新上传</button>
                            </div>
                        </div>
                        <p id="ocrHint" class="ocr-hint">正在等待识别结果。</p>
                    </div>
                    <div class="draft-steps" aria-label="合同模板处理进度">
                        <div class="draft-step active" data-step="1"><span>1</span>上传文件</div>
                        <div class="connector"></div>
                        <div class="draft-step" data-step="2"><span>2</span>解析格式</div>
                        <div class="connector"></div>
                        <div class="draft-step" data-step="3"><span>3</span>载入编辑器</div>
                    </div>
                    <div class="hint" id="draftStatus">尚未创建草稿</div>
                </div>
            </div>
        </div>
        <!-- 下方：A4 编辑器 -->
        <div class="panel result-panel">
            <div class="result-actions">
                <div class="editor-heading">
                    <span>合同正文校对</span>
                    <span id="draftCharCount" class="ocr-char-count"></span>
                </div>
                <div class="editor-action-buttons">
                    <button id="analyzeFieldsBtn" type="button" class="secondary">识别待填写/风险字段</button>
                    <button id="downloadDraftBtn" type="button" class="secondary">下载 Word</button>
                    <button id="saveDraftBtn" type="button">保存草稿</button>
                </div>
            </div>
            <div class="editor-workspace">
                <div class="editor-main">
                    <div class="editor-toolbar">
                        <div id="editorToolbar" class="wang-editor-toolbar"></div>
                    </div>
                    <div class="a4-editor-wrapper">
                        <div id="draftEditor" class="rich-editor"></div>
                    </div>
                </div>
                <aside id="fieldPanel" class="contract-field-panel" hidden>
                    <div class="contract-field-head">
                        <div><strong id="fieldPanelTitle">待填写字段与风险修改点</strong><small id="fieldAnalysisSummary"></small></div>
                        <button id="closeFieldPanelBtn" type="button" class="secondary">收起</button>
                    </div>
                    <div id="contractFieldList" class="contract-field-list" tabindex="0" aria-label="待填写字段与风险修改点列表"></div>
                </aside>
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
  await runLegacyPage('edit');
});
</script>

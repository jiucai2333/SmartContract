<template>
  <div class="legacy-view audit-page">
    <section class="section audit-summary-grid">
      <div class="panel audit-summary-panel">
        <span class="audit-summary-label">审计记录</span>
        <strong id="auditTotal">-</strong>
        <small>按权限、操作、时间追踪关键动作</small>
      </div>
      <div class="panel audit-summary-panel">
        <span class="audit-summary-label">安全事件</span>
        <strong id="securityEventTotal">-</strong>
        <small>记录 AI、审批、归档、履约等敏感动作</small>
      </div>
    </section>

    <section class="section panel audit-filter-panel">
      <div class="filters audit-filters">
        <label>
          用户 ID
          <input id="auditUserId" type="number" min="1" placeholder="全部用户">
        </label>
        <label>
          操作类型
          <input id="auditOperation" placeholder="如 LOGIN、RISK_REVIEW">
        </label>
        <label>
          结果
          <select id="auditResult">
            <option value="">全部结果</option>
            <option value="SUCCESS">SUCCESS</option>
            <option value="FAIL">FAIL</option>
          </select>
        </label>
        <label>
          开始时间
          <input id="auditStart" type="datetime-local">
        </label>
        <label>
          结束时间
          <input id="auditEnd" type="datetime-local">
        </label>
        <div class="audit-filter-actions">
          <button id="auditSearchBtn" type="button">查询</button>
          <button id="auditResetBtn" class="secondary" type="button">重置</button>
        </div>
      </div>
    </section>

    <section class="section panel">
      <div class="panel-head">
        <div>
          <p class="eyebrow">操作留痕</p>
          <h2>操作审计日志</h2>
        </div>
        <button id="auditRefreshBtn" class="secondary" type="button">刷新</button>
      </div>
      <div class="table-wrap">
        <table class="audit-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>用户</th>
              <th>操作</th>
              <th>对象</th>
              <th>IP</th>
              <th>结果</th>
            </tr>
          </thead>
          <tbody id="auditTbody"></tbody>
        </table>
      </div>
      <div id="auditPager" class="table-pagination"></div>
    </section>

    <section class="section grid-two audit-grid">
      <div class="panel">
        <div class="panel-head">
          <div>
            <p class="eyebrow">安全事件</p>
            <h2>事件登记</h2>
          </div>
        </div>
        <form id="securityEventForm" class="form-grid audit-event-form">
          <label>
            事件类型
            <select id="securityEventType" required>
              <option value="AI_REVIEW">AI 审查</option>
              <option value="CONTRACT_CREATE_OR_UPDATE">合同创建/更新</option>
              <option value="VERSION_RESTORE">版本恢复</option>
              <option value="APPROVAL_ACTION">审批动作</option>
              <option value="SIGN_FILE_UPLOAD">签署文件上传</option>
              <option value="ARCHIVE">归档</option>
              <option value="DELIVERY_PAYMENT_CHANGE">履约/付款变更</option>
            </select>
          </label>
          <label>
            目标类型
            <input id="securityTargetType" required placeholder="CONTRACT / VERSION / PAYMENT">
          </label>
          <label>
            目标 ID
            <input id="securityTargetId" placeholder="可选">
          </label>
          <label class="wide">
            摘要
            <input id="securitySummary" placeholder="说明本次安全事件">
          </label>
          <label class="wide">
            载荷
            <textarea id="securityPayload" placeholder="敏感信息会在后端脱敏后保存"></textarea>
          </label>
          <div class="button-row wide">
            <button id="securityEventSubmitBtn" type="submit">登记事件</button>
          </div>
        </form>
      </div>

      <div class="panel">
        <div class="panel-head">
          <div>
            <p class="eyebrow">事件列表</p>
            <h2>最近安全事件</h2>
          </div>
          <button id="securityEventRefreshBtn" class="secondary" type="button">刷新</button>
        </div>
        <div id="securityEventList" class="audit-event-list"></div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { nextTick, onMounted } from 'vue';
import { runLegacyPage } from '../legacy/pageLoader';

onMounted(async () => {
  await nextTick();
  await runLegacyPage('audit');
});
</script>

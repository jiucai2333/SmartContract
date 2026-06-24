<template>
  <div class="legacy-view fulfillment-page">

    <div class="section fulfillment-filter-row">
      <section class="panel fulfillment-filter-panel">
        <div class="filters">
          <div class="search-field">
            <i data-lucide="search"></i>
            <input id="planKeyword" placeholder="按节点、负责人、备注搜索">
          </div>
          <select id="filterContract"></select>
          <select id="filterStatus">
            <option value="">全部状态</option>
            <option value="WARNING">预警节点</option>
            <option value="NOT_STARTED">待开始</option>
            <option value="PENDING_CONFIRM">待人工确认</option>
            <option value="IN_PROGRESS">进行中</option>
            <option value="COMPLETED">已完成</option>
            <option value="OVERDUE">已逾期</option>
            <option value="CLOSED">已关闭</option>
          </select>
          <button id="resetBtn" class="secondary" type="button">
            <i data-lucide="refresh-cw"></i>
            重置
          </button>
        </div>
      </section>
    </div>

    <section class="section metric-grid">
      <div class="metric-card">
        <span>履约节点</span>
        <strong id="statTotal">0</strong>
      </div>
      <div class="metric-card">
        <span>三级预警</span>
        <strong id="statWarning">0</strong>
      </div>
      <div class="metric-card">
        <span>逾期节点</span>
        <strong id="statOverdue">0</strong>
      </div>
      <div class="metric-card">
        <span>交付确认</span>
        <strong id="statDeliverable">0/0</strong>
      </div>
      <div class="metric-card">
        <span>逾期付款</span>
        <strong id="statPaymentOverdue">0</strong>
      </div>
      <div class="metric-card">
        <span>推送记录</span>
        <strong id="statReminder">0</strong>
      </div>
    </section>

    <section class="section responsibility-notice">
      <i data-lucide="info"></i>
      <span>责任归属仅作辅助提示，最终由人工确认，不自动作出法律结论。</span>
    </section>

    <section class="section feature-grid member-e-grid">
      <section class="panel feature-panel">
        <div class="panel-head">
          <div><p class="eyebrow">Member E</p><h2>初始化履约与付款台账</h2></div>
        </div>
        <div class="inline-form">
          <label>
            合同
            <select id="initContract"></select>
          </label>
          <button id="initMemberEBtn" type="button">
            <i data-lucide="circle-check"></i>
            生成标准台账
          </button>
        </div>
      </section>

      <section class="panel feature-panel">
        <div class="panel-head">
          <div><p class="eyebrow">履约计划</p><h2>履约节点抽取</h2></div>
        </div>
        <div class="inline-form">
          <label>
            合同
            <select id="extractContract"></select>
          </label>
          <button id="extractBtn" type="button">
            <i data-lucide="file-text"></i>
            抽取节点
          </button>
        </div>
      </section>

      <section class="panel feature-panel">
        <div class="panel-head">
          <div><p class="eyebrow">站内提醒</p><h2>预警消息分发</h2></div>
        </div>
        <div class="button-row compact-row">
          <button id="dispatchBtn" type="button">
            <i data-lucide="shield-alert"></i>
            生成提醒
          </button>
          <button id="refreshReminderBtn" class="secondary" type="button">
            <i data-lucide="refresh-cw"></i>
            刷新日志
          </button>
        </div>
      </section>
    </section>

    <section class="section panel fulfillment-table-panel">
      <div class="panel-head">
        <div><p class="eyebrow">履约进度</p><h2>履约节点维护、三级到期预警与逾期处置</h2></div>
        <button id="newPlanBtn" class="page-action-btn" type="button">
          <i data-lucide="plus"></i>
          新增节点
        </button>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
          <tr>
            <th>合同</th>
            <th>履约节点</th>
            <th>计划日期</th>
            <th>实际完成</th>
            <th>进度</th>
            <th>状态</th>
            <th>预警</th>
            <th>AI信息</th>
            <th>负责人</th>
            <th>操作</th>
          </tr>
          </thead>
          <tbody id="planTbody"></tbody>
        </table>
      </div>
    </section>

    <section class="section panel fulfillment-table-panel">
      <div class="panel-head">
        <div><p class="eyebrow">交付物</p><h2>交付物逐项确认</h2></div>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
          <tr>
            <th>合同</th>
            <th>交付物</th>
            <th>对应节点</th>
            <th>确认方式</th>
            <th>确认状态</th>
            <th>确认人</th>
            <th>备注</th>
          </tr>
          </thead>
          <tbody id="deliverableTbody"></tbody>
        </table>
      </div>
    </section>

    <section class="section panel fulfillment-table-panel">
      <div class="panel-head">
        <div><p class="eyebrow">付款台账</p><h2>付款计划、到账确认与责任提示</h2></div>
        <div class="panel-action-group">
          <div id="paymentRatioSummary" class="payment-ratio-summary">付款比例合计：0%</div>
          <button id="newPaymentPlanBtn" class="page-action-btn" type="button">
            <i data-lucide="plus"></i>
            新增付款计划
          </button>
        </div>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
          <tr>
            <th>付款阶段</th>
            <th>比例 / 金额</th>
            <th>到期日期</th>
            <th>前置交付</th>
            <th>到账情况</th>
            <th>违约金额</th>
            <th>责任提示</th>
            <th>操作</th>
          </tr>
          </thead>
          <tbody id="paymentPlanTbody"></tbody>
        </table>
      </div>
    </section>

    <section class="section panel reminder-table-panel">
      <div class="panel-head">
        <div><p class="eyebrow">付款记录</p><h2>到账确认记录</h2></div>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
          <tr>
            <th>合同</th>
            <th>付款阶段</th>
            <th>到账金额</th>
            <th>到账日期</th>
            <th>付款方</th>
            <th>收款方</th>
            <th>备注</th>
            <th>操作</th>
          </tr>
          </thead>
          <tbody id="paymentRecordTbody"></tbody>
        </table>
      </div>
    </section>

    <section class="section panel reminder-table-panel">
      <div class="panel-head">
        <div><p class="eyebrow">付款资料</p><h2>发票材料记录</h2></div>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
          <tr>
            <th>合同</th>
            <th>付款阶段</th>
            <th>发票号码</th>
            <th>发票金额</th>
            <th>开票日期</th>
            <th>状态</th>
            <th>附件</th>
            <th>备注</th>
            <th>操作</th>
          </tr>
          </thead>
          <tbody id="invoiceTbody"></tbody>
        </table>
      </div>
    </section>

    <section class="section panel reminder-table-panel">
      <div class="panel-head">
        <div><p class="eyebrow">提醒日志</p><h2>预警推送日志</h2></div>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
          <tr>
            <th>合同</th>
            <th>节点</th>
            <th>等级</th>
            <th>接收人</th>
            <th>渠道</th>
            <th>发送时间</th>
            <th>内容</th>
          </tr>
          </thead>
          <tbody id="reminderTbody"></tbody>
        </table>
      </div>
    </section>

    <!-- Plan Modal -->
    <div id="planModal" class="modal" hidden>
      <div class="modal-card">
        <div class="modal-head">
          <h3 id="planModalTitle">新增履约节点</h3>
          <button type="button" id="closePlanModal" class="secondary">关闭</button>
        </div>
        <form id="planForm" class="form-grid">
          <input type="hidden" id="planId">
          <label>
            合同
            <select id="planContract" required></select>
          </label>
          <label>
            节点类型
            <select id="planType">
              <option value="PREPARE">材料准备</option>
              <option value="CHECK">进度确认</option>
              <option value="ACCEPTANCE">验收交付</option>
              <option value="PAYMENT">付款节点</option>
              <option value="DELIVERY">交付节点</option>
              <option value="WARRANTY">质保节点</option>
              <option value="RENEWAL">续签节点</option>
              <option value="TERMINATION">终止节点</option>
              <option value="INVOICE">发票节点</option>
              <option value="CONFIDENTIALITY">保密期限</option>
              <option value="OTHER">其他</option>
            </select>
          </label>
          <label>
            节点名称
            <input id="nodeName" required placeholder="请输入履约节点名称">
          </label>
          <label>
            计划日期
            <input id="dueDate" type="date">
          </label>
          <label>
            状态
            <select id="planStatus">
              <option value="NOT_STARTED">待开始</option>
              <option value="PENDING_CONFIRM">待人工确认</option>
              <option value="IN_PROGRESS">进行中</option>
              <option value="COMPLETED">已完成</option>
              <option value="OVERDUE">已逾期</option>
              <option value="CLOSED">已关闭</option>
            </select>
          </label>
          <label>
            实际完成日期
            <input id="actualCompletedDate" type="date">
          </label>
          <label>
            负责人
            <input id="ownerName" placeholder="默认当前用户">
          </label>
          <label class="wide">
            完成进度
            <div class="progress-input-row">
              <input id="progressRange" type="range" min="0" max="100" step="5">
              <input id="progressValue" type="number" min="0" max="100" step="5">
            </div>
          </label>
          <label class="wide">
            备注
            <textarea id="planRemark" rows="2" placeholder="可填写处理说明"></textarea>
          </label>
          <div class="button-row wide">
            <button type="submit">
              <i data-lucide="circle-check"></i>
              保存
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- Voucher Modal -->
    <div id="voucherModal" class="modal" hidden>
      <div class="modal-card wide-modal">
        <div class="modal-head">
          <h3 id="voucherModalTitle">履约凭证与进度日志</h3>
          <button type="button" id="closeVoucherModal" class="secondary">关闭</button>
        </div>
        <form id="voucherForm" class="form-grid">
          <input type="hidden" id="voucherPlanId">
          <label>
            凭证类型
            <select id="voucherType">
              <option value="PROGRESS">进度凭证</option>
              <option value="COMPLETION">完成凭证</option>
              <option value="EXCEPTION">异常说明</option>
              <option value="PAYMENT">付款凭证</option>
            </select>
          </label>
          <label>
            上传文件
            <input id="voucherFile" type="file" accept=".pdf,.jpg,.jpeg,.png,.doc,.docx,.xls,.xlsx">
          </label>
          <label class="wide">
            凭证备注
            <textarea id="voucherRemark" rows="2" placeholder="可填写凭证说明、异常原因或复核意见"></textarea>
          </label>
          <div class="button-row wide">
            <button type="submit">
              <i data-lucide="upload"></i>
              上传凭证
            </button>
          </div>
        </form>
        <div class="detail-grid">
          <section>
            <h4>凭证记录</h4>
            <div class="table-wrap compact-table">
              <table>
                <thead>
                <tr>
                  <th>文件</th>
                  <th>类型</th>
                  <th>复核</th>
                  <th>上传人</th>
                  <th>操作</th>
                </tr>
                </thead>
                <tbody id="voucherTbody"></tbody>
              </table>
            </div>
          </section>
          <section>
            <h4>进度日志</h4>
            <div class="table-wrap compact-table">
              <table>
                <thead>
                <tr>
                  <th>操作</th>
                  <th>状态变化</th>
                  <th>操作人</th>
                  <th>时间</th>
                  <th>备注</th>
                </tr>
                </thead>
                <tbody id="progressLogTbody"></tbody>
              </table>
            </div>
          </section>
        </div>
      </div>
    </div>

    <!-- Overdue Modal -->
    <div id="overdueModal" class="modal" hidden>
      <div class="modal-card">
        <div class="modal-head">
          <h3 id="overdueModalTitle">逾期处置</h3>
          <button type="button" id="closeOverdueModal" class="secondary">关闭</button>
        </div>
        <form id="overdueForm" class="form-grid">
          <input type="hidden" id="overduePlanId">
          <label>
            处置方式
            <select id="overdueAction">
              <option value="COMPLETE">标记完成</option>
              <option value="DELAY">申请延期</option>
            </select>
          </label>
          <label class="overdue-complete-field">
            实际完成日期
            <input id="overdueActualCompletedDate" type="date">
          </label>
          <label class="overdue-delay-field" hidden>
            新计划日期
            <input id="overdueNewPlannedDate" type="date">
          </label>
          <label class="wide overdue-delay-field" hidden>
            延期原因
            <textarea id="overdueDelayReason" rows="2" placeholder="请填写延期原因，提交后等待部门主管审批"></textarea>
          </label>
          <label class="wide">
            处置说明
            <textarea id="overdueDisposalRemark" rows="3" placeholder="请填写本次逾期处置说明"></textarea>
          </label>
          <p class="field-hint wide" id="overdueHandleHint">
            标记完成前请先在"凭证/日志"中上传完成凭证；付款节点需上传付款凭证并复核通过。
          </p>
          <div class="button-row wide">
            <button type="submit">
              <i data-lucide="circle-check"></i>
              提交处置
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- Payment Plan Modal -->
    <div id="paymentPlanModal" class="modal" hidden>
      <div class="modal-card">
        <div class="modal-head">
          <h3 id="paymentPlanModalTitle">新增付款计划</h3>
          <button type="button" id="closePaymentPlanModal" class="secondary">关闭</button>
        </div>
        <form id="paymentPlanForm" class="form-grid">
          <input type="hidden" id="paymentPlanId">
          <label>
            合同
            <select id="paymentContract" required></select>
          </label>
          <label>
            付款阶段
            <input id="phaseName" required placeholder="如：首期款 30%">
          </label>
          <label>
            付款比例（%）
            <input id="percentage" type="number" min="0" max="100" step="0.01" required>
          </label>
          <div id="paymentRatioCheck" class="ratio-check wide" aria-live="polite"></div>
          <label>
            计划金额
            <input id="plannedAmount" type="number" min="0" step="0.01" required>
          </label>
          <label>
            到期日期
            <input id="paymentDueDate" type="date" required>
          </label>
          <label>
            付款对象
            <input id="paymentPayee" placeholder="默认合同相对方">
          </label>
          <label>
            付款条件类型
            <select id="paymentConditionType">
              <option value="NONE">无前置条件</option>
              <option value="DELIVERABLE">交付确认后付款</option>
              <option value="ACCEPTANCE">验收确认后付款</option>
              <option value="INVOICE">发票齐全后付款</option>
            </select>
          </label>
          <label>
            每日违约金比例（%）
            <input id="penaltyRate" type="number" min="0" step="0.0001" value="0.05">
          </label>
          <label class="wide">
            付款条件说明
            <textarea id="paymentCondition" rows="2" placeholder="如：验收确认并收到等额有效发票后付款"></textarea>
          </label>
          <label class="wide">
            前置交付物
            <select id="prerequisiteDelivery" class="multi-select" multiple size="4"></select>
            <small class="field-hint">可按住 Ctrl 多选，未选择时视为无前置交付要求</small>
          </label>
          <label class="wide">
            备注
            <textarea id="paymentRemark" rows="2" placeholder="可填写付款条件说明"></textarea>
          </label>
          <div class="button-row wide">
            <button type="submit">
              <i data-lucide="circle-check"></i>
              保存
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- Payment Record Modal -->
    <div id="paymentRecordModal" class="modal" hidden>
      <div class="modal-card">
        <div class="modal-head">
          <h3>登记到账</h3>
          <button type="button" id="closePaymentRecordModal" class="secondary">关闭</button>
        </div>
        <form id="paymentRecordForm" class="form-grid">
          <input type="hidden" id="recordPaymentPlanId">
          <label>
            到账金额
            <input id="paidAmount" type="number" min="0" step="0.01" required>
          </label>
          <label>
            到账日期
            <input id="paidDate" type="date" required>
          </label>
          <label>
            银行流水号
            <input id="bankSerialNo" placeholder="请输入银行流水号">
          </label>
          <label>
            付款经办人
            <input id="paymentHandler" placeholder="默认当前用户">
          </label>
          <label>
            付款方
            <input id="payer" placeholder="甲方">
          </label>
          <label>
            收款方
            <input id="receiver" placeholder="乙方">
          </label>
          <label class="wide">
            备注
            <textarea id="paymentRecordRemark" rows="2" placeholder="可填写到账凭证或说明"></textarea>
          </label>
          <label class="wide">
            付款凭证
            <input id="paymentVoucherFile" type="file" accept=".pdf,.jpg,.jpeg,.png,.doc,.docx,.xls,.xlsx">
          </label>
          <div class="button-row wide">
            <button type="submit">
              <i data-lucide="circle-check"></i>
              保存
            </button>
          </div>
        </form>
      </div>
    </div>

  </div>
</template>

<script setup>
import { nextTick, onMounted } from 'vue';
import { runLegacyPage } from '../legacy/pageLoader';

onMounted(async () => {
  await nextTick();
  await runLegacyPage('fulfillment');
});
</script>

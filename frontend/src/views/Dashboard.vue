<template>
  <div class="legacy-view">
    <section class="section dashboard-section">
        <div class="dashboard-hero" aria-label="合同全生命周期工作台">
            <div class="hero-copy">
                <p id="heroGreeting" class="hero-greeting">早上好，admin · 今日待办加载中</p>
                <p class="hero-clock" id="heroClock"></p>
            </div>
            <div class="hero-flow" aria-label="合同流转步骤">
                <a class="hero-step" data-flow-step="draft" href="edit.html">
                    <span class="step-dot"><i data-lucide="check"></i></span><span>编制</span>
                </a>
                <a class="hero-step" data-flow-step="risk" href="approval.html">
                    <span class="step-dot"></span><span>审查</span>
                </a>
                <a class="hero-step" data-flow-step="approval" href="approval.html">
                    <span class="step-dot"></span><span>审批</span>
                </a>
                <a class="hero-step" data-flow-step="seal" href="/seal">
                    <span class="step-dot"></span><span>签章</span>
                </a>
                <a class="hero-step" data-flow-step="fulfillment" href="fulfillment.html">
                    <span class="step-dot"></span><span>履约</span>
                </a>
            </div>
        </div>
        <div class="metric-grid">
            <a class="metric" href="/ledger"><i data-lucide="file-text"></i><span>合同总数</span><strong id="totalContracts" class="skeleton skeleton-pulse" aria-live="polite"></strong><small>台账与归档统计</small><div class="metric-trend" id="trendTotal"></div><span class="metric-go">查看台账 →</span></a>
            <a class="metric accent" href="/approval"><i data-lucide="clock"></i><span>待办审批</span><strong id="approvingContracts" class="skeleton skeleton-pulse" aria-live="polite"></strong><small>流程超时自动催办</small><div class="metric-trend" id="trendApproving"></div><span class="metric-go">前往审批 →</span></a>
            <a class="metric danger" href="/risk"><i data-lucide="shield-alert"></i><span>高风险</span><strong id="highRiskContracts" class="skeleton skeleton-pulse" aria-live="polite"></strong><small>未复核禁止提交</small><div class="metric-trend" id="trendRisk"></div><span class="metric-go">风险审查 →</span></a>
            <a class="metric warn" href="/fulfillment"><i data-lucide="refresh-cw"></i><span>30天内预警</span><strong id="dueSoonPlans" class="skeleton skeleton-pulse" aria-live="polite"></strong><small>30/7/1 天三级提醒</small><div class="metric-trend" id="trendDue"></div><span class="metric-go">履约预警 →</span></a>
        </div>
        <div class="dashboard-body">
            <div class="dash-left">
                <div class="panel chart-panel">
                    <div class="panel-head"><div><h2>合同趋势图</h2></div></div>
                    <div class="bar-chart" id="monthlyTrendChart"><div class="chart-loading"><span class="loading-spinner"></span></div></div>
                </div>
                <div class="charts-row">
                    <div class="panel chart-panel">
                        <div class="panel-head"><div><h2>合同类型分布</h2></div></div>
                        <div class="hbar-chart" id="typeDistChart"><div class="chart-loading"><span class="loading-spinner"></span></div></div>
                    </div>
                    <div class="panel chart-panel">
                        <div class="panel-head"><div><h2>合同状态分布</h2></div></div>
                        <div class="donut-chart-wrap" id="statusDistChart"><div class="chart-loading"><span class="loading-spinner"></span></div></div>
                    </div>
                </div>
            </div>
            <div class="dash-right">
                <div class="panel warn-panel warn-panel--full">
                    <div class="panel-head"><div><h2>到期预警</h2></div></div>
                    <div class="warn-list" id="expiryWarnings"></div>
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
  await runLegacyPage('dashboard');
});
</script>

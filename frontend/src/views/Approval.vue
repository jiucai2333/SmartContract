<template>
  <div class="legacy-view">
    <section class="section panel">
        <div class="panel-head"><div><p class="eyebrow">审批流程</p><h2>可视化审批流</h2></div></div>
        <div id="approvalList" class="flowline"></div>
    </section>
  </div>
</template>

<script setup>
import { nextTick, onMounted } from 'vue';
import { runLegacyPage } from '../legacy/pageLoader';

onMounted(async () => {
  await nextTick();
  await runLegacyPage('approval');
});
</script>

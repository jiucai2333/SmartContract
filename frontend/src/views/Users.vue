<template>
  <div class="legacy-view">
    <section class="section panel users-table-panel">
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>用户ID</th>
              <th>用户名</th>
              <th>当前角色</th>
              <th>部门</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody id="userTbody"></tbody>
        </table>
      </div>
      <div id="userPager" class="table-pagination"></div>
    </section>
  </div>
</template>

<script setup>
import { nextTick, onMounted } from 'vue';
import { runLegacyPage } from '../legacy/pageLoader';

onMounted(async () => {
  await nextTick();
  await runLegacyPage('users');
});
</script>

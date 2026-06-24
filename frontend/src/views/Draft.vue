<template>
  <div class="legacy-view">
    <div class="section draft-filter-row">
        <section class="panel draft-filter-panel">
            <div class="filters">
                <div class="search-field">
                    <i data-lucide="search"></i>
                    <input id="draftKeyword" placeholder="按合同编号、标题、相对方搜索">
                </div>
    
                <select id="draftType">
                    <option value="">全部类型</option>
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
    
                <button id="draftResetBtn" class="secondary">
                    <i data-lucide="refresh-cw"></i>
                    重置
                </button>
            </div>
        </section>
    
        <a class="primary-link page-action-btn" href="/edit">
            <i data-lucide="plus"></i>
            新建编制
        </a>
    </div>
    
    <section class="section panel draft-table-panel">
        <div class="table-wrap">
            <table>
                <thead>
                <tr>
                    <th>合同编号</th>
                    <th>合同名称</th>
                    <th>合同相对方</th>
                    <th>合同类型</th>
                    <th>合同金额</th>
                    <th>版本</th>
                    <th>最近保存</th>
                    <th>负责人</th>
                    <th>操作</th>
                </tr>
                </thead>
    
                <tbody id="draftRows"></tbody>
            </table>
        </div>
        <div id="draftPager" class="table-pagination"></div>
    </section>
  </div>
</template>

<script setup>
import { nextTick, onMounted } from 'vue';
import { runLegacyPage } from '../legacy/pageLoader';

onMounted(async () => {
  await nextTick();
  await runLegacyPage('draft');
});
</script>

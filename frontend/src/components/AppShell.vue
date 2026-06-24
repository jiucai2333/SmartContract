<template>
  <div class="watermark-container" id="wmContainer"></div>
  <aside class="sidebar">
    <div class="brand">
      <span class="brand-mark">合</span>
      <div><strong>智能合同管理</strong></div>
    </div>
    <nav id="mainNav"></nav>
    <div class="security-note">
      <strong>合规控制</strong>
      <span>Qwen 调用前脱敏，高风险未复核阻断提交，预览叠加登录人水印。</span>
    </div>
  </aside>
  <main class="app-shell">
    <header class="topbar">
      <div class="topbar-heading">
        <h1 class="topbar-title" id="pageTitle">智能合同管理</h1>
        <p class="topbar-subtitle" id="pageSubtitle">合同全生命周期管理</p>
      </div>
      <div class="topbar-right">
        <div class="user-chip" id="userChip"></div>
        <button id="logoutBtn" class="secondary" type="button">退出登录</button>
      </div>
    </header>
    <slot />
  </main>
  <div id="toast" class="toast"></div>
</template>

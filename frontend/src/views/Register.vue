<template>
  <div class="auth-page">
    <div class="auth-card">
        <div class="brand auth-brand">
            <span class="brand-mark">合</span>
            <div><strong>智能合同管理</strong></div>
        </div>
        <h1>注册</h1>
        <p class="auth-subtitle">创建账号后自动登录，默认角色为普通用户</p>
        <form id="registerForm" class="auth-form" action="javascript:void(0)" novalidate>
            <label>用户名<input name="username" required minlength="3" maxlength="50" autocomplete="username" placeholder="至少 3 个字符"></label>
            <label>密码<input name="password" type="password" required minlength="6" autocomplete="new-password" placeholder="至少 6 个字符"></label>
            <label>确认密码<input name="confirmPassword" type="password" required minlength="6" autocomplete="new-password" placeholder="再次输入密码"></label>
            <button type="button" id="registerBtn">注册并登录</button>
        </form>
        <p class="auth-footer">已有账号？<a href="/login">返回登录</a></p>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onMounted } from 'vue';
import { runLegacyPage } from '../legacy/pageLoader';

onMounted(async () => {
  await nextTick();
  await runLegacyPage('auth', 'initRegisterPage');
});
</script>

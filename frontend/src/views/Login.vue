<template>
  <div class="auth-page">
    <div class="auth-card">
        <div class="brand auth-brand">
            <span class="brand-mark">合</span>
            <div><strong>智能合同管理</strong></div>
        </div>
        <h1>登录</h1>
        <p class="auth-subtitle">使用账号登录后进入工作台</p>
        <form id="loginForm" class="auth-form" action="javascript:void(0)" novalidate>
            <label>用户名<input name="username" required autocomplete="username" placeholder="请输入用户名"></label>
            <label>密码<input name="password" type="password" required autocomplete="current-password" placeholder="请输入密码"></label>
            <button type="button" id="loginBtn">登录</button>
        </form>
        <p class="auth-footer">还没有账号？<a href="/register">立即注册</a></p>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onMounted } from 'vue';
import { runLegacyPage } from '../legacy/pageLoader';

onMounted(async () => {
  await nextTick();
  await runLegacyPage('auth', 'initLoginPage');
});
</script>

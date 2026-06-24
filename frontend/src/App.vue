<template>
  <RouterView v-if="$route.meta.public" :key="$route.fullPath" />
  <AppShell v-else>
    <RouterView :key="$route.fullPath" />
  </AppShell>
</template>

<script setup>
import AppShell from './components/AppShell.vue';
</script>

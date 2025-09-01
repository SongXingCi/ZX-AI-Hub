<template>
  <div id="app">
    <!-- 页面内容 -->
  </div>
</template>

<script lang="ts">
import { createApp } from 'vue';
import App from './App.vue';
import router from './router'; // 确保 router 已正确导出

createApp(App).use(router).mount('#app');
</script>
<script setup>
import AppFooter from './components/AppFooter.vue'
</script>

<template>
  <div class="app-frame">
    <router-view />
    <AppFooter />
  </div>
</template>

<style scoped>
.app-frame {
  min-height: 100vh;
  background: var(--vs-page);
}
</style>

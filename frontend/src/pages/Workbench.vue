<template>
  <!-- 首页按平台分流：App 外壳（APP + 手机浏览器）走移动端首页，桌面端保持原版 -->
  <MobileHome v-if="isAppShell" />
  <DesktopHome v-else />
</template>

<script setup>
import { usePlatform } from '@/composables/usePlatform'
import MobileHome from './workbench/MobileHome.vue'
import DesktopHome from './workbench/DesktopHome.vue'

const { isAppShell } = usePlatform()
</script>

<template>
  <!-- App 外壳下隐藏标题区（顶栏已显示页面名），仅保留操作按钮 -->
  <div v-if="!isMobile" class="page-header-comp">
    <div>
      <div class="page-kicker">SPARES LIFECYCLE MANAGEMENT SYSTEM</div>
      <h2 class="page-title"><slot /></h2>
      <div v-if="$slots.desc" class="page-desc"><slot name="desc" /></div>
    </div>
    <div v-if="$slots.actions" class="page-actions"><slot name="actions" /></div>
  </div>
  <div v-else-if="$slots.actions" class="page-header-actions">
    <slot name="actions" />
  </div>
</template>

<script setup>
// 通用页面标题组件
import { useMobile } from '@/composables/useMobile'

const { isMobile } = useMobile()
</script>

<style scoped lang="scss">
.page-header-comp {display:flex;align-items:flex-end;justify-content:space-between;gap:16px;margin-bottom:20px;padding-bottom:16px;border-bottom:1px solid rgba(148,163,184,.2)}
.page-kicker{font-size:10px;letter-spacing:1.6px;font-weight:700;color:#06b6d4;margin-bottom:5px}
.page-title{margin:0;font-size:22px;line-height:1.3;color:#10233f;letter-spacing:.2px}
.page-desc {
  color: #64748b;
  font-size: 13px;
  margin-top: 6px;
}
.page-actions{display:flex;gap:10px;align-items:center}
.page-header-actions{display:flex;gap:10px;align-items:center;margin-bottom:12px}
</style>

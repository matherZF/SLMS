<template>
  <Teleport to="body">
    <Transition name="notice-mask">
      <div v-if="visible" class="notice-mask" @click.self="close"></div>
    </Transition>
    <Transition name="notice-panel">
      <div v-if="visible" class="notice-panel" role="dialog" aria-label="消息通知">
        <header class="panel-head">
          <div class="head-title">
            消息通知
            <span v-if="unread > 0" class="head-badge">{{ unread > 99 ? '99+' : unread }} 条未读</span>
          </div>
          <div class="head-actions">
            <span v-if="unread > 0" class="head-readall" @click="emit('read-all')">全部已读</span>
            <el-icon class="head-close" @click="close"><Close /></el-icon>
          </div>
        </header>

        <div class="panel-body">
          <div v-if="loading" class="panel-empty">
            <el-icon class="spin"><Loading /></el-icon>
            <p>加载中…</p>
          </div>

          <div v-else-if="!items.length" class="panel-empty">
            <el-icon class="empty-icon"><BellFilled /></el-icon>
            <p>暂无消息</p>
            <p class="empty-hint">备件预警、待审批会在这里提醒你</p>
          </div>

          <ul v-else class="notice-list">
            <li v-for="m in items" :key="m.category + '-' + m.targetId"
                class="notice-item" :class="{ 'is-read': m.read }" @click="emit('item-click', m)">
              <div class="np-icon-wrap">
                <div class="np-icon" :class="'icon-' + m.category">
                  <el-icon><component :is="iconFor(m.category)" /></el-icon>
                </div>
                <!-- 未读红点：点了就是已读，红点消失但消息留在列表里 -->
                <span v-if="!m.read" class="np-dot"></span>
              </div>
              <div class="np-body">
                <div class="np-row-1">
                  <span class="np-ttl">{{ m.title }}</span>
                  <span v-if="m.severity === 'HIGH'" class="np-hot">急</span>
                  <span class="np-time">{{ fmtTime(m.createdAt) }}</span>
                </div>
                <div class="np-sub">{{ m.subtitle }}</div>
                <div class="np-msg">{{ m.message }}</div>
              </div>
              <el-icon class="np-arrow"><ArrowRight /></el-icon>
            </li>
          </ul>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ArrowRight, BellFilled, Close, Loading } from '@element-plus/icons-vue'

/**
 * 移动端顶栏铃铛的消息通知弹层（从顶部下拉的通知中心，底部圆角）。
 * 只负责展示：未读条目标红点、已读条目仍留在列表里只是变淡；
 * 数据由 AppLayout 拉取，点条目 / 全部已读由父级处理已读与跳转。
 */
defineProps({
  visible: { type: Boolean, default: false },
  items: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  unread: { type: Number, default: 0 }
})

const emit = defineEmits(['update:visible', 'item-click', 'read-all'])

function close() { emit('update:visible', false) }

const ICONS = { ALERT: 'WarningFilled', APPROVAL: 'Stamp', PICKING: 'Box', STOCKTAKE: 'List' }
const iconFor = c => ICONS[c] || 'Bell'

function fmtTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const diff = Date.now() - d.getTime()
  if (diff < 60_000) return '刚刚'
  if (diff < 3600_000) return Math.floor(diff / 60_000) + ' 分钟前'
  if (diff < 86400_000) return Math.floor(diff / 3600_000) + ' 小时前'
  const p = n => String(n).padStart(2, '0')
  if (d.getFullYear() === new Date().getFullYear()) return `${p(d.getMonth() + 1)}/${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
</script>

<style scoped lang="scss">
.notice-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 30, 60, 0.45);
  z-index: 250;
}
.notice-panel {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 260;
  background: #fff;
  border-radius: 0 0 18px 18px;
  box-shadow: 0 10px 30px rgba(20, 45, 100, 0.2);
  // 顶部下拉：把状态栏高度让出来，标题不被刘海/状态栏压住
  padding-top: calc(6px + var(--safe-area-inset-top, env(safe-area-inset-top, 0px)));
  display: flex;
  flex-direction: column;
  max-height: 74vh;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 18px 10px;
  background: linear-gradient(180deg, #f7faff, #ffffff);
  border-bottom: 1px solid #f1f5f9;
  flex-shrink: 0;
}
.head-title {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
  display: flex;
  align-items: center;
  gap: 8px;
}
.head-badge {
  font-size: 11px;
  font-weight: 600;
  color: #e34d59;
  background: #fdeaea;
  padding: 2px 8px;
  border-radius: 9px;
}
.head-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.head-readall {
  font-size: 12px;
  color: #2e6ef2;
  padding: 4px 8px;
  border-radius: 8px;
  background: #eef4ff;
  cursor: pointer;
  &:active { background: #e0ebff; }
}
.head-close {
  font-size: 20px;
  color: #8a97a8;
  cursor: pointer;
  padding: 4px;
  &:active { opacity: .7; }
}
.panel-body {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding: 10px 12px calc(12px + env(safe-area-inset-bottom, 0px));
  min-height: 140px;
}
.panel-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 36px 0 30px;
  color: #94a3b8;
  font-size: 14px;
  p { margin: 0; }
  .empty-icon { font-size: 42px; color: #d6dee9; margin-bottom: 10px; }
  .spin { font-size: 26px; color: #2e6ef2; animation: notice-spin .9s linear infinite; margin-bottom: 8px; }
  .empty-hint { font-size: 12px; color: #b6c0cd; margin-top: 4px; }
}
@keyframes notice-spin { to { transform: rotate(360deg); } }

.notice-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.notice-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 10px;
  background: #fff;
  border: 1px solid #eef2f7;
  border-radius: 12px;
  cursor: pointer;
  transition: background .15s;
  &:active { background: #eef4fc; }
  // 已读：底色回落、标题变淡，但消息仍然在列表里
  &.is-read {
    background: #fbfcfe;
    .np-ttl { font-weight: 500; color: #52627a; }
    .np-sub, .np-msg { color: #96a3b4; }
    .np-icon { opacity: .6; }
    .np-arrow { color: #dbe2ea; }
  }
}
// 图标右上角的未读红点
.np-icon-wrap {
  position: relative;
  flex-shrink: 0;
}
.np-dot {
  position: absolute;
  top: -3px;
  right: -3px;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #f5222d;
  border: 1.5px solid #fff;
  box-shadow: 0 0 0 1px rgba(245, 34, 45, .08);
}
// 分类图标：与原 popover 同一套渐变配色
.np-icon {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  font-size: 19px;
  &.icon-ALERT     { background: linear-gradient(135deg, #fef2f2, #fee2e2); color: #dc2626; }
  &.icon-APPROVAL  { background: linear-gradient(135deg, #fff7ed, #ffedd5); color: #c2410c; }
  &.icon-PICKING   { background: linear-gradient(135deg, #eff6ff, #dbeafe); color: #2563eb; }
  &.icon-STOCKTAKE { background: linear-gradient(135deg, #f0fdf4, #dcfce7); color: #16a34a; }
}
.np-body { flex: 1; min-width: 0; padding-top: 1px; }
.np-row-1 {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 3px;
}
.np-ttl {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.np-hot {
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 700;
  color: #fff;
  background: #e34d59;
  border-radius: 4px;
  padding: 1px 5px;
  line-height: 1.4;
}
.np-time {
  margin-left: auto;
  font-size: 11px;
  color: #a4b0bf;
  flex-shrink: 0;
}
.np-sub {
  font-size: 12px;
  color: #475569;
  margin-bottom: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.np-msg {
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-all;
}
.np-arrow {
  font-size: 15px;
  color: #cbd5e1;
  flex-shrink: 0;
  margin-top: 10px;
}
// ---------- 过渡：从顶部滑下 ----------
.notice-mask-enter-active, .notice-mask-leave-active { transition: opacity .22s ease; }
.notice-mask-enter-from, .notice-mask-leave-to { opacity: 0; }
.notice-panel-enter-active, .notice-panel-leave-active { transition: transform .28s cubic-bezier(.32,.72,.35,1); }
.notice-panel-enter-from, .notice-panel-leave-to { transform: translateY(-100%); }
</style>

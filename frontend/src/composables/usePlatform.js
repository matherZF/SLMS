import { computed, ref } from 'vue'

// 与 styles/variables.scss 的 $md 断点保持一致
const NARROW_BREAKPOINT = 768

// Capacitor 原生环境只判定一次（capacitor.js 在 index.html 中先于应用加载）
const isNative = !!(typeof window !== 'undefined' && window.Capacitor?.isNativePlatform?.())

const viewportWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 0)

if (typeof window !== 'undefined') {
  window.addEventListener('resize', () => {
    viewportWidth.value = window.innerWidth
  })
}

const isNarrow = computed(() => viewportWidth.value > 0 && viewportWidth.value < NARROW_BREAKPOINT)

/**
 * 平台判定：桌面 WEB / Android APP / 手机浏览器 三端共用两套外壳。
 * isAppShell = APP 原生环境 或 窄视口 → 使用 AppLayout，与手机浏览器视觉一致。
 * 外壳选择只在 App.vue 中做，页面内不要自行判断平台。
 */
export function usePlatform() {
  const isAppShell = computed(() => isNative || isNarrow.value)
  return { isNative, isNarrow, isAppShell }
}

import { usePlatform } from './usePlatform'

/**
 * 兼容旧接口：页面里已有大量 `const { isMobile } = useMobile()`。
 * 语义升级：isMobile = APP 原生环境 或 窄视口（< 768px）。
 * 这样 APP 内无论 WebView 视口多宽，页面一律使用移动端模板，与手机浏览器表现一致。
 */
export function useMobile() {
  const { isAppShell } = usePlatform()
  return { isMobile: isAppShell }
}

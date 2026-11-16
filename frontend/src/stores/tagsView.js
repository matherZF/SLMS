import { defineStore } from 'pinia'

/**
 * 桌面端页签导航 + 页面缓存
 * - views：已打开的页签（按 fullPath 区分，工作台固定）
 * - cachedNames：KeepAlive include 名单（组件名=路由名，script-setup 自动推断自文件名）
 *   关掉页签时若没有同组件名的其它页签，才从缓存名单移除——下次打开是全新页面
 */
export const useTagsViewStore = defineStore('tagsView', {
  state: () => ({
    views: [],
    cachedNames: []
  }),
  actions: {
    addView(route) {
      if (!route.meta?.title) return
      if (!this.views.some(v => v.path === route.fullPath)) {
        this.views.push({ path: route.fullPath, title: route.meta.title, name: route.name, affix: route.path === '/workbench' })
      }
      // hidden 路由（详情页等）不缓存：桌面端详情走抽屉，路由直达的场景每次都要求新实例
      if (route.name && !route.meta.hidden && !this.cachedNames.includes(route.name)) {
        this.cachedNames.push(route.name)
      }
    },
    /** 关页签：返回关闭后应跳转的相邻页签（可能为 null） */
    removeView(view) {
      const i = this.views.findIndex(v => v.path === view.path)
      if (i < 0) return null
      this.views.splice(i, 1)
      if (view.name && !this.views.some(v => v.name === view.name)) {
        this.cachedNames = this.cachedNames.filter(n => n !== view.name)
      }
      return this.views[i - 1] || this.views[i] || this.views[0] || null
    },
    clear() {
      this.views = []
      this.cachedNames = []
    }
  }
})

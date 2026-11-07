<template>
  <el-dialog
    :model-value="modelValue"
    title="出库扫码核验"
    :width="isMobile ? '94%' : '520px'"
    @close="close"
  >
    <el-alert type="info" :closable="false" show-icon>
      <template #title>
        请先扫描备件二维码，再扫描实际取货货位二维码。两项匹配后才能确认出库。
      </template>
    </el-alert>

    <div class="expected">
      <div>应取备件：<b>{{ item?.sku }} - {{ item?.name }}</b></div>
      <div>应取货位：<b>{{ item?.locationCode }}</b></div>
    </div>

    <div id="outbound-qr-reader" class="reader" :class="{ active: h5Active }" />
    <div v-if="h5Active" class="scan-hint">对准二维码，识别成功后自动填入（可随时点按钮切换目标）</div>

    <div class="scan-actions">
      <el-button
        :type="scanTarget === 'part' ? 'primary' : 'default'"
        @click="startScanner('part')"
      >扫描备件二维码</el-button>
      <el-button
        :type="scanTarget === 'location' ? 'primary' : 'default'"
        @click="startScanner('location')"
      >扫描货位二维码</el-button>
    </div>

    <el-form label-position="top" class="codes">
      <el-form-item label="备件二维码内容">
        <el-input v-model="partCode" placeholder="例如 PART:SP-003107" />
      </el-form-item>
      <el-form-item label="货位二维码内容">
        <el-input v-model="locationCode" placeholder="例如 LOC:B-01-02-08" />
      </el-form-item>
    </el-form>

    <el-alert v-if="cameraError" type="warning" :closable="false" :title="cameraError" />

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button
        type="success"
        :disabled="!partCode || !locationCode"
        :loading="loading"
        @click="confirm"
      >核验并确认</el-button>
    </template>
  </el-dialog>

  <!-- 原生全屏扫码浮层：插件把 WebView 提到相机预览层之上，这里画出来的就是唯一可见的 UI。
       没有它用户只能按手机返回键，而返回键会直接把应用退回桌面。 -->
  <Teleport to="body">
    <div v-if="nativeScanning" class="native-scan-ui">
      <button class="ns-back" type="button" @click="cancelNativeScan">
        <span class="ns-back-arrow">‹</span>
        <span>返回</span>
      </button>
      <div class="ns-tip">
        {{ scanTarget === 'location' ? '对准货位二维码' : '对准备件二维码' }}，识别成功自动返回
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Html5Qrcode, Html5QrcodeSupportedFormats } from 'html5-qrcode'
import { useMobile } from '@/composables/useMobile'

// Capacitor 原生扫码（可选，未装 Capacitor 时为 null）
// 两个坑，务必保持现状：
// 1) 不能在 <script setup> 顶层 await import —— 那会让组件变成异步 setup 组件，
//    没有 <Suspense> 包裹时 Vue 不会渲染，弹框就打不开了。改为在函数内懒加载。
// 2) 插件对象是 thenable（有 .then），绝不能从 async 函数里直接 return 它：
//    async 返回值会被 promise 同化，等于 await 插件对象 → 触发 proxy.then() →
//    桥里没有 then 这个方法，抛 "BarcodeScanner.then() is not implemented on android"。
//    所以统一返回一层普通包装对象 { plugin }，避免被当成 thenable 同化。
const nativeScannerRef = { plugin: null, loaded: false }

function ensureNativeScanner() {
  if (nativeScannerRef.loaded) return Promise.resolve(nativeScannerRef)
  return import('@capacitor-community/barcode-scanner')
    .then((mod) => {
      nativeScannerRef.plugin = mod?.BarcodeScanner || null
      nativeScannerRef.loaded = true
      return nativeScannerRef
    })
    .catch(() => {
      // H5 环境没有这个模块，正常
      nativeScannerRef.plugin = null
      nativeScannerRef.loaded = true
      return nativeScannerRef
    })
}

const props = defineProps({
  modelValue: Boolean,
  item: Object,
  loading: Boolean
})
const emit = defineEmits(['update:modelValue', 'confirm'])
const { isMobile } = useMobile()
const partCode = ref('')
const locationCode = ref('')
const scanTarget = ref('')
const cameraError = ref('')
// 是否正在用网页扫码（html5-qrcode）取景，用于撑开容器
const h5Active = ref(false)
// 是否正在原生全屏扫码（期间弹框自动隐藏，组件实例必须存活以便回填结果）
const nativeScanning = ref(false)
let scanner = null
// 原生扫码完成会自动把弹框重新打开，那一次"打开"不能清空刚扫到的内容
let skipResetOnOpen = false
// 扫码会话序号：防止上一次扫码迟到的结果覆盖本次输入
let scanRunSeq = 0
// 原生扫码期间压入的历史记录标记：让手机返回键先取消扫码，而不是直接退出应用
let scanGuardPushed = false
// 扫码结束后主动回退一格历史记录时，需要吞掉那次 popstate
let swallowPop = false

// 取消原生扫码：等价于"返回上一级"（回到核验弹框），也是手机返回键的处理入口
function cancelNativeScan() {
  const plugin = nativeScannerRef.plugin
  if (plugin?.stopScan) {
    // resolveScan: true —— 让 startScan 的 promise 立即以"无内容"结束
    plugin.stopScan({ resolveScan: true }).catch(() => {})
  }
}

function pushScanGuard() {
  try {
    history.pushState({ ...(history.state || {}), __wmsScan: true }, '', location.href)
    scanGuardPushed = true
  } catch (_) {
    scanGuardPushed = false
  }
}

// 扫码自然结束（识别成功/取消）后清掉守卫记录，避免之后按返回键"空跳"一次
function releaseScanGuard() {
  if (!scanGuardPushed) return
  scanGuardPushed = false
  if (history.state?.__wmsScan) {
    swallowPop = true
    try {
      history.back()
    } catch (_) {
      swallowPop = false
    }
  }
}

function onGlobalPopState() {
  if (swallowPop) {
    swallowPop = false
    return
  }
  if (nativeScanning.value) cancelNativeScan()
}

function onVisibilityChange() {
  // 切后台或锁屏时收起相机，否则 startScan 的 promise 可能永不 resolve
  if (document.hidden && nativeScanning.value) cancelNativeScan()
}

const isCapacitorNative = () =>
  typeof window !== 'undefined' &&
  window.Capacitor?.isNativePlatform?.() === true

watch(() => props.modelValue, async (visible) => {
  // 原生扫码期间弹框的隐藏/恢复由扫码流程自己控制，不做重置也不停止相机
  if (nativeScanning.value) return
  if (visible) {
    if (skipResetOnOpen) {
      skipResetOnOpen = false
      return
    }
    partCode.value = ''
    locationCode.value = ''
    cameraError.value = ''
    scanTarget.value = ''
  } else {
    await stopScanner()
  }
})

// 扫码结果统一回填：写入对应输入框 + 切换下一个扫描目标 + 与应取项做一致性提醒
function applyScanResult(target, rawText) {
  const text = (rawText || '').trim()
  if (!text) return
  const strip = (v, prefix) =>
    v.replace(new RegExp('^' + prefix + ':', 'i'), '').trim().toLowerCase()

  if (target === 'part') {
    partCode.value = text
    scanTarget.value = 'location'
    const expect = (props.item?.sku || '').trim()
    if (expect && strip(text, 'PART') !== expect.toLowerCase()) {
      ElMessage.warning(`扫到的内容与应取备件 ${expect} 不一致，请确认扫的是本单备件标签`)
    }
  } else {
    locationCode.value = text
    scanTarget.value = ''
    const expect = (props.item?.locationCode || '').trim()
    if (expect && strip(text, 'LOC') !== expect.toLowerCase()) {
      ElMessage.warning(`扫到的内容与应取货位 ${expect} 不一致，请确认扫的是该货位标签`)
    }
  }
}

// 原生全屏扫码：系统级解码器 + 硬件连续对焦，速度和成功率远高于 WebView 里的 JS 解码
// 返回 true = 已处理（成功或用户取消）；返回 false = 不可用，交给网页扫码兜底
async function runNativeScan(target, myRun) {
  const { plugin } = await ensureNativeScanner()
  if (!plugin) return false

  // force: true —— 首次使用时弹出系统相机权限申请
  const permission = await plugin.checkPermission({ force: true })
  if (!permission.granted) {
    cameraError.value = '未授予相机权限，请到系统设置中开启相机权限后重试'
    ElMessage.warning('未授予相机权限，已切换为网页扫码')
    return false
  }

  // 原生相机取景渲染在 WebView 背后：隐藏页面 + 让 WebView 透明，才能看到镜头画面
  nativeScanning.value = true
  document.body.classList.add('native-scanning')
  emit('update:modelValue', false)
  // 压一条同 URL 的历史记录：手机返回键先消费它（触发 popstate → 取消扫码），不会直接退出应用
  pushScanGuard()

  try {
    try {
      await plugin.hideBackground()
    } catch (_) {}
    const result = await plugin.startScan()
    if (myRun !== scanRunSeq) return true // 已有新的扫码会话，丢弃这次迟到的结果
    if (result?.hasContent && result.content) {
      applyScanResult(target, result.content)
      ElMessage.success('识别成功：' + result.content)
    }
  } finally {
    try {
      plugin.showBackground()
    } catch (_) {}
    document.body.classList.remove('native-scanning')
    nativeScanning.value = false
    // 标记这次"重新打开"是扫码流程触发的，watch 里不要再清空已回填的内容
    skipResetOnOpen = true
    emit('update:modelValue', true) // 恢复弹框并回填结果
    releaseScanGuard()
  }
  return true
}

// 取景框尺寸：跟随视频容器自适应，越大二维码在画面里占的像素越多，识别越快
function computeQrBox(viewfinderWidth, viewfinderHeight) {
  const size = Math.floor(Math.min(viewfinderWidth, viewfinderHeight) * 0.78)
  const side = Math.max(160, Math.min(size, 360))
  return { width: side, height: side }
}

// 网页扫码：把摄像头画面直接渲染在弹框里（浏览器环境 / 原生插件异常时兜底）
async function startWebScanner(target) {
  h5Active.value = true
  await nextTick() // 等容器撑开后再初始化，否则 html5-qrcode 拿不到尺寸
  scanner = new Html5Qrcode('outbound-qr-reader', {
    verbose: false,
    // 只识别 QR 码，避免在其它码制上浪费时间
    formatsToSupport: [Html5QrcodeSupportedFormats.QR_CODE],
    // 支持时用系统 BarcodeDetector（部分设备 WebView 提供，识别更快）
    experimentalFeatures: { useBarCodeDetectorIfSupported: true }
  })
  try {
    await scanner.start(
      { facingMode: 'environment' },
      {
        fps: 12,
        qrbox: computeQrBox,
        aspectRatio: 1.3333,
        // 后置摄像头不需要镜像识别，关掉可省一半解码开销
        disableFlip: true,
        videoConstraints: {
          facingMode: { ideal: 'environment' },
          // 这里只能走 JS 解码，分辨率太高会卡成幻灯片，720p 是清晰度与流畅度的平衡点
          width: { ideal: 1280 },
          height: { ideal: 720 }
        }
      },
      async (decodedText) => {
        await stopScanner()
        applyScanResult(target, decodedText)
        ElMessage.success('识别成功：' + decodedText)
      },
      () => {}
    )
    // 开启连续对焦：近距扫码时对不上焦是"扫半天扫不出"的主因
    try {
      const caps = scanner.getRunningTrackCapabilities?.()
      if (caps?.focusMode?.includes?.('continuous')) {
        await scanner.applyVideoConstraints({ advanced: [{ focusMode: 'continuous' }] })
      }
    } catch (_) {}
  } catch (e) {
    console.warn('[扫码] 网页扫码启动失败', e)
    throw e
  }
}

async function startScanner(target) {
  // 任何分支出错都必须给出可见提示，绝不能静默失败
  try {
    const myRun = ++scanRunSeq
    scanTarget.value = target
    cameraError.value = ''
    await stopScanner()
    h5Active.value = false

    // ① APP 内优先原生全屏扫码：WebView 不提供可用的 BarcodeDetector，
    //    网页扫码只能靠 JS 解码（卡顿、成功率低），原生才是正解
    if (isCapacitorNative()) {
      try {
        const done = await runNativeScan(target, myRun)
        if (done) return
      } catch (e) {
        console.warn('[扫码] 原生扫码异常，改用网页扫码', e)
        document.body.classList.remove('native-scanning')
        nativeScanning.value = false
        releaseScanGuard()
        emit('update:modelValue', true)
        cameraError.value = '原生扫码异常（' + (e?.message || String(e)) + '），已切换网页扫码'
        ElMessage.warning('原生扫码异常，已切换为网页扫码方式')
      }
    }

    // ② Android 原生桥接（历史兼容，自研壳）
    if (window.AndroidScanner?.scanQr) {
      window.AndroidScanner.scanQr(target)
      return
    }

    // ③ 网页扫码
    try {
      await startWebScanner(target)
    } catch (e) {
      cameraError.value = `无法打开摄像头（${e?.message || '相机不可用'}）。请确认相机权限，或直接手工输入二维码内容。`
      ElMessage.error('无法打开摄像头，可手工输入二维码内容')
      throw e
    }
  } catch (outer) {
    console.error('[扫码] 启动扫码异常', outer)
    h5Active.value = false
    document.body.classList.remove('native-scanning')
    nativeScanning.value = false
    releaseScanGuard()
    cameraError.value = '启动扫码失败：' + (outer?.message || String(outer))
    ElMessage.error('启动扫码失败：' + (outer?.message || String(outer)))
  }
}

function receiveNativeScan(event) {
  const { target, text } = event.detail || {}
  if (!text) return
  if (target === 'location') locationCode.value = text
  else partCode.value = text
  scanTarget.value = target === 'part' ? 'location' : ''
}

async function stopScanner() {
  if (nativeScanning.value) return // 原生扫码进行中，不要把刚启动的相机停掉
  if (scanner) {
    try {
      if (scanner.isScanning) await scanner.stop()
      await scanner.clear()
    } catch (_) {
      // 摄像头可能已被浏览器回收
    } finally {
      scanner = null
    }
  }
  h5Active.value = false
  // Capacitor 原生扫码不需要 Web 层资源释放
  try {
    await nativeScannerRef.plugin?.stopScan?.()
    nativeScannerRef.plugin?.showBackground?.()
  } catch (_) {}
}

async function close() {
  // 原生扫码期间的自动隐藏不是用户关闭，忽略，避免把相机停掉
  if (nativeScanning.value) return
  await stopScanner()
  emit('update:modelValue', false)
}

function confirm() {
  emit('confirm', {
    itemId: props.item.id,
    partCode: partCode.value.trim(),
    locationCode: locationCode.value.trim()
  })
}

onMounted(() => {
  window.addEventListener('native-qr-result', receiveNativeScan)
  // 常驻监听：手机返回键 / 切后台都从这里统一取消原生扫码
  window.addEventListener('popstate', onGlobalPopState)
  document.addEventListener('visibilitychange', onVisibilityChange)
})
onBeforeUnmount(() => {
  window.removeEventListener('native-qr-result', receiveNativeScan)
  window.removeEventListener('popstate', onGlobalPopState)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  stopScanner()
})
</script>

<style scoped lang="scss">
.expected { margin: 16px 0 10px; line-height: 1.9; }
.reader {
  width: 100%;
  min-height: 12px;
  overflow: hidden;
  border-radius: 8px;
  position: relative;
  // 取景中：撑开成取景框
  &.active {
    min-height: 200px;
    background: #000;
    margin: 10px 0 6px;
    display: flex;
    align-items: center;
    justify-content: center;
    // 尺寸交给 html5-qrcode 自己控制（它会按视频比例计算取景框遮罩位置），只做圆角
    :deep(video) {
      display: block;
      border-radius: 8px;
    }
    :deep(#qr-shaded-region) { border-radius: 8px; }
  }
}
.scan-hint {
  font-size: 12px;
  color: #8a97a8;
  text-align: center;
  margin-bottom: 8px;
}
.scan-actions { display: flex; gap: 10px; margin: 12px 0; }
.codes { margin-top: 14px; }
@media (max-width: 767px) {
  .scan-actions { flex-direction: column; }
  .scan-actions :deep(.el-button) { width: 100%; margin-left: 0; min-height: 44px; }
}
</style>

<style>
/* 原生全屏扫码期间：页面所有内容隐藏且透明，让 WebView 背后的相机取景露出来。
   只把 .app-layout 设 visibility:hidden 是不够的 —— body/#app/弹框层的底色
   依然会把相机画面盖住，这就是之前"全白"的原因。 */
html:has(body.native-scanning),
body.native-scanning {
  background: transparent !important;
}
body.native-scanning > * {
  visibility: hidden !important;
}
/* 扫码浮层必须例外：它是扫码过程中唯一能点的东西（返回按钮 + 提示） */
body.native-scanning > .native-scan-ui,
body.native-scanning > .native-scan-ui * {
  visibility: visible !important;
}
.native-scan-ui {
  position: fixed;
  inset: 0;
  z-index: 2147483000;
  pointer-events: none;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
}
.native-scan-ui .ns-back {
  position: absolute;
  left: 16px;
  top: calc(var(--safe-area-inset-top, env(safe-area-inset-top, 0px)) + 14px);
  display: inline-flex;
  align-items: center;
  gap: 2px;
  height: 40px;
  padding: 0 16px 0 10px;
  border: none;
  border-radius: 20px;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  font-size: 15px;
  line-height: 1;
  pointer-events: auto;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.native-scan-ui .ns-back:active { background: rgba(0, 0, 0, 0.72); }
.native-scan-ui .ns-back-arrow {
  font-size: 24px;
  line-height: 1;
  margin-top: -2px;
}
.native-scan-ui .ns-tip {
  position: absolute;
  left: 50%;
  bottom: calc(var(--safe-area-inset-bottom, env(safe-area-inset-bottom, 0px)) + 12%);
  transform: translateX(-50%);
  padding: 10px 18px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 14px;
  white-space: nowrap;
}
</style>

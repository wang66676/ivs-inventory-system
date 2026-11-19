import { createApp } from 'vue'
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import './styles/tables.css'
import './styles/helpTips.css'
import App from './App.vue'
import { router } from './router'
import { pinia } from './stores'
import { installGlobalTableColumnBehavior, resizableColumns } from './directives/resizableColumns'
import MoneyInput from './components/MoneyInput.vue'
import GlobalTable from './components/GlobalTable.vue'
import { installGlobalHelpTips } from './utils/helpTips'

// 部署更新后，旧页面可能引用已被替换的哈希资源。首次预加载失败时强制刷新一次。
const PRELOAD_RELOAD_KEY = 'ivs:preload-reload-at'
window.addEventListener('vite:preloadError', (event) => {
  event.preventDefault()
  const lastReload = Number(sessionStorage.getItem(PRELOAD_RELOAD_KEY) || '0')
  if (Date.now() - lastReload < 60000) return
  sessionStorage.setItem(PRELOAD_RELOAD_KEY, String(Date.now()))
  const url = new URL(window.location.href)
  url.searchParams.set('_refresh', Date.now().toString())
  window.location.replace(url.toString())
})

const app = createApp(App)
app.use(pinia).use(router).use(Antd)
// 覆盖 Ant Design Vue 的全局表格组件：所有模块统一 10/20/50 条分页。
app.component('ATable', GlobalTable)
app.component('MoneyInput', MoneyInput)
app.directive('resizable-columns', resizableColumns)
app.mount('#app')

installGlobalTableColumnBehavior()
installGlobalHelpTips()

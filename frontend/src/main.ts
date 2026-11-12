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
import { installGlobalHelpTips } from './utils/helpTips'

const app = createApp(App)
app
  .use(pinia)
  .use(router)
  .use(Antd)
  .component('MoneyInput', MoneyInput)
  .directive('resizable-columns', resizableColumns)
  .mount('#app')

installGlobalTableColumnBehavior()
installGlobalHelpTips()

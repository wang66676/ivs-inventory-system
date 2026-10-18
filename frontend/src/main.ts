import { createApp } from 'vue'
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import './styles/tables.css'
import App from './App.vue'
import { router } from './router'
import { pinia } from './stores'
import { installGlobalTableColumnBehavior, resizableColumns } from './directives/resizableColumns'

const app = createApp(App)
app
  .use(pinia)
  .use(router)
  .use(Antd)
  .directive('resizable-columns', resizableColumns)
  .mount('#app')

installGlobalTableColumnBehavior()

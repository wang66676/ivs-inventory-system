import MoneyInput from '../components/MoneyInput.vue'

declare module 'vue' {
  export interface GlobalComponents {
    MoneyInput: typeof MoneyInput
  }
}

export {}

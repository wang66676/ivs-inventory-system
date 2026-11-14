<template>
  <div class="money-input-wrap">
    <a-input-number v-bind="$attrs" :value="modelValue" @update:value="modelValue = $event" />
    <div v-if="uppercase" class="money-uppercase">人民币 {{ uppercase }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

defineOptions({ inheritAttrs: false })
const modelValue = defineModel<any>('value')

const uppercase = computed(() => {
  if (modelValue.value == null) return ''
  const value = Number(modelValue.value)
  return Number.isFinite(value) ? toChineseCurrency(value) : ''
})

function toChineseCurrency(value: number) {
  if (!Number.isFinite(value)) return ''
  if (value === 0) return '零元整'
  const negative = value < 0
  const absolute = Math.abs(value)
  let integer = Math.floor(absolute)
  let cents = Math.round((absolute - integer) * 100)
  if (cents >= 100) {
    integer += 1
    cents -= 100
  }
  const jiao = Math.floor(cents / 10)
  const fen = cents % 10
  let result = integer === 0 ? '零元' : `${integerToChinese(integer)}元`
  if (jiao === 0 && fen === 0) {
    result += '整'
  } else {
    if (jiao > 0) result += `${DIGITS[jiao]}角`
    if (fen > 0) result += `${integer > 0 && jiao === 0 ? '零' : ''}${DIGITS[fen]}分`
  }
  return `${negative ? '负' : ''}${result}`
}

const DIGITS = '零壹贰叁肆伍陆柒捌玖'
const SMALL_UNITS = ['', '拾', '佰', '仟']
const BIG_UNITS = ['', '万', '亿', '兆']

function integerToChinese(value: number) {
  if (value === 0) return '零'
  let remaining = value
  let groupIndex = 0
  let result = ''
  let needZero = false
  while (remaining > 0) {
    const group = remaining % 10000
    if (needZero && group > 0) result = `零${result}`
    if (group > 0) {
      result = `${groupToChinese(group)}${BIG_UNITS[groupIndex]}${result}`
      needZero = group < 1000
    }
    remaining = Math.floor(remaining / 10000)
    groupIndex += 1
  }
  return result.replace(/零+/g, '零').replace(/零$/g, '')
}

function groupToChinese(group: number) {
  let remaining = group
  let unitIndex = 0
  let result = ''
  let zero = true
  while (remaining > 0) {
    const digit = remaining % 10
    if (digit === 0) {
      if (!zero) {
        result = `零${result}`
        zero = true
      }
    } else {
      result = `${DIGITS[digit]}${SMALL_UNITS[unitIndex]}${result}`
      zero = false
    }
    remaining = Math.floor(remaining / 10)
    unitIndex += 1
  }
  return result.replace(/零+$/g, '')
}
</script>

<style scoped>
.money-input-wrap { width: 100%; }
.money-uppercase { margin-top: 2px; color: #8b96a8; font-size: 12px; line-height: 18px; }
</style>

const HELP_SELECTORS = [
  '.ant-alert',
  '.ant-form-item-extra',
  '.brand-subtitle',
  '.scope-hint',
  '.header-note',
  '.module-note',
  '.field-hint',
  '.form-hint',
  '.page-description',
  '.description-text',
  '.release-note',
].join(',')

function helpText(element: HTMLElement) {
  return (element.textContent || '').replace(/\s+/g, ' ').trim()
}

function canCollapse(element: HTMLElement) {
  if (element.classList.contains('ant-alert')
      && element.querySelector('button, input, textarea, select, a')) {
    return false
  }
  return Boolean(helpText(element))
}

function markHelpTip(element: HTMLElement) {
  if (!canCollapse(element)) return
  const text = helpText(element)
  if (!text) return
  element.classList.add('ivs-help-tip')
  element.dataset.ivsHelpText = text
  element.setAttribute('aria-label', text)
}

function scan(root: ParentNode) {
  if (root instanceof HTMLElement && root.matches(HELP_SELECTORS)) markHelpTip(root)
  root.querySelectorAll<HTMLElement>(HELP_SELECTORS).forEach(markHelpTip)
}

/** 全站说明文字统一折叠为 ❓，悬停时通过自定义浮层显示原文。 */
export function installGlobalHelpTips() {
  scan(document.body)
  const observer = new MutationObserver((mutations) => {
    for (const mutation of mutations) {
      if (mutation.type === 'characterData') {
        const element = mutation.target.parentElement?.closest<HTMLElement>(HELP_SELECTORS)
        if (element) markHelpTip(element)
        continue
      }
      for (const node of mutation.addedNodes) {
        if (node instanceof HTMLElement) scan(node)
      }
      const target = mutation.target instanceof HTMLElement
        ? mutation.target.closest<HTMLElement>(HELP_SELECTORS)
        : mutation.target.parentElement?.closest<HTMLElement>(HELP_SELECTORS)
      if (target) markHelpTip(target)
    }
  })
  observer.observe(document.body, { childList: true, characterData: true, subtree: true })
  return () => observer.disconnect()
}

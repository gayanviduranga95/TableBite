import { reactive } from 'vue'

const dialog = reactive({
  open: false, title: '', message: '', confirmText: 'Confirm', cancelText: 'Cancel',
  danger: false, input: false, inputLabel: '', inputValue: '', resolve: null,
})

/**
 * Promise-based confirmation dialog.
 *   if (await confirm({ title: 'Delete dish?', danger: true })) ...
 * With { input: true } it resolves to the typed text (or null when cancelled).
 */
export function useConfirm() {
  function confirm(options) {
    Object.assign(dialog, {
      title: 'Are you sure?', message: '', confirmText: 'Confirm', cancelText: 'Cancel',
      danger: false, input: false, inputLabel: '', inputValue: '', ...options, open: true,
    })
    return new Promise((resolve) => { dialog.resolve = resolve })
  }
  function close(result) {
    dialog.open = false
    const value = dialog.input ? (result ? dialog.inputValue : null) : result
    dialog.resolve?.(value)
    dialog.resolve = null
  }
  return { dialog, confirm, close }
}

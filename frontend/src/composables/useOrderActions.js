import { ref } from 'vue'
import { confirmPayment, updateOrderStatus } from '../services/orderService'
import { useConfirm } from './useConfirm'
import { useToast } from './useToast'
import { STATUS } from '../utils/status'

/** Status changes / payments with confirmation dialogs, toasts and a busy flag per order. */
export function useOrderActions(onChanged = () => {}) {
  const { confirm } = useConfirm()
  const toast = useToast()
  const busy = ref({})

  async function setStatus(order, status) {
    let reason
    if (status === 'CANCELLED') {
      reason = await confirm({
        title: order.status === 'PENDING' ? `Reject order ${order.orderNumber}?` : `Cancel order ${order.orderNumber}?`,
        message: 'The customer will be notified immediately.',
        confirmText: order.status === 'PENDING' ? 'Reject order' : 'Cancel order',
        cancelText: 'Keep order',
        danger: true,
        input: true,
        inputLabel: 'Reason shown to the customer (optional)',
      })
      if (reason === null) return null
    }
    busy.value = { ...busy.value, [order.id]: true }
    try {
      const updated = await updateOrderStatus(order.id, status, reason || undefined)
      toast.success(`${order.orderNumber} → ${STATUS[status].label}`)
      onChanged(updated)
      return updated
    } catch (exception) {
      toast.error(exception)
      return null
    } finally {
      busy.value = { ...busy.value, [order.id]: false }
    }
  }

  async function markPaid(order, method = 'CASH') {
    const reference = await confirm({
      title: `Confirm payment for ${order.orderNumber}`,
      message: `Record ${method === 'CARD' ? 'a card' : method === 'CASH' ? 'a cash' : 'a counter'} payment for the full amount.`,
      confirmText: 'Mark as paid',
      input: true,
      inputLabel: 'Receipt / reference (optional)',
    })
    if (reference === null) return null
    busy.value = { ...busy.value, [order.id]: true }
    try {
      const updated = await confirmPayment(order.id, method, reference || undefined)
      toast.success(`Payment recorded for ${order.orderNumber}`)
      onChanged(updated)
      return updated
    } catch (exception) {
      toast.error(exception)
      return null
    } finally {
      busy.value = { ...busy.value, [order.id]: false }
    }
  }

  return { busy, setStatus, markPaid }
}

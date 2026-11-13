// Order status metadata shared by every screen.
export const STATUS = {
  PENDING: { label: 'Pending', customer: 'Order received', description: 'Waiting for the kitchen to accept your order.', tone: 'amber' },
  CONFIRMED: { label: 'Confirmed', customer: 'Confirmed', description: 'The kitchen has accepted your order.', tone: 'sky' },
  PREPARING: { label: 'Preparing', customer: 'Being prepared', description: 'Our chefs are cooking your food right now.', tone: 'violet' },
  READY: { label: 'Ready', customer: 'Ready', description: 'Your food is ready and on its way.', tone: 'emerald' },
  SERVED: { label: 'Served', customer: 'Served', description: 'Enjoy your meal!', tone: 'teal' },
  COMPLETED: { label: 'Completed', customer: 'Completed', description: 'Thank you for dining with us.', tone: 'stone' },
  CANCELLED: { label: 'Cancelled', customer: 'Cancelled', description: 'This order was cancelled.', tone: 'red' },
}

export const TONE_CLASSES = {
  amber: 'bg-amber-50 text-amber-700 ring-amber-200',
  sky: 'bg-sky-50 text-sky-700 ring-sky-200',
  violet: 'bg-violet-50 text-violet-700 ring-violet-200',
  emerald: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  teal: 'bg-teal-50 text-teal-700 ring-teal-200',
  stone: 'bg-stone-100 text-stone-600 ring-stone-200',
  red: 'bg-red-50 text-red-700 ring-red-200',
}

export const DOT_CLASSES = {
  amber: 'bg-amber-500', sky: 'bg-sky-500', violet: 'bg-violet-500', emerald: 'bg-emerald-500',
  teal: 'bg-teal-500', stone: 'bg-stone-400', red: 'bg-red-500',
}

export const ACTIVE_STATUSES = ['PENDING', 'CONFIRMED', 'PREPARING', 'READY']

export function progressSteps(orderType) {
  return orderType === 'TAKEAWAY'
    ? ['PENDING', 'CONFIRMED', 'PREPARING', 'READY', 'COMPLETED']
    : ['PENDING', 'CONFIRMED', 'PREPARING', 'READY', 'SERVED']
}

/** Next actions staff can take from a status (kitchen / floor). */
export function nextActions(order, role) {
  const kitchen = ['ADMIN', 'KITCHEN', 'STAFF'].includes(role)
  const floor = ['ADMIN', 'WAITER', 'STAFF'].includes(role)
  const actions = []
  switch (order.status) {
    case 'PENDING':
      if (kitchen) actions.push({ status: 'CONFIRMED', label: 'Accept', style: 'btn-success' })
      actions.push({ status: 'CANCELLED', label: 'Reject', style: 'btn-light', confirm: true })
      break
    case 'CONFIRMED':
      if (kitchen) actions.push({ status: 'PREPARING', label: 'Start preparing', style: 'btn-primary' })
      actions.push({ status: 'CANCELLED', label: 'Cancel', style: 'btn-light', confirm: true })
      break
    case 'PREPARING':
      if (kitchen) actions.push({ status: 'READY', label: 'Mark ready', style: 'btn-success' })
      break
    case 'READY':
      if (floor && order.orderType !== 'TAKEAWAY') actions.push({ status: 'SERVED', label: 'Mark served', style: 'btn-primary' })
      if (floor && order.orderType === 'TAKEAWAY') actions.push({ status: 'COMPLETED', label: 'Picked up', style: 'btn-primary' })
      break
    case 'SERVED':
      if (floor) actions.push({ status: 'COMPLETED', label: 'Complete', style: 'btn-dark' })
      break
  }
  return actions
}

export const PAYMENT_LABELS = {
  CASH: 'Cash',
  PAY_AT_COUNTER: 'Pay at counter',
  CARD: 'Card',
  ONLINE: 'Online',
}

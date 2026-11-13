import { useSettings } from '../stores/settingsStore'

export function money(value) {
  const currency = useSettings().state.currency || 'Rs'
  const amount = Number(value || 0)
  return `${currency} ${amount.toLocaleString('en-LK', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

export function shortMoney(value) {
  const amount = Number(value || 0)
  if (amount >= 1_000_000) return `${(amount / 1_000_000).toFixed(1)}M`
  if (amount >= 10_000) return `${(amount / 1000).toFixed(1)}k`
  return amount.toLocaleString('en-LK', { maximumFractionDigits: 0 })
}

export function dateTime(value) {
  if (!value) return ''
  return new Date(value).toLocaleString('en-GB', {
    day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit',
  })
}

export function time(value) {
  if (!value) return ''
  return new Date(value).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })
}

export function minutesSince(value, now = Date.now()) {
  if (!value) return 0
  return Math.max(0, Math.floor((now - new Date(value).getTime()) / 60000))
}

export function timeAgo(value, now = Date.now()) {
  const minutes = minutesSince(value, now)
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes} min ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} h ago`
  return dateTime(value)
}

export function initials(name = '') {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0].toUpperCase()).join('') || 'TB'
}

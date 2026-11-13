// Browser (system) notifications for order updates, with graceful fallback to toasts.
export function notificationsSupported() {
  return typeof window !== 'undefined' && 'Notification' in window
}

export async function requestNotificationPermission() {
  if (!notificationsSupported() || Notification.permission !== 'default') return
  try { await Notification.requestPermission() } catch { /* ignored */ }
}

export function showSystemNotification(title, body) {
  if (!notificationsSupported() || Notification.permission !== 'granted' || document.visibilityState === 'visible') return
  try { new Notification(title, { body, icon: '/favicon.svg', tag: 'tablebite-order' }) } catch { /* mobile browsers may refuse */ }
}

/** Short beep for kitchen screens (no audio file needed). */
export function playChime() {
  try {
    const context = new (window.AudioContext || window.webkitAudioContext)()
    const notes = [880, 1320]
    notes.forEach((frequency, index) => {
      const oscillator = context.createOscillator()
      const gain = context.createGain()
      oscillator.type = 'sine'
      oscillator.frequency.value = frequency
      gain.gain.setValueAtTime(0.0001, context.currentTime + index * 0.18)
      gain.gain.exponentialRampToValueAtTime(0.25, context.currentTime + index * 0.18 + 0.02)
      gain.gain.exponentialRampToValueAtTime(0.0001, context.currentTime + index * 0.18 + 0.3)
      oscillator.connect(gain).connect(context.destination)
      oscillator.start(context.currentTime + index * 0.18)
      oscillator.stop(context.currentTime + index * 0.18 + 0.32)
    })
  } catch { /* audio blocked until user interaction */ }
}

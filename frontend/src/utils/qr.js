import QRCode from 'qrcode'

/**
 * Extracts a table token from scanned QR content. Accepts our table URLs
 * (https://host/table/<token>) or a bare token. Returns null for anything else.
 */
export function extractTableToken(text) {
  if (!text) return null
  const value = String(text).trim()
  try {
    const url = new URL(value)
    const match = url.pathname.match(/\/table\/([A-Za-z0-9_-]{6,128})\/?$/)
    return match ? decodeURIComponent(match[1]) : null
  } catch {
    return /^[A-Za-z0-9_-]{6,128}$/.test(value) ? value : null
  }
}

export function tableUrl(baseUrl, token) {
  return `${baseUrl.replace(/\/$/, '')}/table/${encodeURIComponent(token)}`
}

/** Plain QR code as a data URL. */
export function qrDataUrl(text, size = 512) {
  return QRCode.toDataURL(text, { width: size, margin: 1, errorCorrectionLevel: 'M', color: { dark: '#1c1410', light: '#ffffff' } })
}

/**
 * Branded, print-ready table card (PNG data URL): restaurant name, table number,
 * QR code and short instructions.
 */
export async function tableCardPng({ restaurant, tableNumber, url }) {
  const width = 1200
  const height = 1650
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')

  ctx.fillStyle = '#fffaf4'
  ctx.fillRect(0, 0, width, height)
  ctx.fillStyle = '#1c1410'
  roundRect(ctx, 60, 60, width - 120, 330, 48)
  ctx.fill()
  ctx.fillStyle = '#f26a17'
  ctx.font = '800 44px "Plus Jakarta Sans", Arial, sans-serif'
  ctx.textAlign = 'center'
  ctx.fillText('SCAN · ORDER · ENJOY', width / 2, 170)
  ctx.fillStyle = '#ffffff'
  ctx.font = '400 104px "DM Serif Display", Georgia, serif'
  ctx.fillText(restaurant, width / 2, 300)

  const qrImage = await loadImage(await QRCode.toDataURL(url, { width: 760, margin: 1, errorCorrectionLevel: 'H', color: { dark: '#1c1410', light: '#ffffff' } }))
  ctx.fillStyle = '#ffffff'
  roundRect(ctx, 200, 450, 800, 800, 40)
  ctx.fill()
  ctx.drawImage(qrImage, 220, 470, 760, 760)

  ctx.fillStyle = '#1c1410'
  ctx.font = '800 120px "Plus Jakarta Sans", Arial, sans-serif'
  ctx.fillText(`Table ${tableNumber}`, width / 2, 1410)
  ctx.fillStyle = '#84705f'
  ctx.font = '600 40px "Plus Jakarta Sans", Arial, sans-serif'
  ctx.fillText('Point your phone camera at the code to see the menu', width / 2, 1490)
  ctx.fillText('and order straight to the kitchen.', width / 2, 1545)
  return canvas.toDataURL('image/png')
}

function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + w, y, x + w, y + h, r)
  ctx.arcTo(x + w, y + h, x, y + h, r)
  ctx.arcTo(x, y + h, x, y, r)
  ctx.arcTo(x, y, x + w, y, r)
  ctx.closePath()
}

function loadImage(src) {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = reject
    image.src = src
  })
}

export function downloadDataUrl(dataUrl, fileName) {
  const link = document.createElement('a')
  link.href = dataUrl
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
}

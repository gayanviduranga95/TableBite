<script setup>
// Camera QR scanner. Uses the native BarcodeDetector when the browser has it and
// falls back to jsQR on canvas frames. Also supports decoding an uploaded photo,
// which works everywhere (including plain-http LAN access where cameras are blocked).
import { onBeforeUnmount, onMounted, ref } from 'vue'
import jsQR from 'jsqr'
import { Camera, CameraOff, ImagePlus, LoaderCircle, Zap } from 'lucide-vue-next'

const emit = defineEmits(['detected'])
const video = ref(null)
const fileInput = ref(null)
const state = ref('starting') // starting | scanning | denied | unsupported | insecure | error
const message = ref('')
const decoding = ref(false)
const torchOn = ref(false)
const torchAvailable = ref(false)

let stream = null
let frame = 0
let detector = null
let stopped = false
const canvas = document.createElement('canvas')
const context = canvas.getContext('2d', { willReadFrequently: true })

async function start() {
  stopped = false
  if (!window.isSecureContext) {
    state.value = 'insecure'
    return
  }
  if (!navigator.mediaDevices?.getUserMedia) {
    state.value = 'unsupported'
    return
  }
  state.value = 'starting'
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 }, height: { ideal: 720 } },
      audio: false,
    })
    if (stopped) { stop(); return }
    video.value.srcObject = stream
    await video.value.play()
    const track = stream.getVideoTracks()[0]
    torchAvailable.value = !!track?.getCapabilities?.().torch
    if ('BarcodeDetector' in window) {
      try {
        const formats = await window.BarcodeDetector.getSupportedFormats()
        if (formats.includes('qr_code')) detector = new window.BarcodeDetector({ formats: ['qr_code'] })
      } catch { detector = null }
    }
    state.value = 'scanning'
    loop()
  } catch (error) {
    state.value = error?.name === 'NotAllowedError' ? 'denied' : error?.name === 'NotFoundError' ? 'unsupported' : 'error'
    message.value = error?.message || ''
  }
}

let lastScan = 0
async function loop() {
  if (stopped || !video.value) return
  frame = requestAnimationFrame(loop)
  const now = performance.now()
  if (now - lastScan < 120 || video.value.readyState < 2) return
  lastScan = now
  const text = await decodeVideo()
  if (text) {
    if (navigator.vibrate) navigator.vibrate(60)
    emit('detected', text)
  }
}

async function decodeVideo() {
  const el = video.value
  if (!el) return null
  if (detector) {
    try {
      const codes = await detector.detect(el)
      if (codes.length) return codes[0].rawValue
      return null
    } catch { detector = null }
  }
  const width = el.videoWidth
  const height = el.videoHeight
  if (!width || !height) return null
  const scale = Math.min(1, 640 / Math.max(width, height))
  canvas.width = Math.round(width * scale)
  canvas.height = Math.round(height * scale)
  context.drawImage(el, 0, 0, canvas.width, canvas.height)
  const image = context.getImageData(0, 0, canvas.width, canvas.height)
  return jsQR(image.data, image.width, image.height, { inversionAttempts: 'dontInvert' })?.data || null
}

function stop() {
  stopped = true
  cancelAnimationFrame(frame)
  stream?.getTracks().forEach((track) => track.stop())
  stream = null
}

async function toggleTorch() {
  const track = stream?.getVideoTracks()[0]
  if (!track) return
  try {
    torchOn.value = !torchOn.value
    await track.applyConstraints({ advanced: [{ torch: torchOn.value }] })
  } catch { torchAvailable.value = false }
}

async function decodeFile(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  decoding.value = true
  try {
    const bitmap = await createImageBitmap(file)
    const scale = Math.min(1, 1600 / Math.max(bitmap.width, bitmap.height))
    canvas.width = Math.round(bitmap.width * scale)
    canvas.height = Math.round(bitmap.height * scale)
    context.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
    const image = context.getImageData(0, 0, canvas.width, canvas.height)
    const result = jsQR(image.data, image.width, image.height, { inversionAttempts: 'attemptBoth' })
    emit('detected', result?.data || '')
  } catch {
    emit('detected', '')
  } finally {
    decoding.value = false
  }
}

defineExpose({ start, stop })
onMounted(start)
onBeforeUnmount(stop)
</script>

<template>
  <div>
    <div class="relative mx-auto aspect-square w-full max-w-sm overflow-hidden rounded-[2rem] bg-ink-900 shadow-lift">
      <video ref="video" class="h-full w-full object-cover" playsinline muted autoplay />

      <!-- Viewfinder -->
      <div v-if="state === 'scanning'" class="pointer-events-none absolute inset-0">
        <div class="absolute inset-[14%] rounded-3xl shadow-[0_0_0_9999px_rgb(28_20_16/0.45)]" />
        <div class="absolute inset-[14%]">
          <span class="absolute top-0 left-0 h-10 w-10 rounded-tl-3xl border-t-4 border-l-4 border-brand-400" />
          <span class="absolute top-0 right-0 h-10 w-10 rounded-tr-3xl border-t-4 border-r-4 border-brand-400" />
          <span class="absolute bottom-0 left-0 h-10 w-10 rounded-bl-3xl border-b-4 border-l-4 border-brand-400" />
          <span class="absolute right-0 bottom-0 h-10 w-10 rounded-br-3xl border-r-4 border-b-4 border-brand-400" />
          <span class="absolute inset-x-3 h-0.5 animate-scan rounded-full bg-brand-400 shadow-[0_0_14px_2px_rgb(253_138_60/0.8)]" />
        </div>
        <p class="absolute inset-x-0 bottom-4 text-center text-xs font-bold text-white/90">Point at the QR code on your table</p>
      </div>
      <button v-if="state === 'scanning' && torchAvailable" class="absolute top-3 right-3 grid h-10 w-10 place-items-center rounded-full bg-white/15 text-white backdrop-blur" :class="{ 'bg-brand-500': torchOn }" aria-label="Toggle flashlight" @click="toggleTorch">
        <Zap class="h-5 w-5" />
      </button>

      <!-- Status overlays -->
      <div v-if="state !== 'scanning'" class="absolute inset-0 grid place-items-center p-8 text-center text-white">
        <div v-if="state === 'starting'" class="flex flex-col items-center gap-3">
          <LoaderCircle class="h-8 w-8 animate-spin text-brand-400" />
          <p class="text-sm font-semibold">Starting camera…</p>
        </div>
        <div v-else class="flex flex-col items-center gap-3">
          <span class="grid h-14 w-14 place-items-center rounded-2xl bg-white/10"><CameraOff class="h-7 w-7 text-brand-300" /></span>
          <p class="font-bold">
            {{ state === 'denied' ? 'Camera access was blocked'
              : state === 'insecure' ? 'Live camera needs a secure (https) connection'
              : state === 'unsupported' ? 'No camera available'
              : 'The camera could not start' }}
          </p>
          <p class="text-xs text-white/70">
            {{ state === 'denied' ? 'Allow camera access in your browser settings, or take a photo of the QR code instead.'
              : 'You can still take a photo of the QR code, or enter your table number below.' }}
          </p>
          <button v-if="state === 'denied' || state === 'error'" class="btn btn-sm mt-1 bg-white/15 text-white hover:bg-white/25" @click="start">
            <Camera class="h-4 w-4" /> Try again
          </button>
        </div>
      </div>
    </div>

    <div class="mt-4 flex justify-center">
      <button class="btn-light" :disabled="decoding" @click="fileInput.click()">
        <LoaderCircle v-if="decoding" class="h-4 w-4 animate-spin" />
        <ImagePlus v-else class="h-4 w-4" />
        {{ decoding ? 'Reading photo…' : 'Scan from a photo' }}
      </button>
      <input ref="fileInput" type="file" accept="image/*" capture="environment" class="hidden" @change="decodeFile">
    </div>
  </div>
</template>

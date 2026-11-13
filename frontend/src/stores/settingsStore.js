import { reactive } from 'vue'
import { getSettings } from '../services/orderService'

const state = reactive({
  restaurant_name: 'TableBite',
  tagline: 'Fresh food, served to your table',
  address: '',
  phone: '',
  opening_hours: '',
  currency: 'Rs',
  tax_rate: '0',
  service_charge_rate: '0',
  allow_manual_table_selection: 'true',
  allow_takeaway: 'true',
  public_base_url: '',
  online_payment_enabled: false,
  loaded: false,
})

export function useSettings() {
  async function load() {
    try {
      Object.assign(state, await getSettings(), { loaded: true })
    } catch {
      state.loaded = true // keep defaults; pages show their own errors
    }
  }
  return {
    state,
    load,
    get taxRate() { return Number(state.tax_rate) || 0 },
    get serviceRate() { return Number(state.service_charge_rate) || 0 },
    get manualTables() { return String(state.allow_manual_table_selection) === 'true' },
    get takeaway() { return String(state.allow_takeaway) === 'true' },
    /** Base URL printed inside table QR codes. */
    get qrBaseUrl() { return state.public_base_url || window.location.origin },
  }
}

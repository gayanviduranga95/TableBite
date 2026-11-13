import { useRouter } from 'vue-router'
import { getMenu } from '../services/orderService'
import { useOrderStore } from '../stores/orderStore'
import { useToast } from './useToast'

/** Adds every still-available dish from a past order to the cart. */
export function useReorder() {
  const router = useRouter()
  const cart = useOrderStore()
  const toast = useToast()

  return async function reorder(order) {
    try {
      const menu = await getMenu()
      const byId = new Map(menu.map((item) => [item.id, item]))
      let added = 0
      const skipped = []
      order.items.forEach((line) => {
        const dish = byId.get(line.menuItemId)
        if (dish && dish.available) {
          cart.add(dish, line.quantity, line.notes || undefined)
          added += 1
        } else {
          skipped.push(line.name)
        }
      })
      if (order.orderType === 'TAKEAWAY') cart.state.orderType = 'TAKEAWAY'
      if (!added) {
        toast.error('None of these dishes are available right now.')
        return
      }
      if (skipped.length) toast.info(`Not available right now: ${skipped.join(', ')}`, { title: 'Some items skipped' })
      else toast.success('Items added to your cart')
      router.push('/cart')
    } catch (exception) {
      toast.error(exception)
    }
  }
}

// Food image helpers: every dish must show an appropriate photo, never a broken image.
export const FALLBACK_IMAGE = '/images/food-fallback.svg'

const KEYWORD_IMAGES = [
  ['biryani', 'chicken-biryani'], ['fried rice', 'chicken-fried-rice'], ['rice', 'vegetable-fried-rice'],
  ['chicken burger', 'crispy-chicken-burger'], ['burger', 'classic-beef-burger'],
  ['pepperoni', 'pepperoni-pizza'], ['pizza', 'margherita-pizza'],
  ['ramen', 'spicy-ramen'], ['pad thai', 'prawn-pad-thai'], ['noodle', 'chicken-chow-mein'], ['kottu', 'chicken-chow-mein'],
  ['carbonara', 'spaghetti-carbonara'], ['spaghetti', 'spaghetti-bolognese'], ['pasta', 'penne-arrabbiata'],
  ['curry', 'sri-lankan-chicken-curry'], ['wing', 'hot-chicken-wings'], ['fries', 'classic-french-fries'],
  ['salad', 'caesar-salad'], ['bowl', 'avocado-power-bowl'], ['samosa', 'vegetable-samosa'], ['bread', 'garlic-bread'],
  ['cheesecake', 'blueberry-cheesecake'], ['brownie', 'brownie-a-la-mode'], ['cake', 'brownie-a-la-mode'],
  ['sundae', 'chocolate-sundae'], ['ice cream', 'chocolate-sundae'], ['pancake', 'berry-pancake-stack'],
  ['shake', 'strawberry-milkshake'], ['smoothie', 'mango-smoothie'], ['orange', 'fresh-orange-juice'],
  ['lime', 'fresh-lime-juice'], ['lemon', 'iced-lemon-tea'], ['juice', 'fresh-orange-juice'],
  ['latte', 'iced-caramel-latte'], ['coffee', 'cappuccino'], ['cappuccino', 'cappuccino'], ['tea', 'iced-lemon-tea'],
]

const CATEGORY_IMAGES = {
  burgers: 'classic-beef-burger', pizza: 'margherita-pizza', rice: 'chicken-fried-rice',
  'noodles & pasta': 'chicken-chow-mein', curries: 'sri-lankan-chicken-curry', salads: 'avocado-power-bowl',
  desserts: 'brownie-a-la-mode', drinks: 'fresh-lime-juice', starters: 'garlic-bread',
}

/** Best image for a dish: its own image, else a keyword/category match, else the fallback. */
export function dishImage(item) {
  if (!item) return FALLBACK_IMAGE
  if (item.imageUrl) return item.imageUrl
  const text = `${item.name || ''}`.toLowerCase()
  const match = KEYWORD_IMAGES.find(([keyword]) => text.includes(keyword))
  if (match) return `/images/menu/${match[1]}.jpg`
  const byCategory = CATEGORY_IMAGES[(item.category || '').toLowerCase()]
  return byCategory ? `/images/menu/${byCategory}.jpg` : FALLBACK_IMAGE
}

/** <img @error="onImageError"> replaces a broken image with the fallback once. */
export function onImageError(event) {
  const img = event.target
  if (img.dataset.fallback) return
  img.dataset.fallback = '1'
  img.src = FALLBACK_IMAGE
}

-- Scripts contain UTF-8 text (e.g. "à", "–"); make psql read them correctly on Windows.
SET client_encoding = 'UTF8';

-- =====================================================================
-- V4: Full demo menu, category icons, food images and extra tables.
-- Idempotent: items/categories/tables are only inserted when missing and
-- images are only assigned to items that do not have one yet.
-- =====================================================================

INSERT INTO categories (category_name, icon, sort_order) VALUES
    ('Starters', 'starter', 10),
    ('Burgers', 'burger', 20),
    ('Pizza', 'pizza', 30),
    ('Rice', 'rice', 40),
    ('Noodles & Pasta', 'noodles', 50),
    ('Curries', 'curry', 60),
    ('Salads', 'salad', 70),
    ('Desserts', 'dessert', 80),
    ('Drinks', 'drink', 90)
ON CONFLICT (category_name) DO NOTHING;

-- Give icons/order to categories that existed before this migration.
UPDATE categories SET icon = 'rice', sort_order = 40 WHERE category_name = 'Rice' AND icon IS NULL;
UPDATE categories SET icon = 'drink', sort_order = 90 WHERE category_name = 'Drinks' AND icon IS NULL;

-- Helper: insert a dish only when no dish with that name exists.
CREATE OR REPLACE FUNCTION tablebite_seed_item(
    p_name TEXT, p_desc TEXT, p_price NUMERIC, p_category TEXT, p_image TEXT,
    p_popular BOOLEAN, p_recommended BOOLEAN, p_veg BOOLEAN, p_spicy BOOLEAN,
    p_prep INTEGER, p_age_days INTEGER)
RETURNS VOID AS $$
BEGIN
    INSERT INTO menu_items (item_name, description, price, category_id, image_url,
                            is_popular, is_recommended, is_vegetarian, is_spicy,
                            prep_minutes, created_at)
    SELECT p_name, p_desc, p_price, c.category_id, p_image,
           p_popular, p_recommended, p_veg, p_spicy, p_prep,
           NOW() - make_interval(days => p_age_days)
    FROM categories c
    WHERE c.category_name = p_category
      AND NOT EXISTS (SELECT 1 FROM menu_items m WHERE LOWER(m.item_name) = LOWER(p_name));
END;
$$ LANGUAGE plpgsql;

SELECT tablebite_seed_item('Hot Chicken Wings', 'Crispy wings tossed in a smoky chilli glaze, served with cooling ranch dip.', 1250, 'Starters', '/images/menu/hot-chicken-wings.jpg', TRUE, FALSE, FALSE, TRUE, 15, 60);
SELECT tablebite_seed_item('Garlic Bread', 'Toasted baguette with roasted garlic butter, parsley and melted mozzarella.', 650, 'Starters', '/images/menu/garlic-bread.jpg', FALSE, FALSE, TRUE, FALSE, 8, 60);
SELECT tablebite_seed_item('Vegetable Samosa', 'Golden pastry parcels filled with spiced potato and peas, with mint chutney.', 600, 'Starters', '/images/menu/vegetable-samosa.jpg', FALSE, TRUE, TRUE, TRUE, 10, 3);
SELECT tablebite_seed_item('Loaded Cheese Fries', 'Skin-on fries smothered in cheddar sauce, crispy onions and spring onion.', 950, 'Starters', '/images/menu/loaded-cheese-fries.jpg', TRUE, FALSE, TRUE, FALSE, 10, 45);
SELECT tablebite_seed_item('Classic French Fries', 'Hand-cut fries, sea salt and our house ketchup.', 550, 'Starters', '/images/menu/classic-french-fries.jpg', FALSE, FALSE, TRUE, FALSE, 8, 90);

SELECT tablebite_seed_item('Classic Beef Burger', 'Grilled beef patty, cheddar, lettuce, tomato, pickles and house sauce on a brioche bun.', 1650, 'Burgers', '/images/menu/classic-beef-burger.jpg', TRUE, TRUE, FALSE, FALSE, 15, 90);
SELECT tablebite_seed_item('Double Cheese Burger', 'Two smashed beef patties, double cheddar, caramelised onions and burger sauce.', 1950, 'Burgers', '/images/menu/double-cheese-burger.jpg', TRUE, FALSE, FALSE, FALSE, 15, 30);
SELECT tablebite_seed_item('Crispy Chicken Burger', 'Buttermilk fried chicken, slaw and chipotle mayo in a toasted bun.', 1450, 'Burgers', '/images/menu/crispy-chicken-burger.jpg', FALSE, TRUE, FALSE, TRUE, 15, 5);
SELECT tablebite_seed_item('Smoky Bacon Burger', 'Beef patty stacked with streaky bacon, smoked cheese and BBQ glaze.', 1850, 'Burgers', '/images/menu/smoky-bacon-burger.jpg', FALSE, FALSE, FALSE, FALSE, 16, 2);

SELECT tablebite_seed_item('Margherita Pizza', 'Wood-fired base, San Marzano tomato, fresh mozzarella and basil.', 2200, 'Pizza', '/images/menu/margherita-pizza.jpg', TRUE, TRUE, TRUE, FALSE, 18, 120);
SELECT tablebite_seed_item('Pepperoni Pizza', 'Loaded with spicy pepperoni and a three-cheese blend.', 2600, 'Pizza', '/images/menu/pepperoni-pizza.jpg', TRUE, FALSE, FALSE, TRUE, 18, 120);
SELECT tablebite_seed_item('BBQ Chicken Pizza', 'Smoky BBQ sauce, roast chicken, red onion and peppers.', 2700, 'Pizza', '/images/menu/bbq-chicken-pizza.jpg', FALSE, TRUE, FALSE, FALSE, 20, 40);
SELECT tablebite_seed_item('Veggie Supreme Pizza', 'Mushrooms, olives, peppers, onions and sweetcorn on tomato and mozzarella.', 2300, 'Pizza', '/images/menu/veggie-supreme-pizza.jpg', FALSE, FALSE, TRUE, FALSE, 18, 4);

SELECT tablebite_seed_item('Chicken Fried Rice', 'Freshly prepared chicken fried rice', 950, 'Rice', '/images/menu/chicken-fried-rice.jpg', TRUE, FALSE, FALSE, FALSE, 12, 150);
SELECT tablebite_seed_item('Vegetable Fried Rice', 'Wok-fried basmati with seasonal vegetables, egg and spring onion.', 850, 'Rice', '/images/menu/vegetable-fried-rice.jpg', FALSE, FALSE, TRUE, FALSE, 12, 100);
SELECT tablebite_seed_item('Chicken Biryani', 'Fragrant dum-cooked basmati layered with spiced chicken, fried onions and raita.', 1600, 'Rice', '/images/menu/chicken-biryani.jpg', TRUE, TRUE, FALSE, TRUE, 20, 80);

SELECT tablebite_seed_item('Prawn Pad Thai', 'Rice noodles, tiger prawns, tamarind, peanuts, bean sprouts and lime.', 1750, 'Noodles & Pasta', '/images/menu/prawn-pad-thai.jpg', FALSE, TRUE, FALSE, FALSE, 15, 6);
SELECT tablebite_seed_item('Spicy Ramen', 'Rich chilli broth, ramen noodles, soft egg, prawns and greens.', 1850, 'Noodles & Pasta', '/images/menu/spicy-ramen.jpg', TRUE, FALSE, FALSE, TRUE, 15, 1);
SELECT tablebite_seed_item('Chicken Chow Mein', 'Stir-fried egg noodles with chicken, cabbage, carrot and soy.', 1250, 'Noodles & Pasta', '/images/menu/chicken-chow-mein.jpg', FALSE, FALSE, FALSE, FALSE, 12, 70);
SELECT tablebite_seed_item('Spaghetti Carbonara', 'Creamy egg and parmesan sauce, crispy pancetta and black pepper.', 1750, 'Noodles & Pasta', '/images/menu/spaghetti-carbonara.jpg', FALSE, TRUE, FALSE, FALSE, 15, 70);
SELECT tablebite_seed_item('Spaghetti Bolognese', 'Slow-cooked beef ragù, parmesan and fresh basil.', 1650, 'Noodles & Pasta', '/images/menu/spaghetti-bolognese.jpg', FALSE, FALSE, FALSE, FALSE, 15, 70);
SELECT tablebite_seed_item('Penne Arrabbiata', 'Penne in a fiery tomato, garlic and chilli sauce with pecorino.', 1450, 'Noodles & Pasta', '/images/menu/penne-arrabbiata.jpg', FALSE, FALSE, TRUE, TRUE, 14, 70);

SELECT tablebite_seed_item('Sri Lankan Chicken Curry', 'Village-style chicken curry with roasted spices, served with rice and sambol.', 1350, 'Curries', '/images/menu/sri-lankan-chicken-curry.jpg', TRUE, TRUE, FALSE, TRUE, 18, 110);
SELECT tablebite_seed_item('Butter Chicken & Naan', 'Tandoori chicken in a velvety tomato-butter sauce with garlic naan.', 1750, 'Curries', '/images/menu/butter-chicken-naan.jpg', FALSE, TRUE, FALSE, FALSE, 18, 50);

SELECT tablebite_seed_item('Caesar Salad', 'Crisp romaine, parmesan, garlic croutons and creamy Caesar dressing.', 1200, 'Salads', '/images/menu/caesar-salad.jpg', FALSE, FALSE, FALSE, FALSE, 8, 100);
SELECT tablebite_seed_item('Avocado Power Bowl', 'Avocado, chickpeas, quinoa, roasted veg, cherry tomatoes and tahini.', 1400, 'Salads', '/images/menu/avocado-power-bowl.jpg', FALSE, TRUE, TRUE, FALSE, 8, 7);

SELECT tablebite_seed_item('Brownie à la Mode', 'Warm chocolate fudge brownie with vanilla ice cream and chocolate sauce.', 850, 'Desserts', '/images/menu/brownie-a-la-mode.jpg', TRUE, TRUE, TRUE, FALSE, 6, 60);
SELECT tablebite_seed_item('Blueberry Cheesecake', 'Baked vanilla cheesecake on a buttery biscuit base with blueberry compote.', 950, 'Desserts', '/images/menu/blueberry-cheesecake.jpg', FALSE, FALSE, TRUE, FALSE, 5, 60);
SELECT tablebite_seed_item('Chocolate Sundae', 'Chocolate ice cream, brownie bits, whipped cream and hot fudge.', 750, 'Desserts', '/images/menu/chocolate-sundae.jpg', FALSE, FALSE, TRUE, FALSE, 5, 2);
SELECT tablebite_seed_item('Berry Pancake Stack', 'Fluffy buttermilk pancakes with fresh berries, banana and maple syrup.', 900, 'Desserts', '/images/menu/berry-pancake-stack.jpg', FALSE, TRUE, TRUE, FALSE, 10, 40);

SELECT tablebite_seed_item('Lime Juice', 'Fresh lime juice', 350, 'Drinks', '/images/menu/fresh-lime-juice.jpg', TRUE, FALSE, TRUE, FALSE, 3, 150);
SELECT tablebite_seed_item('Iced Lemon Tea', 'Ceylon black tea, fresh lemon and a touch of honey over ice.', 450, 'Drinks', '/images/menu/iced-lemon-tea.jpg', FALSE, FALSE, TRUE, FALSE, 3, 90);
SELECT tablebite_seed_item('Iced Caramel Latte', 'Double espresso, cold milk and salted caramel over ice.', 750, 'Drinks', '/images/menu/iced-caramel-latte.jpg', TRUE, FALSE, TRUE, FALSE, 4, 30);
SELECT tablebite_seed_item('Cappuccino', 'Rich espresso with velvety steamed milk foam.', 600, 'Drinks', '/images/menu/cappuccino.jpg', FALSE, FALSE, TRUE, FALSE, 4, 90);
SELECT tablebite_seed_item('Mango Smoothie', 'Ripe Alphonso mango blended with yoghurt and honey.', 700, 'Drinks', '/images/menu/mango-smoothie.jpg', FALSE, TRUE, TRUE, FALSE, 4, 3);
SELECT tablebite_seed_item('Strawberry Milkshake', 'Fresh strawberries, vanilla ice cream and cold milk.', 750, 'Drinks', '/images/menu/strawberry-milkshake.jpg', FALSE, FALSE, TRUE, FALSE, 4, 60);
SELECT tablebite_seed_item('Cookies & Cream Shake', 'Thick vanilla shake blended with chocolate cookies and whipped cream.', 850, 'Drinks', '/images/menu/cookies-cream-shake.jpg', TRUE, FALSE, TRUE, FALSE, 4, 6);
SELECT tablebite_seed_item('Fresh Orange Juice', 'Freshly squeezed oranges, nothing else.', 550, 'Drinks', '/images/menu/fresh-orange-juice.jpg', FALSE, FALSE, TRUE, FALSE, 3, 90);

DROP FUNCTION IF EXISTS tablebite_seed_item(TEXT, TEXT, NUMERIC, TEXT, TEXT, BOOLEAN, BOOLEAN, BOOLEAN, BOOLEAN, INTEGER, INTEGER);

-- ---------------------------------------------------------------------
-- Give every pre-existing dish without an image an appropriate photo.
-- (Exact names first, then keyword matching.)
-- ---------------------------------------------------------------------
UPDATE menu_items SET image_url = '/images/menu/chicken-fried-rice.jpg'
 WHERE image_url IS NULL AND item_name ILIKE '%fried rice%' AND item_name ILIKE '%chicken%';
UPDATE menu_items SET image_url = '/images/menu/vegetable-fried-rice.jpg'
 WHERE image_url IS NULL AND item_name ILIKE '%fried rice%';
UPDATE menu_items SET image_url = '/images/menu/fresh-lime-juice.jpg'
 WHERE image_url IS NULL AND (item_name ILIKE '%lime%' OR item_name ILIKE '%lemonade%');

WITH rules(pattern, image) AS (VALUES
    ('%biryani%', '/images/menu/chicken-biryani.jpg'),
    ('%rice%', '/images/menu/chicken-fried-rice.jpg'),
    ('%chicken burger%', '/images/menu/crispy-chicken-burger.jpg'),
    ('%cheese%burger%', '/images/menu/double-cheese-burger.jpg'),
    ('%burger%', '/images/menu/classic-beef-burger.jpg'),
    ('%pepperoni%', '/images/menu/pepperoni-pizza.jpg'),
    ('%chicken%pizza%', '/images/menu/bbq-chicken-pizza.jpg'),
    ('%pizza%', '/images/menu/margherita-pizza.jpg'),
    ('%kottu%', '/images/menu/chicken-chow-mein.jpg'),
    ('%noodle%', '/images/menu/chicken-chow-mein.jpg'),
    ('%ramen%', '/images/menu/spicy-ramen.jpg'),
    ('%pad thai%', '/images/menu/prawn-pad-thai.jpg'),
    ('%carbonara%', '/images/menu/spaghetti-carbonara.jpg'),
    ('%spaghetti%', '/images/menu/spaghetti-bolognese.jpg'),
    ('%pasta%', '/images/menu/penne-arrabbiata.jpg'),
    ('%curry%', '/images/menu/sri-lankan-chicken-curry.jpg'),
    ('%wing%', '/images/menu/hot-chicken-wings.jpg'),
    ('%fries%', '/images/menu/classic-french-fries.jpg'),
    ('%salad%', '/images/menu/caesar-salad.jpg'),
    ('%samosa%', '/images/menu/vegetable-samosa.jpg'),
    ('%bread%', '/images/menu/garlic-bread.jpg'),
    ('%cheesecake%', '/images/menu/blueberry-cheesecake.jpg'),
    ('%brownie%', '/images/menu/brownie-a-la-mode.jpg'),
    ('%cake%', '/images/menu/brownie-a-la-mode.jpg'),
    ('%ice cream%', '/images/menu/chocolate-sundae.jpg'),
    ('%sundae%', '/images/menu/chocolate-sundae.jpg'),
    ('%pancake%', '/images/menu/berry-pancake-stack.jpg'),
    ('%shake%', '/images/menu/strawberry-milkshake.jpg'),
    ('%smoothie%', '/images/menu/mango-smoothie.jpg'),
    ('%orange%', '/images/menu/fresh-orange-juice.jpg'),
    ('%juice%', '/images/menu/fresh-orange-juice.jpg'),
    ('%latte%', '/images/menu/iced-caramel-latte.jpg'),
    ('%coffee%', '/images/menu/cappuccino.jpg'),
    ('%cappuccino%', '/images/menu/cappuccino.jpg'),
    ('%tea%', '/images/menu/iced-lemon-tea.jpg')
)
UPDATE menu_items m SET image_url = (
    SELECT r.image FROM rules r WHERE m.item_name ILIKE r.pattern
    ORDER BY length(r.pattern) DESC LIMIT 1)
WHERE m.image_url IS NULL
  AND EXISTS (SELECT 1 FROM rules r WHERE m.item_name ILIKE r.pattern);

-- Anything still without a photo gets a category-appropriate one.
UPDATE menu_items m SET image_url = CASE c.category_name
        WHEN 'Burgers' THEN '/images/menu/classic-beef-burger.jpg'
        WHEN 'Pizza' THEN '/images/menu/margherita-pizza.jpg'
        WHEN 'Rice' THEN '/images/menu/chicken-fried-rice.jpg'
        WHEN 'Noodles & Pasta' THEN '/images/menu/chicken-chow-mein.jpg'
        WHEN 'Curries' THEN '/images/menu/sri-lankan-chicken-curry.jpg'
        WHEN 'Salads' THEN '/images/menu/avocado-power-bowl.jpg'
        WHEN 'Desserts' THEN '/images/menu/brownie-a-la-mode.jpg'
        WHEN 'Drinks' THEN '/images/menu/fresh-lime-juice.jpg'
        WHEN 'Starters' THEN '/images/menu/garlic-bread.jpg'
        ELSE NULL END
FROM categories c
WHERE m.category_id = c.category_id AND m.image_url IS NULL;

-- Mark the original seed dishes so they appear in the home page sections.
UPDATE menu_items SET is_popular = TRUE
 WHERE item_name IN ('Chicken Fried Rice', 'Lime Juice') AND is_popular = FALSE;

-- ---------------------------------------------------------------------
-- Extra demo tables with unguessable QR tokens.
-- ---------------------------------------------------------------------
INSERT INTO restaurant_tables (table_number, qr_token, seats, location)
SELECT v.num, 'tb-' || replace(gen_random_uuid()::text, '-', ''), v.seats, v.loc
FROM (VALUES ('T03', 4, 'Main hall'), ('T04', 4, 'Main hall'), ('T05', 6, 'Main hall'),
             ('T06', 2, 'Window'), ('T07', 2, 'Window'), ('T08', 8, 'Terrace')) AS v(num, seats, loc)
WHERE NOT EXISTS (SELECT 1 FROM restaurant_tables t WHERE t.table_number = v.num);

UPDATE restaurant_tables SET location = 'Main hall' WHERE location IS NULL;

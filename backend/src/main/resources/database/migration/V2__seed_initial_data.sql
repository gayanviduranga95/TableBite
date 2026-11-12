-- DEMO DATA. Replace these QR tokens in a deployment.
INSERT INTO restaurant_tables (table_number, qr_token)
VALUES ('T01', 'tablebite-demo-t01-8c9d4d1e'), ('T02', 'tablebite-demo-t02-6e4fb5a2')
ON CONFLICT (table_number) DO NOTHING;
INSERT INTO categories (category_name) VALUES ('Rice'), ('Drinks')
ON CONFLICT (category_name) DO NOTHING;
INSERT INTO menu_items (item_name, description, price, category_id)
SELECT 'Chicken Fried Rice', 'Freshly prepared chicken fried rice', 950,
       category_id FROM categories WHERE category_name = 'Rice'
AND NOT EXISTS (SELECT 1 FROM menu_items WHERE item_name = 'Chicken Fried Rice');
INSERT INTO menu_items (item_name, description, price, category_id)
SELECT 'Lime Juice', 'Fresh lime juice', 350,
       category_id FROM categories WHERE category_name = 'Drinks'
AND NOT EXISTS (SELECT 1 FROM menu_items WHERE item_name = 'Lime Juice');

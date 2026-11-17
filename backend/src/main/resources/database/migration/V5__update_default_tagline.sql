UPDATE restaurant_settings
SET setting_value = 'Restaurant menu and ordering', updated_at = NOW()
WHERE setting_key = 'tagline'
  AND setting_value = 'Fresh food, served to your table';
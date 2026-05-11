-- V2: Seed data for common Pakistani crops with prices

INSERT INTO catalog.crops (id, name_en, name_ur, category, unit) VALUES
    (gen_random_uuid(), 'Wheat', 'گندم', 'Grain', 'kg'),
    (gen_random_uuid(), 'Rice', 'چاول', 'Grain', 'kg'),
    (gen_random_uuid(), 'Corn', 'مکئی', 'Grain', 'kg'),
    (gen_random_uuid(), 'Cotton', 'کپاس', 'Cash Crop', 'kg'),
    (gen_random_uuid(), 'Sugarcane', 'گنّا', 'Cash Crop', 'ton'),
    (gen_random_uuid(), 'Mustard', 'سرسوں', 'Oilseed', 'kg'),
    (gen_random_uuid(), 'Chickpea', 'چنا', 'Pulse', 'kg'),
    (gen_random_uuid(), 'Lentil', 'مسور', 'Pulse', 'kg'),
    (gen_random_uuid(), 'Mango', 'آم', 'Fruit', 'kg'),
    (gen_random_uuid(), 'Potato', 'آلو', 'Vegetable', 'kg'),
    (gen_random_uuid(), 'Onion', 'پیاز', 'Vegetable', 'kg'),
    (gen_random_uuid(), 'Tomato', 'ٹماٹر', 'Vegetable', 'kg');

-- Seed prices for a few crops (using sub-select to get IDs)
INSERT INTO catalog.crop_prices (crop_id, market_name, min_price, max_price, avg_price, source, price_date)
SELECT id, 'Lahore Mandi', 2800, 3200, 3000, 'MANUAL', CURRENT_DATE FROM catalog.crops WHERE name_en = 'Wheat'
UNION ALL
SELECT id, 'Lahore Mandi', 3500, 4200, 3850, 'MANUAL', CURRENT_DATE FROM catalog.crops WHERE name_en = 'Rice'
UNION ALL
SELECT id, 'Lahore Mandi', 2200, 2600, 2400, 'MANUAL', CURRENT_DATE FROM catalog.crops WHERE name_en = 'Corn'
UNION ALL
SELECT id, 'Faisalabad Mandi', 8000, 9500, 8750, 'MANUAL', CURRENT_DATE FROM catalog.crops WHERE name_en = 'Cotton'
UNION ALL
SELECT id, 'Lahore Mandi', 40, 65, 52, 'MANUAL', CURRENT_DATE FROM catalog.crops WHERE name_en = 'Potato'
UNION ALL
SELECT id, 'Lahore Mandi', 80, 150, 115, 'MANUAL', CURRENT_DATE FROM catalog.crops WHERE name_en = 'Onion'
UNION ALL
SELECT id, 'Lahore Mandi', 60, 120, 90, 'MANUAL', CURRENT_DATE FROM catalog.crops WHERE name_en = 'Tomato';

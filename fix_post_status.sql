-- Fix posts cũ có status NULL
-- Chạy script này trong database

-- 1. Kiểm tra số lượng posts có status NULL
SELECT COUNT(*) as posts_with_null_status
FROM post 
WHERE status IS NULL;

-- 2. Xem chi tiết posts có status NULL
SELECT id, account_id, caption, visibility, status, created_at 
FROM post 
WHERE status IS NULL 
ORDER BY created_at DESC;

-- 3. Update tất cả posts có status NULL thành ACTIVE
UPDATE post 
SET status = 'ACTIVE' 
WHERE status IS NULL;

-- 4. Verify - Kiểm tra lại
SELECT COUNT(*) as posts_with_null_status_after_update
FROM post 
WHERE status IS NULL;

-- 5. Check posts của usr_002
SELECT id, caption, visibility, status, created_at 
FROM post 
WHERE account_id = 'usr_002' 
ORDER BY created_at DESC 
LIMIT 5;

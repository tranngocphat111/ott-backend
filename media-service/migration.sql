-- 3. Update ImageItem table (story images)
UPDATE image_items
SET url = REGEXP_REPLACE(url, '^https?://[^/]+/', '')
WHERE url LIKE 'http%';

COMMENT ON TABLE image_items IS 'Updated: url column now stores relative paths (e.g., stories/uuid.jpg)';

-- 4. Update VideoItem table (story videos)
UPDATE video_items
SET url = REGEXP_REPLACE(url, '^https?://[^/]+/', ''),
    thumbnail_url = REGEXP_REPLACE(thumbnail_url, '^https?://[^/]+/', '')
WHERE url LIKE 'http%' OR thumbnail_url LIKE 'http%';

COMMENT ON TABLE video_items IS 'Updated: url and thumbnail_url columns now store relative paths';

-- 5. Update Account table (user avatars & covers)
UPDATE accounts
SET avatar_url = REGEXP_REPLACE(avatar_url, '^https?://[^/]+/', ''),
    cover_url = REGEXP_REPLACE(cover_url, '^https?://[^/]+/', '')
WHERE avatar_url LIKE 'http%' OR cover_url LIKE 'http%';

COMMENT ON TABLE accounts IS 'Updated: avatar_url and cover_url columns now store relative paths';

-- 6. Update Music table (audio files)
UPDATE musics
SET audio_url = REGEXP_REPLACE(audio_url, '^https?://[^/]+/', '')
WHERE audio_url LIKE 'http%';

COMMENT ON TABLE musics IS 'Updated: audio_url column now stores relative paths (e.g., music/uuid.mp3)';

-- ========================================
-- VERIFICATION QUERIES
-- ========================================

-- Check if any full URLs remain (should return 0 for all)
SELECT
    'medias.url' as column_name,
    COUNT(*) as full_urls_remaining
FROM medias
WHERE url LIKE 'http%'

UNION ALL

SELECT
    'video_medias.thumbnail_url',
    COUNT(*)
FROM video_medias
WHERE thumbnail_url LIKE 'http%'

UNION ALL

SELECT
    'image_items.url',
    COUNT(*)
FROM image_items
WHERE url LIKE 'http%'

UNION ALL

SELECT
    'video_items.url',
    COUNT(*)
FROM video_items
WHERE url LIKE 'http%'

UNION ALL

SELECT
    'video_items.thumbnail_url',
    COUNT(*)
FROM video_items
WHERE thumbnail_url LIKE 'http%'

UNION ALL

SELECT
    'accounts.avatar_url',
    COUNT(*)
FROM accounts
WHERE avatar_url LIKE 'http%'

UNION ALL

SELECT
    'accounts.cover_url',
    COUNT(*)
FROM accounts
WHERE cover_url LIKE 'http%'

UNION ALL

SELECT
    'musics.audio_url',
    COUNT(*)
FROM musics
WHERE audio_url LIKE 'http%';

-- ========================================
-- Sample data check (verify relative paths)
-- ========================================

-- Check sample URLs from each table
SELECT 'medias' as table_name, url as sample_url FROM medias LIMIT 5;
SELECT 'video_medias' as table_name, thumbnail_url as sample_url FROM video_medias LIMIT 5;
SELECT 'image_items' as table_name, url as sample_url FROM image_items LIMIT 5;
SELECT 'video_items' as table_name, url as sample_url FROM video_items LIMIT 5;
SELECT 'accounts' as table_name, avatar_url as sample_url FROM accounts LIMIT 5;
SELECT 'musics' as table_name, audio_url as sample_url FROM musics LIMIT 5;

-- ========================================
-- ROLLBACK (if needed)
-- ========================================

/*
-- Restore from backup if something goes wrong
DROP TABLE medias;
CREATE TABLE medias AS SELECT * FROM medias_backup;

DROP TABLE video_medias;
CREATE TABLE video_medias AS SELECT * FROM video_medias_backup;

DROP TABLE image_items;
CREATE TABLE image_items AS SELECT * FROM image_items_backup;

DROP TABLE video_items;
CREATE TABLE video_items AS SELECT * FROM video_items_backup;

DROP TABLE accounts;
CREATE TABLE accounts AS SELECT * FROM accounts_backup;

DROP TABLE musics;
CREATE TABLE musics AS SELECT * FROM musics_backup;
*/

-- ========================================
-- SUCCESS CONFIRMATION
-- ========================================
SELECT 'Migration completed successfully!' as status;
-- ========================================
-- DATABASE MIGRATION SCRIPT
-- Convert Full URLs to Relative Paths
-- ========================================
--
-- Purpose: Migrate existing full URLs to relative paths
-- Run this AFTER deploying the new code with backward compatibility
--
-- IMPORTANT: Test on staging environment first!
-- ========================================

-- Step 1: Backup current data
-- CREATE TABLE medias_backup AS SELECT * FROM medias;
-- CREATE TABLE video_medias_backup AS SELECT * FROM video_medias;
-- CREATE TABLE image_items_backup AS SELECT * FROM image_items;
-- CREATE TABLE video_items_backup AS SELECT * FROM video_items;
-- CREATE TABLE accounts_backup AS SELECT * FROM accounts;
-- CREATE TABLE musics_backup AS SELECT * FROM musics;

-- ========================================
-- MIGRATION QUERIES
-- ========================================

-- 1. Update Media table (posts images/videos)
UPDATE medias
SET url = REGEXP_REPLACE(url, '^https?://[^/]+/', '')
WHERE url LIKE 'http%';

COMMENT ON TABLE medias IS 'Updated: url column now stores relative paths (e.g., posts/uuid.jpg)';

-- 2. Update VideoMedia table (video thumbnails)
UPDATE video_medias
SET thumbnail_url = REGEXP_REPLACE(thumbnail_url, '^https?://[^/]+/', '')
WHERE thumbnail_url LIKE 'http%';

COMMENT ON TABLE video_medias IS 'Updated: thumbnail_url column now stores relative paths';



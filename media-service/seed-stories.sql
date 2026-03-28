BEGIN;

WITH story_ids AS (
    SELECT
        gs AS idx,
        format('story_%03s', gs) AS story_id,
        (ARRAY['usr_003', 'usr_001', 'usr_002'])[(gs - 1) % 3 + 1] AS account_id
    FROM generate_series(1, 100) AS gs
)
INSERT INTO contents (
    content_type,
    id,
    created_at,
    status,
    updated_at,
    visibility,
    account_id
)
SELECT
    'STORY',
    story_id,
    NOW(),
    'ACTIVE',
    NOW(),
    'PUBLIC',
    account_id
FROM story_ids;

WITH story_ids AS (
    SELECT
        gs AS idx,
        format('story_%03s', gs) AS story_id
    FROM generate_series(1, 100) AS gs
)
INSERT INTO stories (
    text,
    expire_at,
    highlight_name,
    is_highlight,
    id
)
SELECT
    NULL,
    NOW() + INTERVAL '24 hours',
    NULL,
    FALSE,
    story_id
FROM story_ids;

WITH story_ids AS (
    SELECT
        gs AS idx,
        format('story_%03s', gs) AS story_id
    FROM generate_series(1, 100) AS gs
),
items AS (
    SELECT
        s.idx,
        s.story_id,
        i AS item_idx,
        CASE
            WHEN i = 1 THEN 'IMAGE_ITEM'
            WHEN i = 2 THEN 'VIDEO_ITEM'
            ELSE 'TEXT_ITEM'
        END AS item_type,
        format('story_item_%03s_%02s', s.idx, i) AS item_id
    FROM story_ids s
    CROSS JOIN generate_series(1, 5) AS i
)
INSERT INTO story_items (
    type,
    id,
    end_time,
    is_primary,
    positionx,
    positiony,
    rotation,
    scale,
    start_time,
    z_index,
    story_id
)
SELECT
    item_type,
    item_id,
    10000,
    CASE WHEN item_idx = 1 THEN TRUE ELSE FALSE END,
    0.5,
    0.5,
    0.0,
    1.0,
    0,
    item_idx,
    story_id
FROM items;

WITH story_ids AS (
    SELECT
        gs AS idx,
        format('story_%03s', gs) AS story_id
    FROM generate_series(1, 100) AS gs
),
items AS (
    SELECT
        s.idx,
        s.story_id,
        i AS item_idx,
        CASE
            WHEN i = 1 THEN 'IMAGE_ITEM'
            WHEN i = 2 THEN 'VIDEO_ITEM'
            ELSE 'TEXT_ITEM'
        END AS item_type,
        format('story_item_%03s_%02s', s.idx, i) AS item_id
    FROM story_ids s
    CROSS JOIN generate_series(1, 5) AS i
)
INSERT INTO image_items (
    height,
    url,
    width,
    id
)
SELECT
    1920,
    format('stories/sample-image-%03s.jpg', idx),
    1080,
    item_id
FROM items
WHERE item_type = 'IMAGE_ITEM';

WITH story_ids AS (
    SELECT
        gs AS idx,
        format('story_%03s', gs) AS story_id
    FROM generate_series(1, 100) AS gs
),
items AS (
    SELECT
        s.idx,
        s.story_id,
        i AS item_idx,
        CASE
            WHEN i = 1 THEN 'IMAGE_ITEM'
            WHEN i = 2 THEN 'VIDEO_ITEM'
            ELSE 'TEXT_ITEM'
        END AS item_type,
        format('story_item_%03s_%02s', s.idx, i) AS item_id
    FROM story_ids s
    CROSS JOIN generate_series(1, 5) AS i
)
INSERT INTO video_items (
    duration,
    height,
    thumbnail_url,
    url,
    width,
    id
)
SELECT
    15000,
    1280,
    format('stories/sample-video-%03s.jpg', idx),
    format('stories/sample-video-%03s.mp4', idx),
    720,
    item_id
FROM items
WHERE item_type = 'VIDEO_ITEM';

WITH story_ids AS (
    SELECT
        gs AS idx,
        format('story_%03s', gs) AS story_id
    FROM generate_series(1, 100) AS gs
),
items AS (
    SELECT
        s.idx,
        s.story_id,
        i AS item_idx,
        CASE
            WHEN i = 1 THEN 'IMAGE_ITEM'
            WHEN i = 2 THEN 'VIDEO_ITEM'
            ELSE 'TEXT_ITEM'
        END AS item_type,
        format('story_item_%03s_%02s', s.idx, i) AS item_id
    FROM story_ids s
    CROSS JOIN generate_series(1, 5) AS i
),
text_items AS (
    SELECT
        idx,
        item_id,
        item_idx,
        (item_idx - 2) AS text_idx
    FROM items
    WHERE item_type = 'TEXT_ITEM'
)
INSERT INTO text_items (
    alignment,
    background_color,
    color,
    content,
    font,
    id
)
SELECT
    'CENTER',
    '#111827',
    '#FFFFFF',
    format('Story %03s text %s', idx, text_idx),
    'Inter',
    item_id
FROM text_items;

COMMIT;

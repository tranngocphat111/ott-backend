-- Sample Data for Media Service
-- Created: January 17, 2026

-- Clear existing data (optional - comment out if you want to keep existing data)
-- TRUNCATE TABLE comments, reactions, tags, posts, stories, notes, contents, mentioned_users,
-- content_access_controll_user, content_access_controll, oa_followers, official_accounts, users CASCADE;

-- Insert Users
INSERT INTO users (id, username, avatar_url) VALUES
('user-001', 'john_doe', 'https://i.pravatar.cc/150?img=1'),
('user-002', 'jane_smith', 'https://i.pravatar.cc/150?img=2'),
('user-003', 'bob_wilson', 'https://i.pravatar.cc/150?img=3'),
('user-004', 'alice_brown', 'https://i.pravatar.cc/150?img=4'),
('user-005', 'charlie_davis', 'https://i.pravatar.cc/150?img=5');

-- Insert Contents (Posts)
INSERT INTO contents (content_type, id, comments_count, created_at, deleted_at, metadata, share_count, total_reactions_count, updated_at, visibility, user_id) VALUES
('POST', 'post-001', 5, NOW() - INTERVAL '2 days', NULL,
 '{"images": [
    {"imageUrl": "https://images.unsplash.com/photo-1506905925346-21bda4d32df4", "caption": "Beautiful mountain landscape at sunset"},
    {"imageUrl": "https://images.unsplash.com/photo-1511593358241-7eea1f3c84e5", "caption": "Crystal clear lake reflection"},
    {"imageUrl": "https://images.unsplash.com/photo-1506905925346-21bda4d32df4", "caption": "Hiking trail through the forest"}
  ]}'::jsonb,
 12, 45, NOW() - INTERVAL '1 day', 0, 'user-001'),

('POST', 'post-002', 3, NOW() - INTERVAL '1 day', NULL,
 '{"images": [
    {"imageUrl": "https://images.unsplash.com/photo-1504674900247-0877df9cc836", "caption": "Delicious homemade pasta"},
    {"imageUrl": "https://images.unsplash.com/photo-1540189549336-e6e99c3679fe", "caption": "Fresh garden salad"}
  ]}'::jsonb,
 8, 32, NOW() - INTERVAL '12 hours', 0, 'user-002'),

('POST', 'post-003', 8, NOW() - INTERVAL '5 hours', NULL,
 '{"images": [
    {"imageUrl": "https://images.unsplash.com/photo-1517836357463-d25dfeac3438", "caption": "Morning workout session"},
    {"imageUrl": "https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b", "caption": "Gym equipment ready"},
    {"imageUrl": "https://images.unsplash.com/photo-1534438327276-14e5300c3a48", "caption": "Fitness motivation"},
    {"imageUrl": "https://images.unsplash.com/photo-1518310383802-640c2de311b2", "caption": "Post-workout smoothie"}
  ]}'::jsonb,
 5, 67, NOW() - INTERVAL '3 hours', 0, 'user-003'),

('POST', 'post-004', 2, NOW() - INTERVAL '3 days', NULL,
 '{"images": [
    {"imageUrl": "https://images.unsplash.com/photo-1488590528505-98d2b5aba04b", "caption": "Latest tech gadgets showcase"}
  ]}'::jsonb,
 15, 89, NOW() - INTERVAL '2 days', 0, 'user-004'),

('POST', 'post-005', 12, NOW() - INTERVAL '6 hours', NULL,
 '{"images": [
    {"imageUrl": "https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba", "caption": "My adorable cat sleeping"},
    {"imageUrl": "https://images.unsplash.com/photo-1495360010541-f48722b34f7d", "caption": "Playtime with yarn"},
    {"imageUrl": "https://images.unsplash.com/photo-1574158622682-e40e69881006", "caption": "Cat enjoying the window view"}
  ]}'::jsonb,
 25, 156, NOW() - INTERVAL '4 hours', 0, 'user-005'),

('POST', 'post-006', 0, NOW() - INTERVAL '30 minutes', NULL,
 '{"images": [
    {"imageUrl": "https://images.unsplash.com/photo-1506744038136-46273834b3fb", "caption": "Sunset over the ocean"},
    {"imageUrl": "https://images.unsplash.com/photo-1507525428034-b723cf961d3e", "caption": "Beach vibes"},
    {"imageUrl": "https://images.unsplash.com/photo-1510414842594-a61c69b5ae57", "caption": "Peaceful waves"}
  ]}'::jsonb,
 0, 3, NOW() - INTERVAL '20 minutes', 0, 'user-001');

-- Insert Posts (with content text)
INSERT INTO posts (id, content) VALUES
('post-001', 'Amazing weekend trip to the mountains! The views were breathtaking 🏔️ #nature #travel'),
('post-002', 'Just tried this new recipe and it turned out perfect! 🍝 #cooking #foodie'),
('post-003', 'Finished my morning workout! Feeling energized and ready for the day 💪 #fitness #health'),
('post-004', 'Check out these cool new gadgets I got! Tech lovers, what do you think? 🤖 #technology'),
('post-005', 'My cat is the cutest thing ever! 😻 Can''t stop taking pictures #catlover #pets'),
('post-006', 'Perfect evening at the beach 🌅 #sunset #peaceful');

-- Insert Stories
INSERT INTO contents (content_type, id, comments_count, created_at, deleted_at, metadata, share_count, total_reactions_count, updated_at, visibility, user_id) VALUES
('STORY', 'story-001', 0, NOW() - INTERVAL '2 hours', NULL,
 '{"images": [
    {"imageUrl": "https://images.unsplash.com/photo-1511785176398-c91c22e78906", "caption": "Coffee time ☕"}
  ]}'::jsonb,
 0, 23, NOW() - INTERVAL '2 hours', 0, 'user-002'),

('STORY', 'story-002', 0, NOW() - INTERVAL '5 hours', NULL,
 '{"images": [
    {"imageUrl": "https://images.unsplash.com/photo-1542291026-7eec264c27ff", "caption": "New shoes! 👟"}
  ]}'::jsonb,
 0, 45, NOW() - INTERVAL '5 hours', 0, 'user-003');

INSERT INTO stories (id, expire_at) VALUES
('story-001', NOW() + INTERVAL '19 hours'),
('story-002', NOW() + INTERVAL '16 hours');

-- Insert Notes
INSERT INTO contents (content_type, id, comments_count, created_at, deleted_at, metadata, share_count, total_reactions_count, updated_at, visibility, user_id) VALUES
('NOTE', 'note-001', 1, NOW() - INTERVAL '30 minutes', NULL,
 '{"images": [
    {"imageUrl": "https://images.unsplash.com/photo-1484480974693-6ca0a78fb36b", "caption": "Quick thought"}
  ]}'::jsonb,
 0, 8, NOW() - INTERVAL '30 minutes', 0, 'user-001');

INSERT INTO notes (id, expire_at) VALUES
('note-001', NOW() + INTERVAL '23 hours 30 minutes');

-- Insert Comments
INSERT INTO comments (id, created_at, deleted_at, text, total_reactions_count, updated_at, content_id, parent_id, user_id) VALUES
('comment-001', NOW() - INTERVAL '1 day', NULL, 'Wow, this looks amazing! 😍', 5, NOW() - INTERVAL '1 day', 'post-001', NULL, 'user-002'),
('comment-002', NOW() - INTERVAL '1 day', NULL, 'Where is this place?', 2, NOW() - INTERVAL '1 day', 'post-001', NULL, 'user-003'),
('comment-003', NOW() - INTERVAL '20 hours', NULL, 'It''s in the Swiss Alps!', 3, NOW() - INTERVAL '20 hours', 'post-001', 'comment-002', 'user-001'),
('comment-004', NOW() - INTERVAL '12 hours', NULL, 'Thanks for sharing!', 1, NOW() - INTERVAL '12 hours', 'post-002', NULL, 'user-004'),
('comment-005', NOW() - INTERVAL '6 hours', NULL, 'I need to try this recipe!', 4, NOW() - INTERVAL '6 hours', 'post-002', NULL, 'user-005'),
('comment-006', NOW() - INTERVAL '4 hours', NULL, 'Great job! Keep it up! 💪', 8, NOW() - INTERVAL '4 hours', 'post-003', NULL, 'user-001'),
('comment-007', NOW() - INTERVAL '3 hours', NULL, 'What''s your workout routine?', 3, NOW() - INTERVAL '3 hours', 'post-003', NULL, 'user-002'),
('comment-008', NOW() - INTERVAL '2 hours', NULL, 'So adorable! 😻', 12, NOW() - INTERVAL '2 hours', 'post-005', NULL, 'user-003'),
('comment-009', NOW() - INTERVAL '1 hour', NULL, 'What''s your cat''s name?', 5, NOW() - INTERVAL '1 hour', 'post-005', NULL, 'user-004'),
('comment-010', NOW() - INTERVAL '30 minutes', NULL, 'Her name is Luna! 🌙', 7, NOW() - INTERVAL '30 minutes', 'post-005', 'comment-009', 'user-005');

-- Insert Reactions for Posts
INSERT INTO reactions (id, reaction_type, comment_id, content_id, user_id) VALUES
('reaction-001', 'LIKE', NULL, 'post-001', 'user-002'),
('reaction-002', 'LOVE', NULL, 'post-001', 'user-003'),
('reaction-003', 'WOW', NULL, 'post-001', 'user-004'),
('reaction-004', 'LIKE', NULL, 'post-002', 'user-001'),
('reaction-005', 'LOVE', NULL, 'post-002', 'user-003'),
('reaction-006', 'LIKE', NULL, 'post-003', 'user-002'),
('reaction-007', 'FIRE', NULL, 'post-003', 'user-004'),
('reaction-008', 'LIKE', NULL, 'post-004', 'user-001'),
('reaction-009', 'LOVE', NULL, 'post-005', 'user-001'),
('reaction-010', 'LOVE', NULL, 'post-005', 'user-002'),
('reaction-011', 'LOVE', NULL, 'post-005', 'user-003'),
('reaction-012', 'LOVE', NULL, 'post-005', 'user-004');

-- Insert Reactions for Comments
INSERT INTO reactions (id, reaction_type, comment_id, content_id, user_id) VALUES
('reaction-013', 'LIKE', 'comment-001', NULL, 'user-001'),
('reaction-014', 'LIKE', 'comment-002', NULL, 'user-004'),
('reaction-015', 'LIKE', 'comment-003', NULL, 'user-002'),
('reaction-016', 'LIKE', 'comment-006', NULL, 'user-003'),
('reaction-017', 'LOVE', 'comment-008', NULL, 'user-001'),
('reaction-018', 'LOVE', 'comment-008', NULL, 'user-005');

-- Insert Tags
INSERT INTO tags (id, name, post_id) VALUES
('tag-001', 'nature', 'post-001'),
('tag-002', 'travel', 'post-001'),
('tag-003', 'cooking', 'post-002'),
('tag-004', 'foodie', 'post-002'),
('tag-005', 'fitness', 'post-003'),
('tag-006', 'health', 'post-003'),
('tag-007', 'technology', 'post-004'),
('tag-008', 'catlover', 'post-005'),
('tag-009', 'pets', 'post-005'),
('tag-010', 'sunset', 'post-006'),
('tag-011', 'peaceful', 'post-006');

-- Insert Mentioned Users
INSERT INTO mentioned_users (content_id, user_id) VALUES
('post-001', 'user-003'),
('post-002', 'user-004'),
('post-003', 'user-002'),
('post-005', 'user-001');

-- Insert Official Accounts
INSERT INTO official_accounts (id, address, category, created_at, description, email, followers_count, is_verified, name, phone, verified_at, website, user_id) VALUES
('oa-001', '123 Tech Street, Silicon Valley, CA', 'Technology', NOW() - INTERVAL '6 months', 'Official tech news and updates', 'contact@technews.com', 125000, true, 'Tech News Daily', '+1-555-0123', NOW() - INTERVAL '5 months', 'https://www.technews.com', 'user-004'),
('oa-002', '456 Food Ave, New York, NY', 'Food & Dining', NOW() - INTERVAL '1 year', 'Delicious recipes and cooking tips', 'hello@foodlovers.com', 89000, true, 'Food Lovers Hub', '+1-555-0456', NOW() - INTERVAL '11 months', 'https://www.foodlovers.com', 'user-002'),
('oa-003', '789 Fitness Blvd, Los Angeles, CA', 'Health & Fitness', NOW() - INTERVAL '8 months', 'Your daily dose of fitness motivation', 'info@fitlife.com', 156000, true, 'FitLife Pro', '+1-555-0789', NOW() - INTERVAL '7 months', 'https://www.fitlife.com', 'user-003');

-- Insert OA Followers
INSERT INTO oa_followers (id, followed_at, oa_id, user_id) VALUES
('oaf-001', NOW() - INTERVAL '3 months', 'oa-001', 'user-001'),
('oaf-002', NOW() - INTERVAL '2 months', 'oa-001', 'user-002'),
('oaf-003', NOW() - INTERVAL '1 month', 'oa-001', 'user-005'),
('oaf-004', NOW() - INTERVAL '5 months', 'oa-002', 'user-001'),
('oaf-005', NOW() - INTERVAL '4 months', 'oa-002', 'user-003'),
('oaf-006', NOW() - INTERVAL '6 months', 'oa-003', 'user-001'),
('oaf-007', NOW() - INTERVAL '5 months', 'oa-003', 'user-002'),
('oaf-008', NOW() - INTERVAL '3 months', 'oa-003', 'user-004');

-- Insert Content Access Control
INSERT INTO content_access_controll (id, is_excluded, content_id) VALUES
('cac-001', true, 'post-003');

INSERT INTO content_access_controll_user (content_access_controll_id, user_id) VALUES
('cac-001', 'user-005');

-- Verify data
SELECT 'Users' as table_name, COUNT(*) as count FROM users
UNION ALL
SELECT 'Contents', COUNT(*) FROM contents
UNION ALL
SELECT 'Posts', COUNT(*) FROM posts
UNION ALL
SELECT 'Stories', COUNT(*) FROM stories
UNION ALL
SELECT 'Notes', COUNT(*) FROM notes
UNION ALL
SELECT 'Comments', COUNT(*) FROM comments
UNION ALL
SELECT 'Reactions', COUNT(*) FROM reactions
UNION ALL
SELECT 'Tags', COUNT(*) FROM tags
UNION ALL
SELECT 'Mentioned Users', COUNT(*) FROM mentioned_users
UNION ALL
SELECT 'Official Accounts', COUNT(*) FROM official_accounts
UNION ALL
SELECT 'OA Followers', COUNT(*) FROM oa_followers
UNION ALL
SELECT 'Content Access Control', COUNT(*) FROM content_access_controll;


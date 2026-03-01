# Social Content System – Tài liệu thiết kế

> **Service:** `media-service` · **Port:** `8080` · **Base path:** `/media/api`  
> **Database:** PostgreSQL (Neon – `db_social`)  
> **Storage:** AWS S3 `social-riff-app-demo` (ap-southeast-1, account `642058032746`)

---

## 1. Tổng quan kiến trúc

```
┌──────────────────────────────────────────────────────────────┐
│                        media-service                         │
│                                                              │
│   Controller  →  Service (interface + impl)  →  Repository  │
│        ↓                    ↓                               │
│    DTO / Mapper         S3Service  ←──────── AWS S3         │
│        ↓                                   (social-riff-app-demo)
│    JSON Response        Redis Cache                          │
└──────────────────────────────────────────────────────────────┘
```

---

## 2. Domain Model

### 2.1 Hierarchy tổng quan

```
Account (abstract)
├── UserAccount          – người dùng cá nhân
└── OfficialAccount      – trang / kênh chính thức

Content (abstract, JOINED inheritance)
├── Post                 – bài viết (caption + media)
├── Note                 – note ngắn / status
└── Story                – story 24h

Media (abstract, JOINED inheritance)
├── ImageMedia           – ảnh đã upload lên S3
└── VideoMedia           – video đã upload lên S3

StoryItem (abstract)
├── ImageItem            – ảnh trong story
├── VideoItem            – video trong story
└── TextItem             – text overlay trong story
```

### 2.2 Bảng dữ liệu chính

| Bảng                      | Thực thể               | Mô tả                                  |
| ------------------------- | ---------------------- | -------------------------------------- |
| `accounts`                | `Account`              | Bảng cha (JOINED)                      |
| `users`                   | `UserAccount`          | Tài khoản người dùng cá nhân           |
| `official_accounts`       | `OfficialAccount`      | Trang / kênh                           |
| `contents`                | `Content`              | Bảng cha JOINED cho mọi nội dung       |
| `posts`                   | `Post`                 | Bài đăng – thêm cột `caption`          |
| `notes`                   | `Note`                 | Ghi chú ngắn                           |
| `stories`                 | `Story`                | Story 24h                              |
| `medias`                  | `Media`                | Bảng cha JOINED cho media              |
| `image_medias`            | `ImageMedia`           | Ảnh (URL S3)                           |
| `video_medias`            | `VideoMedia`           | Video (URL S3 + thumbnail)             |
| `story_items`             | `StoryItem`            | Items trong story                      |
| `image_items`             | `ImageItem`            | Ảnh item                               |
| `video_items`             | `VideoItem`            | Video item                             |
| `text_items`              | `TextItem`             | Text overlay                           |
| `comments`                | `Comment`              | Bình luận (nested, depth ≤ 2)          |
| `reactions`               | `Reaction`             | Like / Love / Haha / Wow / Sad / Angry |
| `relationships`           | `Relationship`         | Kết bạn giữa UserAccount               |
| `follows`                 | `Follow`               | Follow User hoặc OfficialAccount       |
| `hashtags`                | `HashTag`              | Tag nội dung                           |
| `content_hashtag`         | (join)                 | N-N Content ↔ HashTag                  |
| `content_access_controls` | `ContentAccessControl` | Whitelist / blacklist account          |
| `mentions`                | `Mention`              | Mention account trong content          |

---

## 3. Enum quan trọng

### VisibilityType

```
PUBLIC    – ai cũng xem được
FRIENDS   – chỉ bạn bè
PRIVATE   – chỉ mình tôi
CUSTOM    – theo ContentAccessControl
```

### ContentStatusType

```
ACTIVE    – đang hiển thị
HIDDEN    – ẩn tạm thời
DELETED   – đã xoá mềm
REPORTED  – đang bị báo cáo, chờ review
```

### ReactionType

```
LIKE  LOVE  HAHA  WOW  SAD  ANGRY
```

### RelationshipStatusType

```
PENDING   – đã gửi lời mời, chờ xác nhận
ACCEPTED  – là bạn bè
BLOCKED   – bị chặn
REMOVED   – đã huỷ kết bạn
```

---

## 4. AWS S3 – Cấu hình

| Key        | Giá trị                |
| ---------- | ---------------------- |
| Bucket     | `social-riff-app-demo` |
| Region     | `ap-southeast-1`       |
| Account ID | `642058032746`         |
| IAM User   | `social-app-test-user` |
| Access Key | `AKIAZK7NECZVK4HC6NG5` |

### Cấu trúc thư mục trong bucket

```
social-riff-app-demo/
├── avatars/           ← ảnh đại diện người dùng
├── covers/            ← ảnh bìa
├── posts/             ← media thuộc bài đăng
│   ├── images/
│   └── videos/
├── stories/           ← media thuộc story
│   ├── images/
│   └── videos/
└── seed/              ← ảnh mẫu (DataSeeder)
```

### URL công khai

```
https://social-riff-app-demo.s3.ap-southeast-1.amazonaws.com/<key>
```

### Presigned URL

- Hết hạn mặc định: **60 phút**
- Dùng cho content có `visibility = PRIVATE` hoặc `CUSTOM`

---

## 5. Dữ liệu mẫu (Seed Data)

Kích hoạt bằng Spring profile `dev` hoặc `seed`:

```bash
# Maven
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Jar
java -jar media-service.jar --spring.profiles.active=dev
```

### 4 người dùng mẫu

| #     | Username       | Tên            | Quan hệ                               |
| ----- | -------------- | -------------- | ------------------------------------- |
| user1 | `nguyennhan`   | Nguyễn Nhân    | Current user                          |
| user2 | `tranminhkhoa` | Trần Minh Khoa | Bạn của **user1**                     |
| user3 | `lethyhuong`   | Lê Thu Hương   | Bạn của **user2**, người lạ với user1 |
| user4 | `phamvanlong`  | Phạm Văn Long  | Người lạ với tất cả                   |

### Graph quan hệ

```
user1 ──── FRIEND ──── user2
                          └─── FRIEND ──── user3
user4  (không kết nối với ai)
```

### 7 bài post mẫu

| Post | Tác giả | Nội dung              | Media           | Visibility |
| ---- | ------- | --------------------- | --------------- | ---------- |
| 1    | user2   | Leo núi Bà Đen 🏔️     | 3 ảnh           | PUBLIC     |
| 2    | user1   | React tip 🚀          | text            | FRIENDS    |
| 3    | user3   | Bánh kem tặng mẹ 🎂   | 1 ảnh           | PUBLIC     |
| 4    | user2   | Quán cà phê ☕        | 2 ảnh           | PUBLIC     |
| 5    | user1   | Chào buổi sáng 🌟     | text            | FRIENDS    |
| 6    | user3   | Chụp ảnh công viên 📸 | 4 ảnh           | PUBLIC     |
| 7    | user4   | IELTS tips 📚         | 2 ảnh + 1 video | PUBLIC     |

---

## 6. API Endpoints (kế hoạch)

### Post

| Method   | Path                          | Mô tả                             |
| -------- | ----------------------------- | --------------------------------- |
| `POST`   | `/posts`                      | Tạo bài viết mới (multipart)      |
| `GET`    | `/posts/{id}`                 | Lấy chi tiết bài viết             |
| `PUT`    | `/posts/{id}`                 | Cập nhật caption / visibility     |
| `DELETE` | `/posts/{id}`                 | Xoá mềm bài viết                  |
| `GET`    | `/posts/feed`                 | Feed bài viết (bạn bè + public)   |
| `GET`    | `/accounts/{accountId}/posts` | Tất cả bài viết của một tài khoản |

### Media Upload

| Method   | Path                    | Mô tả                     |
| -------- | ----------------------- | ------------------------- |
| `POST`   | `/media/upload`         | Upload ảnh / video lên S3 |
| `DELETE` | `/media/{id}`           | Xoá media (S3 + DB)       |
| `GET`    | `/media/{id}/presigned` | Lấy presigned URL         |

### Reaction

| Method   | Path                               | Mô tả                  |
| -------- | ---------------------------------- | ---------------------- |
| `POST`   | `/reactions`                       | Thêm / đổi reaction    |
| `DELETE` | `/reactions/{id}`                  | Xoá reaction           |
| `GET`    | `/reactions?targetId=&targetType=` | Lấy danh sách reaction |

### Comment

| Method   | Path                              | Mô tả                               |
| -------- | --------------------------------- | ----------------------------------- |
| `POST`   | `/comments`                       | Thêm comment (top-level hoặc reply) |
| `PUT`    | `/comments/{id}`                  | Sửa comment                         |
| `DELETE` | `/comments/{id}`                  | Xoá mềm comment                     |
| `GET`    | `/comments?targetId=&targetType=` | Lấy thread comment                  |

### Relationship

| Method   | Path                         | Mô tả                             |
| -------- | ---------------------------- | --------------------------------- |
| `POST`   | `/relationships/request`     | Gửi lời mời kết bạn               |
| `PUT`    | `/relationships/{id}/accept` | Chấp nhận lời mời                 |
| `DELETE` | `/relationships/{id}`        | Huỷ kết bạn / thu hồi lời mời     |
| `GET`    | `/relationships/friends`     | Danh sách bạn bè của current user |
| `GET`    | `/relationships/suggestions` | Gợi ý kết bạn (bạn của bạn)       |

---

## 7. Feed Algorithm (sơ lược)

```
Feed = UNION(
    posts WHERE author IN friends(currentUser) AND visibility IN (PUBLIC, FRIENDS)
  , posts WHERE visibility = PUBLIC
      AND author NOT IN blocked(currentUser)
      ORDER BY createdAt DESC
)
LIMIT 20 OFFSET page * 20
```

Bộ lọc bổ sung:

- `HIDDEN`, `DELETED`, `REPORTED` → loại khỏi feed
- `ContentAccessControl.BLOCK_LIST` → ẩn với account bị chặn
- Redis cache key: `feed:{userId}:page:{n}` TTL 10 phút

---

## 8. Ghi chú kỹ thuật

- **Inheritance strategy:** `JOINED` cho cả `Content` và `Media` → mỗi subtype có bảng riêng, foreign key về bảng cha.
- **Soft delete:** `ContentStatusType.DELETED` — không xoá row, set status = DELETED.
- **Nested comments:** `depth` tối đa **2** (comment → reply; không cho reply-of-reply vô tận).
- **Media order:** `orderIndex` trên `Media` quyết định thứ tự hiển thị trong grid ảnh.
- **Video thumbnail:** `VideoMedia.thumbnailUrl` trỏ tới ảnh preview trong S3.
- **Presigned URL** được dùng thay thế URL tĩnh khi `visibility ≠ PUBLIC`.

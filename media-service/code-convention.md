# 📋 Post API – Tóm tắt các lệnh

Base URL: `/posts`

---

## 1. Tạo bài post mới

```
POST /posts
Content-Type: multipart/form-data
```

| Param        | Loại            | Bắt buộc | Mô tả                              |
|--------------|-----------------|----------|------------------------------------|
| `accountId`  | `String`        | ✅        | ID của account đăng bài            |
| `caption`    | `String`        | ✅        | Nội dung bài post                  |
| `visibility` | `String`        | ❌        | `PUBLIC` / `FRIENDS` / `CUSTOM` (mặc định: `PUBLIC`) |
| `files`      | `MultipartFile[]` | ❌      | Danh sách ảnh/video đính kèm       |

**Response:** `PostResponse`

---

## 2. Lấy tất cả bài post

```
GET /posts
```

**Response:** `List<PostResponse>`

---

## 3. Lấy bài post có phân trang

```
GET /posts/page?page=0&size=10&sort=createdAt
```

| Query Param | Loại  | Mặc định     | Mô tả        |
|-------------|-------|--------------|--------------|
| `page`      | `int` | `0`          | Số trang     |
| `size`      | `int` | `10`         | Số item/trang|
| `sort`      | `String` | `createdAt` | Trường sắp xếp |

**Response:** `Page<PostResponse>`

---

## 4. Lấy bài post theo ID

```
GET /posts/{id}
```

| Path Var | Loại     | Mô tả         |
|----------|----------|---------------|
| `id`     | `String` | ID bài post   |

**Response:** `PostResponse`

---

## 5. Lấy bài post theo User

```
GET /posts/user/{userId}
```

| Path Var  | Loại     | Mô tả       |
|-----------|----------|-------------|
| `userId`  | `String` | ID account  |

**Response:** `List<PostResponse>`

---

## 6. Cập nhật bài post

```
PUT /posts/{id}
Content-Type: application/json
```

| Path Var | Loại     | Mô tả       |
|----------|----------|-------------|
| `id`     | `String` | ID bài post |

**Request Body – `PostRequest`:**

```json
{
  "userId": "string",
  "visibility": "PUBLIC | FRIENDS | CUSTOM",
  "hashTags": ["string"],
  "accessControls": [
    {
      "accountId": "string",
      "ruleType": "INCLUDE | EXCLUDE"
    }
  ],
  "mentions": [
    {
      "taggedAccountId": "string"
    }
  ],
  "caption": "string",
  "medias": [
    {
      "type": "IMAGE_MEDIA | VIDEO_MEDIA",
      "url": "string",
      "caption": "string",
      "orderIndex": 0,
      "thumbnailUrl": "string (video only)",
      "duration": 0,
      "hasAudio": true
    }
  ]
}
```

**Response:** `PostResponse`

---

## 7. Xoá bài post

```
DELETE /posts/{id}
```

| Path Var | Loại     | Mô tả       |
|----------|----------|-------------|
| `id`     | `String` | ID bài post |

**Response:** `204 No Content`

---

## 8. Like / Unlike bài post (Toggle)

```
POST /posts/{postId}/like?accountId=xxx&reactionType=LIKE
```

| Path Var   | Loại     | Mô tả       |
|------------|----------|-------------|
| `postId`   | `String` | ID bài post |

| Query Param    | Loại     | Mặc định | Mô tả                                    |
|----------------|----------|----------|------------------------------------------|
| `accountId`    | `String` | ✅        | ID account thực hiện like                |
| `reactionType` | `String` | `LIKE`   | `LIKE / LOVE / HAHA / WOW / SAD / ANGRY` |

**Response:**
```json
{
  "liked": true,
  "totalReactions": 10,
  "reaction": { /* ReactionResponse nếu đã like, {} nếu unlike */ }
}
```

---

## 9. Lấy tất cả reactions của bài post

```
GET /posts/{postId}/reactions
```

| Path Var  | Loại     | Mô tả       |
|-----------|----------|-------------|
| `postId`  | `String` | ID bài post |

**Response:** `List<ReactionResponse>`

---

## 10. Lấy tất cả comments của bài post

```
GET /posts/{postId}/comments
```

| Path Var  | Loại     | Mô tả       |
|-----------|----------|-------------|
| `postId`  | `String` | ID bài post |

**Response:** `List<CommentResponse>`

---

## 11. Thêm comment vào bài post

```
POST /posts/{postId}/comments?accountId=xxx&text=hello&parentCommentId=yyy
```

| Path Var  | Loại     | Mô tả       |
|-----------|----------|-------------|
| `postId`  | `String` | ID bài post |

| Query Param       | Loại     | Bắt buộc | Mô tả                              |
|-------------------|----------|----------|------------------------------------|
| `accountId`       | `String` | ✅        | ID account viết comment             |
| `text`            | `String` | ✅        | Nội dung comment                    |
| `parentCommentId` | `String` | ❌        | ID comment cha (nếu là reply)       |

**Response:** `CommentResponse`

---

## 12. Xoá comment (soft-delete)

```
DELETE /posts/{postId}/comments/{commentId}
```

| Path Var     | Loại     | Mô tả          |
|--------------|----------|----------------|
| `postId`     | `String` | ID bài post    |
| `commentId`  | `String` | ID comment     |

**Response:** `204 No Content`

---

## 📦 Data Models

### PostResponse
| Field            | Loại              | Mô tả                         |
|------------------|-------------------|-------------------------------|
| `id`             | `String`          | ID bài post                   |
| `accountId`      | `String`          | ID tác giả                    |
| `accountUsername`| `String`          | Username tác giả              |
| `accountDisplayName` | `String`     | Tên hiển thị tác giả          |
| `accountAvatarUrl`   | `String`     | Avatar URL tác giả            |
| `visibility`     | `VisibilityType`  | Chế độ hiển thị               |
| `hashTags`       | `List<String>`    | Danh sách hashtag             |
| `caption`        | `String`          | Nội dung bài post             |
| `medias`         | `List<MediaResponse>` | Danh sách ảnh/video       |
| `totalReactions` | `int`             | Tổng số reactions             |
| `totalComments`  | `int`             | Tổng số comments              |
| `totalShares`    | `int`             | Tổng số shares                |
| `createdAt`      | `LocalDateTime`   | Thời gian tạo                 |
| `updatedAt`      | `LocalDateTime`   | Thời gian cập nhật            |

### MediaResponse
| Field          | Loại        | Mô tả                        |
|----------------|-------------|------------------------------|
| `id`           | `String`    | ID media                     |
| `type`         | `MediaType` | `IMAGE_MEDIA` / `VIDEO_MEDIA`|
| `url`          | `String`    | URL đầy đủ trên S3           |
| `caption`      | `String`    | Caption của media            |
| `orderIndex`   | `int`       | Thứ tự hiển thị              |
| `thumbnailUrl` | `String`    | Thumbnail (video only)       |
| `duration`     | `Long`      | Thời lượng ms (video only)   |
| `hasAudio`     | `Boolean`   | Có âm thanh không (video)    |
| `createdAt`    | `LocalDateTime` | Thời gian tạo            |
| `updatedAt`    | `LocalDateTime` | Thời gian cập nhật       |

### ReactionResponse
| Field         | Loại                 | Mô tả                          |
|---------------|----------------------|--------------------------------|
| `id`          | `String`             | ID reaction                    |
| `accountId`   | `String`             | ID người react                 |
| `accountUsername` | `String`         | Username người react           |
| `accountAvatarUrl` | `String`        | Avatar người react             |
| `targetId`    | `String`             | ID đối tượng được react        |
| `targetType`  | `ReactionTargetType` | `POST` / `COMMENT`             |
| `reactionType`| `ReactionType`       | `LIKE / LOVE / HAHA / WOW / SAD / ANGRY` |
| `createdAt`   | `LocalDateTime`      | Thời gian react                |

### CommentResponse
| Field               | Loại            | Mô tả                           |
|---------------------|-----------------|----------------------------------|
| `id`                | `String`        | ID comment                       |
| `text`              | `String`        | Nội dung comment                 |
| `accountId`         | `String`        | ID người comment                 |
| `accountUsername`   | `String`        | Username người comment           |
| `accountDisplayName`| `String`        | Tên hiển thị người comment       |
| `accountAvatarUrl`  | `String`        | Avatar người comment             |
| `parentCommentId`   | `String`        | ID comment cha (nếu là reply)    |
| `isEdited`          | `boolean`       | Đã chỉnh sửa chưa               |
| `isDeleted`         | `boolean`       | Đã xoá chưa (soft-delete)        |
| `depth`             | `int`           | Độ sâu lồng nhau                 |
| `totalReplies`      | `int`           | Tổng số reply                    |
| `totalReactions`    | `int`           | Tổng số reaction trên comment    |
| `createdAt`         | `LocalDateTime` | Thời gian tạo                    |
| `updatedAt`         | `LocalDateTime` | Thời gian cập nhật               |

---

## 🏷️ Enum Values

| Enum               | Giá trị                                      |
|--------------------|----------------------------------------------|
| `VisibilityType`   | `PUBLIC`, `FRIENDS`, `CUSTOM`                |
| `MediaType`        | `IMAGE_MEDIA`, `VIDEO_MEDIA`                 |
| `ReactionType`     | `LIKE`, `LOVE`, `HAHA`, `WOW`, `SAD`, `ANGRY`|
| `ReactionTargetType` | `POST`, `COMMENT`                          |
| `RuleType`         | `INCLUDE`, `EXCLUDE`                         |


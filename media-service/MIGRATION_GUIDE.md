# Migration Guide - URL Storage Refactoring

## 📋 Tổng Quan

Đã refactor cách lưu trữ URL media từ **FULL URL** sang **RELATIVE PATH** (chỉ lưu folder/filename).

### Thay Đổi Chính

| Trước | Sau |
|-------|-----|
| `https://social-riff-app-demo.s3.ap-southeast-1.amazonaws.com/posts/uuid.jpg` | `posts/uuid.jpg` |
| Full URL trong DB | Relative path trong DB |
| URL khó thay đổi | URL linh hoạt, dễ migrate |

---

## ✅ Các Thay Đổi Đã Thực Hiện

### 1. **application.properties**
Thêm cấu hình base URL và music folder:
```properties
aws.s3.base-url=https://social-riff-app-demo.s3.ap-southeast-1.amazonaws.com
aws.s3.folder.music=music
```

### 2. **MediaUrlBuilder.java** (NEW)
Utility class để xử lý URL conversion:
- `buildFullUrl(folder, fileName)` - Build full URL từ folder + filename
- `buildS3Url(folder, fileName)` - Build S3 URL format
- `extractFileName(fullUrl)` - Extract filename từ full URL
- `extractRelativePath(fullUrl)` - Extract relative path
- `isFullUrl(url)` - Check nếu là full URL

**Location:** `src/main/java/mediaservice/utils/MediaUrlBuilder.java`

### 3. **S3ServiceImpl.java**
**Thay đổi:** Method `uploadFile()` giờ return **relative path** thay vì full URL
```java
// Trước:
return amazonS3.getUrl(bucketName, fileKey).toString();

// Sau:
return fileKey;  // Trả về "posts/uuid.jpg"
```

**Method mới:** `getFullUrl(fileKey)` - Lấy full URL khi cần

### 4. **Updated Mappers**
Tất cả mappers sau đã được update để tự động convert relative path → full URL khi trả response:

#### a. **ImageMediaMapper.java**
```java
@Mapping(target = "url", expression = "java(buildFullUrl(imageMedia.getUrl()))")
public abstract ImageMediaResponse toResponse(ImageMedia imageMedia);
```

#### b. **VideoMediaMapper.java**
```java
@Mapping(target = "url", expression = "java(buildFullUrl(videoMedia.getUrl()))")
@Mapping(target = "thumbnailUrl", expression = "java(buildFullUrl(videoMedia.getThumbnailUrl()))")
public abstract VideoMediaResponse toResponse(VideoMedia videoMedia);
```

#### c. **MusicMapper.java**
```java
@Mapping(target = "audioUrl", expression = "java(buildFullUrl(music.getAudioUrl()))")
public abstract MusicResponse toResponse(Music music);
```

#### d. **ImageItemMapper.java**
```java
@Mapping(target = "url", expression = "java(buildFullUrl(imageItem.getUrl()))")
public abstract ImageItemResponse toResponse(ImageItem imageItem);
```

#### e. **VideoItemMapper.java**
```java
@Mapping(target = "url", expression = "java(buildFullUrl(videoItem.getUrl()))")
@Mapping(target = "thumbnailUrl", expression = "java(buildFullUrl(videoItem.getThumbnailUrl()))")
public abstract VideoItemResponse toResponse(VideoItem videoItem);
```

#### f. **UserAccountMapper.java**
```java
@Mapping(target = "avatarUrl", expression = "java(buildFullUrl(userAccount.getAvatarUrl()))")
@Mapping(target = "coverUrl", expression = "java(buildFullUrl(userAccount.getCoverUrl()))")
public abstract UserAccountResponse toResponse(UserAccount userAccount);
```

#### g. **OfficialAccountMapper.java**
```java
@Mapping(target = "avatarUrl", expression = "java(buildFullUrl(officialAccount.getAvatarUrl()))")
@Mapping(target = "coverUrl", expression = "java(buildFullUrl(officialAccount.getCoverUrl()))")
public abstract OfficialAccountResponse toResponse(OfficialAccount officialAccount);
```

### 5. **BaseMediaMapper.java** (NEW)
Base class với utility methods cho URL conversion (optional, để mở rộng sau này)

**Location:** `src/main/java/mediaservice/mappers/BaseMediaMapper.java`

---

## 🔄 BACKWARD COMPATIBILITY

Tất cả mappers đều có logic backward compatibility:
```java
// If already a full URL, return as-is (for backward compatibility)
if (relativePath.startsWith("http://") || relativePath.startsWith("https://")) {
    return relativePath;
}
```

**→ Code mới sẽ hoạt động với cả:**
- ✅ Data cũ (full URLs)
- ✅ Data mới (relative paths)

---

## 📊 DATABASE MIGRATION (Optional)

Nếu muốn migrate data hiện có từ full URL → relative path:

### SQL Script

```sql
-- 1. Update Media table (posts)
UPDATE medias 
SET url = REGEXP_REPLACE(url, '^https?://[^/]+/', '')
WHERE url LIKE 'http%';

-- 2. Update VideoMedia table (thumbnails)
UPDATE video_medias 
SET thumbnail_url = REGEXP_REPLACE(thumbnail_url, '^https?://[^/]+/', '')
WHERE thumbnail_url LIKE 'http%';

-- 3. Update ImageItem table (stories)
UPDATE image_items 
SET url = REGEXP_REPLACE(url, '^https?://[^/]+/', '')
WHERE url LIKE 'http%';

-- 4. Update VideoItem table (stories)
UPDATE video_items 
SET url = REGEXP_REPLACE(url, '^https?://[^/]+/', ''),
    thumbnail_url = REGEXP_REPLACE(thumbnail_url, '^https?://[^/]+/', '')
WHERE url LIKE 'http%' OR thumbnail_url LIKE 'http%';

-- 5. Update Account table (avatars & covers)
UPDATE accounts 
SET avatar_url = REGEXP_REPLACE(avatar_url, '^https?://[^/]+/', ''),
    cover_url = REGEXP_REPLACE(cover_url, '^https?://[^/]+/', '')
WHERE avatar_url LIKE 'http%' OR cover_url LIKE 'http%';

-- 6. Update Music table (audio)
UPDATE musics 
SET audio_url = REGEXP_REPLACE(audio_url, '^https?://[^/]+/', '')
WHERE audio_url LIKE 'http%';
```

### Verify Migration
```sql
-- Check if any full URLs remain
SELECT 'medias' as table_name, COUNT(*) as count 
FROM medias WHERE url LIKE 'http%'
UNION ALL
SELECT 'video_medias', COUNT(*) 
FROM video_medias WHERE thumbnail_url LIKE 'http%'
UNION ALL
SELECT 'image_items', COUNT(*) 
FROM image_items WHERE url LIKE 'http%'
UNION ALL
SELECT 'video_items', COUNT(*) 
FROM video_items WHERE url LIKE 'http%' OR thumbnail_url LIKE 'http%'
UNION ALL
SELECT 'accounts', COUNT(*) 
FROM accounts WHERE avatar_url LIKE 'http%' OR cover_url LIKE 'http%'
UNION ALL
SELECT 'musics', COUNT(*) 
FROM musics WHERE audio_url LIKE 'http%';
```

---

## 🧪 TESTING

### 1. Build Project
```bash
mvn clean compile
```

MapStruct sẽ generate implementation cho các abstract mappers.

### 2. Test Upload
```java
// Upload file
String relativePath = s3Service.uploadFile(file, "posts");
// Result: "posts/550e8400-e29b-41d4-a716-446655440000.jpg"

// Save to DB
media.setUrl(relativePath);
mediaRepository.save(media);
```

### 3. Test Response
```java
// Get from DB
Media media = mediaRepository.findById(id);
// media.getUrl() = "posts/uuid.jpg"

// Convert to response
MediaResponse response = mediaMapper.toResponse(media);
// response.getUrl() = "https://social-riff-app-demo.s3.ap-southeast-1.amazonaws.com/posts/uuid.jpg"
```

---

## 📝 BẢNG TỔNG HỢP - CÁC BẢNG ĐÃ REFACTOR

| # | Bảng DB | Model | Cột URL | Mapper Updated | Status |
|---|---------|-------|---------|----------------|--------|
| 1 | `medias` | Media | `url` | ImageMediaMapper, VideoMediaMapper | ✅ |
| 2 | `video_medias` | VideoMedia | `url`, `thumbnailUrl` | VideoMediaMapper | ✅ |
| 3 | `image_items` | ImageItem | `url` | ImageItemMapper | ✅ |
| 4 | `video_items` | VideoItem | `url`, `thumbnailUrl` | VideoItemMapper | ✅ |
| 5 | `accounts` | Account | `avatarUrl`, `coverUrl` | UserAccountMapper, OfficialAccountMapper | ✅ |
| 6 | `musics` | Music | `audioUrl` | MusicMapper | ✅ |

**Tổng:** 6 bảng, 9 cột URL, 7 mappers đã update

---

## 🎯 LỢI ÍCH

1. ✅ **Linh hoạt:** Dễ dàng đổi bucket, region, hoặc dùng CDN
2. ✅ **Tiết kiệm:** Database nhỏ hơn (lưu path thay vì full URL)
3. ✅ **Bảo mật:** Có thể tạo presigned URLs cho private files
4. ✅ **Multi-env:** Dev/Staging/Prod có thể dùng base URL khác nhau
5. ✅ **Backward compatible:** Hoạt động với cả data cũ và mới

---

## ⚠️ LƯU Ý

1. **Không cần migration ngay:** Code mới tự động xử lý cả full URL và relative path
2. **S3 upload mới:** Từ giờ sẽ lưu relative path vào DB
3. **Response API:** Client vẫn nhận full URL như trước
4. **MapStruct generation:** Chạy `mvn compile` để generate mapper implementations

---

## 🚀 NEXT STEPS

1. **Build project:** `mvn clean compile`
2. **Test locally:** Upload file và verify response
3. **Optional:** Run migration script để convert data cũ
4. **Deploy:** Code sẵn sàng cho production

---

## 📞 SUPPORT

Nếu có vấn đề, kiểm tra:
1. MapStruct generated classes trong `target/generated-sources/annotations/`
2. S3ServiceImpl có return đúng relative path không
3. MediaUrlBuilder được inject vào mappers chưa
4. application.properties có đủ config chưa


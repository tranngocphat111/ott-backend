# 📌 Quick Reference - Media URL Refactoring

## 🎯 Tóm Tắt Nhanh

**Trước:** Database lưu full URL (`https://s3.amazonaws.com/.../posts/uuid.jpg`)  
**Sau:** Database lưu relative path (`posts/uuid.jpg`)  
**API Response:** Vẫn trả full URL cho client

---

## 🔧 Cách Hoạt Động

### Upload File
```java
// S3ServiceImpl.uploadFile() returns:
return fileKey;  // "posts/550e8400-e29b-41d4-a716-446655440000.jpg"
```

### Save to DB
```java
media.setUrl("posts/550e8400-e29b-41d4-a716-446655440000.jpg");
mediaRepository.save(media);
```

### Get Response
```java
// Mapper tự động convert
Media media = repository.findById(id);
// media.getUrl() = "posts/uuid.jpg"

MediaResponse response = mapper.toResponse(media);
// response.getUrl() = "https://social-riff-app-demo.s3.ap-southeast-1.amazonaws.com/posts/uuid.jpg"
```

---

## 📝 Configuration

### application.properties
```properties
# Base URL for building full URLs
aws.s3.base-url=https://social-riff-app-demo.s3.ap-southeast-1.amazonaws.com

# Folders
aws.s3.folder.posts=posts
aws.s3.folder.stories=stories
aws.s3.folder.avatars=avatars
aws.s3.folder.covers=covers
aws.s3.folder.videos=videos
aws.s3.folder.music=music
```

---

## 🗂️ Database Columns

| Table | Column | Example Value |
|-------|--------|---------------|
| medias | url | `posts/a1b2c3d4.jpg` |
| video_medias | url | `videos/e5f6g7h8.mp4` |
| video_medias | thumbnail_url | `videos/thumbnails/i9j0k1l2.jpg` |
| image_items | url | `stories/m3n4o5p6.jpg` |
| video_items | url | `stories/q7r8s9t0.mp4` |
| video_items | thumbnail_url | `stories/thumbnails/u1v2w3x4.jpg` |
| accounts | avatar_url | `avatars/y5z6a7b8.png` |
| accounts | cover_url | `covers/c9d0e1f2.jpg` |
| musics | audio_url | `music/g3h4i5j6.mp3` |

---

## 🔄 Mapper Pattern

### All Media Mappers Follow This Pattern:
```java
@Mapper(componentModel = "spring")
public abstract class [Type]Mapper {
    
    @Autowired
    protected MediaUrlBuilder mediaUrlBuilder;
    
    public abstract [Type]Response toResponse([Type] entity);
    
    @AfterMapping
    protected void buildFullUrls(@MappingTarget [Type]Response response, [Type] source) {
        if (source.getUrl() != null) {
            response.setUrl(convertToFullUrl(source.getUrl()));
        }
    }
    
    private String convertToFullUrl(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return null;
        }
        if (relativePath.startsWith("http")) {
            return relativePath; // Already full URL (backward compat)
        }
        return mediaUrlBuilder.buildS3Url("", relativePath);
    }
}
```

---

## 📋 Affected Mappers

1. **ImageMediaMapper** - posts images
2. **VideoMediaMapper** - posts videos (url + thumbnailUrl)
3. **MusicMapper** - music files
4. **ImageItemMapper** - story images
5. **VideoItemMapper** - story videos (url + thumbnailUrl)
6. **UserAccountMapper** - user avatars & covers
7. **OfficialAccountMapper** - official account avatars & covers

---

## 🧪 Testing

### Test Upload
```bash
curl -X POST http://localhost:8080/media/api/posts \
  -F "file=@image.jpg" \
  -F "caption=Test post"
```

**Expected DB:** `url = "posts/uuid.jpg"`  
**Expected Response:** `url = "https://...s3.amazonaws.com/posts/uuid.jpg"`

### Test Get Post
```bash
curl http://localhost:8080/media/api/posts/POST_ID
```

**Expected:** All media URLs are full URLs in response

---

## 🔍 Debugging

### Check Generated Mappers
```
target/generated-sources/annotations/mediaservice/mappers/
```

### Verify URL Conversion
```java
// In mapper implementation, should see:
imageMediaResponse.setUrl( imageMedia.getUrl() );  // Direct copy
buildFullUrls( imageMediaResponse, imageMedia );   // Then convert
```

### Test MediaUrlBuilder
```java
@Autowired
private MediaUrlBuilder mediaUrlBuilder;

String fullUrl = mediaUrlBuilder.buildS3Url("", "posts/uuid.jpg");
// Result: "https://social-riff-app-demo.s3.ap-southeast-1.amazonaws.com/posts/uuid.jpg"
```

---

## ⚡ Quick Commands

### Build
```bash
.\mvnw.cmd clean compile -DskipTests
```

### Run
```bash
.\mvnw.cmd spring-boot:run
```

### Package
```bash
.\mvnw.cmd clean package -DskipTests
```

---

## 🐛 Common Issues

### Issue: URLs not converting in response
**Check:**
1. MediaUrlBuilder is @Component
2. Mapper has @Autowired MediaUrlBuilder
3. @AfterMapping method is present
4. Build was successful (regenerate mappers)

### Issue: MapStruct applying to all fields
**Fix:** Use private method `convertToFullUrl()` instead of protected `buildFullUrl()`

### Issue: Backward compatibility not working
**Check:** Method checks for `http://` or `https://` prefix

---

## 📚 Documentation Files

- `BUILD_SUCCESS_REPORT.md` - Build results & verification
- `MIGRATION_GUIDE.md` - Detailed migration guide
- `REFACTORING_SUMMARY.md` - Overall summary
- `migration.sql` - Database migration script
- `QUICK_REFERENCE.md` - This file

---

## ✅ Success Indicators

- [x] Build success without errors
- [x] MapStruct generates 24 mapper implementations
- [x] URL fields convert to full URLs in responses
- [x] Non-URL fields remain unchanged
- [x] Backward compatible with old data

---

**Last Updated:** March 1, 2026  
**Build Status:** ✅ SUCCESS  
**Build Time:** 17.198s


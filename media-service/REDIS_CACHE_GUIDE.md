# 🚀 Hướng Dẫn Chạy Redis và Sử Dụng Cache

## 📋 Tổng Quan

**Trạng thái Redis Cache:** ✅ **ĐÃ THIẾT LẬP**

Chương trình đã có:
- ✅ RedisConfig với @EnableCaching
- ✅ RedisCacheManager
- ✅ RedisTemplate
- ✅ Caching annotations đã được thêm vào PostService và UserAccountService

---

## 🐳 Cách 1: Chạy Redis với Docker (Khuyến Nghị)

### Option A: Docker Desktop (Windows)

#### 1. Install Docker Desktop
- Download: https://www.docker.com/products/docker-desktop/
- Cài đặt và khởi động Docker Desktop

#### 2. Chạy Redis Container
```bash
# Pull Redis image
docker pull redis:latest

# Run Redis container
docker run -d ^
  --name redis-media-service ^
  -p 6379:6379 ^
  redis:latest

# Verify Redis is running
docker ps
```

#### 3. Test Connection
```bash
# Connect to Redis CLI
docker exec -it redis-media-service redis-cli

# Test commands
127.0.0.1:6379> ping
PONG

127.0.0.1:6379> set test "Hello Redis"
OK

127.0.0.1:6379> get test
"Hello Redis"

127.0.0.1:6379> exit
```

### Option B: Docker Compose (Tốt hơn cho development)

#### 1. Tạo file `docker-compose.yml`
```yaml
version: '3.8'

services:
  redis:
    image: redis:latest
    container_name: redis-media-service
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    command: redis-server --appendonly yes
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5

volumes:
  redis-data:
    driver: local
```

#### 2. Chạy Redis
```bash
# Start Redis
docker-compose up -d

# Check status
docker-compose ps

# View logs
docker-compose logs -f redis

# Stop Redis
docker-compose down

# Stop and remove data
docker-compose down -v
```

---

## 💻 Cách 2: Cài Redis Trực Tiếp trên Windows

### Option A: Windows Subsystem for Linux (WSL2)

#### 1. Enable WSL2
```powershell
# Run as Administrator
wsl --install
```

#### 2. Install Ubuntu from Microsoft Store
- Mở Microsoft Store
- Tìm "Ubuntu"
- Cài đặt

#### 3. Install Redis trong Ubuntu
```bash
# Update packages
sudo apt update

# Install Redis
sudo apt install redis-server

# Start Redis
sudo service redis-server start

# Check status
sudo service redis-server status

# Test connection
redis-cli ping
```

### Option B: Redis for Windows (Unofficial)

#### 1. Download Redis for Windows
- Link: https://github.com/microsoftarchive/redis/releases
- Download file `.msi` mới nhất (ví dụ: Redis-x64-3.0.504.msi)

#### 2. Install và Start
- Chạy file .msi
- Redis sẽ tự động start như Windows Service
- Default port: 6379

#### 3. Test
```cmd
# Open Command Prompt
redis-cli.exe ping
```

---

## ⚙️ Configuration trong Application

### application.properties (Đã có sẵn)
```properties
# Redis Server Settings
spring.data.redis.host=localhost
spring.data.redis.port=6379
# spring.data.redis.password=your_password_if_any

# Lettuce Connection Pool Settings
spring.data.redis.lettuce.pool.max-active=8
spring.data.redis.lettuce.pool.max-idle=8
spring.data.redis.lettuce.pool.min-idle=2
spring.data.redis.lettuce.pool.max-wait=1000ms

# Cache Configuration
spring.cache.type=redis
spring.cache.redis.time-to-live=600000
```

### RedisConfig.java (Đã có sẵn)
- ✅ @EnableCaching
- ✅ RedisCacheManager với TTL 10 phút
- ✅ JSON serialization
- ✅ RedisTemplate configured

---

## 📊 Cache đã được áp dụng

### PostService
```java
// Cache read operations
@Cacheable(value = "posts", key = "#id")
public PostResponse getPostById(String id)

@Cacheable(value = "allPosts")
public List<PostResponse> getAllPosts()

// Clear cache on updates
@CacheEvict(value = {"posts", "allPosts"}, key = "#id")
public PostResponse updatePost(String id, PostRequest request)

@CacheEvict(value = {"posts", "allPosts"}, key = "#id")
public void deletePost(String id)
```

### UserAccountService
```java
// Cache read operations
@Cacheable(value = "users", key = "#id")
public UserAccountResponse getUserAccountById(String id)

@Cacheable(value = "users", key = "'username:' + #username")
public UserAccountResponse getUserAccountByUsername(String username)

@Cacheable(value = "allUsers")
public List<UserAccountResponse> getAllUserAccounts()

// Clear cache on mutations
@CacheEvict(value = {"users", "allUsers"}, allEntries = true)
public UserAccountResponse createUserAccount(UserAccountRequest request)

@CacheEvict(value = {"users", "allUsers"}, key = "#id")
public UserAccountResponse updateUserAccount(String id, UserAccountRequest request)

@CacheEvict(value = {"users", "allUsers"}, key = "#id")
public void deleteUserAccount(String id)
```

---

## 🧪 Testing Cache

### 1. Start Redis
```bash
docker run -d --name redis-media-service -p 6379:6379 redis:latest
```

### 2. Start Application
```bash
.\mvnw.cmd spring-boot:run
```

### 3. Test Cache với API

#### Test Post Cache
```bash
# First call - hits database (slow)
curl http://localhost:8080/media/api/posts/{POST_ID}

# Second call - hits cache (fast!)
curl http://localhost:8080/media/api/posts/{POST_ID}
```

#### Test User Cache
```bash
# First call - hits database
curl http://localhost:8080/media/api/users/{USER_ID}

# Second call - hits cache
curl http://localhost:8080/media/api/users/{USER_ID}
```

### 4. Monitor Cache với Redis CLI

```bash
# Connect to Redis
docker exec -it redis-media-service redis-cli

# View all keys
127.0.0.1:6379> KEYS *

# Get specific cached value
127.0.0.1:6379> GET posts::{POST_ID}

# View cache info
127.0.0.1:6379> INFO stats

# Monitor real-time commands
127.0.0.1:6379> MONITOR

# Clear all cache
127.0.0.1:6379> FLUSHALL
```

---

## 📈 Xác Minh Cache Hoạt Động

### Dấu hiệu cache đang hoạt động:

1. **Logs trong application:**
```
[nio-8080-exec-1] o.s.cache.interceptor.CacheInterceptor : Cache hit for key 'posts::abc123'
```

2. **Response time:**
- Lần 1: 50-200ms (database query)
- Lần 2+: <5ms (from cache)

3. **Redis CLI:**
```bash
127.0.0.1:6379> KEYS posts::*
1) "posts::550e8400-e29b-41d4-a716-446655440000"

127.0.0.1:6379> TTL posts::550e8400-e29b-41d4-a716-446655440000
(integer) 587  # seconds remaining
```

---

## 🔧 Redis Management Tools

### Option 1: Redis Commander (Web UI)
```bash
# Install globally
npm install -g redis-commander

# Run
redis-commander --port 8081

# Access: http://localhost:8081
```

### Option 2: RedisInsight (GUI Desktop App)
- Download: https://redis.com/redis-enterprise/redis-insight/
- Powerful GUI with real-time monitoring

### Option 3: Another Redis Desktop Manager
- Download: https://github.com/qishibo/AnotherRedisDesktopManager/releases

---

## 🚀 Quick Start Commands

### Full Setup in 3 Commands:

```bash
# 1. Start Redis
docker run -d --name redis-media-service -p 6379:6379 redis:latest

# 2. Verify Redis
docker exec -it redis-media-service redis-cli ping

# 3. Start Application
.\mvnw.cmd spring-boot:run
```

**Done!** Cache đã hoạt động.

---

## 🐛 Troubleshooting

### Redis không kết nối được
```bash
# Check Redis is running
docker ps

# Check logs
docker logs redis-media-service

# Restart Redis
docker restart redis-media-service
```

### Cache không hoạt động
1. Check Redis connection trong application logs
2. Verify @EnableCaching trong RedisConfig
3. Ensure Redis is running on port 6379
4. Check application.properties configuration

### Clear cache khi testing
```bash
# Via Redis CLI
docker exec -it redis-media-service redis-cli FLUSHALL

# Or restart Redis
docker restart redis-media-service
```

---

## 📊 Performance Benefits

### Without Cache:
- Database query: 50-200ms
- Network latency to DB
- CPU overhead for query execution

### With Cache:
- First request: 50-200ms (cache miss)
- Subsequent requests: <5ms (cache hit)
- **10-40x faster!**
- Reduced database load
- Better scalability

### Cache TTL:
- Default: **10 minutes** (600 seconds)
- Configurable in RedisConfig or per cache

---

## 🎯 Best Practices

1. ✅ Use cache for frequently accessed data
2. ✅ Set appropriate TTL (time-to-live)
3. ✅ Clear cache on updates/deletes
4. ✅ Monitor cache hit rate
5. ✅ Use Redis persistence for important data
6. ✅ Configure max memory policy

---

## 📚 Additional Resources

- Redis Official Docs: https://redis.io/docs/
- Spring Cache: https://spring.io/guides/gs/caching/
- Redis Commands: https://redis.io/commands/

---

**Status:** ✅ Redis cache fully configured and ready to use!

**Next Steps:**
1. Start Redis container
2. Start application
3. Test API endpoints
4. Monitor cache performance


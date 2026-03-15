@echo off
echo ========================================
echo Redis Quick Start for Media Service
echo ========================================
echo.

echo Checking Docker...
docker --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker is not installed!
    echo Please install Docker Desktop from: https://www.docker.com/products/docker-desktop/
    pause
    exit /b 1
)

echo [OK] Docker is installed
echo.

echo Starting Redis with Docker Compose...
docker-compose up -d

echo.
echo Waiting for Redis to be ready...
timeout /t 5 /nobreak >nul

echo.
echo Checking Redis status...
docker exec redis-media-service redis-cli ping >nul 2>&1
if errorlevel 1 (
    echo [WARNING] Redis may not be ready yet. Wait a few seconds and try again.
) else (
    echo [OK] Redis is running!
)

echo.
echo ========================================
echo Redis Services:
echo ========================================
echo - Redis Server: localhost:6379
echo - Redis Commander (Web UI): http://localhost:8081
echo.

echo ========================================
echo Quick Commands:
echo ========================================
echo Stop Redis:     docker-compose down
echo View Logs:      docker-compose logs -f redis
echo Redis CLI:      docker exec -it redis-media-service redis-cli
echo Clear Cache:    docker exec redis-media-service redis-cli FLUSHALL
echo.

echo ========================================
echo Next Steps:
echo ========================================
echo 1. Start your application: mvnw.cmd spring-boot:run
echo 2. Test API endpoints
echo 3. Monitor cache: http://localhost:8081
echo.

pause


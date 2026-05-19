# Huong dan deploy ott-backend len AWS voi Docker microservice va GitHub CI/CD

Tai lieu nay duoc viet rieng cho `ott-backend` hien tai:

- Java Spring services: `api-gateway`, `auth-service`, `user-service`, `notification-service`, `media-service`, `analytic-service`.
- Node service: `chat-service`.
- Infrastructure dang chay trong Docker Compose: `redis`, `rabbitmq`, `analytic-postgres`.
- Gateway dang route `auth`, `user`, `notification`, `media`, `chat`, `ai` va `/socket.io`, nen production nen cho FE goi qua gateway/domain thay vi mo tung service ra Internet.

Muc tieu: khi push code len GitHub, service nao bi anh huong thi chi build image va restart service do. Khong build lai toan bo backend neu chi sua 1 service.

> Tai lieu AWS/GitHub nen kiem tra lai truoc khi tao resource vi Free Tier thay doi theo thoi gian. Tai thoi diem 14/05/2026, AWS Free Tier moi co Free Plan toi da 6 thang va credits toi da 200 USD; EC2 free eligible phu thuoc ngay tao account. Nguon tham khao nam o cuoi file.

---

## 1. Ket luan nhanh: may 2.6GB RAM co deploy duoc tren Free Tier khong?

Anh Docker Desktop cua ban dang chay 13 containers va dung khoang `2.6GB / 7.45GB` RAM khi gan nhu idle. Day moi la luc local, chua tinh:

- RAM cua he dieu hanh EC2.
- Docker daemon, Nginx, CloudWatch/SSM agent.
- Spike khi Spring Boot warm up.
- Spike xu ly file/video cua `media-service` vi service nay co `ffmpeg`.
- Redis/RabbitMQ/Postgres buffer.

Voi full stack nhu hien tai:

- `t3.micro`/`t4g.micro` 1GB RAM: khong nen. Gan nhu chac chan OOM.
- `t3.small`/`t4g.small` 2GB RAM: chi nen dung staging nhe, phai offload DB, bat swap, gioi han heap Java, co the van crash khi media xu ly file.
- `c7i-flex.large` 4GB RAM neu AWS Console cua ban hien `Free tier eligible`: nen chon cho full stack tiet kiem, vi 4GB la muc toi thieu thuc te de chay Docker Compose mot may.
- `m7i-flex.large` 8GB RAM neu AWS Console cua ban hien `Free tier eligible`: on dinh hon cho full stack, upload media, websocket va analytics, nhung hourly price cao hon nen se an credits nhanh hon.
- `t3.medium`/`t4g.medium` 4GB RAM: chi chon neu region/account cua ban khong co `c7i-flex.large` free eligible.

Neu ban chi dung Free Plan 6 thang va muon can tien:

1. Phuong an tiet kiem nhat: deploy staging tren `t3.small`/`t4g.small`, bat swap 2GB, tat cac service chua can nhu `analytic-service` hoac `media-service` khi demo khong dung.
2. Phuong an nen dung cho full stack theo anh AWS Console cua ban: `c7i-flex.large` 4GB. No dang duoc danh dau `Free tier eligible`, phu hop hon `t3.small` 2GB cho backend nay.
3. Phuong an production ve sau: tach DB/cache/message broker sang managed services hoac ECS, nhung khong hop voi muc tieu tiet kiem Free Tier luc nay.

Uoc tinh voi 100 USD credits va gia trong anh cua ban:

| Cach chay `c7i-flex.large` 4GB | Chi phi uoc tinh | 100 USD dung duoc khoang |
| --- | ---: | ---: |
| Chi tinh EC2 Linux `0.09778 USD/gio` | 2.35 USD/ngay | 42.6 ngay |
| EC2 + 1 public IPv4 `0.005 USD/gio` | 2.47 USD/ngay | 40.6 ngay |
| Chay 12 gio/ngay, giu Elastic IP 24/7 | 1.29 USD/ngay | 77.5 ngay |
| Chay 8 gio/ngay, giu Elastic IP 24/7 | 0.90 USD/ngay | 110.8 ngay |
| Chay 6 gio/ngay, giu Elastic IP 24/7 | 0.71 USD/ngay | 141.5 ngay |
| Chay 4 gio/ngay, giu Elastic IP 24/7 | 0.51 USD/ngay | gan 6 thang, bi gioi han boi Free Plan |

Neu khong can public IP co dinh khi tat may, tranh giu Elastic IP luc instance stopped. EBS, ECR, S3, log va data transfer co the tru them credits, nen thuc te nen tru hao 5-15% neu deploy thuong xuyen.

Luu y ECR private Free Tier chi cho dung luong nho; 7 service image Java/Node se vuot rat nhanh neu giu nhieu tag. Bat buoc dat lifecycle policy giu 5 image gan nhat moi repository.

---

## 2. Kien truc de xuat

Dung mot EC2 instance cai Docker Compose:

- GitHub Actions build image tung service.
- Day image vao Amazon ECR, moi service 1 repository rieng.
- GitHub Actions dung OIDC de lay AWS credential ngan han, khong luu AWS access key dai han trong GitHub.
- Deploy bang AWS Systems Manager Run Command, khong can mo SSH public.
- EC2 pull image tu ECR va chay `docker compose up -d --no-deps <service>`.

So do:

```text
GitHub push
  -> GitHub Actions paths-filter
  -> chi service thay doi moi duoc build
  -> push image len ECR
  -> SSM Run Command vao EC2
  -> /opt/ott/deploy-service.sh <service> <image-uri>
  -> docker compose pull service
  -> docker compose up -d --no-deps service
```

Vi sao chon cach nay:

- It dich vu AWS nhat, de quan ly chi phi.
- Van ton trong microservice: moi service co Dockerfile, image, ECR repo, deploy unit rieng.
- Build chay tren GitHub runner, khong build tren EC2 nen EC2 khong bi an RAM/CPU khi CI.
- Neu sau nay len ECS, cau truc ECR + path-based build van dung lai duoc.

---

## 3. Cac service va mapping production

| Compose service | Thu muc | Cong noi bo | ECR repository de xuat | Ghi chu |
| --- | --- | ---: | --- | --- |
| `api-gateway` | `api-gateway` | 8080 | `ott-api-gateway` | Chi service nay nen public qua Nginx |
| `auth-service` | `auth-service` | 8081 | `ott-auth-service` | Java 21 |
| `user-service` | `user-service` | 8082 | `ott-user-service` | Java 21 |
| `notification-service` | `notification-service` | 8083 | `ott-notification-service` | Java 21 |
| `media-service` | `media-service` | 8090/8091 | `ott-media-service` | Java 17, co `ffmpeg`, Dockerfile dang `EXPOSE 8080` nhung app chay `8090` |
| `chat-service` | `chat-service` | 5000 | `ott-chat-service` | Node 20, Socket.IO, AI, MongoDB |
| `analytic-service` | `analytic-service` | 8092 | `ott-analytic-service` | Java 21 |
| `redis` | Docker image public | 6379 | Khong can ECR | Chi noi bo Docker network |
| `rabbitmq` | Docker image public | 5672/15672 | Khong can ECR | Khong mo public |
| `analytic-postgres` | Docker image public | 5432 | Khong can ECR | Chi cho analytics, can backup volume |

Trong code hien tai, `auth`, `user`, `notification`, `media` dang dung DB URL qua bien moi truong. `chat-service` dung `MONGO_URI`. Nghia la EC2 khong nhat thiet phai chay tat ca database, tru `analytic-postgres` trong compose hien tai.

Can sua sau:

- `media-service/Dockerfile` nen doi `EXPOSE 8080` thanh `EXPOSE 8090 8091` de dung voi `application.properties`.
- `media-service/src/main/resources/application.properties` dang co fallback DB credential that trong file. Hay rotate credential do va xoa default sensitive value khoi code. Production phai lay tu `.env`, GitHub Secrets, AWS SSM Parameter Store hoac Secrets Manager.

---

## 4. Chuan bi AWS de khong vuot Free Tier

### 4.1 Chon region

Neu database/S3 hien tai dang o Singapore, chon:

```text
ap-southeast-1
```

Dung cung region cho EC2, ECR, S3 de giam latency va tranh phi transfer cheo region.

### 4.2 Tao budget alert truoc khi tao EC2

Vao AWS Console:

1. Billing and Cost Management.
2. Budgets.
3. Create budget.
4. Chon Monthly cost budget.
5. Dat nguong canh bao vi du `5 USD`, `10 USD`, `20 USD`.
6. Them email cua ban.

Khong bo qua buoc nay. Free Plan/credits khong co nghia la duoc deploy full stack ma khong can theo doi.

### 4.3 Chon instance

Theo anh AWS Console cua ban, cac instance sau dang duoc danh dau `Free tier eligible`:

| Instance | RAM | Nen dung cho ott-backend khong? |
| --- | ---: | --- |
| `t3.micro` | 1GB | Khong nen, full stack se OOM |
| `t3.small` | 2GB | Co the dung staging/toi gian, nhung khong on cho full stack |
| `c7i-flex.large` | 4GB | Nen chon neu muon chay full stack tiet kiem |
| `m7i-flex.large` | 8GB | Tot hon 4GB, nhung an credits nhanh hon |

Ket luan: neu muon deploy du backend hien tai, hay chon `c7i-flex.large` truoc. Neu sau khi chay `docker stats` RAM van sat tran, chuyen sang `m7i-flex.large`.

Neu uu tien de build/deploy don gian va khong dung cac goi flex free eligible:

- Chon `t3.medium`, architecture `x86_64`, Docker build platform `linux/amd64`.

Neu uu tien gia/credits tot hon va chap nhan ARM:

- Chon `t4g.medium`, architecture `arm64`, Docker build platform `linux/arm64`.
- Cac base image trong repo (`eclipse-temurin`, `maven`, `node`, `redis`, `rabbitmq`, `postgres`) thuong co multi-arch, nhung can test ky.

Neu bat buoc dung instance Free Tier nho:

- Dung `t3.small`/`t4g.small` de staging.
- Bat swap.
- Giam heap Java.
- Khong chay full media/analytics neu khong can.

### 4.4 EBS

Dung volume `gp3` 60GB de bat dau cho stack hien tai. 30GB chi du luc moi deploy, nhung neu CI/CD push nhieu tag Docker thi `/var/lib/docker` se phinh rat nhanh. Media van nen nam tren S3, khong luu file upload truc tiep tren EC2.

Sau khi modify EBS tren AWS Console, Ubuntu chua tu mo rong filesystem ngay. Chay:

```bash
cd ~/ott-backend
sudo bash deploy/aws/grow-root-volume-ubuntu.sh
```

Neu repo tren EC2 la repo cha `ott-project`, chay trong thu muc `ott-backend` ben trong:

```bash
cd ~/ott-backend/ott-backend
sudo bash deploy/aws/grow-root-volume-ubuntu.sh
```

Neu OS chua nhan size moi, chay:

```bash
sudo partprobe /dev/nvme0n1 || true
sudo reboot
```

### 4.5 Security Group

Inbound:

| Port | Source | Ly do |
| ---: | --- | --- |
| 80 | `0.0.0.0/0`, `::/0` | HTTP de Certbot cap SSL |
| 443 | `0.0.0.0/0`, `::/0` | HTTPS public |
| 22 | IP nha ban/khong mo | Chi can neu dung SSH; neu dung SSM thi khong can |
| 8080 | IP cua ban tam thoi | Chi mo khi test gateway truc tiep, sau do dong |

Khong mo public:

- `5432` Postgres.
- `6379` Redis.
- `5672` RabbitMQ.
- `15672` RabbitMQ management.
- `5000` chat-service.
- `8090`, `8091`, `8092` neu da proxy qua gateway/Nginx.

---

## 5. Tao ECR repository moi service

Cai AWS CLI local hoac dung AWS CloudShell.

Dat bien:

```bash
export AWS_REGION=ap-southeast-1
export AWS_ACCOUNT_ID=<aws-account-id-cua-ban>
```

Tao repositories:

```bash
for repo in \
  ott-api-gateway \
  ott-auth-service \
  ott-user-service \
  ott-notification-service \
  ott-media-service \
  ott-chat-service \
  ott-analytic-service
do
  aws ecr create-repository \
    --region "$AWS_REGION" \
    --repository-name "$repo" \
    --image-scanning-configuration scanOnPush=true \
    --encryption-configuration encryptionType=AES256
done
```

Tao lifecycle policy giu 5 image gan nhat de tranh ECR phinh dung luong:

```json
{
  "rules": [
    {
      "rulePriority": 1,
      "description": "Keep only last 5 images",
      "selection": {
        "tagStatus": "any",
        "countType": "imageCountMoreThan",
        "countNumber": 5
      },
      "action": {
        "type": "expire"
      }
    }
  ]
}
```

Luu thanh `ecr-lifecycle.json`, roi chay:

```bash
for repo in \
  ott-api-gateway \
  ott-auth-service \
  ott-user-service \
  ott-notification-service \
  ott-media-service \
  ott-chat-service \
  ott-analytic-service
do
  aws ecr put-lifecycle-policy \
    --region "$AWS_REGION" \
    --repository-name "$repo" \
    --lifecycle-policy-text file://ecr-lifecycle.json
done
```

---

## 6. Tao IAM roles

Can 2 role:

- Role cho GitHub Actions: push ECR va gui SSM command.
- Role cho EC2: pull ECR va nhan SSM command.

### 6.1 Role cho EC2

Tao IAM Role:

1. IAM -> Roles -> Create role.
2. Trusted entity: AWS service.
3. Use case: EC2.
4. Attach policies:
   - `AmazonSSMManagedInstanceCore`
   - `AmazonEC2ContainerRegistryReadOnly`
5. Dat ten: `ott-backend-ec2-role`.

Gan role nay vao EC2 instance khi launch.

### 6.2 OIDC provider cho GitHub Actions

Trong IAM -> Identity providers:

- Provider type: OpenID Connect.
- Provider URL: `https://token.actions.githubusercontent.com`
- Audience: `sts.amazonaws.com`

### 6.3 Role cho GitHub Actions

Tao role `ott-github-actions-deploy-role` voi trust policy dang mau sau. Thay `OWNER`, `REPO`, `main` dung GitHub repo cua ban.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::<AWS_ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
          "token.actions.githubusercontent.com:sub": "repo:OWNER/REPO:ref:refs/heads/main"
        }
      }
    }
  ]
}
```

Gan inline permission toi thieu:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "EcrPushPull",
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:CompleteLayerUpload",
        "ecr:DescribeImages",
        "ecr:DescribeRepositories",
        "ecr:InitiateLayerUpload",
        "ecr:PutImage",
        "ecr:UploadLayerPart"
      ],
      "Resource": "*"
    },
    {
      "Sid": "FindDeployInstance",
      "Effect": "Allow",
      "Action": [
        "ec2:DescribeInstances"
      ],
      "Resource": "*"
    },
    {
      "Sid": "StartTaggedDeployInstance",
      "Effect": "Allow",
      "Action": [
        "ec2:StartInstances"
      ],
      "Resource": "arn:aws:ec2:*:<AWS_ACCOUNT_ID>:instance/*",
      "Condition": {
        "StringEquals": {
          "aws:ResourceTag/Name": "ott-backend-prod"
        }
      }
    },
    {
      "Sid": "DeployWithSsm",
      "Effect": "Allow",
      "Action": [
        "ssm:SendCommand"
      ],
      "Resource": [
        "arn:aws:ec2:*:<AWS_ACCOUNT_ID>:instance/*",
        "arn:aws:ssm:*:*:document/AWS-RunShellScript"
      ]
    },
    {
      "Sid": "ReadSsmCommandStatus",
      "Effect": "Allow",
      "Action": [
        "ssm:GetCommandInvocation",
        "ssm:DescribeInstanceInformation"
      ],
      "Resource": "*"
    }
  ]
}
```

Sau do vao GitHub repo:

- Settings -> Secrets and variables -> Actions -> Variables.
- Them:
  - `AWS_ACCOUNT_ID=<aws-account-id>`
  - `AWS_REGION=ap-southeast-1`
  - `AWS_DEPLOY_ROLE_ARN=arn:aws:iam::<AWS_ACCOUNT_ID>:role/ott-github-actions-deploy-role`
  - `EC2_TAG_NAME=ott-backend-prod`
  - `IMAGE_PLATFORM=linux/amd64` neu dung `t3.*`, hoac `linux/arm64` neu dung `t4g.*`.

Khong tao `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` dai han trong GitHub neu dung OIDC.

---

## 7. Launch EC2 va cai Docker

Launch EC2:

- Name tag: `ott-backend-prod`.
- AMI: Ubuntu Server 24.04 LTS hoac 22.04 LTS.
- Instance type: `t3.medium` hoac `t4g.medium`.
- IAM instance profile: `ott-backend-ec2-role`.
- EBS: `30GB gp3`.
- Security group nhu muc 4.5.

SSH vao may hoac dung EC2 Instance Connect/SSM Session Manager.

Cap nhat package:

```bash
sudo apt-get update
sudo apt-get upgrade -y
sudo apt-get install -y ca-certificates curl gnupg unzip jq nginx certbot python3-certbot-nginx
```

Cai Docker:

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
sudo systemctl enable --now docker
```

Neu instance la `t4g.*`, AWS CLI zip can dung ban ARM64:

```bash
curl "https://awscli.amazonaws.com/awscli-exe-linux-aarch64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install
```

Neu instance la `t3.*`, dung ban x86_64:

```bash
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install
```

Bat swap 2GB de tranh OOM dot ngot:

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

Tao thu muc deploy:

```bash
sudo mkdir -p /opt/ott
sudo chown -R ubuntu:ubuntu /opt/ott
cd /opt/ott
```

Kiem tra SSM da nhan instance:

```bash
aws ssm describe-instance-information --region ap-southeast-1
```

Neu khong thay instance, kiem tra:

- EC2 da gan role `AmazonSSMManagedInstanceCore`.
- Instance co outbound Internet qua public subnet/NAT.
- SSM Agent dang chay.

---

## 8. Tao runtime env tren EC2

Tao `/opt/ott/.env`. File nay khong commit len GitHub.

```bash
nano /opt/ott/.env
```

Mau bien can co:

```properties
AWS_REGION=ap-southeast-1
AWS_ACCOUNT_ID=<aws-account-id>
TZ=Asia/Ho_Chi_Minh

FRONTEND_URL=https://app.example.com
JWT_SECRET=<long-random-secret>
JWT_EXPIRATION=3600
JWT_REFRESH_EXPIRATION=86400
INTERNAL_API_KEY=<long-random-internal-key>

AUTH_DB_URL=jdbc:postgresql://...
AUTH_DB_USERNAME=...
AUTH_DB_PASSWORD=...

USER_DB_URL=jdbc:postgresql://...
USER_DB_USERNAME=...
USER_DB_PASSWORD=...

NOTIF_DB_URL=jdbc:postgresql://...
NOTIF_DB_USERNAME=...
NOTIF_DB_PASSWORD=...

SOCIAL_DB_URL=jdbc:postgresql://...
SOCIAL_DB_USERNAME=...
SOCIAL_DB_PASSWORD=...

MONGO_URI=mongodb+srv://...

REDIS_PASSWORD=<redis-password>
RABBITMQ_USERNAME=<rabbit-user>
RABBITMQ_PASSWORD=<rabbit-password>

MAIL_USERNAME=...
MAIL_PASSWORD=...
MAIL_FROM=noreply@example.com
MAIL_FROM_NAME=Riff

GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...

AWS_ACCESS_KEY_ID=...
AWS_SECRET_ACCESS_KEY=...
AWS_S3_BUCKET_NAME=...
AWS_DEFAULT_AVATAR_URL=...
AWS_DEFAULT_COVER_PHOTO_URL=...

AWS_SOCIAL_ACCESS_KEY_ID=...
AWS_SOCIAL_SECRET_ACCESS_KEY=...
AWS_SOCIAL_REGION=ap-southeast-1
AWS_SOCIAL_S3_BUCKET_NAME=...
AWS_SOCIAL_S3_BASE_URL=https://<bucket>.s3.ap-southeast-1.amazonaws.com

ANALYTIC_DB_NAME=admin_analytic_db
ANALYTIC_DB_USERNAME=postgres
ANALYTIC_DB_PASSWORD=<analytic-postgres-password>
ANALYTIC_ALLOWED_ORIGINS=https://app.example.com

GROQ_API_KEY=...
LIVEKIT_API_KEY=...
LIVEKIT_API_SECRET=...
LIVEKIT_URL=wss://...
```

Phan URL noi bo giua service se set trong Compose production, khong can de FE biet.

Tao `/opt/ott/.env.images` cho image hien tai. Lan dau co the dat tag `latest`, sau CI se thay tung dong bang tag commit SHA:

```properties
API_GATEWAY_IMAGE=<aws-account-id>.dkr.ecr.ap-southeast-1.amazonaws.com/ott-api-gateway:latest
AUTH_SERVICE_IMAGE=<aws-account-id>.dkr.ecr.ap-southeast-1.amazonaws.com/ott-auth-service:latest
USER_SERVICE_IMAGE=<aws-account-id>.dkr.ecr.ap-southeast-1.amazonaws.com/ott-user-service:latest
NOTIFICATION_SERVICE_IMAGE=<aws-account-id>.dkr.ecr.ap-southeast-1.amazonaws.com/ott-notification-service:latest
MEDIA_SERVICE_IMAGE=<aws-account-id>.dkr.ecr.ap-southeast-1.amazonaws.com/ott-media-service:latest
CHAT_SERVICE_IMAGE=<aws-account-id>.dkr.ecr.ap-southeast-1.amazonaws.com/ott-chat-service:latest
ANALYTIC_SERVICE_IMAGE=<aws-account-id>.dkr.ecr.ap-southeast-1.amazonaws.com/ott-analytic-service:latest
```

---

## 9. Tao Docker Compose production tren EC2

Tao `/opt/ott/docker-compose.prod.yml`:

```yaml
services:
  analytic-postgres:
    image: postgres:15-alpine
    container_name: analytic-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${ANALYTIC_DB_NAME}
      POSTGRES_USER: ${ANALYTIC_DB_USERNAME}
      POSTGRES_PASSWORD: ${ANALYTIC_DB_PASSWORD}
    volumes:
      - analytic_postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${ANALYTIC_DB_USERNAME} -d ${ANALYTIC_DB_NAME}"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - ott-network

  redis:
    image: redis:7-alpine
    container_name: ott-redis
    restart: unless-stopped
    command: redis-server --requirepass ${REDIS_PASSWORD}
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "${REDIS_PASSWORD}", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - ott-network

  rabbitmq:
    image: rabbitmq:3.12-management-alpine
    container_name: ott-rabbitmq
    restart: unless-stopped
    environment:
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_USERNAME}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD}
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - ott-network

  notification-service:
    image: ${NOTIFICATION_SERVICE_IMAGE}
    container_name: ott-notification
    restart: unless-stopped
    env_file:
      - .env
    environment:
      JAVA_TOOL_OPTIONS: -XX:+UseContainerSupport -XX:MaxRAMPercentage=60.0 -XX:InitialRAMPercentage=20.0
      REDIS_HOST: redis
      REDIS_PORT: 6379
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_PORT: 5672
    depends_on:
      redis:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
    networks:
      - ott-network

  user-service:
    image: ${USER_SERVICE_IMAGE}
    container_name: ott-user
    restart: unless-stopped
    env_file:
      - .env
    environment:
      JAVA_TOOL_OPTIONS: -XX:+UseContainerSupport -XX:MaxRAMPercentage=60.0 -XX:InitialRAMPercentage=20.0
      REDIS_HOST: redis
      REDIS_PORT: 6379
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_PORT: 5672
      NOTIF_SERVICE_URL: http://notification-service:8083
      AUTH_SERVICE_URL: http://auth-service:8081
    depends_on:
      redis:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
    networks:
      - ott-network

  auth-service:
    image: ${AUTH_SERVICE_IMAGE}
    container_name: ott-auth
    restart: unless-stopped
    env_file:
      - .env
    environment:
      JAVA_TOOL_OPTIONS: -XX:+UseContainerSupport -XX:MaxRAMPercentage=60.0 -XX:InitialRAMPercentage=20.0
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_PORT: 5672
      USER_SERVICE_URL: http://user-service:8082
      NOTIF_SERVICE_URL: http://notification-service:8083
    depends_on:
      rabbitmq:
        condition: service_healthy
    networks:
      - ott-network

  media-service:
    image: ${MEDIA_SERVICE_IMAGE}
    container_name: ott-media
    restart: unless-stopped
    env_file:
      - .env
    environment:
      JAVA_TOOL_OPTIONS: -XX:+UseContainerSupport -XX:MaxRAMPercentage=60.0 -XX:InitialRAMPercentage=20.0
      REDIS_HOST: redis
      REDIS_PORT: 6379
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_PORT: 5672
      USER_SERVICE_URL: http://user-service:8082
      RELATIONSHIP_SOCKET_HOST: https://relationship.example.com
      RELATIONSHIP_SOCKET_PORT: 443
    ports:
      - "127.0.0.1:8091:8091"
    depends_on:
      redis:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
    networks:
      - ott-network

  chat-service:
    image: ${CHAT_SERVICE_IMAGE}
    container_name: ott-chat
    restart: unless-stopped
    env_file:
      - .env
    environment:
      NODE_ENV: production
      PORT: 5000
      REDIS_HOST: redis
      REDIS_PORT: 6379
      REDIS_URL: redis://:${REDIS_PASSWORD}@redis:6379/0
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_PORT: 5672
      RABBITMQ_USERNAME: ${RABBITMQ_USERNAME}
      RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}
    depends_on:
      redis:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
    networks:
      - ott-network

  analytic-service:
    image: ${ANALYTIC_SERVICE_IMAGE}
    container_name: ott-analytic
    restart: unless-stopped
    env_file:
      - .env
    environment:
      JAVA_TOOL_OPTIONS: -XX:+UseContainerSupport -XX:MaxRAMPercentage=60.0 -XX:InitialRAMPercentage=20.0
      ANALYTIC_SERVER_PORT: 8092
      ANALYTIC_DB_URL: jdbc:postgresql://analytic-postgres:5432/${ANALYTIC_DB_NAME}
      ANALYTIC_RABBITMQ_HOST: rabbitmq
      ANALYTIC_RABBITMQ_PORT: 5672
      ANALYTIC_RABBITMQ_USERNAME: ${RABBITMQ_USERNAME}
      ANALYTIC_RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}
      USER_SERVICE_URL: http://user-service:8082
    depends_on:
      analytic-postgres:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
    networks:
      - ott-network

  api-gateway:
    image: ${API_GATEWAY_IMAGE}
    container_name: ott-gateway
    restart: unless-stopped
    env_file:
      - .env
    environment:
      JAVA_TOOL_OPTIONS: -XX:+UseContainerSupport -XX:MaxRAMPercentage=60.0 -XX:InitialRAMPercentage=20.0
      AUTH_SERVICE_URL: http://auth-service:8081
      USER_SERVICE_URL: http://user-service:8082
      NOTIF_SERVICE_URL: http://notification-service:8083
      MEDIA_SERVICE_URL: http://media-service:8090
      CHAT_SERVICE_URL: http://chat-service:5000
    ports:
      - "127.0.0.1:8080:8080"
    depends_on:
      - auth-service
      - user-service
      - notification-service
      - media-service
      - chat-service
    networks:
      - ott-network

volumes:
  analytic_postgres_data:
  redis_data:
  rabbitmq_data:

networks:
  ott-network:
    driver: bridge
```

Neu dang chay tren may 2GB va bi OOM, them `mem_limit` vao cac Java service:

```yaml
mem_limit: 384m
```

Nhung day chi la cach chong chay. Neu service can xu ly tai that, 2GB van khong phai cau hinh on.

---

## 10. Cau hinh Nginx va SSL

Khuyen nghi dung domain:

- `api.example.com` -> gateway.
- `relationship.example.com` -> socket rieng cua media-service neu FE dang dung `VITE_RELATIONSHIP_SOCKET_URL`.
- `app.example.com` -> frontend web.

Tao `/etc/nginx/sites-available/ott-api`:

```nginx
server {
    listen 80;
    server_name api.example.com;

    client_max_body_size 200m;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 3600;
        proxy_send_timeout 3600;
    }
}
```

Neu can media relationship socket rieng:

```nginx
server {
    listen 80;
    server_name relationship.example.com;

    location / {
        proxy_pass http://127.0.0.1:8091;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 3600;
        proxy_send_timeout 3600;
    }
}
```

Enable site:

```bash
sudo ln -s /etc/nginx/sites-available/ott-api /etc/nginx/sites-enabled/ott-api
sudo nginx -t
sudo systemctl reload nginx
```

Cap SSL:

```bash
sudo certbot --nginx -d api.example.com
sudo certbot --nginx -d relationship.example.com
```

Sau khi co SSL, FE nen cau hinh:

Web:

```properties
VITE_API_URL=https://api.example.com/riff/api
VITE_FRONTEND_URL=https://app.example.com
VITE_RELATIONSHIP_SOCKET_URL=https://relationship.example.com
VITE_MEDIA_SOCKET_URL=https://relationship.example.com
VITE_LIVEKIT_URL=wss://...
```

Mobile:

```properties
EXPO_PUBLIC_API_URL=https://api.example.com/riff/api
EXPO_PUBLIC_WEB_URL=https://app.example.com
```

Voi web hien tai, `SOCKET_CHAT_SERVER_URL` duoc tinh tu `VITE_API_URL.replace("/riff/api", "")`, nen neu dat `VITE_API_URL=https://api.example.com/riff/api` thi chat socket se di vao `https://api.example.com/socket.io`, dung voi route gateway hien co.

Neu deploy web len Vercel khi backend chua co HTTPS/domain rieng, dung proxy same-origin trong `ott-frontend-web/vercel.json`:

```properties
VITE_API_URL=/riff/api
VITE_FRONTEND_URL=https://your-vercel-project.vercel.app
VITE_LIVEKIT_URL=wss://chat-service-wplw6oap.livekit.cloud
```

Gateway/chat-service can cho phep origin local va Vercel:

```properties
FRONTEND_URL=http://localhost:5173
FRONTEND_URL_ALT=http://127.0.0.1:5173
FRONTEND_URL_DEPLOYED=https://*.vercel.app
CHAT_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173,https://*.vercel.app
```

Khi da co domain HTTPS that cho backend, nen doi web ve cau hinh truc tiep:

```properties
VITE_API_URL=https://api.example.com/riff/api
```

---

## 11. Tao script deploy tung service tren EC2

Tao `/opt/ott/deploy-service.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

SERVICE="${1:?Missing compose service name}"
IMAGE_URI="${2:?Missing image uri}"
APP_DIR="/opt/ott"
COMPOSE_FILE="$APP_DIR/docker-compose.prod.yml"
IMAGES_FILE="$APP_DIR/.env.images"
AWS_REGION="${AWS_REGION:-ap-southeast-1}"

case "$SERVICE" in
  api-gateway) IMAGE_KEY="API_GATEWAY_IMAGE" ;;
  auth-service) IMAGE_KEY="AUTH_SERVICE_IMAGE" ;;
  user-service) IMAGE_KEY="USER_SERVICE_IMAGE" ;;
  notification-service) IMAGE_KEY="NOTIFICATION_SERVICE_IMAGE" ;;
  media-service) IMAGE_KEY="MEDIA_SERVICE_IMAGE" ;;
  chat-service) IMAGE_KEY="CHAT_SERVICE_IMAGE" ;;
  analytic-service) IMAGE_KEY="ANALYTIC_SERVICE_IMAGE" ;;
  *)
    echo "Unknown service: $SERVICE" >&2
    exit 1
    ;;
esac

cd "$APP_DIR"
touch "$IMAGES_FILE"

if grep -q "^${IMAGE_KEY}=" "$IMAGES_FILE"; then
  sed -i "s|^${IMAGE_KEY}=.*|${IMAGE_KEY}=${IMAGE_URI}|" "$IMAGES_FILE"
else
  echo "${IMAGE_KEY}=${IMAGE_URI}" >> "$IMAGES_FILE"
fi

ECR_REGISTRY="${IMAGE_URI%%/*}"
aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$ECR_REGISTRY"

docker compose --env-file .env --env-file .env.images -f "$COMPOSE_FILE" pull "$SERVICE"
docker compose --env-file .env --env-file .env.images -f "$COMPOSE_FILE" up -d --no-deps "$SERVICE"
docker image prune -f

docker compose --env-file .env --env-file .env.images -f "$COMPOSE_FILE" ps "$SERVICE"
```

Cap quyen:

```bash
chmod +x /opt/ott/deploy-service.sh
```

Lenh deploy manual mau:

```bash
/opt/ott/deploy-service.sh chat-service <aws-account-id>.dkr.ecr.ap-southeast-1.amazonaws.com/ott-chat-service:<git-sha>
```

---

## 12. First deploy lan dau

Lan dau can co image cho tat ca service trong ECR.

Cach de nhat:

1. Tao workflow GitHub Actions o muc 13.
2. Push len branch `main`.
3. Vao tab Actions.
4. Chon workflow `backend microservice ci cd`.
5. Run workflow voi `force_all=true`.

Khi workflow build xong, vao EC2:

```bash
cd /opt/ott
docker compose --env-file .env --env-file .env.images -f docker-compose.prod.yml pull
docker compose --env-file .env --env-file .env.images -f docker-compose.prod.yml up -d
docker compose --env-file .env --env-file .env.images -f docker-compose.prod.yml ps
```

Xem log:

```bash
docker compose --env-file .env --env-file .env.images -f docker-compose.prod.yml logs -f api-gateway
docker compose --env-file .env --env-file .env.images -f docker-compose.prod.yml logs -f chat-service
```

Test gateway:

```bash
curl -i https://api.example.com/riff/api/auth/health
```

Neu khong co endpoint health, test mot endpoint public that cua ban.

---

## 13. GitHub Actions: build va deploy dung service thay doi

Tao file trong backend repo:

```text
.github/workflows/backend-ci-cd.yml
```

Repo hien da co file workflow that tai duong dan tren. Neu co khac nhau giua block mau ben duoi va file trong repo, hay uu tien file `.github/workflows/backend-ci-cd.yml` vi file do da co them che do `start_instance=true` va buoc cho SSM online khi ban bat/tat EC2 de tiet kiem credits.

Neu GitHub repo root la `ott-project` thay vi `ott-backend`, them prefix `ott-backend/` vao tat ca path va context trong workflow.

Noi dung mau:

```yaml
name: backend microservice ci cd

on:
  push:
    branches:
      - main
    paths:
      - "api-gateway/**"
      - "auth-service/**"
      - "user-service/**"
      - "notification-service/**"
      - "media-service/**"
      - "chat-service/**"
      - "analytic-service/**"
      - "docker-compose.yml"
      - ".dockerignore"
      - ".github/workflows/backend-ci-cd.yml"
  workflow_dispatch:
    inputs:
      force_all:
        description: "Build and deploy all services"
        type: boolean
        required: false
        default: false

permissions:
  id-token: write
  contents: read

env:
  AWS_ACCOUNT_ID: ${{ vars.AWS_ACCOUNT_ID }}
  AWS_REGION: ${{ vars.AWS_REGION }}
  AWS_DEPLOY_ROLE_ARN: ${{ vars.AWS_DEPLOY_ROLE_ARN }}
  EC2_TAG_NAME: ${{ vars.EC2_TAG_NAME }}
  IMAGE_PLATFORM: ${{ vars.IMAGE_PLATFORM || 'linux/amd64' }}

jobs:
  changes:
    name: detect changed services
    runs-on: ubuntu-latest
    outputs:
      services: ${{ steps.matrix.outputs.services }}
    steps:
      - uses: actions/checkout@v4

      - uses: dorny/paths-filter@v3
        id: filter
        with:
          filters: |
            api-gateway:
              - "api-gateway/**"
              - "docker-compose.yml"
              - ".dockerignore"
              - ".github/workflows/backend-ci-cd.yml"
            auth-service:
              - "auth-service/**"
              - "docker-compose.yml"
              - ".dockerignore"
              - ".github/workflows/backend-ci-cd.yml"
            user-service:
              - "user-service/**"
              - "docker-compose.yml"
              - ".dockerignore"
              - ".github/workflows/backend-ci-cd.yml"
            notification-service:
              - "notification-service/**"
              - "docker-compose.yml"
              - ".dockerignore"
              - ".github/workflows/backend-ci-cd.yml"
            media-service:
              - "media-service/**"
              - "docker-compose.yml"
              - ".dockerignore"
              - ".github/workflows/backend-ci-cd.yml"
            chat-service:
              - "chat-service/**"
              - "docker-compose.yml"
              - ".dockerignore"
              - ".github/workflows/backend-ci-cd.yml"
            analytic-service:
              - "analytic-service/**"
              - "docker-compose.yml"
              - ".dockerignore"
              - ".github/workflows/backend-ci-cd.yml"

      - id: matrix
        shell: bash
        run: |
          if [[ "${{ github.event_name }}" == "workflow_dispatch" && "${{ inputs.force_all }}" == "true" ]]; then
            echo 'services=["api-gateway","auth-service","user-service","notification-service","media-service","chat-service","analytic-service"]' >> "$GITHUB_OUTPUT"
          else
            echo 'services=${{ steps.filter.outputs.changes }}' >> "$GITHUB_OUTPUT"
          fi

  build-and-deploy:
    name: build and deploy ${{ matrix.service }}
    runs-on: ubuntu-latest
    needs: changes
    if: needs.changes.outputs.services != '[]'
    strategy:
      max-parallel: 1
      fail-fast: false
      matrix:
        service: ${{ fromJSON(needs.changes.outputs.services) }}

    steps:
      - uses: actions/checkout@v4

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: ${{ env.AWS_DEPLOY_ROLE_ARN }}
          aws-region: ${{ env.AWS_REGION }}

      - name: Login to Amazon ECR
        id: ecr
        uses: aws-actions/amazon-ecr-login@v2

      - name: Setup Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Map service metadata
        id: meta
        shell: bash
        run: |
          case "${{ matrix.service }}" in
            api-gateway)
              echo "dir=api-gateway" >> "$GITHUB_OUTPUT"
              echo "repo=ott-api-gateway" >> "$GITHUB_OUTPUT"
              ;;
            auth-service)
              echo "dir=auth-service" >> "$GITHUB_OUTPUT"
              echo "repo=ott-auth-service" >> "$GITHUB_OUTPUT"
              ;;
            user-service)
              echo "dir=user-service" >> "$GITHUB_OUTPUT"
              echo "repo=ott-user-service" >> "$GITHUB_OUTPUT"
              ;;
            notification-service)
              echo "dir=notification-service" >> "$GITHUB_OUTPUT"
              echo "repo=ott-notification-service" >> "$GITHUB_OUTPUT"
              ;;
            media-service)
              echo "dir=media-service" >> "$GITHUB_OUTPUT"
              echo "repo=ott-media-service" >> "$GITHUB_OUTPUT"
              ;;
            chat-service)
              echo "dir=chat-service" >> "$GITHUB_OUTPUT"
              echo "repo=ott-chat-service" >> "$GITHUB_OUTPUT"
              ;;
            analytic-service)
              echo "dir=analytic-service" >> "$GITHUB_OUTPUT"
              echo "repo=ott-analytic-service" >> "$GITHUB_OUTPUT"
              ;;
            *)
              echo "Unknown service: ${{ matrix.service }}" >&2
              exit 1
              ;;
          esac

      - name: Run service tests
        shell: bash
        run: |
          DIR="${{ steps.meta.outputs.dir }}"
          if [[ -f "$DIR/pom.xml" ]]; then
            cd "$DIR"
            if [[ -x "./mvnw" ]]; then
              ./mvnw -B test
            else
              mvn -B test
            fi
          elif [[ -f "$DIR/package.json" ]]; then
            cd "$DIR"
            npm ci
            npm run test --if-present
          fi

      - name: Build and push image
        id: image
        uses: docker/build-push-action@v6
        with:
          context: ${{ steps.meta.outputs.dir }}
          file: ${{ steps.meta.outputs.dir }}/Dockerfile
          platforms: ${{ env.IMAGE_PLATFORM }}
          push: true
          tags: |
            ${{ steps.ecr.outputs.registry }}/${{ steps.meta.outputs.repo }}:${{ github.sha }}
            ${{ steps.ecr.outputs.registry }}/${{ steps.meta.outputs.repo }}:latest
          cache-from: type=gha,scope=${{ matrix.service }}
          cache-to: type=gha,mode=max,scope=${{ matrix.service }}

      - name: Deploy service through SSM
        shell: bash
        run: |
          IMAGE_URI="${{ steps.ecr.outputs.registry }}/${{ steps.meta.outputs.repo }}:${{ github.sha }}"
          INSTANCE_ID="$(aws ec2 describe-instances \
            --region "$AWS_REGION" \
            --filters "Name=tag:Name,Values=$EC2_TAG_NAME" "Name=instance-state-name,Values=running" \
            --query "Reservations[0].Instances[0].InstanceId" \
            --output text)"

          if [[ -z "$INSTANCE_ID" || "$INSTANCE_ID" == "None" ]]; then
            echo "Cannot find running EC2 instance with tag Name=$EC2_TAG_NAME" >&2
            exit 1
          fi

          COMMAND_ID="$(aws ssm send-command \
            --region "$AWS_REGION" \
            --instance-ids "$INSTANCE_ID" \
            --document-name "AWS-RunShellScript" \
            --comment "Deploy ${{ matrix.service }} from GitHub Actions" \
            --parameters "commands=sudo AWS_REGION=$AWS_REGION /opt/ott/deploy-service.sh '${{ matrix.service }}' '$IMAGE_URI'" \
            --query "Command.CommandId" \
            --output text)"

          aws ssm wait command-executed \
            --region "$AWS_REGION" \
            --command-id "$COMMAND_ID" \
            --instance-id "$INSTANCE_ID"

          aws ssm get-command-invocation \
            --region "$AWS_REGION" \
            --command-id "$COMMAND_ID" \
            --instance-id "$INSTANCE_ID" \
            --query "{Status:Status,Output:StandardOutputContent,Error:StandardErrorContent}" \
            --output json
```

Co che tren dam bao:

- Sua `chat-service/**` thi chi build/push/deploy `chat-service`.
- Sua `auth-service/**` thi chi build/push/deploy `auth-service`.
- Sua `docker-compose.yml`, `.dockerignore`, workflow thi build tat ca service vi co the anh huong build/deploy chung.
- Deploy dung `docker compose up -d --no-deps <service>`, khong restart dependency nhu Redis/RabbitMQ/Postgres.

---

## 14. Neu service interface thay doi thi sao?

Path-based deploy chi biet file nao thay doi, khong tu hieu contract giua service. Can quy tac:

- Neu sua DTO/API contract trong `auth-service` ma `user-service` hoac FE phu thuoc, phai push cung thay doi service/FE lien quan.
- Neu sua route gateway trong `api-gateway`, chi gateway build lai, nhung service backend khong can build neu code khong doi.
- Neu sua event RabbitMQ schema, phai deploy producer va consumer lien quan. Vi du `user-service` publish event moi, `notification-service` consume event moi thi sua ca hai service trong cung PR.
- Neu sua shared env trong `.env` tren EC2, khong can rebuild image; chi can restart service lien quan:

```bash
cd /opt/ott
docker compose --env-file .env --env-file .env.images -f docker-compose.prod.yml up -d --no-deps auth-service
```

---

## 15. Toi uu RAM cho EC2 nho

Neu ban co gang chay tren `t3.small`/`t4g.small`:

1. Khong build Docker image tren EC2.
2. Bat swap 2GB.
3. Dat `JAVA_TOOL_OPTIONS` nhu Compose production.
4. Giam Hikari pool cho tung Spring service. `notification-service` da co pool nho, nhung service khac co the can them:

```properties
spring.datasource.hikari.maximum-pool-size=3
spring.datasource.hikari.minimum-idle=1
```

5. Tat service chua can:

```bash
docker compose --env-file .env --env-file .env.images -f docker-compose.prod.yml stop analytic-service media-service
```

6. Giam log DEBUG o production, dac biet gateway dang co:

```properties
logging.level.org.springframework.cloud.gateway=INFO
```

7. Canh chung `media-service`: upload/transcode video co the vuot RAM nhanh hon service khac.

Neu da toi uu ma van OOM, dung instance 4GB. Day la loi giai dung hon viec ep full microservice vao 1GB.

---

## 16. Backup va rollback

### 16.1 Backup volumes local

`analytic-postgres`, `redis`, `rabbitmq` dang co Docker volumes. Toi thieu backup Postgres:

```bash
docker exec analytic-postgres pg_dump -U "$ANALYTIC_DB_USERNAME" "$ANALYTIC_DB_NAME" > "/opt/ott/backups/analytic-$(date +%F).sql"
```

Nen dong bo backup len S3:

```bash
aws s3 sync /opt/ott/backups s3://<backup-bucket>/ott-backups/
```

### 16.2 Rollback 1 service

Tim image tag cu:

```bash
aws ecr describe-images \
  --region ap-southeast-1 \
  --repository-name ott-chat-service \
  --query "sort_by(imageDetails,& imagePushedAt)[-10:].imageTags" \
  --output table
```

Rollback:

```bash
/opt/ott/deploy-service.sh chat-service <aws-account-id>.dkr.ecr.ap-southeast-1.amazonaws.com/ott-chat-service:<old-sha>
```

### 16.3 Logs

```bash
cd /opt/ott
docker compose --env-file .env --env-file .env.images -f docker-compose.prod.yml logs -f --tail=200 api-gateway
docker stats
df -h
free -h
```

---

## 17. Checklist truoc khi public

- [ ] Da tao budget alert.
- [ ] Security group chi mo 80/443 public.
- [ ] Khong mo Redis/RabbitMQ/Postgres ra Internet.
- [ ] `.env` khong nam trong Git.
- [ ] Da rotate cac secret tung bi commit hoac nam trong fallback config.
- [ ] ECR lifecycle policy da bat.
- [ ] EC2 co IAM role `AmazonSSMManagedInstanceCore` va ECR read-only.
- [ ] GitHub Actions dung OIDC, khong dung AWS key dai han.
- [ ] FE web/mobile da doi API URL sang `https://api.example.com/riff/api`.
- [ ] Gateway CORS da them production frontend origin.
- [ ] Nginx da support WebSocket header.
- [ ] `docker stats` sau khi idle con it nhat 25-30% RAM trong.
- [ ] Da test login, chat socket, AI suggest, upload media, notification, analytics.

---

## 18. Nguon tham khao chinh thuc

- AWS Free Tier: https://aws.amazon.com/free/
- EC2 Free Tier theo ngay tao account: https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-free-tier-usage.html
- EC2 general purpose instance specs: https://aws.amazon.com/ec2/instance-types/general-purpose/
- EBS pricing/free tier: https://aws.amazon.com/ebs/pricing/
- ECR pricing/free tier: https://aws.amazon.com/ecr/pricing/
- GitHub OIDC voi AWS: https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws
- AWS Systems Manager Run Command: https://docs.aws.amazon.com/systems-manager/latest/userguide/run-command.html
- Docker Compose production reference: https://docs.docker.com/compose/

---

## 19. Viec nen lam tiep trong repo

Day la cac viec nen lam sau khi doc guide, chua bat buoc de tao AWS resource:

1. Tao file workflow that tai `.github/workflows/backend-ci-cd.yml` theo mau muc 13.
2. Tao `docker-compose.prod.yml` versioned trong repo neu muon review thay doi infra qua PR; khi deploy thi copy len `/opt/ott`.
3. Sua `media-service/Dockerfile` `EXPOSE 8080` thanh `EXPOSE 8090 8091`.
4. Xoa fallback secret khoi `media-service/application.properties` va rotate credential.
5. Them health endpoint cho tung service de CI/CD/monitoring kiem tra duoc sau deploy.
6. Them `spring.datasource.hikari.maximum-pool-size` nho hon cho cac service neu chay tren instance 2-4GB.
7. Can nhac them CloudWatch Agent hoac it nhat cron backup cho `/opt/ott/backups`.

---

## 20. Bo file deploy da duoc tao trong repo

Trong repo nay da co san cac file de trien khai:

```text
.github/workflows/backend-ci-cd.yml
deploy/docker-compose.prod.yml
deploy/deploy-service.sh
deploy/aws/configure-ec2-disk-guardrails.sh
deploy/aws/grow-root-volume-ubuntu.sh
deploy/all-variables.env.example
deploy/env.example
deploy/env.images.example
deploy/ecr-lifecycle-policy.json
deploy/github-actions.env.example
deploy/nginx/ott-api.conf
deploy/aws/create-ecr-repos.sh
deploy/aws/github-actions-permissions-policy.example.json
deploy/aws/github-oidc-trust-policy.example.json
deploy/aws/install-ec2-dependencies-ubuntu.sh
deploy/aws/start-ec2-by-tag.sh
deploy/aws/stop-ec2-by-tag.sh
```

Workflow co 2 che do quan trong:

- Push len `main`: chi build/deploy service co file thay doi. Neu EC2 dang tat, workflow se fail som de khong tu bat may lam hao credits.
- Run workflow thu cong: co the chon `force_all=true` de build tat ca service va `start_instance=true` de tu bat EC2 truoc khi deploy.

Sau khi launch EC2, copy cac file production len may:

```bash
sudo mkdir -p /opt/ott
sudo cp deploy/docker-compose.prod.yml /opt/ott/docker-compose.prod.yml
sudo cp deploy/deploy-service.sh /opt/ott/deploy-service.sh
sudo cp deploy/aws/configure-ec2-disk-guardrails.sh /opt/ott/configure-ec2-disk-guardrails.sh
sudo cp deploy/aws/grow-root-volume-ubuntu.sh /opt/ott/grow-root-volume-ubuntu.sh
sudo chmod +x /opt/ott/deploy-service.sh
sudo chmod +x /opt/ott/configure-ec2-disk-guardrails.sh /opt/ott/grow-root-volume-ubuntu.sh
sudo cp deploy/env.example /opt/ott/.env
sudo cp deploy/env.images.example /opt/ott/.env.images
sudo nano /opt/ott/.env
sudo nano /opt/ott/.env.images
```

Bat/tat EC2 de tiet kiem credits:

```bash
AWS_REGION=ap-southeast-1 EC2_TAG_NAME=ott-backend-prod bash deploy/aws/start-ec2-by-tag.sh
AWS_REGION=ap-southeast-1 EC2_TAG_NAME=ott-backend-prod bash deploy/aws/stop-ec2-by-tag.sh
```

Neu dung Elastic IP co dinh, hay nho rang public IPv4 co the van tinh phi khi giu IP. De tiet kiem nhat, khi test/dev thi co the chap nhan IP thay doi moi lan start va cap nhat DNS sau.

---

## 21. Huong dan tung buoc tren AWS Console va GitHub

Phan nay viet lai that cham, theo dung thu tu nen lam. Muc tieu la chay duoc EC2 4GB `c7i-flex.large`, sau do GitHub Actions tu build image va deploy service thay doi.

### 21.1 Tao IAM role cho EC2

Role nay de EC2 co quyen:

- Nhan lenh deploy tu AWS Systems Manager.
- Pull Docker image tu Amazon ECR.

Cac buoc:

1. Vao AWS Console.
2. Tim `IAM`.
3. Chon `Roles`.
4. Bam `Create role`.
5. Trusted entity type: chon `AWS service`.
6. Use case: chon `EC2`.
7. Bam `Next`.
8. Tim va tick policy `AmazonSSMManagedInstanceCore`.
9. Tim va tick policy `AmazonEC2ContainerRegistryReadOnly`.
10. Bam `Next`.
11. Role name dat:

```text
ott-backend-ec2-role
```

12. Bam `Create role`.

Ket qua can co: role `ott-backend-ec2-role`.

### 21.2 Tao EC2 `c7i-flex.large`

Cac buoc:

1. Vao AWS Console.
2. Tim `EC2`.
3. Chon `Instances`.
4. Bam `Launch instances`.
5. Name dat:

```text
ott-backend-prod
```

6. Application and OS Images: chon `Ubuntu`.
7. Chon Ubuntu Server LTS, uu tien 24.04 hoac 22.04.
8. Instance type: chon `c7i-flex.large`.
9. Key pair:
   - Neu ban muon SSH: tao/chon key pair.
   - Neu muon dung SSM thoi: van co the tao key pair de phong loi.
10. Network settings:
   - Allow SSH traffic: chi chon IP cua ban, khong chon `Anywhere`.
   - Allow HTTP traffic: tick.
   - Allow HTTPS traffic: tick.
11. Configure storage:
   - 30GB.
   - Type `gp3`.
12. Advanced details:
   - IAM instance profile: chon `ott-backend-ec2-role`.
13. Bam `Launch instance`.

Sau khi tao xong, kiem tra tag:

1. Mo EC2 instance vua tao.
2. Tab `Tags`.
3. Can co:

```text
Key: Name
Value: ott-backend-prod
```

Workflow GitHub se tim EC2 bang tag nay.

### 21.3 Cai Docker tren EC2 bang Session Manager

Phan nay danh cho truong hop ban dang thao tac bang **Session Manager** nhu anh. O buoc nay **khong clone repo tren EC2**. Chi can cai Docker, Docker Compose, swap va tao folder `/opt/ott`.

#### 21.3.1 Mo Session Manager

1. Vao AWS Console -> EC2 -> Instances.
2. Chon instance `ott-backend-prod`.
3. Bam `Connect`.
4. Chon tab `Session Manager`.
5. Bam `Connect`.

Khi vao duoc man hinh den, kiem tra user:

```bash
whoami
pwd
```

Neu user la `ssm-user` thi binh thuong. Session Manager thuong vao bang `ssm-user`, khong phai `ubuntu`.

#### 21.3.2 Neu `docker --version` bi loi

Neu ban go:

```bash
docker --version
```

ma thay:

```text
docker: not found
```

thi binh thuong. Nghia la EC2 moi tao chua cai Docker.

#### 21.3.3 Chay tung block ngan sau

Dung Session Manager doi khi paste mot block qua dai se bi dut/nuot lenh. Vi vay hay chay tung block ngan duoi day.

**Block 1: cai Docker**

```bash
sudo apt-get update -y
sudo apt-get install -y ca-certificates curl gnupg unzip jq nginx certbot python3-certbot-nginx docker.io
sudo systemctl enable --now docker
sudo docker --version
```

Ket qua dung: dong cuoi hien kieu:

```text
Docker version ...
```

**Block 2: cai Docker Compose plugin**

```bash
sudo mkdir -p /usr/local/lib/docker/cli-plugins
COMPOSE_VERSION="$(curl -fsSL https://api.github.com/repos/docker/compose/releases/latest | jq -r .tag_name)"
ARCH="$(uname -m)"
if [ "$ARCH" = "x86_64" ]; then ARCH="x86_64"; elif [ "$ARCH" = "aarch64" ]; then ARCH="aarch64"; fi
sudo curl -fsSL "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-${ARCH}" -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
sudo docker compose version
```

Ket qua dung: dong cuoi hien kieu:

```text
Docker Compose version ...
```

**Block 3: tao swap 2GB va folder deploy**

```bash
if ! swapon --show | grep -q '/swapfile'; then
  sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
fi

sudo mkdir -p /opt/ott
sudo chown -R "$USER:$USER" /opt/ott || true

free -h
sudo docker run --rm hello-world
```

Ket qua dung:

- `free -h` hien co swap khoang `2.0Gi`.
- `sudo docker run --rm hello-world` hien `Hello from Docker!`.

Sau buoc nay, Docker da cai xong.

#### 21.3.4 Co can clone repo tren EC2 khong?

Khong. Voi cach deploy nay, **khong clone repo tren EC2**.

Ly do:

- Code nam tren GitHub.
- GitHub Actions build Docker image.
- Image duoc push vao ECR.
- EC2 chi pull image tu ECR ve chay.
- EC2 chi can cac file production trong `/opt/ott`, se copy o buoc 21.8.

Vay sau khi block 1, 2, 3 chay OK, di tiep sang:

```text
21.4 Tao ECR repositories
```

### 21.4 Tao ECR repositories

ECR la "kho Docker image" cua AWS. GitHub Actions se build image cua tung service, push vao ECR, sau do EC2 se pull image tu ECR ve de chay Docker Compose.

Can tao 7 private repositories, moi service 1 repo:

| Service | ECR repository |
| --- | --- |
| `api-gateway` | `ott-api-gateway` |
| `auth-service` | `ott-auth-service` |
| `user-service` | `ott-user-service` |
| `notification-service` | `ott-notification-service` |
| `media-service` | `ott-media-service` |
| `chat-service` | `ott-chat-service` |
| `analytic-service` | `ott-analytic-service` |

#### Cach A: tao bang AWS Console

1. Vao AWS Console.
2. O goc phai tren, chon dung region:

```text
Singapore / ap-southeast-1
```

3. Tim `ECR`.
4. Chon `Elastic Container Registry`.
5. O menu trai, chon `Private registry` -> `Repositories`.
6. Bam `Create repository`.
7. Visibility settings: chon `Private`.
8. Repository name nhap repo dau tien:

```text
ott-api-gateway
```

9. Tag immutability:
   - De `Mutable` neu muon tag `latest` luon tro toi image moi nhat.
   - Neu can production chat hon sau nay, co the doi sang immutable va chi deploy bang SHA tag.
10. Image scan settings: bat `Scan on push`.
11. Encryption settings: de `AES-256`.
12. Bam `Create repository`.
13. Lap lai cac buoc tren cho 6 repo con lai:

```text
ott-auth-service
ott-user-service
ott-notification-service
ott-media-service
ott-chat-service
ott-analytic-service
```

Sau khi tao xong, ban se thay moi repo co URI dang:

```text
<AWS_ACCOUNT_ID>.dkr.ecr.ap-southeast-1.amazonaws.com/ott-chat-service
```

Hay copy `AWS_ACCOUNT_ID` tu URI nay de dung cho GitHub variable va `/opt/ott/.env.images`.

#### Cach B: tao nhanh bang AWS CloudShell

Cach nay nhanh hon va khong can cai AWS CLI tren may local.

1. Vao AWS Console.
2. Chon region `ap-southeast-1`.
3. Bam icon `CloudShell` tren thanh tren cung.
4. Doi CloudShell mo xong, paste toan bo lenh duoi day:

```bash
export AWS_REGION=ap-southeast-1

for repo in \
  ott-api-gateway \
  ott-auth-service \
  ott-user-service \
  ott-notification-service \
  ott-media-service \
  ott-chat-service \
  ott-analytic-service
do
  aws ecr describe-repositories --region "$AWS_REGION" --repository-names "$repo" >/dev/null 2>&1 \
    || aws ecr create-repository \
      --region "$AWS_REGION" \
      --repository-name "$repo" \
      --image-scanning-configuration scanOnPush=true \
      --encryption-configuration encryptionType=AES256
done
```

5. Kiem tra ket qua:

```bash
aws ecr describe-repositories \
  --region ap-southeast-1 \
  --query "repositories[].repositoryName" \
  --output table
```

Can thay du 7 repo:

```text
ott-api-gateway
ott-auth-service
ott-user-service
ott-notification-service
ott-media-service
ott-chat-service
ott-analytic-service
```

#### Dat lifecycle policy de tiet kiem credits

Moi lan GitHub Actions build, ECR se luu them image moi. Neu khong xoa image cu, ECR co the phinh dung luong. Nen dat policy giu 5 image gan nhat moi repo.

Trong CloudShell, paste:

```bash
cat > ecr-lifecycle-policy.json <<'JSON'
{
  "rules": [
    {
      "rulePriority": 1,
      "description": "Keep only the last 5 images to protect free credits",
      "selection": {
        "tagStatus": "any",
        "countType": "imageCountMoreThan",
        "countNumber": 5
      },
      "action": {
        "type": "expire"
      }
    }
  ]
}
JSON

for repo in \
  ott-api-gateway \
  ott-auth-service \
  ott-user-service \
  ott-notification-service \
  ott-media-service \
  ott-chat-service \
  ott-analytic-service
do
  aws ecr put-lifecycle-policy \
    --region ap-southeast-1 \
    --repository-name "$repo" \
    --lifecycle-policy-text file://ecr-lifecycle-policy.json
done
```

Kiem tra policy cua 1 repo:

```bash
aws ecr get-lifecycle-policy \
  --region ap-southeast-1 \
  --repository-name ott-chat-service \
  --query lifecyclePolicyText \
  --output text
```

#### Cap nhat `/opt/ott/.env.images`

Sau khi co ECR repos, mo file tren EC2:

```bash
nano /opt/ott/.env.images
```

Sua `123456789012` thanh AWS Account ID that. Vi du:

```properties
API_GATEWAY_IMAGE=642058032746.dkr.ecr.ap-southeast-1.amazonaws.com/ott-api-gateway:latest
AUTH_SERVICE_IMAGE=642058032746.dkr.ecr.ap-southeast-1.amazonaws.com/ott-auth-service:latest
USER_SERVICE_IMAGE=642058032746.dkr.ecr.ap-southeast-1.amazonaws.com/ott-user-service:latest
NOTIFICATION_SERVICE_IMAGE=642058032746.dkr.ecr.ap-southeast-1.amazonaws.com/ott-notification-service:latest
MEDIA_SERVICE_IMAGE=642058032746.dkr.ecr.ap-southeast-1.amazonaws.com/ott-media-service:latest
CHAT_SERVICE_IMAGE=642058032746.dkr.ecr.ap-southeast-1.amazonaws.com/ott-chat-service:latest
ANALYTIC_SERVICE_IMAGE=642058032746.dkr.ecr.ap-southeast-1.amazonaws.com/ott-analytic-service:latest
```

Sau workflow deploy lan dau, file nay se tu dong duoc update sang tag commit SHA cua tung service.

#### Loi hay gap o buoc ECR

- Sai region: repo tao o region khac, GitHub Actions push vao `ap-southeast-1` se bao khong tim thay repo.
- Sai ten repo: workflow dang hard-code `ott-chat-service`, `ott-auth-service`,... nen ten repo phai dung y het.
- Chua gan quyen ECR cho GitHub role: build xong nhung push image bi `AccessDenied`.
- Chua gan `AmazonEC2ContainerRegistryReadOnly` cho EC2 role: deploy bi loi khi EC2 pull image.

### 21.5 Tao GitHub OIDC provider tren AWS

Buoc nay de GitHub Actions deploy ma khong can luu AWS secret key dai han.

1. Vao AWS Console.
2. Tim `IAM`.
3. Chon `Identity providers`.
4. Bam `Add provider`.
5. Provider type: chon `OpenID Connect`.
6. Provider URL nhap:

```text
https://token.actions.githubusercontent.com
```

7. Audience nhap:

```text
sts.amazonaws.com
```

8. Bam `Add provider`.

Neu provider nay da ton tai, bo qua buoc nay.

### 21.6 Tao IAM role cho GitHub Actions

Role nay de GitHub Actions co quyen:

- Push image len ECR.
- Tim EC2 theo tag `Name=ott-backend-prod`.
- Neu chay workflow thu cong voi `start_instance=true`, no duoc phep bat EC2 co tag tren.
- Gui lenh SSM vao EC2 de restart dung service.

Cac buoc:

1. Vao IAM -> Roles.
2. Bam `Create role`.
3. Trusted entity type: chon `Web identity`.
4. Identity provider: chon `token.actions.githubusercontent.com`.
5. Audience: chon `sts.amazonaws.com`.
6. Bam `Next`.
7. Den permissions, co the tam thoi bam `Next` truoc, lat nua add inline policy.
8. Role name dat:

```text
ott-github-actions-deploy-role
```

9. Bam `Create role`.
10. Mo role vua tao.
11. Tab `Trust relationships`.
12. Bam `Edit trust policy`.
13. Mo file:

```text
deploy/aws/github-oidc-trust-policy.example.json
```

14. Thay:

```text
<AWS_ACCOUNT_ID>
<GITHUB_OWNER>
<GITHUB_REPO>
```

Vi du neu GitHub repo la `nguyenvana/ott-backend`:

```text
repo:nguyenvana/ott-backend:ref:refs/heads/main
```

15. Paste JSON vao trust policy.
16. Bam `Update policy`.

Them permission:

1. Van trong role `ott-github-actions-deploy-role`.
2. Tab `Permissions`.
3. Bam `Add permissions`.
4. Chon `Create inline policy`.
5. Chon tab `JSON`.
6. Mo file:

```text
deploy/aws/github-actions-permissions-policy.example.json
```

7. Thay `<AWS_ACCOUNT_ID>` bang account id that.
8. Paste JSON vao.
9. Bam `Next`.
10. Policy name dat:

```text
ott-github-actions-deploy-policy
```

11. Bam `Create policy`.

Sau do copy ARN cua role. Dang co dang:

```text
arn:aws:iam::<AWS_ACCOUNT_ID>:role/ott-github-actions-deploy-role
```

Can dung ARN nay cho GitHub variable `AWS_DEPLOY_ROLE_ARN`.

### 21.7 Tao GitHub Variables

Vao GitHub repo backend:

1. `Settings`.
2. `Secrets and variables`.
3. `Actions`.
4. Chon tab `Variables`.
5. Bam `New repository variable`.

Tao 5 variables:

```text
AWS_ACCOUNT_ID=123456789012
AWS_REGION=ap-southeast-1
AWS_DEPLOY_ROLE_ARN=arn:aws:iam::123456789012:role/ott-github-actions-deploy-role
EC2_TAG_NAME=ott-backend-prod
IMAGE_PLATFORM=linux/amd64
```

Neu ban chon EC2 `c7i-flex.large` thi `IMAGE_PLATFORM=linux/amd64` la dung.

Trong repo da co file mau de ban nhin cho de:

```text
deploy/github-actions.env.example
```

Neu muon xem **tat ca bien can cho toan bo deploy** trong mot file, xem:

```text
deploy/all-variables.env.example
```

File `all-variables.env.example` gom 4 nhom:

- GitHub Actions Variables: tao tren GitHub.
- EC2 runtime env: luu tren server tai `/opt/ott/.env`.
- EC2 image env: luu tren server tai `/opt/ott/.env.images`.
- Frontend env reminder: dung cho web/mobile frontend, khong phai backend CI/CD.

Luu y: 5 dong tren la **GitHub Variables**, khong phai file `.env` trong source code. App secrets nhu DB password, JWT secret, Gmail password, S3 key,... khong dua len GitHub trong setup nay; chung nam tren EC2 tai `/opt/ott/.env`.

### 21.8 Dua file production len EC2

Buoc nay chi lam sau khi Docker da cai xong va ECR/GitHub role da tao xong.

Can dua cac file production vao `/opt/ott`:

```text
deploy/docker-compose.prod.yml              -> /opt/ott/docker-compose.prod.yml
deploy/deploy-service.sh                    -> /opt/ott/deploy-service.sh
deploy/aws/configure-ec2-disk-guardrails.sh -> /opt/ott/configure-ec2-disk-guardrails.sh
deploy/aws/grow-root-volume-ubuntu.sh       -> /opt/ott/grow-root-volume-ubuntu.sh
deploy/env.example                          -> /opt/ott/.env
deploy/env.images.example                   -> /opt/ott/.env.images
```

Neu da co file local da dien san secret tu `.env`:

```text
deploy/env.ec2.local
deploy/env.images.local
```

thi dung 2 file `.local` nay thay cho `env.example` va `env.images.example`:

```text
deploy/env.ec2.local    -> /opt/ott/.env
deploy/env.images.local -> /opt/ott/.env.images
```

Hai file `.local` nay bi `.gitignore`, khong commit len GitHub.

#### 21.8.1 Tao folder `/opt/ott`

Neu ban dang dung Session Manager, chay:

```bash
sudo mkdir -p /opt/ott
sudo chown -R "$USER:$USER" /opt/ott || true
```

#### 21.8.2 Cach dua file len EC2 khi dung Session Manager

Session Manager khong co nut upload file truc tiep nhu SSH/SCP. Cach de nhat la clone repo **chi de copy 4 file deploy**. Day khong phai cach deploy code; code van duoc GitHub Actions build thanh Docker image.

Tren EC2, chay:

```bash
cd ~
sudo apt-get update -y
sudo apt-get install -y git
```

Clone repo backend cua ban. Thay URL ben duoi bang URL repo GitHub that:

```bash
git clone https://github.com/<GITHUB_OWNER>/<GITHUB_REPO>.git ott-backend
```

Vi du:

```bash
git clone https://github.com/nguyenvana/ott-backend.git ott-backend
```

Neu repo private, GitHub se doi login/token. Khi do co 2 cach:

- Dung HTTPS voi Personal Access Token.
- Hoac tam thoi dung SSH/SCP tu may local neu ban co key pair.

Sau khi clone xong, copy 4 file:

```bash
cd ~/ott-backend
cp deploy/docker-compose.prod.yml /opt/ott/docker-compose.prod.yml
cp deploy/deploy-service.sh /opt/ott/deploy-service.sh
cp deploy/aws/configure-ec2-disk-guardrails.sh /opt/ott/configure-ec2-disk-guardrails.sh
cp deploy/aws/grow-root-volume-ubuntu.sh /opt/ott/grow-root-volume-ubuntu.sh
cp deploy/env.ec2.local /opt/ott/.env
cp deploy/env.images.local /opt/ott/.env.images
```

Neu GitHub repo cua ban la repo cha `ott-project` va `ott-backend` nam ben trong, thi lenh copy se la:

```bash
cd ~/ott-backend/ott-backend
cp deploy/docker-compose.prod.yml /opt/ott/docker-compose.prod.yml
cp deploy/deploy-service.sh /opt/ott/deploy-service.sh
cp deploy/aws/configure-ec2-disk-guardrails.sh /opt/ott/configure-ec2-disk-guardrails.sh
cp deploy/aws/grow-root-volume-ubuntu.sh /opt/ott/grow-root-volume-ubuntu.sh
cp deploy/env.ec2.local /opt/ott/.env
cp deploy/env.images.local /opt/ott/.env.images
```

Sau khi copy xong, ban co the xoa folder clone de do rac:

```bash
rm -rf ~/ott-backend
```

#### 21.8.3 Neu ban dung SSH/SCP thay Session Manager

Chi dung cach nay neu ban co key pair va SSH vao duoc EC2:

```bash
scp -i your-key.pem deploy/docker-compose.prod.yml ubuntu@<EC2_PUBLIC_IP>:/opt/ott/docker-compose.prod.yml
scp -i your-key.pem deploy/deploy-service.sh ubuntu@<EC2_PUBLIC_IP>:/opt/ott/deploy-service.sh
scp -i your-key.pem deploy/aws/configure-ec2-disk-guardrails.sh ubuntu@<EC2_PUBLIC_IP>:/opt/ott/configure-ec2-disk-guardrails.sh
scp -i your-key.pem deploy/aws/grow-root-volume-ubuntu.sh ubuntu@<EC2_PUBLIC_IP>:/opt/ott/grow-root-volume-ubuntu.sh
scp -i your-key.pem deploy/env.example ubuntu@<EC2_PUBLIC_IP>:/opt/ott/.env
scp -i your-key.pem deploy/env.images.example ubuntu@<EC2_PUBLIC_IP>:/opt/ott/.env.images
```

#### 21.8.4 Cap quyen va sua env

Cap quyen script deploy:

```bash
chmod +x /opt/ott/deploy-service.sh
chmod +x /opt/ott/configure-ec2-disk-guardrails.sh /opt/ott/grow-root-volume-ubuntu.sh
```

Sua `/opt/ott/.env`. Day la file secret that cua server:

```bash
nano /opt/ott/.env
```

Bat buoc thay het cac gia tri `replace-me`, `example.com`, `123456789012`.

Sua `/opt/ott/.env.images`:

```bash
nano /opt/ott/.env.images
```

Thay `123456789012` bang AWS account id that cua ban.

### 21.9 Chay workflow lan dau

Sau khi push code len GitHub:

1. Vao GitHub repo.
2. Tab `Actions`.
3. Chon workflow `backend microservice ci cd`.
4. Bam `Run workflow`.
5. Branch chon `main`.
6. Tick:

```text
force_all = true
start_instance = true
```

7. Bam `Run workflow`.

Lan dau workflow se:

- Build 7 image.
- Push vao 7 ECR repositories.
- Neu EC2 dang tat thi bat EC2.
- Cho SSM online.
- Deploy tung service vao Docker Compose.

Neu workflow fail o buoc deploy, vao EC2 kiem tra:

```bash
cd /opt/ott
docker compose --env-file .env --env-file .env.images -f docker-compose.prod.yml ps
docker compose --env-file .env --env-file .env.images -f docker-compose.prod.yml logs -f --tail=200 api-gateway
```

### 21.10 Chua loi full disk Docker tren EC2

Neu `df -h` bao `/` day 100%, hay kiem tra:

```bash
sudo du -h --max-depth=1 /var/lib | sort -h
sudo docker system df
sudo journalctl --disk-usage
```

Neu ket qua giong truong hop hien tai:

```text
/var/lib/docker 49G
/var/lib/containerd 5.9G
journal 136M
```

thi thu pham la Docker image/layer cu, khong phai RabbitMQ. RabbitMQ data volume cua compose la volume that, nen **khong auto chay `docker volume prune`** vi co the xoa data cua `rabbitmq_data`, `redis_data`, `analytic_postgres_data`.

Chay mot lan de mo rong partition sau khi da modify EBS tren AWS Console:

```bash
cd ~/ott-backend
sudo bash deploy/aws/grow-root-volume-ubuntu.sh
```

Neu repo tren EC2 la repo cha:

```bash
cd ~/ott-backend/ott-backend
sudo bash deploy/aws/grow-root-volume-ubuntu.sh
```

Chay mot lan de cai guardrail Docker/log va tao timer don rac moi ngay:

```bash
cd ~/ott-backend
sudo bash deploy/aws/configure-ec2-disk-guardrails.sh
```

Neu repo tren EC2 la repo cha:

```bash
cd ~/ott-backend/ott-backend
sudo bash deploy/aws/configure-ec2-disk-guardrails.sh
```

Sau khi cai guardrail, moi container moi se bi gioi han log theo:

```properties
DOCKER_LOG_MAX_SIZE=20m
DOCKER_LOG_MAX_FILE=3
```

Neu muon tat ca container hien co nhan logging config moi ngay lap tuc, chay luc chap nhan downtime ngan:

```bash
cd /opt/ott
sudo docker compose --env-file .env --env-file .env.images -f docker-compose.prod.yml up -d --force-recreate
```

Moi lan deploy thanh cong, `/opt/ott/deploy-service.sh` cung se tu don:

```bash
docker image prune -af --filter "until=72h"
docker builder prune -af --filter "until=72h"
docker container prune -f --filter "until=24h"
find /var/lib/docker/containers -type f -name "*-json.log" -size +200M -exec truncate -s 0 {} \;
journalctl --vacuum-time=7d
```

Lenh don khan cap khi sap het dung luong:

```bash
sudo docker image prune -af --filter "until=24h"
sudo docker builder prune -af --filter "until=24h"
sudo docker container prune -f --filter "until=24h"
sudo find /var/lib/docker/containers -type f -name "*-json.log" -exec truncate -s 0 {} \;
df -h
sudo docker system df
```

Chi chay lenh nay neu da hieu rui ro xoa volume:

```bash
sudo docker volume prune -f
```

Voi compose hien tai, khong nen dua `docker volume prune` vao CI/CD vi RabbitMQ, Redis va analytic Postgres dang dung Docker volumes.

### 21.11 Cach bat/tat EC2 de tiet kiem credits

Khi khong demo/test nua, stop EC2:

1. Vao AWS Console -> EC2 -> Instances.
2. Chon `ott-backend-prod`.
3. Instance state -> `Stop instance`.

Khi can deploy/test lai:

1. Vao AWS Console -> EC2 -> Instances.
2. Chon `ott-backend-prod`.
3. Instance state -> `Start instance`.

Neu chay workflow thu cong voi `start_instance=true`, GitHub co the bat may giup ban. Workflow push binh thuong se khong tu bat may, de tranh hao credits.

Sau khi stop EC2:

- EC2 compute khong tinh tien.
- EBS volume van con va co the van tinh trong credits.
- Public IPv4/Elastic IP co the tinh phi, dac biet neu giu Elastic IP. Neu chua can domain co dinh, cach tiet kiem nhat la khong giu Elastic IP.

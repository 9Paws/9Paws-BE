# Cẩm Nang Kiến Trúc & Hướng Dẫn Triển Khai GitOps CI/CD Toàn Diện (End-to-End Master Guide)

> **Mục đích tài liệu:** Đóng gói toàn bộ kiến trúc GitOps, luồng tự động hóa CI/CD, quy trình thiết lập từ con số 0 (Zero to Hero), và kinh nghiệm xử lý triệt để tất cả các lỗi phát sinh trong thực tế cho dự án NinePaws Backend và các dự án sau này.

---

## MỤC LỤC
1. [Tổng Quan Kiến Trúc GitOps (Pull-Based Architecture)](#1-tổng-quan-kiến-trúc-gitops-pull-based-architecture)
2. [Thiết Lập Phía App Repo (`9Paws-BE`)](#2-thiết-lập-phía-app-repo-9paws-be)
3. [Cấu Hình Quyền & Secret Trên GitHub (`GITOPS_PAT`)](#3-cấu-hình-quyền--secret-trên-github-gitops_pat)
4. [Khởi Tạo & Cấu Trúc GitOps Repo (`9Paws-Gitops`)](#4-khởi-tạo--cấu-trúc-gitops-repo-9paws-gitops)
5. [Thiết Lập Tên Miền & Cloudflare DNS](#5-thiết-lập-tên-miền--cloudflare-dns)
6. [Hướng Dẫn Thiết Lập Máy Chủ VPS Từ Con Số 0](#6-hướng-dẫn-thiết-lập-máy-chủ-vps-từ-con-số-0)
7. [Kinh Nghiệm Xử Lý Sự Cố Thực Tế (Troubleshooting Handbook)](#7-kinh-nghiệm-xử-lý-sự-cố-thực-tế-troubleshooting-handbook)
8. [Quy Trình Vận Hành, Giám Sát & Hoàn Tác (Rollback) 10 Giây](#8-quy-trình-vận-hành-giám-sát--hoàn-tác-rollback-10-giây)

---

## 1. TỔNG QUAN KIẾN TRÚC GITOPS (PULL-BASED ARCHITECTURE)

### 1.1 Triết lý & Vấn đề giải quyết
* **Loại bỏ "God Mode" của CI:** Trong DevOps truyền thống (Push-based), CI runner giữ khóa SSH private key của server. Nếu CI bị lộ token, hacker chiếm toàn quyền kiểm soát VPS. Trong GitOps Pull-based, CI **không bao giờ có quyền SSH vào server**.
* **Nguồn chân lý duy nhất (Single Source of Truth):** Trạng thái hạ tầng được khai báo bằng code trên kho Git (`docker-compose-prod.yml`). Nhìn vào Git là biết chính xác server đang chạy gì.
* **Cơ chế Kéo (Pull-based via Systemd Timer):** VPS chủ động chạy một Linux Timer định kỳ mỗi 3 phút để kiểm tra Git. Khi có commit mới, VPS tự kéo về và cập nhật.
* **Tối ưu triệt để tài nguyên cho VPS nhỏ (1GB RAM):** Không dùng Kubernetes hay ArgoCD cồng kềnh; không dùng Portainer CE (vì Portainer CE thiếu tính năng Git Polling và tốn ~150MB RAM). Dùng **Systemd Timer + Shell Script** tiêu tốn **đúng 0 MB RAM** khi ở trạng thái chờ.

### 1.2 Sơ đồ Kiến trúc Tổng thể

```text
+-----------------------------------------------------------------------------------+
| 1. APP REPO (9Paws-BE)                                                            |
|                                                                                   |
|  [Dev] --(Pull Request)--> [CI: Test Only (Postgres & Redis container services)]  |
|                                                                                   |
|  [Maintainer] --(Merge main)--> [CI/CD Full Pipeline]                            |
|                                     |                                             |
|                                     +--> 1. Chạy 206 Tests (mvn test)             |
|                                     +--> 2. Build Multi-stage Image (Java 25)     |
|                                     +--> 3. Tag: v1.0.0-<sha> & latest            |
|                                     +--> 4. Đẩy Image lên GHCR (Public)           |
|                                     +--> 5. Bắn commit chéo sang 9Paws-Gitops     |
+-----------------------------------------------------------------------------------+
                                                      |
                                                      v
+-----------------------------------------------------------------------------------+
| 2. GITOPS REPO (9Paws-Gitops - Public)                                            |
|                                                                                   |
|  - docker-compose-prod.yml (Khống chế RAM: Backend 500M, DB 200M, Redis 100M)    |
|  - nginx/ (Reverse Proxy, SSL termination, HSTS, Dynamic Resolver)                 |
|  - scripts/sync.sh & systemd/ninepaws-sync.{service,timer}                        |
|  - .env.example                                                                   |
+-----------------------------------------------------------------------------------+
                                                      |
                                            (Git Pull mỗi 3 phút)
                                                      v
+-----------------------------------------------------------------------------------+
| 3. PRODUCTION HOST (Azure VPS: Standard_B1s, 1 vCPU, 1.0 GiB RAM)                 |
|                                                                                   |
|  [Systemd Timer: ninepaws-sync.timer] --> [scripts/sync.sh]                       |
|                                                      |                            |
|                                (Tự phát hiện drift & cập nhật)                    |
|                                                      v                            |
|  +------------------------ Docker Bridge (internal_net) -----------------------+  |
|  |                                                                             |  |
|  |  Internet (80/443) --> [ Nginx Reverse Proxy + Let's Encrypt Certbot ]      |  |
|  |                                       |                                     |  |
|  |                                       v (cổng nội bộ 8080)                  |  |
|  |                             [ Spring Boot Backend ]                         |  |
|  |                                   /         \                               |  |
|  |                                  v           v                              |  |
|  |                       [ PostgreSQL 17 ]   [ Redis 8 ]                       |  |
|  |                        (Volume dữ liệu)   (Volume dữ liệu)                  |  |
|  +-----------------------------------------------------------------------------+  |
|                                                                                   |
|  Vùng đệm bộ nhớ: 4GB Linux Swap File (/swapfile, swappiness=10)                 |
|  Mật khẩu thực tế: /opt/ninepaws/.env (Quyền chmod 600, KHÔNG lưu trên Git)       |
+-----------------------------------------------------------------------------------+
```

---

## 2. THIẾT LẬP PHÍA APP REPO (`9Paws-BE`)

### 2.1 File `docker/Dockerfile` (Tối ưu Java 25 & 1GB RAM)

> ⚠️ **Bài học kinh nghiệm:** Spring Boot 3.3+ và 4.x đã **loại bỏ `layertools`** (`Unsupported jarmode 'layertools'`). Sử dụng mô hình Multi-stage chuẩn sao chép trực tiếp file `.jar` hoàn chỉnh để đảm bảo tương thích vĩnh viễn.

```dockerfile
# STAGE 1: BUILD
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app
COPY pom.xml ./
RUN mvn dependency:resolve -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# STAGE 2: RUNTIME
FROM eclipse-temurin:25-jre AS runtime
RUN groupadd --system appgroup && \
    useradd --system --gid appgroup appuser
WORKDIR /app
COPY --from=builder /app/target/*.jar /app/app.jar
RUN chown -R appuser:appgroup /app

# Giới hạn RAM an toàn cho gói VPS 1GB RAM
ENV JAVA_TOOL_OPTIONS="-XX:InitialRAMPercentage=25.0 -XX:MaxRAMPercentage=75.0 -Xmx450m -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

### 2.2 Phân quyền thực thi cho Maven Wrapper (`mvnw`)
Nếu file `mvnw` trong git không có cờ thực thi `+x`, GitHub Actions sẽ báo lỗi: `Permission denied: ./mvnw`. 

Chạy lệnh sau tại local để cấp quyền trên Git:
```bash
git update-index --chmod=+x mvnw
git commit -m "fix(ci): make mvnw executable"
```

### 2.3 File GitHub Actions Workflow (`.github/workflows/ci.yml`)

Workflow được chia thành 2 Job độc lập:
1. **`test`:** Chạy trên cả Pull Request và Push vào `main`. Khởi chạy container PostgreSQL 17 và Redis 8 nội bộ để phục vụ cho các Integration Tests.
2. **`build-and-deploy`:** Chỉ chạy khi **Push/Merge vào `main`**. Tự động build image, ép tên Owner về chữ thường, đẩy lên GHCR và commit chéo sang `9Paws-Gitops`.

```yaml
name: CI/CD Pipeline

on:
  pull_request:
    branches: [ main ]
  push:
    branches: [ main ]

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}

jobs:
  test:
    name: Run Unit & Integration Tests
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:17-alpine
        env:
          POSTGRES_DB: ninepaws_db
          POSTGRES_USER: ninepaws_user
          POSTGRES_PASSWORD: password
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

      redis:
        image: redis:8-alpine
        command: redis-server --requirepass redis_password
        ports:
          - 6379:6379
        options: >-
          --health-cmd "redis-cli -a redis_password ping"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

    env:
      POSTGRES_HOST: localhost
      POSTGRES_PORT: 5432
      POSTGRES_DB: ninepaws_db
      POSTGRES_USER: ninepaws_user
      POSTGRES_PASSWORD: password
      REDIS_HOST: localhost
      REDIS_PORT: 6379
      REDIS_PASSWORD: redis_password
      JWT_SECRET: dGVzdC1zZWNyZXQta2V5LWF0LWxlYXN0LTMyLWJ5dGVzLWxvbmctMTIzNDU2Nzg5MA==
      JWT_ACCESS_EXPIRATION: 86400000
      JWT_REFRESH_EXPIRATION: 604800000
      CLOUDINARY_CLOUD_NAME: dummy
      CLOUDINARY_API_KEY: dummy
      CLOUDINARY_API_SECRET: dummy
      SPRING_AI_GOOGLE_GENAI_API_KEY: dummy

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 25
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
          cache: maven

      - name: Run Maven Tests
        run: |
          chmod +x ./mvnw
          ./mvnw clean test -B

  build-and-deploy:
    name: Build Image & Propagate to GitOps
    needs: test
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - name: Checkout App Repo
        uses: actions/checkout@v4

      - name: Set up JDK 25
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
          cache: maven

      - name: Extract Project Version & Short SHA
        id: meta
        run: |
          chmod +x ./mvnw
          VERSION=$(./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout)
          SHORT_SHA=$(echo "${{ github.sha }}" | cut -c1-7)
          # Bắt buộc chuyển owner về chữ thường vì Docker Tag cấm chữ hoa
          OWNER_LC=$(echo "${{ github.repository_owner }}" | tr '[:upper:]' '[:lower:]')
          echo "owner=${OWNER_LC}" >> $GITHUB_OUTPUT
          echo "version=${VERSION}" >> $GITHUB_OUTPUT
          echo "tag=v${VERSION}-${SHORT_SHA}" >> $GITHUB_OUTPUT

      - name: Log in to GitHub Container Registry
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Build and Push Docker Image
        uses: docker/build-push-action@v5
        with:
          context: .
          file: docker/Dockerfile
          push: true
          tags: |
            ghcr.io/${{ steps.meta.outputs.owner }}/ninepaws-backend:${{ steps.meta.outputs.tag }}
            ghcr.io/${{ steps.meta.outputs.owner }}/ninepaws-backend:latest
          cache-from: type=gha
          cache-to: type=gha,mode=max

      - name: Checkout GitOps Repo
        uses: actions/checkout@v4
        with:
          repository: ${{ github.repository_owner }}/9Paws-Gitops
          token: ${{ secrets.GITOPS_PAT }}
          path: gitops-repo

      - name: Update Image Tag in docker-compose-prod.yml
        run: |
          cd gitops-repo
          git config user.name "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
          git pull --rebase origin main --quiet
          
          IMAGE_LINE="image: ghcr.io/${{ steps.meta.outputs.owner }}/ninepaws-backend:${{ steps.meta.outputs.tag }}"
          sed -i -E "s|image: ghcr.io/[^/]+/ninepaws-backend:.*|${IMAGE_LINE}|g" docker-compose-prod.yml
          
          git add docker-compose-prod.yml
          if git diff --staged --quiet; then
            echo "No change in image tag."
          else
            git commit -m "deploy(backend): bump image to ${{ steps.meta.outputs.tag }} [skip ci]"
            git push origin main
          fi
```

---

## 3. CẤU HÌNH QUYỀN & SECRET TRÊN GITHUB (`GITOPS_PAT`)

Vì `GITHUB_TOKEN` mặc định chỉ có quyền trong phạm vi repo hiện tại (`9Paws-BE`), nó **không thể commit sang repo khác (`9Paws-Gitops`)**. Do đó bắt buộc phải tạo Personal Access Token (PAT):

1. **Tạo GitHub PAT:**
   * Truy cập: **`https://github.com/settings/tokens/new`**
   * Đặt tên (Note): `gitops-cross-commit-token`
   * Chọn thời hạn (Expiration): 90 days hoặc No expiration (tùy chính sách).
   * Đánh dấu chọn quyền: **`repo`** (Full control of private repositories).
   * Kéo xuống bấm **Generate token** và sao chép chuỗi mã (bắt đầu bằng `ghp_...`).
2. **Cấu hình Secret trên `9Paws-BE`:**
   * Vào repo `9Paws-BE` trên web $\rightarrow$ **Settings** $\rightarrow$ **Secrets and variables** $\rightarrow$ **Actions**.
   * Nhấn **New repository secret**.
   * **Name:** `GITOPS_PAT`
   * **Secret:** (Dán chuỗi token vừa tạo).
   * Nhấn **Add secret**.

---

## 4. KHỞI TẠO & CẤU TRÚC GITOPS REPO (`9Paws-Gitops`)

Tạo một repo mới độc lập trên GitHub tên là `9Paws-Gitops` (chế độ **Public** để VPS pull image và code ẩn danh không cần token).

### Cấu trúc thư mục:
```text
9Paws-Gitops/
├── docker-compose-prod.yml       # Khai báo toàn bộ dịch vụ & giới hạn RAM
├── nginx/
│   ├── nginx.conf                # Cấu hình chính Nginx
│   └── conf.d/
│       └── app.conf              # Upstream proxy, SSL Certbot, HSTS, Dynamic resolver
├── scripts/
│   ├── sync.sh                   # Script kiểm tra drift & chạy docker compose
│   └── init-ssl.sh               # Script tự động xin cấp SSL lần đầu
├── systemd/
│   ├── ninepaws-sync.service     # Linux systemd service
│   └── ninepaws-sync.timer       # Linux timer chạy ngầm mỗi 3 phút
├── .env.example                  # Template biến môi trường
└── README.md
```

### 4.1 File `docker-compose-prod.yml`
```yaml
services:
  backend:
    image: ghcr.io/9paws/ninepaws-backend:latest
    container_name: ninepaws-backend
    restart: unless-stopped
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    env_file:
      - /opt/ninepaws/.env
    networks:
      - internal_net
    deploy:
      resources:
        limits:
          memory: 500M

  postgres:
    image: postgres:17-alpine
    container_name: ninepaws-postgres
    restart: unless-stopped
    command: postgres -c shared_buffers=64MB -c work_mem=4MB -c max_connections=50
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - internal_net
    deploy:
      resources:
        limits:
          memory: 200M

  redis:
    image: redis:8-alpine
    container_name: ninepaws-redis
    restart: unless-stopped
    command: redis-server --requirepass ${REDIS_PASSWORD} --maxmemory 64mb --maxmemory-policy allkeys-lru --appendonly yes
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD-SHELL", "redis-cli -a ${REDIS_PASSWORD} ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - internal_net
    deploy:
      resources:
        limits:
          memory: 100M

  nginx:
    image: nginx:1.27-alpine
    container_name: ninepaws-nginx
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./nginx/conf.d:/etc/nginx/conf.d:ro
      - certbot_conf:/etc/letsencrypt
      - certbot_www:/var/www/certbot
    networks:
      - internal_net
    depends_on:
      - backend
    deploy:
      resources:
        limits:
          memory: 64M

  certbot:
    image: certbot/certbot:v2.11.0
    container_name: ninepaws-certbot
    restart: unless-stopped
    volumes:
      - certbot_conf:/etc/letsencrypt
      - certbot_www:/var/www/certbot
    entrypoint: "/bin/sh -c 'trap exit TERM; while :; do certbot renew; sleep 12h & wait $${!}; done;'"
    deploy:
      resources:
        limits:
          memory: 32M

networks:
  internal_net:
    driver: bridge

volumes:
  postgres_data:
  redis_data:
  certbot_conf:
  certbot_www:
```

### 4.2 File `nginx/conf.d/app.conf`
> 💡 **Giải pháp Nginx Resolver:** Dùng `resolver 127.0.0.11` và biến `$backend_upstream` giúp Nginx khởi động mượt mà ngay cả khi container `backend` đang restart mà không bị báo lỗi `host not found in upstream`.

```nginx
server {
    listen 80;
    listen [::]:80;
    server_name ${DOMAIN_NAME};

    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    location / {
        return 301 https://$host$request_uri;
    }
}

server {
    listen 443 ssl;
    listen [::]:443 ssl;
    http2 on;
    server_name ${DOMAIN_NAME};

    ssl_certificate /etc/letsencrypt/live/${DOMAIN_NAME}/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/${DOMAIN_NAME}/privkey.pem;

    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_prefer_server_ciphers off;
    ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384;

    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "no-referrer-when-downgrade" always;
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

    client_max_body_size 50M;

    resolver 127.0.0.11 valid=30s;
    set $backend_upstream http://backend:8080;

    location / {
        proxy_pass $backend_upstream;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### 4.3 File `scripts/sync.sh`
```bash
#!/bin/bash
set -euo pipefail

REPO_DIR="/opt/ninepaws/9Paws-Gitops"
ENV_FILE="/opt/ninepaws/.env"

if [ ! -d "$REPO_DIR" ]; then
    echo "[$(date -u '+%Y-%m-%d %H:%M:%S')] Error: $REPO_DIR does not exist."
    exit 1
fi

if [ ! -f "$ENV_FILE" ]; then
    echo "[$(date -u '+%Y-%m-%d %H:%M:%S')] Error: Environment file $ENV_FILE does not exist. Please create it from .env.example."
    exit 1
fi

cd "$REPO_DIR"

git fetch origin main --quiet
LOCAL_HASH=$(git rev-parse HEAD)
REMOTE_HASH=$(git rev-parse origin/main)

if [ "$LOCAL_HASH" != "$REMOTE_HASH" ]; then
    echo "[$(date -u '+%Y-%m-%d %H:%M:%S')] Drift detected: updating from $LOCAL_HASH to $REMOTE_HASH"
    git pull origin main --quiet
    if grep -q '\${DOMAIN_NAME}' "$REPO_DIR/nginx/conf.d/app.conf"; then
        echo "[$(date -u '+%Y-%m-%d %H:%M:%S')] Warning: app.conf contains uninitialized \${DOMAIN_NAME}. Please run scripts/init-ssl.sh first."
    fi
    docker compose --env-file "$ENV_FILE" -f docker-compose-prod.yml up -d --remove-orphans
    docker image prune -f --filter "until=24h"
    echo "[$(date -u '+%Y-%m-%d %H:%M:%S')] Reconciliation completed successfully."
fi
```

### 4.4 File Cấu hình Systemd Unit
* **`/etc/systemd/system/ninepaws-sync.service`**:
```ini
[Unit]
Description=9Paws GitOps Reconciliation Service
After=network.target docker.service
Requires=docker.service

[Service]
Type=oneshot
User=root
ExecStart=/opt/ninepaws/9Paws-Gitops/scripts/sync.sh
StandardOutput=journal
StandardError=journal
TimeoutStartSec=300
```

* **`/etc/systemd/system/ninepaws-sync.timer`**:
```ini
[Unit]
Description=Trigger 9Paws GitOps Reconciliation every 3 minutes

[Timer]
OnBootSec=1min
OnUnitActiveSec=3min
Unit=ninepaws-sync.service

[Install]
WantedBy=timers.target
```

---

## 5. THIẾT LẬP TÊN MIỀN & CLOUDFLARE DNS

1. Đăng ký tên miền (Ví dụ: `ninepaws.dpdns.org` qua DigitalPlat hoặc `ninepaws.me` qua GitHub Student Pack).
2. Tạo tài khoản Cloudflare, thêm domain và trỏ Nameserver từ trang đăng ký sang Cloudflare.
3. Thêm bản ghi DNS trên Cloudflare:
   * **Bản ghi A:** `ninepaws.dpdns.org` $\rightarrow$ IP VPS (`172.198.60.211`).
   * **Bản ghi CNAME:** `www` $\rightarrow$ `ninepaws.dpdns.org`.
4. ⚠️ **QUAN TRỌNG KHI XIN SSL LẦN ĐẦU:** Tạm thời chuyển đám mây từ **`Proxied` (cam)** sang **`DNS only` (xám)** để Let's Encrypt kết nối trực tiếp vào máy chủ cấp chứng chỉ. Sau khi có SSL thì bật lại màu cam và chọn chế độ SSL là **Full (strict)**.

---

## 6. HƯỚNG DẪN THIẾT LẬP MÁY CHỦ VPS TỪ CON SỐ 0

### Bước 1: Mở cổng Tường lửa Firewall (Azure NSG / Security Group)
Mặc định các nhà cung cấp đám mây (như Azure) chỉ mở cổng 22 (SSH). Bắt buộc phải mở cổng 80 và 443:
```bash
# Chạy từ Azure CLI trên máy tính của bạn:
az network nsg rule create -g RG-FREE-VM --nsg-name vm-free-linuxNSG \
  -n allow-http-https --priority 1010 --direction Inbound --access Allow \
  --protocol Tcp --source-port-ranges "*" --destination-port-ranges 80 443 \
  --source-address-prefixes "*"
```

### Bước 2: Đăng nhập SSH & Cấu hình 4GB Swap File
```bash
# 1. SSH vào server
ssh azureuser@<IP_VPS>

# 2. Tạo và kích hoạt 4GB Swap
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile

# 3. Mount tự động khi khởi động lại
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
sudo sysctl vm.swappiness=10
echo 'vm.swappiness=10' | sudo tee -a /etc/sysctl.conf

# 4. Kiểm tra (Swap phải hiện 4.0Gi)
free -h
```

### Bước 3: Cài đặt Docker & Docker Compose Plugin
```bash
# 1. Cập nhật hệ thống
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg lsb-release

# 2. Thêm GPG key của Docker
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# 3. Thêm Docker repo
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 4. Cài đặt Docker packages
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 5. Phân quyền chạy docker không cần sudo
sudo usermod -aG docker $USER
newgrp docker
```

### Bước 4: Chuẩn bị Thư mục & File Môi trường Thật
```bash
sudo mkdir -p /opt/ninepaws
sudo chown -R $USER:$USER /opt/ninepaws

# Clone repo GitOps
git clone https://github.com/9Paws/9Paws-Gitops.git /opt/ninepaws/9Paws-Gitops

# Tạo file .env và phân quyền bảo mật
cp /opt/ninepaws/9Paws-Gitops/.env.example /opt/ninepaws/.env
chmod 600 /opt/ninepaws/.env

# Mở nano để dán các mật khẩu thật
nano /opt/ninepaws/.env
```

*Nội dung mẫu chuẩn trong `/opt/ninepaws/.env`:*
```env
SPRING_PROFILES_ACTIVE=prod
PORT=8080
BACKEND_IP=127.0.0.1
CORS_ALLOWED_ORIGIN_PATTERNS=*

# Chú ý: Dùng tên service trong Docker Compose, KHÔNG dùng localhost
POSTGRES_HOST=postgres
POSTGRES_PORT=5432
POSTGRES_DB=ninepaws_db
POSTGRES_USER=ninepaws_user
POSTGRES_PASSWORD=your_super_strong_postgres_password

REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=your_super_strong_redis_password

JWT_SECRET=your_base64_jwt_secret_at_least_32_characters_long
JWT_ACCESS_EXPIRATION=86400000
JWT_REFRESH_EXPIRATION=604800000

MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
EMAIL_USERNAME=your_email@gmail.com
EMAIL_PASSWORD=your_gmail_app_password

CLOUDINARY_CLOUD_NAME=your_cloudinary_name
CLOUDINARY_API_KEY=your_cloudinary_key
CLOUDINARY_API_SECRET=your_cloudinary_secret

SPRING_AI_GOOGLE_GENAI_API_KEY=your_gemini_api_key
```

### Bước 5: Cấp Chứng chỉ SSL Let's Encrypt (Standalone Mode)
Chạy Certbot ở chế độ standalone trực tiếp trên cổng 80 để lưu chứng chỉ vào Docker volume:
```bash
# 1. Đảm bảo không có thư mục rác cũ
sudo docker run --rm -v 9paws-gitops_certbot_conf:/etc/letsencrypt certbot/certbot:v2.11.0 sh -c "rm -rf /etc/letsencrypt/live/ninepaws.dpdns.org /etc/letsencrypt/archive/ninepaws.dpdns.org"

# 2. Cấp chứng chỉ chính thức
sudo docker run --rm -p 80:80 \
  -v 9paws-gitops_certbot_conf:/etc/letsencrypt \
  -v 9paws-gitops_certbot_www:/var/www/certbot \
  certbot/certbot:v2.11.0 certonly --standalone \
  --email your_email@gmail.com --agree-tos --no-eff-email \
  -d ninepaws.dpdns.org --force-renewal
```
*(Sau khi báo `Successfully received certificate`, quay lại Cloudflare bật lại đám mây cam `Proxied` và chọn SSL `Full strict`)*.

### Bước 6: Cài đặt và Kích hoạt Systemd Timer
```bash
sudo cp /opt/ninepaws/9Paws-Gitops/systemd/ninepaws-sync.* /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now ninepaws-sync.timer

# Kích hoạt chạy ngay lần đầu tiên
sudo systemctl start ninepaws-sync.service

# Kiểm tra trạng thái
sudo systemctl status ninepaws-sync.timer
docker ps
```

---

## 7. KINH NGHIỆM XỬ LÝ SỰ CỐ THỰC TẾ (TROUBLESHOOTING HANDBOOK)

| STT | Triệu chứng / Lỗi | Nguyên nhân | Cách xử lý triệt để |
| :--- | :--- | :--- | :--- |
| **1** | `E: Could not get lock /var/lib/dpkg/lock-frontend` | Tiến trình ngầm `unattended-upgr` của Ubuntu đang tự động tải bản vá bảo mật khi vừa boot máy. | Chờ 1–2 phút cho tiến trình kết thúc, hoặc tắt tạm thời bằng: `sudo systemctl stop unattended-upgrades`. |
| **2** | Màn hình hồng `Daemons using outdated libraries` khi chạy `apt-get` | Trình quản lý `needrestart` hỏi các dịch vụ có cần khởi động lại sau khi thư viện libc được update không. | Nhấn phím `Enter` (chọn `<Ok>`) để Ubuntu tự nạp lại thư viện trong nền, không làm đứt SSH. |
| **3** | `Certbot: Timeout during connect (likely firewall problem)` | Tường lửa đám mây (Azure NSG) chặn cổng 80 & 443 ngoài internet. | Tạo luật mở cổng 80 và 443 TCP Inbound trên Azure NSG / Security Group của VPS. |
| **4** | `Certbot: live directory exists for domain` | Trước đó đã sinh thư mục chứng chỉ giả lập (placeholder) nên Certbot từ chối ghi đè. | Chạy lệnh docker xóa thư mục cũ trong volume `certbot_conf` rồi thêm cờ `--force-renewal`. |
| **5** | `Error: buildx failed: invalid tag ... repository name must be lowercase` | Tên Organization trên GitHub có chữ in hoa (ví dụ: `9Paws`), Docker Registry cấm chữ hoa. | Thêm lệnh chuyển owner về chữ thường trong CI: `OWNER_LC=$(echo "${{ github.repository_owner }}" \| tr '[:upper:]' '[:lower:]')`. |
| **6** | `java -Djarmode=layertools extract: exit code 1 / Unsupported jarmode` | Spring Boot 3.3+ và 4.x đã loại bỏ cú pháp `layertools`. | Bỏ cơ chế extract phức tạp, dùng `COPY --from=builder /app/target/*.jar /app/app.jar` và chạy `java -jar /app/app.jar`. |
| **7** | `Permission denied: ./mvnw` trên GitHub Actions | File `mvnw` trong git index mang mode 100644 (không có quyền thực thi). | Chạy lệnh `git update-index --chmod=+x mvnw` và thêm `chmod +x ./mvnw` vào trước lệnh chạy trong workflow. |
| **8** | `Flyway / DB connection: Driver claims to not accept jdbcUrl` khi chạy Test trên CI | Repo không commit file `.env` lên Git nên runner thiếu biến môi trường cho Database. | Thêm `services:` (`postgres:17-alpine` và `redis:8-alpine`) cùng block `env:` vào job test trong GitHub Actions. |
| **9** | `Nginx: [emerg] host not found in upstream "backend"` | Nginx khởi động trước khi container Backend có mặt nên DNS nội bộ không phân giải được hostname. | Thêm `resolver 127.0.0.11 valid=30s;` và dùng biến `set $backend_upstream http://backend:8080;` trong cấu hình Nginx. |

---

## 8. QUY TRÌNH VẬN HÀNH, GIÁM SÁT & HOÀN TÁC (ROLLBACK) 10 GIÂY

### 8.1 Vận hành hàng ngày
* Lập trình viên code tính năng trên nhánh riêng, tạo Pull Request vào `main`.
* GitHub Actions chạy bộ test kiểm duyệt chất lượng code.
* Khi nhấn **Merge PR vào `main`**:
  * CI build Docker image với tag `v1.0.0-<sha>` và `latest`, đẩy lên GHCR.
  * CI tự động sửa tag trong `docker-compose-prod.yml` tại `9Paws-Gitops` và push commit.
  * Timer trên VPS phát hiện commit mới trong vòng 3 phút, kéo image về và khởi động lại đúng container Backend. Không mất kết nối Postgres/Redis.

### 8.2 Quy trình Hoàn tác Khẩn cấp (Rollback) 10 Giây (0 SSH)
Nếu phiên bản mới phát sinh lỗi nghiêm trọng:
1. Mở trình duyệt vào repo **`9Paws/9Paws-Gitops`** trên GitHub.
2. Bấm vào file `docker-compose-prod.yml` $\rightarrow$ Nhấn nút **Edit (Hình cây bút)**.
3. Sửa dòng `image:` của `backend` quay về tag ổn định trước đó (hoặc bấm nút **Revert** commit lỗi).
4. Nhập commit message: `revert: rollback backend to v1.0.0-<stable_sha>` và bấm **Commit changes**.
5. **Hệ thống tự lo phần còn lại:** Trong tối đa 3 phút, systemd timer kéo bản thiết kế cũ về, Docker Compose tắt container lỗi và lôi container cũ ra chạy lại. Hoàn tất khôi phục 100% mà không cần chạm vào terminal!

### 8.3 Các lệnh kiểm tra hữu ích trên VPS
```bash
# Xem trạng thái các container đang chạy
docker ps

# Xem log trực tiếp của Spring Boot Backend
docker logs -f ninepaws-backend

# Xem lịch sử chạy của Systemd Timer đồng bộ GitOps
sudo journalctl -u ninepaws-sync.service -n 50 --no-pager

# Ép hệ thống đồng bộ ngay lập tức (không cần đợi chu kỳ 3 phút)
sudo systemctl start ninepaws-sync.service
```

# Hướng Dẫn Thiết Lập Máy Chủ VPS Cho NinePaws GitOps (Quick Reference)

- **Target VM:** `vm-free-linux`
- **Resource Group:** `RG-FREE-VM`
- **Cấu hình:** `Standard_B1s` (1 vCPU, 1.0 GiB RAM, Ubuntu 22.04 LTS)

Tài liệu này hướng dẫn chi tiết các bước thiết lập một lần duy nhất trên máy chủ VPS mới, đã bao gồm các kinh nghiệm xử lý lỗi thực tế phát sinh trong quá trình triển khai.

---

## 1. Mở Cổng Tường Lửa Firewall (Azure NSG)

Trước khi cấu hình trên server, bắt buộc phải mở cổng 80 (HTTP) và 443 (HTTPS) trên tường lửa đám mây (mặc định Azure chỉ mở cổng 22 SSH):
```bash
# Chạy từ Azure CLI trên máy tính của bạn:
az network nsg rule create -g RG-FREE-VM --nsg-name vm-free-linuxNSG \
  -n allow-http-https --priority 1010 --direction Inbound --access Allow \
  --protocol Tcp --source-port-ranges "*" --destination-port-ranges 80 443 \
  --source-address-prefixes "*"
```

---

## 2. Khởi Động & Đăng Nhập SSH Vào VPS
```bash
# 1. Khởi động máy ảo (nếu đang ở trạng thái deallocated)
az vm start -g RG-FREE-VM -n vm-free-linux

# 2. Lấy IP công cộng
IP=$(az vm show -d -g RG-FREE-VM -n vm-free-linux --query publicIps -o tsv)
echo "Public IP: $IP"

# 3. Đăng nhập SSH
ssh azureuser@$IP
```

---

## 3. Thiết Lập 4GB Swap File (BẮT BUỘC CHO MÁY ẢO 1GB RAM)

Để Java 25, PostgreSQL 17 và Redis không bị Linux OOM Killer tắt do tràn bộ nhớ:
```bash
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile

# Tự động mount khi khởi động lại
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Tối ưu swappiness cho server
sudo sysctl vm.swappiness=10
echo 'vm.swappiness=10' | sudo tee -a /etc/sysctl.conf

# Kiểm tra (Swap phải hiện 4.0Gi)
free -h
```

---

## 4. Cài Đặt Docker & Docker Compose Plugin

> 💡 **Lưu ý 1:** Nếu gặp lỗi `Could not get lock /var/lib/dpkg/lock-frontend (held by process unattended-upgr)`, tiến trình cập nhật ngầm của Ubuntu đang chạy. Bạn chỉ cần chờ 1 phút hoặc chạy `sudo systemctl stop unattended-upgrades`.
>
> 💡 **Lưu ý 2:** Nếu gặp màn hình hồng `Daemons using outdated libraries`, chỉ cần nhấn phím `Enter` (chọn `<Ok>`).

```bash
# Cập nhật và cài gói hỗ trợ
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg lsb-release

# Thêm GPG key của Docker
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# Thêm repository
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Cài Docker Engine và Docker Compose plugin
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Phân quyền cho user hiện tại chạy docker không cần sudo
sudo usermod -aG docker $USER
newgrp docker
```

---

## 5. Chuẩn Bị Thư Mục & File Môi Trường `.env`
```bash
sudo mkdir -p /opt/ninepaws
sudo chown -R $USER:$USER /opt/ninepaws

# Clone repo GitOps về VPS
git clone https://github.com/9Paws/9Paws-Gitops.git /opt/ninepaws/9Paws-Gitops

# Tạo file .env từ mẫu
cp /opt/ninepaws/9Paws-Gitops/.env.example /opt/ninepaws/.env
chmod 600 /opt/ninepaws/.env

# Mở nano để dán các giá trị mật khẩu thật
nano /opt/ninepaws/.env
```

> ⚠️ **Chú ý quan trọng trong file `.env`:**
> - `POSTGRES_HOST=postgres` (KHÔNG điền localhost)
> - `REDIS_HOST=redis` (KHÔNG điền localhost)
> - `CORS_ALLOWED_ORIGIN_PATTERNS=*` (hoặc tên miền chính thức của bạn)

---

## 6. Cấp Chứng Chỉ SSL Let's Encrypt (Certbot Standalone)

> ⚠️ **Lưu ý Cloudflare:** Nếu dùng Cloudflare, tạm thời chuyển bản ghi A (`ninepaws.dpdns.org`) sang chế độ **`DNS only` (đám mây xám)** trước khi chạy lệnh này.

Chạy lệnh cấp chứng chỉ standalone:
```bash
# 1. Xóa thư mục chứng chỉ giả lập cũ trong volume (nếu có)
sudo docker run --rm -v 9paws-gitops_certbot_conf:/etc/letsencrypt certbot/certbot:v2.11.0 sh -c "rm -rf /etc/letsencrypt/live/ninepaws.dpdns.org /etc/letsencrypt/archive/ninepaws.dpdns.org"

# 2. Cấp chứng chỉ chính thức
sudo docker run --rm -p 80:80 \
  -v 9paws-gitops_certbot_conf:/etc/letsencrypt \
  -v 9paws-gitops_certbot_www:/var/www/certbot \
  certbot/certbot:v2.11.0 certonly --standalone \
  --email your_email@gmail.com --agree-tos --no-eff-email \
  -d ninepaws.dpdns.org --force-renewal
```

Sau khi báo `Successfully received certificate`:
1. Bật lại đám mây cam **`Proxied`** trên Cloudflare.
2. Đổi chế độ SSL/TLS trong Cloudflare thành **Full (strict)**.

---

## 7. Kích Hoạt Bộ Máy Đồng Bộ Systemd Timer (GitOps Pull)
```bash
# Copy file systemd unit vào thư mục hệ thống
sudo cp /opt/ninepaws/9Paws-Gitops/systemd/ninepaws-sync.* /etc/systemd/system/

# Nạp lại cấu hình systemd
sudo systemctl daemon-reload

# Kích hoạt timer chạy ngầm
sudo systemctl enable --now ninepaws-sync.timer

# Khởi động đồng bộ ngay lần đầu tiên
sudo systemctl start ninepaws-sync.service

# Kiểm tra trạng thái
sudo systemctl status ninepaws-sync.timer
docker ps
```

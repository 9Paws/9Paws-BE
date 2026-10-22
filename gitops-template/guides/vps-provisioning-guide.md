# Hướng dẫn Thiết lập Máy chủ VPS Azure cho NinePaws GitOps

- **Target VM:** `vm-free-linux`
- **Resource Group:** `RG-FREE-VM`
- **Cấu hình:** `Standard_B1s` (1 vCPU, 1.0 GiB RAM, Ubuntu 22.04 LTS)

Tài liệu này hướng dẫn chi tiết các bước thiết lập một lần duy nhất trên máy chủ VPS mới.

---

## 1. Khởi động và Đăng nhập vào VPS

Trên máy local của bạn (đã đăng nhập Azure CLI):
```bash
# 1. Khởi động máy ảo (nếu đang ở trạng thái deallocated)
az vm start -g RG-FREE-VM -n vm-free-linux

# 2. Lấy địa chỉ IP công cộng
IP=$(az vm show -d -g RG-FREE-VM -n vm-free-linux --query publicIps -o tsv)
echo "Public IP: $IP"

# 3. Đăng nhập SSH vào VPS
ssh azureuser@$IP
```

---

## 2. Thiết lập 4GB Swap File (BẮT BUỘC CHO MÁY ẢO 1GB RAM)

Để Java 25, PostgreSQL 17 và Redis không bị Linux OOM Killer tắt do tràn bộ nhớ, cấu hình 4GB Swap:
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

# Kiểm tra bộ nhớ
free -h
```

---

## 3. Cài đặt Docker & Docker Compose Plugin

Cài đặt phiên bản chính thức từ Docker repository:
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

## 4. Chuẩn bị Thư mục và File Môi trường `.env`

Tạo thư mục triển khai và file cấu hình bí mật:
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

---

## 5. Khởi tạo Chứng chỉ SSL Let's Encrypt

Chạy script khởi tạo SSL tự động (cần trỏ bản ghi DNS Domain về IP của VPS trước):
```bash
cd /opt/ninepaws/9Paws-Gitops
sudo ./scripts/init-ssl.sh your-domain.com your-email@gmail.com
```

---

## 6. Kích hoạt Bộ máy Đồng bộ Systemd Timer (GitOps Pull)

Cài đặt service và timer tự động đồng bộ mỗi 3 phút:
```bash
# Copy file systemd unit vào thư mục hệ điều hành
sudo cp /opt/ninepaws/9Paws-Gitops/systemd/ninepaws-sync.* /etc/systemd/system/

# Nạp lại cấu hình systemd
sudo systemctl daemon-reload

# Kích hoạt timer chạy ngầm
sudo systemctl enable --now ninepaws-sync.timer

# Kiểm tra trạng thái timer
sudo systemctl status ninepaws-sync.timer
sudo systemctl list-timers --all | grep ninepaws
```

Từ thời điểm này, VPS sẽ tự động kiểm tra repo `9Paws-Gitops` định kỳ mỗi 3 phút. Mọi thay đổi tag image trên GitHub sẽ được VPS tự động kéo về và cập nhật không cần can thiệp SSH.

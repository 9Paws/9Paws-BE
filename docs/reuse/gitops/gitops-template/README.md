# 9Paws-Gitops

Repository lưu trữ cấu hình triển khai hạ tầng và dịch vụ cho dự án **NinePaws Backend** theo triết lý GitOps (Pull-based).

## Cấu trúc thư mục

- `docker-compose-prod.yml`: Bản thiết kế trung tâm (Desired State) định nghĩa toàn bộ cụm dịch vụ (Backend, PostgreSQL 17, Redis 8, Nginx reverse proxy, Certbot SSL).
- `nginx/`: Cấu hình Reverse Proxy Nginx, SSL termination và định tuyến yêu cầu.
- `scripts/`:
  - `sync.sh`: Script đồng bộ tự động được systemd timer gọi mỗi 3 phút.
  - `init-ssl.sh`: Script hỗ trợ xin cấp chứng chỉ SSL Let's Encrypt lần đầu tiên.
- `systemd/`: File service và timer của Linux OS quản lý chu kỳ kiểm tra tự động.
- `.env.example`: File mẫu liệt kê danh sách các biến môi trường cần thiết.
- `guides/`: Hướng dẫn cài đặt VPS và quy trình hoàn tác (Rollback).

## Cơ chế vận hành

1. Khi code được merge vào `main` tại App Repo (`9Paws-BE`), GitHub Actions sẽ tự động build image và commit cập nhật tag mới vào file `docker-compose-prod.yml` tại đây.
2. Trên máy chủ VPS, một `systemd.timer` chạy ngầm định kỳ kéo các thay đổi mới nhất về và chạy `docker compose up -d`.
3. Chỉ có service Backend được cập nhật và khởi động lại, PostgreSQL và Redis tiếp tục hoạt động liên tục mà không gián đoạn.

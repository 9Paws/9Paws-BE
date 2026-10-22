# Hướng dẫn Hoàn tác Khẩn cấp (Rollback) 10 Giây qua Git

Khi phiên bản mới deploy lên VPS phát sinh lỗi nghiêm trọng, bạn hoàn toàn **không cần SSH vào máy chủ** để gõ lệnh khôi phục. Toàn bộ quy trình hoàn tác diễn ra trực tiếp trên giao diện web của GitHub.

---

## Các bước thực hiện Rollback:

1. **Mở Repo GitOps trên trình duyệt:**
   Truy cập vào: `https://github.com/9Paws/9Paws-Gitops`

2. **Mở file cấu hình `docker-compose-prod.yml`:**
   Nhấn vào file `docker-compose-prod.yml`, sau đó nhấn nút Edit (biểu tượng cây bút ✏️).

3. **Sửa Image Tag về phiên bản ổn định trước đó:**
   Tìm dòng cấu hình của `backend`:
   ```yaml
   services:
     backend:
       image: ghcr.io/9paws/ninepaws-backend:v1.0.0-<bad-sha>
   ```
   Sửa lại tag thành phiên bản cũ đang chạy ổn định:
   ```yaml
   services:
     backend:
       image: ghcr.io/9paws/ninepaws-backend:v1.0.0-<good-sha>
   ```

4. **Lưu Commit:**
   Kéo xuống dưới, nhập lời nhắn commit (ví dụ: `revert: rollback backend to v1.0.0-<good-sha>`) và nhấn **Commit changes**.

5. **Hệ thống tự động hoàn tất:**
   * Trong vòng tối đa 3 phút, `ninepaws-sync.timer` trên VPS sẽ thức dậy, phát hiện commit mới trên Git.
   * Nó tự động chạy `docker compose up -d`, tải image cũ về và restart container Backend.
   * Toàn bộ cơ sở dữ liệu PostgreSQL và cache Redis vẫn duy trì hoạt động bình thường, không bị khởi động lại.

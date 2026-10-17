## 1. Khẳng định Định hướng Kiến trúc (Architectural Confirmation)

Dự án Cozy Ledger áp dụng triết lý GitOps nhưng loại bỏ hoàn toàn các công cụ Enterprise phức tạp như Kubernetes (K8s) hay ArgoCD. Kiến trúc được thiết kế tối giản, phù hợp với quy mô triển khai trên một máy chủ VPS, sử dụng hệ sinh thái Docker Compose kết hợp với Watchtower để mô phỏng cơ chế Pull-based chuẩn mực.

## 2. Nỗi đau DevOps truyền thống & Triết lý GitOps

**Các vấn đề của DevOps truyền thống (CI/CD Push-based):**

* **Lỗ hổng bảo mật (God Mode):** Máy chủ CI/CD (như GitHub Actions, Jenkins) cần giữ khóa SSH hoặc mật khẩu của máy chủ Production để đẩy code và ra lệnh khởi động. Nếu hệ thống CI bị lộ khóa, hacker chiếm toàn quyền kiểm soát server thực tế.
* **Máy chủ Bông tuyết (Snowflake Servers):** Kỹ sư SSH vào server sửa cấu hình, đổi cổng, hoặc cấp RAM thủ công để xử lý sự cố nhưng quên cập nhật lại mã nguồn. Server trở thành một phiên bản độc nhất không thể tái tạo lại bằng code khi phần cứng gặp lỗi.
* **Ác mộng Hoàn tác (Rollback):** Quá trình hạ cấp phiên bản yêu cầu viết các kịch bản chạy ngược phức tạp để gỡ cài đặt, dễ gây lỗi hệ thống dây chuyền.

**Triết lý GitOps giải quyết:**

* **Nguồn chân lý duy nhất (Single Source of Truth):** Trạng thái mong muốn của hệ thống bắt buộc phải được lưu trữ trên Git. Nhìn vào Git là biết chính xác máy chủ đang chạy gì.
* **Triển khai dạng Khai báo (Declarative):** Khai báo kết quả mong muốn (VD: "Chạy bản v1.0.5") thay vì viết các lệnh thực thi (VD: `docker run...`).
* **Cơ chế Kéo (Pull-based):** Server ẩn mình sau tường lửa, chủ động quan sát sự thay đổi trên Git hoặc Registry và tự kéo bản cập nhật về. CI không được phép đụng vào Server.

## 3. Thuật ngữ Cốt lõi (Terminology)

* **Desired State (Trạng thái mong muốn):** Cấu hình hạ tầng và số phiên bản ứng dụng được định nghĩa và lưu trữ công khai trên kho Git.
* **Actual State / Live State (Trạng thái thực tế):** Các container, network, và bộ nhớ đang thực sự vận hành trên server vật lý tại thời điểm hiện tại.
* **Reconciliation Loop (Vòng lặp đồng bộ hóa):** Cơ chế chạy ngầm liên tục (mỗi vài phút) để so sánh Actual State với Desired State. Nếu phát hiện sai lệch, nó sẽ tự động điều chỉnh Actual State cho khớp 100% với Desired State.
* **Configuration Drift (Sự trôi dạt cấu hình):** Hiện tượng trạng thái thực tế trên server bị thay đổi (thường do can thiệp thủ công bằng tay) dẫn đến lệch khỏi bản thiết kế gốc trên Git.
* **Tự động ghi đè (Automated Overwrite):** Tính năng thiết quân luật của GitOps. Khi Configuration Drift xảy ra, Reconciliation Loop sẽ ngay lập tức vô hiệu hóa sự can thiệp thủ công bằng cách xóa bỏ cấu hình tự chế và ghi đè server về đúng trạng thái mà Git quy định.
* **Infrastructure as Code (IaC):** Quản lý cơ sở hạ tầng, network, và deployment thông qua các tệp mã hóa có thể version-control được thay vì thao tác bằng giao diện.

## 4. Cấu trúc và Chức năng Kho GitOps (GitOps Repo)

Kho GitOps tách biệt hoàn toàn với kho mã nguồn phần mềm (App Repo) và chứa các thành phần sau:

* **`docker-compose-prod.yml`:** Bản thiết kế trung tâm. Khai báo toàn bộ ngăn xếp (Backend, PostgreSQL, Redis) và chứa phiên bản thẻ (Image Tag) chính xác của ứng dụng cần chạy.
* **`nginx/`:** Chứa cấu hình điều hướng Reverse Proxy, thiết lập tên miền và chứng chỉ SSL.
* **`.env.example`:** Tệp mẫu liệt kê danh sách các biến môi trường cần có. (Tệp `.env` chứa mật khẩu thật chỉ được đặt vật lý trên VPS, không bao giờ đưa lên Git).
* **`systemd/ninepaws.service`:** Tệp cấu hình dịch vụ của hệ điều hành Linux, giao quyền cho OS quản lý vòng đời khởi động/tắt của toàn bộ cụm Docker Compose và tự động khôi phục khi VPS khởi động lại.
* **`scripts/`:** Các đoạn mã tự động hóa quy trình bảo trì (sao lưu cơ sở dữ liệu cronjob, gia hạn chứng chỉ Let's Encrypt).

## 5. Luồng CI/CD Tự động hóa (The CI/CD Flow)

**Giai đoạn 1: Tích hợp liên tục (CI) với GitHub Actions**

1. Lập trình viên hoàn thành tính năng, tạo Pull Request và hợp nhất mã vào nhánh `main` của **App Repo**.
2. GitHub Actions tự động chạy unit test, đóng gói mã nguồn thành Docker Image và gắn thẻ phiên bản (VD: `ninepaws-backend:v1.0.5`).
3. GitHub Actions đẩy Image vừa tạo lên kho lưu trữ **GitHub Container Registry (GHCR)**.
4. GitHub Actions chạy một script tự động tạo commit chéo sang **GitOps Repo**, chỉnh sửa tệp `docker-compose-prod.yml` để cập nhật cấu hình thành `image: ...:v1.0.5`. Kết thúc trách nhiệm của CI.

**Giai đoạn 2: Triển khai liên tục (CD) với Watchtower (Cơ chế Pull)**

1. **Watchtower** cài đặt trên VPS hoạt động như một GitOps Agent, chạy ngầm Reconciliation Loop liên tục kiểm tra GHCR.
2. Khi phát hiện file cấu hình trên GitOps Repo có sự thay đổi (đã cập nhật lên tag `v1.0.5`), Watchtower đối chiếu và thấy Actual State đang chạy `v1.0.4`.
3. Watchtower tự động tải (Pull) Image `v1.0.5` mới nhất về VPS.
4. Nó tự động tắt container bản cũ một cách an toàn (Graceful shutdown) và khởi động container bản mới. Toàn bộ biến môi trường tự động được nạp lại từ tệp `.env` vật lý, hoàn tất quá trình triển khai mà không cần con người SSH vào máy chủ.


## 5. Version control for image

Giải pháp tối ưu cho dự án: "Đánh Tag Kép" (Hybrid Tagging)
Trong các dự án chuyên nghiệp, để dung hòa giữa "máy dễ đọc" và "người dễ nhìn", người ta cấu hình GitHub Actions để khi build xong một Image, nó sẽ gắn cả 2 tag cùng lúc, hoặc trộn chúng lại thành 1 tag duy nhất. Cụ thể, với dự án Spring Boot của bạn, cơ chế sẽ hoạt động cực kỳ mượt mà như sau:

- Đọc version gốc: Trong file pom.xml của Backend, bạn đang khai báo <version>1.0.0</version>.
- CI tự động ráp Tag: Khi GitHub Actions chạy, nó sẽ tự động dùng lệnh đọc file pom.xml lấy ra chữ 1.0.0, đồng thời lấy thêm Commit ID (giả sử là a1b2c3d).
- Tạo Image: Nó sẽ tạo ra Image với cái tag lai là: ninepaws-backend:v1.0.0-a1b2c3d.
- Bắn commit chéo: Con Bot tự động sang repo GitOps, sửa file docker-compose-prod.yml thành dòng:
image: ghcr.io/org/ninepaws-backend:v1.0.0-a1b2c3d.







## 6. Một số câu hỏi: 

### 1. Watchtower quan sát Repo GitOps hay GHCR?

Sự thật là: **Watchtower chỉ quan sát GHCR (Kho lưu trữ Image), KHÔNG quan sát Repo GitOps.**

Đây là một điểm giới hạn của hệ sinh thái Docker so với Kubernetes (ArgoCD). Watchtower hoạt động bằng cách liên tục gọi API lên GHCR để hỏi: *"Cái Image mà container này đang chạy có bản build nào mới hơn trên mạng không?"*.

Điều này dẫn đến 2 cách thiết lập luồng vận hành thực tế cho bạn:

* **Cách 1 (Dùng Watchtower - Hơi lai GitOps):** Trong file `docker-compose-prod.yml`, bạn luôn để tag là `ninepaws-backend:latest`. Khi GitHub Actions build xong, nó đè lên tag `latest` trên GHCR. Watchtower thấy `latest` có bản cập nhật, nó sẽ tự kéo về và khởi động lại. (Cách này bỏ qua bước tự động commit tag `v1.0.5` sang repo GitOps, hơi mất đi tính truy vết phiên bản chính xác của GitOps thuần túy).
* **Cách 2 (GitOps thuần túy cho Docker Compose):** Bạn không dùng Watchtower. Thay vào đó, bạn cài một Cronjob (tác vụ định kỳ) đơn giản trên VPS cứ mỗi 3 phút chạy lệnh: `git pull && docker-compose up -d`. Lúc này, VPS thực sự quan sát **Repo GitOps**. Khi GitHub Actions sửa tag thành `v1.0.5` trên Git, Cronjob kéo code mới về và Docker Compose tự động nhận diện có sự thay đổi để kéo Image `v1.0.5` từ GHCR.

Với dự án cá nhân hoặc startup nhỏ, **Cách 1 (Watchtower + tag latest)** được dùng rất phổ biến vì nó thiết lập cực kỳ dễ dàng. Nhưng nếu bạn muốn thực hành lý thuyết chuẩn xác của bài viết, **Cách 2** mới là con đường đúng đắn.

### 2. Có phải khởi động lại cả Postgres và Redis khi cập nhật Backend không?

**Câu trả lời là KHÔNG.** Bạn hoàn toàn yên tâm về điều này nhờ vào cơ chế thông minh của Docker Compose.

* Dù bạn đóng gói cả 3 service chung vào một file `docker-compose-prod.yml`, Docker Compose có khả năng nhận diện chính xác service nào có sự thay đổi (về Image hoặc cấu hình).
* Khi chạy lệnh `docker-compose up -d` (hoặc khi Watchtower can thiệp), hệ thống chỉ **dừng, xóa và tạo lại đúng container của Backend**.
* Postgres và Redis sẽ không bị chạm tới, không bị mất kết nối và tiếp tục chạy nền bình thường. Không có chuyện toàn bộ hệ thống bị sập (downtime) vô lý chỉ vì bạn cập nhật code cho app.

### 3. File `.env` được đưa lên VPS bằng cách nào?

Vì file `.env` chứa mật khẩu nhạy cảm và tuyệt đối không được đưa lên Git, nó được đưa lên VPS thông qua một con đường "ngoại giao" thủ công (Out-of-band), thường chỉ làm **một lần duy nhất** khi bạn mới mua VPS:

* **Chuyển file an toàn (SCP / SFTP):** Bạn mở terminal trên máy local và dùng lệnh bảo mật để copy file thẳng lên server:
`scp .env root@dia-chi-ip-vps:/opt/ninepaws-gitops/`
* **Copy/Paste trực tiếp qua SSH:** Bạn SSH vào server, dùng lệnh `nano .env` để tạo một file trống, sau đó copy nội dung file `.env` từ máy tính cá nhân và paste thẳng vào terminal của VPS, rồi lưu lại.

Trong quá trình vận hành sau này, biến số nào (như `DEV_PORT`, `MAX_FILE_SIZE`) không nhạy cảm thì bạn ghi thẳng vào file `docker-compose-prod.yml` trên Git. Nếu có thêm một key bảo mật mới (ví dụ API key của Gemini), bạn lại SSH vào VPS và thêm đúng 1 dòng đó vào file `.env` bằng tay. Đây là sự đánh đổi chấp nhận được để đảm bảo an toàn tuyệt đối cho hệ thống.

### 4. GitHub Action sẽ bắn một commit chéo sang repo GitOps?

**Đúng 100%.**
Quy trình sẽ diễn ra như sau: Sau khi GitHub Action ở kho Backend (App Repo) build xong Docker Image (giả sử tag là `v1.0.5`) và đẩy thành công lên kho chứa ảnh GHCR, nó sẽ sử dụng một Token bảo mật (Personal Access Token) để đóng giả làm một người dùng. Nó sẽ clone kho GitOps về máy chủ CI, dùng lệnh để tìm và sửa dòng `image: ghcr.io/.../ninepaws-backend:v1.0.4` thành `v1.0.5`. Sau đó, nó tự động `git commit` và `git push` ngược lên kho GitOps.

### 5. Portainer (hoặc Cronjob) nhìn vào file và đọc dòng version để so sánh?

**Không hẳn là nó đọc từng dòng để tìm chữ "version". Cách nó hoạt động ở tầng hệ thống thông minh hơn thế.**

Portainer (hay Agent) không quan tâm bên trong file YAML của bạn viết gì. Nó chỉ quan sát duy nhất một thứ: **Mã Hash của Commit mới nhất trên Git**.

* Cứ mỗi 5 phút, Portainer gọi API của GitHub để kiểm tra nhánh `main` của kho GitOps.
* Nếu GitHub Action vừa bắn sang một commit chéo, mã Hash của nhánh `main` sẽ bị thay đổi (ví dụ từ `a1b2c3d` sang `x9y8z7w`).
* Khi thấy mã Hash thay đổi, Portainer lập tức kéo (Pull) toàn bộ thư mục code mới nhất về.
* Sau đó, nó đưa file `docker-compose-prod.yml` mới đó cho công cụ quản lý Docker. Docker lúc này mới là người đọc file, nhận ra rằng: *"À, container hiện tại đang chạy v1.0.4, nhưng file mới lại yêu cầu v1.0.5"*. Docker sẽ tự động tải Image `v1.0.5` về và thay thế container cũ.

### 6. Ta sẽ Rollback (Hoàn tác) như thế nào nếu Production nổ?

Đây chính là câu hỏi "ăn tiền" nhất của triết lý GitOps mà tôi chưa làm rõ cách thực thi cho bạn.

Trong DevOps cũ, khi server nổ, bạn phải luống cuống SSH vào server, gõ lệnh tắt container lỗi, tìm lại Image cũ, rồi gõ lệnh chạy lại. Quá trình này rất dễ gõ sai lệnh trong lúc hoảng loạn.

Trong phương án GitOps (dùng Portainer hoặc Cronjob), **bạn thực hiện Rollback mà không cần đụng tới Server, bạn Rollback thông qua Git.**

Giả sử bản `v1.0.5` vừa được deploy xong thì Production bị sập. Bạn làm các bước sau:

1. **Thao tác trên Git:** Bạn mở giao diện web của GitHub, vào thẳng kho **GitOps Repo**.
2. **Sửa bản thiết kế:** Bạn ấn nút Edit (Hình cây bút) trên file `docker-compose-prod.yml`, sửa dòng `v1.0.5` ngược lại thành `v1.0.4` (hoặc phiên bản cũ nào đang chạy ổn định).
3. **Commit:** Bạn ấn nút "Commit changes" với lời nhắn: *"Rollback khẩn cấp về v1.0.4 do lỗi sập DB"*.
4. **Hệ thống tự lo phần còn lại:** Vài phút sau, Portainer/Cronjob kiểm tra định kỳ, phát hiện ra có một commit mới trên Git (dù nội dung là lùi version nhưng nó vẫn tính là một commit mới). Nó kéo file YAML về.
5. **Khôi phục trạng thái:** Docker nhận được lệnh mới, thấy yêu cầu là `v1.0.4`. Nó lập tức tắt container `v1.0.5` bị lỗi, lôi container `v1.0.4` ra chạy lại bình thường.

**Kết quả:**

* Bạn không cần SSH vào VPS.
* Lịch sử hoảng loạn của bạn được lưu lại rõ ràng minh bạch trên Git (Ai rollback, rollback lúc mấy giờ, vì lý do gì).
* Việc rollback chỉ tốn của bạn đúng 10 giây sửa text trên giao diện web GitHub.


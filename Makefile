.PHONY: up down logs build run format clean clean-seed db-clean db-reset

# Khởi động PostgreSQL và Redis
up:
	docker-compose -f docker/docker-compose.yml --env-file .env up -d

# Tắt các container
down:
	docker-compose -f docker/docker-compose.yml --env-file .env down

# Xem logs của các container
logs:
	docker-compose -f docker/docker-compose.yml --env-file .env logs -f

# Format code chuẩn Google Java Format (thông qua Spotless plugin)
format:
	./mvnw spotless:apply

# Build project (bỏ qua unit tests)
build: format
	./mvnw clean package -DskipTests

# Chạy ứng dụng Spring Boot local với profile dev
run:
	./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Xóa target folder
clean:
	./mvnw clean

# Reset toàn bộ database và Redis (xóa volume và khởi động lại container mới tinh)
db-reset:
	docker-compose -f docker/docker-compose.yml --env-file .env down -v
	docker-compose -f docker/docker-compose.yml --env-file .env up -d
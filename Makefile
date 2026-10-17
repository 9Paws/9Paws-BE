.PHONY: up down logs build run format clean

# Khởi động PostgreSQL và Redis
up:
	docker-compose -f docker/docker-compose.yml --env-file .env up -d

# Tắt các container
down:
	docker-compose -f docker/docker-compose.yml --env-file .env down

# Xem logs của các container
logs:
	docker-compose -f docker/docker-compose.yml logs -f

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
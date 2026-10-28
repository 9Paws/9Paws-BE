-- ==============================================================================
-- Migration: V3__add_oauth_id_to_users.sql
-- Description: Thêm cột oauth_id để lưu trữ định danh bất biến (sub) từ OAuth Provider (Google)
-- ==============================================================================

ALTER TABLE users ADD COLUMN oauth_id VARCHAR(100);

-- Unique partial index cho oauth_id (cho phép nhiều giá trị NULL cho tài khoản LOCAL)
CREATE UNIQUE INDEX idx_users_oauth_id ON users(oauth_id) WHERE oauth_id IS NOT NULL;

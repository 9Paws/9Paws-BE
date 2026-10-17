-- ==============================================================================
-- Migration: V1__init_ninepaws_schema.sql
-- Description: Khởi tạo schema 13 bảng chuẩn 3NF/BCNF cho 9Paws Backend
-- Standard Invariants: UUID PKs, NUMERIC(18,2) Money, TIMESTAMPTZ, JSONB
-- ==============================================================================

-- 1. BẢNG USERS
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone_number VARCHAR(20),
    password_hash VARCHAR(255),
    auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    avatar_url TEXT,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    biometric_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    daily_reminder_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    daily_reminder_time TIME NOT NULL DEFAULT '21:00:00',
    currency VARCHAR(10) NOT NULL DEFAULT 'VND',
    language VARCHAR(10) NOT NULL DEFAULT 'vi',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. BẢNG PROVIDERS (Master data Ngân hàng & Ví điện tử)
CREATE TABLE providers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    bin_code VARCHAR(20),
    short_name VARCHAR(100) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL,
    logo_url TEXT NOT NULL
);

-- 3. BẢNG WALLETS (Ví tiền, Tài khoản & Heo đất)
CREATE TABLE wallets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider_id UUID REFERENCES providers(id) ON DELETE SET NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    balance NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    target_amount NUMERIC(18,2),
    target_date DATE,
    icon_url TEXT,
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. BẢNG CATEGORIES (Danh mục Thu & Chi)
-- user_id = NULL: Danh mục mặc định hệ thống
-- user_id != NULL: Danh mục do người dùng tự tạo
CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    group_name VARCHAR(100),
    icon_url TEXT NOT NULL,
    color_code VARCHAR(20),
    is_popular BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 5. BẢNG TRANSACTIONS (Giao dịch Sổ Thu/Chi)
-- Tuyệt đối KHÔNG có user_id (Sở hữu bắc cầu qua wallet_id -> wallets.user_id)
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    type VARCHAR(20) NOT NULL,
    amount NUMERIC(18,2) NOT NULL,
    note VARCHAR(255),
    transaction_date TIMESTAMPTZ NOT NULL,
    source VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0)
);

-- 6. BẢNG BUDGETS (Ngân sách tháng tổng & theo danh mục)
-- category_id = NULL: Tổng ngân sách tháng
-- category_id != NULL: Ngân sách theo danh mục
CREATE TABLE budgets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id UUID REFERENCES categories(id) ON DELETE CASCADE,
    month INT NOT NULL CHECK (month BETWEEN 1 AND 12),
    year INT NOT NULL,
    amount_limit NUMERIC(18,2) NOT NULL,
    alert_threshold INT NOT NULL DEFAULT 80,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_budgets_limit_positive CHECK (amount_limit > 0),
    CONSTRAINT uq_budgets_user_category_month_year UNIQUE NULLS NOT DISTINCT (user_id, category_id, month, year)
);

-- 7. BẢNG RECURRING_TRANSACTIONS (Cấu hình giao dịch định kỳ)
-- Tuyệt đối KHÔNG có user_id (Sở hữu bắc cầu qua wallet_id -> wallets.user_id)
CREATE TABLE recurring_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    title VARCHAR(150) NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'EXPENSE',
    estimated_amount NUMERIC(18,2) NOT NULL,
    frequency VARCHAR(20) NOT NULL,
    day_of_period INT,
    start_date DATE NOT NULL,
    end_date DATE,
    next_occurrence_date DATE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 8. BẢNG NOTIFICATIONS (Thông báo & Lời nhắc định kỳ)
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recurring_trans_id UUID REFERENCES recurring_transactions(id) ON DELETE SET NULL,
    budget_id UUID REFERENCES budgets(id) ON DELETE SET NULL,
    created_transaction_id UUID UNIQUE REFERENCES transactions(id) ON DELETE SET NULL,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    action_status VARCHAR(20),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 9. BẢNG CHAT_MESSAGES (Lịch sử Chat AI Miu Miu & Bóc tách thẻ nháp)
CREATE TABLE chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    confirmed_transaction_id UUID UNIQUE REFERENCES transactions(id) ON DELETE SET NULL,
    sender VARCHAR(10) NOT NULL,
    chat_mode VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 10. BẢNG SPLIT_BILLS (Hóa đơn chia tiền nhóm)
-- Tuyệt đối KHÔNG có user_id (Sở hữu bắc cầu qua receiving_wallet_id -> wallets.user_id)
CREATE TABLE split_bills (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receiving_wallet_id UUID NOT NULL REFERENCES wallets(id) ON DELETE RESTRICT,
    title VARCHAR(200) NOT NULL,
    tag VARCHAR(50),
    total_amount NUMERIC(18,2) NOT NULL,
    split_method VARCHAR(20) NOT NULL,
    event_date TIMESTAMPTZ NOT NULL,
    location_name VARCHAR(200),
    location_address TEXT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    vietqr_bank_bin VARCHAR(20) NOT NULL,
    vietqr_account_no VARCHAR(50) NOT NULL,
    vietqr_account_name VARCHAR(100) NOT NULL,
    vietqr_transfer_message VARCHAR(100),
    share_token VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'COLLECTING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_split_bills_total_positive CHECK (total_amount > 0)
);

-- 11. BẢNG BILL_PARTICIPANTS (Thành viên tham gia chia bill)
CREATE TABLE bill_participants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_id UUID NOT NULL REFERENCES split_bills(id) ON DELETE CASCADE,
    transaction_id UUID UNIQUE REFERENCES transactions(id) ON DELETE SET NULL,
    name VARCHAR(100) NOT NULL,
    avatar_url TEXT,
    is_host BOOLEAN NOT NULL DEFAULT FALSE,
    is_auto_balanced BOOLEAN NOT NULL DEFAULT FALSE,
    amount_owed NUMERIC(18,2) NOT NULL,
    custom_note VARCHAR(255),
    payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    paid_at TIMESTAMPTZ,
    CONSTRAINT chk_bill_participants_owed_non_negative CHECK (amount_owed >= 0)
);

-- 12. BẢNG BILL_ITEMS (Món ăn / Khoản chi trong bill)
CREATE TABLE bill_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_id UUID NOT NULL REFERENCES split_bills(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    unit_price NUMERIC(18,2) NOT NULL,
    total_price NUMERIC(18,2) NOT NULL
);

-- 13. BẢNG BILL_ITEM_SHARES (Phân bổ món cho thành viên)
CREATE TABLE bill_item_shares (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_item_id UUID NOT NULL REFERENCES bill_items(id) ON DELETE CASCADE,
    participant_id UUID NOT NULL REFERENCES bill_participants(id) ON DELETE CASCADE,
    shared_amount NUMERIC(18,2) NOT NULL,
    CONSTRAINT uq_bill_item_participant UNIQUE (bill_item_id, participant_id)
);

-- ==============================================================================
-- INDEXES TỐI ƯU TRUY VẤN (Theo postgres-pattern SKILL)
-- ==============================================================================

-- 1. Wallets lookup theo user
CREATE INDEX idx_wallets_user_id ON wallets (user_id) WHERE is_archived = FALSE;

-- 2. Transactions filtering theo ví và danh mục kết hợp thời gian (Core Ledger & Dashboard)
CREATE INDEX idx_transactions_wallet_date ON transactions (wallet_id, transaction_date DESC);
CREATE INDEX idx_transactions_category_date ON transactions (category_id, transaction_date DESC);

-- 3. Categories lookup (Hệ thống + Cá nhân)
CREATE INDEX idx_categories_user_type ON categories (user_id, type);

-- 4. Budgets lookup theo user và chu kỳ tháng/năm
CREATE INDEX idx_budgets_user_period ON budgets (user_id, year, month);

-- 5. Recurring transactions do cronjob quét ngày đến hạn
CREATE INDEX idx_recurring_next_due ON recurring_transactions (next_occurrence_date) WHERE is_active = TRUE;

-- 6. Notifications theo user và thời gian
CREATE INDEX idx_notifications_user_created ON notifications (user_id, is_read, created_at DESC);

-- 7. Chat messages lịch sử theo user và chế độ chat
CREATE INDEX idx_chat_messages_user_mode_created ON chat_messages (user_id, chat_mode, created_at DESC);
CREATE INDEX idx_chat_messages_metadata_gin ON chat_messages USING gin (metadata) WHERE metadata IS NOT NULL;

-- 8. Split Bills theo ví nhận tiền
CREATE INDEX idx_split_bills_wallet ON split_bills (receiving_wallet_id, created_at DESC);

-- 9. Bill participants theo bill và trạng thái thanh toán
CREATE INDEX idx_bill_participants_bill_status ON bill_participants (bill_id, payment_status);

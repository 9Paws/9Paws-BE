-- ==============================================================================
-- Migration: V2__seed_master_providers_and_system_categories.sql
-- Description: Nạp dữ liệu danh mục tĩnh (Master Data): Ngân hàng, Ví điện tử
--              và Danh mục thu/chi mặc định của hệ thống (user_id IS NULL)
-- Quy tắc: Tuyệt đối KHÔNG nạp tài khoản người dùng, PII hay mock transactions.
-- ==============================================================================

-- 1. SEED PROVIDERS (Ngân hàng & Ví điện tử phổ biến tại Việt Nam)
INSERT INTO providers (id, code, bin_code, short_name, full_name, type, logo_url) VALUES
('a0000001-0000-0000-0000-000000000001', 'VCB', '970436', 'Vietcombank', 'Ngân hàng TMCP Ngoại thương Việt Nam', 'BANK', 'https://api.vietqr.io/img/VCB.png'),
('a0000001-0000-0000-0000-000000000002', 'CTG', '970415', 'VietinBank', 'Ngân hàng TMCP Công thương Việt Nam', 'BANK', 'https://api.vietqr.io/img/ICB.png'),
('a0000001-0000-0000-0000-000000000003', 'BIDV', '970418', 'BIDV', 'Ngân hàng TMCP Đầu tư và Phát triển Việt Nam', 'BANK', 'https://api.vietqr.io/img/BIDV.png'),
('a0000001-0000-0000-0000-000000000004', 'TCB', '970407', 'Techcombank', 'Ngân hàng TMCP Kỹ thương Việt Nam', 'BANK', 'https://api.vietqr.io/img/TCB.png'),
('a0000001-0000-0000-0000-000000000005', 'MB', '970422', 'MBBank', 'Ngân hàng TMCP Quân đội', 'BANK', 'https://api.vietqr.io/img/MB.png'),
('a0000001-0000-0000-0000-000000000006', 'ACB', '970416', 'ACB', 'Ngân hàng TMCP Á Châu', 'BANK', 'https://api.vietqr.io/img/ACB.png'),
('a0000001-0000-0000-0000-000000000007', 'VPB', '970432', 'VPBank', 'Ngân hàng TMCP Việt Nam Thịnh Vượng', 'BANK', 'https://api.vietqr.io/img/VPB.png'),
('a0000001-0000-0000-0000-000000000008', 'STB', '970403', 'Sacombank', 'Ngân hàng TMCP Sài Gòn Thương Tín', 'BANK', 'https://api.vietqr.io/img/STB.png'),
('a0000001-0000-0000-0000-000000000009', 'TPB', '970423', 'TPBank', 'Ngân hàng TMCP Tiên Phong', 'BANK', 'https://api.vietqr.io/img/TPB.png'),
('a0000001-0000-0000-0000-000000000010', 'MOMO', NULL, 'MoMo', 'Ví điện tử MoMo', 'E_WALLET', 'https://upload.wikimedia.org/wikipedia/vi/f/fe/MoMo_Logo.png'),
('a0000001-0000-0000-0000-000000000011', 'ZALOPAY', NULL, 'ZaloPay', 'Ví điện tử ZaloPay', 'E_WALLET', 'https://cdn.haitrieu.com/wp-content/uploads/2022/10/Logo-ZaloPay-Square.png')
ON CONFLICT (code) DO NOTHING;

-- 2. SEED SYSTEM CATEGORIES (user_id IS NULL)
-- Nhóm Chi tiêu (EXPENSE)
INSERT INTO categories (id, user_id, name, type, group_name, icon_url, color_code, is_popular) VALUES
('b0000001-0000-0000-0000-000000000001', NULL, 'Ẩm thực', 'EXPENSE', 'Chi phí sinh hoạt', 'utensils', '#FF7A00', TRUE),
('b0000001-0000-0000-0000-000000000002', NULL, 'Mua sắm', 'EXPENSE', 'Chi phí sinh hoạt', 'shopping-bag', '#7B61FF', TRUE),
('b0000001-0000-0000-0000-000000000003', NULL, 'Di chuyển', 'EXPENSE', 'Chi phí sinh hoạt', 'car', '#00C096', TRUE),
('b0000001-0000-0000-0000-000000000004', NULL, 'Hóa đơn & Tiện ích', 'EXPENSE', 'Chi phí cố định', 'receipt', '#FFB800', TRUE),
('b0000001-0000-0000-0000-000000000005', NULL, 'Giải trí', 'EXPENSE', 'Chi phí linh hoạt', 'gamepad', '#FF4D4F', TRUE),
('b0000001-0000-0000-0000-000000000006', NULL, 'Sức khỏe', 'EXPENSE', 'Chi phí cố định', 'heart-pulse', '#36CFC9', TRUE),
('b0000001-0000-0000-0000-000000000007', NULL, 'Giáo dục', 'EXPENSE', 'Phát triển bản thân', 'graduation-cap', '#1890FF', TRUE),
('b0000001-0000-0000-0000-000000000008', NULL, 'Nhà cửa', 'EXPENSE', 'Chi phí cố định', 'house', '#9254DE', TRUE);

-- Nhóm Thu nhập (INCOME)
INSERT INTO categories (id, user_id, name, type, group_name, icon_url, color_code, is_popular) VALUES
('b0000002-0000-0000-0000-000000000001', NULL, 'Tiền lương', 'INCOME', 'Thu nhập chính', 'wallet', '#52C41A', TRUE),
('b0000002-0000-0000-0000-000000000002', NULL, 'Tiền thưởng', 'INCOME', 'Thu nhập phụ', 'award', '#FADB14', TRUE),
('b0000002-0000-0000-0000-000000000003', NULL, 'Thu nợ bill', 'INCOME', 'Thu nợ & Chia tiền', 'hand-coins', '#13C2C2', TRUE),
('b0000002-0000-0000-0000-000000000004', NULL, 'Đầu tư', 'INCOME', 'Đầu tư', 'chart-line', '#2F54EB', FALSE),
('b0000002-0000-0000-0000-000000000005', NULL, 'Khác', 'INCOME', 'Thu nhập khác', 'ellipsis', '#8C8C8C', FALSE);

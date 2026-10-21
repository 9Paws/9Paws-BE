# AGENT.md — Ninepaws (`ninepaws-backend`)

You are working on **`ninepaws-backend`**, the Java Spring Boot backend for **Ninepaws** — a personal finance management, monthly budgeting, dynamic VietQR group bill-splitting, and AI financial assistant mobile application.

---

## 1. Source of Truth Hierarchy (CRITICAL)

When instructions in generic `.agent/skills/` conflict with project specifications, **ALWAYS obey this strict priority order**:

1. **`docs/Logical-ERD.md` & `docs/Conceptual-ERD.md`** (Database schema, data types, relationships, and business constraints).
2. **`.agent/ARCHITECTURE.md` & `.agent/rules/*`** (Package structure, layer responsibilities, and coding guardrails).
3. **`AGENT.md`** (This file).
4. **`.agent/skills/*` & `.agent/workflows/*`** (General technical patterns — adapt examples to match with this project).

---

## 2. Non-Negotiable Core Domain Constraints

1. **NO Direct Bank API Integration**:
   - Never build third-party bank account syncing or webhook payment gateways.
   - Wallet balances (`wallets.balance`) change ONLY via:
     1. Manual transaction input (`source = 'MANUAL'`).
     2. AI Miu Miu natural language draft confirmation (`source = 'AI_CHAT'`).
     3. Recurring transaction due notification confirmation (`source = 'RECURRING'`).
     4. Host confirming a participant's bill payment (`source = 'SPLIT_BILL'`).
2. **NO Gamification / Badges**:
   - Do not create entities, endpoints, or logic for user badges, streaks, or achievements.
3. **Strict 3NF / BCNF Database Normalization (13 Tables in PostgreSQL)**:
   - **Transitive Ownership via `wallets`**: `transactions`, `recurring_transactions`, and `split_bills` DO NOT have a `user_id` column. Ownership MUST always be verified and queried transitively through `wallets.user_id` (`receiving_wallet_id` for `split_bills`).
   - **Primary Keys**: All 13 tables use `UUID` (`java.util.UUID`) as Primary Key — NEVER use `Long` or `BIGSERIAL` even if generic skill templates show `Long`.
   - **Monetary Values**: Always use `NUMERIC(18,2)` in PostgreSQL and `BigDecimal` in Java (VND currency). Never use `Double`, `Float`, or `numeric(10,2)`.
   - **Timestamps**: Always use `TIMESTAMPTZ` in PostgreSQL mapped to `Instant` or `OffsetDateTime` in Java.
   - **OTP Storage**: 6-digit email OTPs (registration & password reset) are stored exclusively in **Redis** (`TTL = 120s`), NEVER in PostgreSQL.
   - **AI Draft Metadata**: Temporary NLP-extracted transaction drafts from Miu Miu are stored in `chat_messages.metadata` (`JSONB`) with `confirmed_transaction_id = NULL` until the user explicitly confirms saving to the ledger.

---

## 3. Functional Modules Overview (8 Modules)

1. **Module 1 — Auth & User Profile (`users`, Redis OTP)**: Local Email/Password + Google OAuth2, 6-digit Redis OTP (120s TTL), biometric toggle, daily reminder time (default `21:00`), companion days counter since `created_at`.
2. **Module 2 — Wallets & Piggy Bank (`providers`, `wallets`)**: Single `wallets` table handling 4 types (`BANK`, `E_WALLET`, `CASH`, `PIGGY_BANK`). `provider_id` is `NULL` for `CASH` and `PIGGY_BANK`. `target_amount` and `target_date` are used for `PIGGY_BANK`.
3. **Module 3 — Core Ledger (`categories`, `transactions`)**: System default categories (`user_id = NULL`) + user custom categories (`user_id != NULL`). Creating/updating/deleting a transaction atomically updates `wallets.balance`.
4. **Module 4 — Dashboard Analytics**: Monthly income/expense summary with MoM % change, Category allocation Pie/Donut chart, 6-month spending trend Bar chart with average line, Budget progress rings.
5. **Module 5 — Monthly Budgets (`budgets`)**: Single table for both Total Monthly Budget (`category_id = NULL`) and Category Budget (`category_id != NULL`), 6-month category trend preview, and threshold alerts (default `80%`).
6. **Module 6 — Recurring Transactions & Smart Notifications (`recurring_transactions`, `notifications`)**: Cron scheduler checks `next_occurrence_date` to create `RECURRING_DUE` notifications (`PENDING`). User confirmation creates the actual `Transaction`, deducts `wallets.balance`, and links `created_transaction_id`.
7. **Module 7 — Group Bill Splitting & Dynamic VietQR (`split_bills`, `bill_participants`, `bill_items`, `bill_item_shares`)**: 4-step bill creation (`EQUAL` with host auto-balancing `is_auto_balanced`, `BY_ITEM`, `CUSTOM`), public web link via `share_token`, dynamic VietQR generation. Marking a participant as `PAID_TRANSFER` or `PAID_CASH` auto-creates an `INCOME` transaction in `receiving_wallet_id`; reverting to `UNPAID` deletes that transaction and reverses the wallet balance.
8. **Module 8 — AI Financial Assistant "Miu Miu" (`chat_messages`)**: Dual mode: `ADVISOR_RAG` (queries real ledger/budget data via Tool Calling) and `ADD_TRANSACTION` (extracts natural language into a JSONB Draft Card in `metadata`, creating a real `Transaction` only upon user confirmation).

---

## 4. Build, Run & Verification Commands

Always use the Maven Wrapper (`./mvnw`) or `Makefile` from the project root:

```bash
# Compile and verify build
./mvnw clean compile

# Run unit and integration tests
./mvnw test

# Run full verification (including coverage / checks)
./mvnw verify

# Start local infrastructure (PostgreSQL, Redis) via Docker Compose
docker compose -f docker/docker-compose.yml up -d

# Run Spring Boot application locally (dev profile)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## 5. Agent Operating Protocol

1. **Before writing or modifying database entities/migrations**: Read `docs/Logical-ERD.md` first to verify exact column names, nullability, and foreign keys.
2. **Before implementing business logic**: Check `.agent/ARCHITECTURE.md` to place classes in the exact package under `com.ninepaws.backend`.
3. **When modifying wallet balances**: Always use `@Transactional` and pessimistic locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) on the target wallet row to prevent race conditions.
4. **Before declaring any task complete**: Run `./mvnw test` (or `./mvnw clean compile` if tests are not yet applicable) to prove zero compilation or regression errors.






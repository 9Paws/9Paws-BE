# Project Architecture — `ninepaws-backend`

## 1. Directory Structure

```text
ninepaws-backend/
├── .agent/
│   ├── agents/                     # Sub-agent personas (planner, java-reviewer, security-reviewer, db-architect)
│   ├── rules/                      # Always-on project & domain guardrails
│   ├── skills/                     # Technical & Cozy Ledger domain playbooks
│   ├── workflows/                  # Step-by-step execution workflows
│   └── ARCHITECTURE.md             # Architectural blueprint & package boundaries (this file)
├── .github/                        # GitHub Actions CI/CD workflows
├── .mvn/
│   └── wrapper/
│       └── maven-wrapper.properties
├── docker/
│   ├── .dockerignore
│   ├── Dockerfile                  # Multi-stage Java/Maven Dockerfile
│   └── docker-compose.yml          # Local PostgreSQL, Redis, and app services
├── docs/
│   ├── Conceptual-ERD.md           # Conceptual entity relationships & nullable FK rationale
│   └── Logical-ERD.md              # Physical schema (13 tables, columns, constraints, DBML)
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── ninepaws/
│   │   │           └── backend/
│   │   │               ├── configs/        # Spring @Configuration beans (Security, Redis, OpenAPI, CORS, AI, Jackson)
│   │   │               ├── constants/      # System-wide constants, regex patterns, error codes, Redis key prefixes
│   │   │               ├── controllers/    # REST API endpoints (@RestController), input @Valid, thin routing
│   │   │               ├── dtos/           # Request & Response DTOs (Java records, grouped by module/feature)
│   │   │               ├── entities/       # JPA @Entity classes mapping 1:1 to the 13 PostgreSQL tables
│   │   │               ├── enums/          # Domain enumerations (WalletType, TransactionType, SplitMethod, etc.)
│   │   │               ├── exceptions/     # Custom domain exceptions & GlobalExceptionHandler (@RestControllerAdvice)
│   │   │               ├── mappers/        # MapStruct / component mappers between Entities and DTOs
│   │   │               ├── repositories/   # Spring Data JPA interfaces, custom @Query, projections, locking
│   │   │               ├── schedulers/     # @Scheduled cron jobs (Recurring transactions due, Daily 21:00 reminders)
│   │   │               ├── security/       # JWT filters, OAuth2 handlers, UserDetails, ownership security evaluators
│   │   │               ├── services/       # Core business logic, @Transactional boundaries, balance synchronization
│   │   │               ├── utils/          # Stateless helper utilities (VietQR payload builder, rounding, date math)
│   │   │               └── NinepawsBackendApplication.java
│   │   └── resources/
│   │       ├── db/
│   │       │   └── migration/              # Flyway SQL migrations (V1__..., V2__...)
│   │       ├── static/
│   │       ├── application.yml             # Shared base configuration
│   │       ├── application-dev.yml         # Local development configuration
│   │       └── application-prod.yaml       # Production hardened configuration
│   └── test/
│       └── java/
│           └── com/
│               └── ninepaws/
│                   └── backend/            # Unit, slice (@WebMvcTest, @DataJpaTest), and integration tests
├── .env
├── .env.example
├── .gitattributes
├── .gitignore
├── AGENT.md
├── HELP.md
├── Makefile
├── mvnw
├── mvnw.cmd
└── pom.xml

```

---

## 2. Package Responsibilities & Strict Layer Boundaries

The project follows a strict **Technical-Layered Architecture** under `com.ninepaws.backend`:

```text
[Client / Mobile App / Public Split-Bill Web]
       │ (JSON DTOs over HTTPS)
       ▼
  controllers/   ──(validates @Valid DTOs, extracts authenticated User ID)──►
       │
       ▼
   services/     ──(@Transactional business rules, ownership checks, balance sync)──►
       │                                      ▲
       ▼                                      │ (uses)
 repositories/   ◄──(maps via mappers/)───────┴──► entities/ & enums/
       │
       ▼
 [PostgreSQL (13 Tables) & Redis (OTP / Cache)]

```

### Package-by-Package Rules

1. **`controllers/`**:
* Keep controllers strictly thin: handle HTTP routing, `@Valid` request validation, pagination parameters, and HTTP status codes.
* **NEVER** inject `repositories/` directly into a Controller (always go through `services/`).
* **NEVER** accept or return JPA `entities/` in controller methods; always use `dtos/`.


2. **`dtos/`**:
* Organize into sub-packages by domain module (e.g., `dtos.auth`, `dtos.wallet`, `dtos.transaction`, `dtos.budget`, `dtos.recurring`, `dtos.notification`, `dtos.splitbill`, `dtos.chat`, `dtos.common`).
* Prefer immutable Java `record` types with Jakarta Validation annotations (`@NotBlank`, `@NotNull`, `@Positive`, `@Size`, `@Email`).


3. **`services/`**:
* Contains all business rules, atomic balance calculations, VietQR generation orchestration, and AI RAG/extraction orchestration.
* Annotate read operations with `@Transactional(readOnly = true)` and write operations with `@Transactional`.
* Enforce **Transitive Ownership Verification**: Before reading or mutating `Transaction`, `RecurringTransaction`, or `SplitBill`, verify that `entity.getWallet().getUser().getId().equals(currentUserId)`.


4. **`repositories/`**:
* Spring Data JPA repositories extending `JpaRepository<Entity, UUID>`.
* Use `@Lock(LockModeType.PESSIMISTIC_WRITE)` on `WalletRepository.findByIdAndUserIdForUpdate(UUID id, UUID userId)` whenever a wallet balance is about to be modified.
* Avoid N+1 queries by using `@EntityGraph` or explicit `JOIN FETCH` JPQL queries.


5. **`entities/`**:
* Maps strictly to the 13 tables defined in `docs/Logical-ERD.md`:
1. `User` (`users`)
2. `Provider` (`providers`)
3. `Wallet` (`wallets`)
4. `Category` (`categories`)
5. `Transaction` (`transactions`)
6. `Budget` (`budgets`)
7. `RecurringTransaction` (`recurring_transactions`)
8. `Notification` (`notifications`)
9. `ChatMessage` (`chat_messages` — uses `@JdbcTypeCode(SqlTypes.JSON)` for `metadata`)
10. `SplitBill` (`split_bills`)
11. `BillParticipant` (`bill_participants`)
12. `BillItem` (`bill_items`)
13. `BillItemShare` (`bill_item_shares`)


* Use `FetchType.LAZY` on all `@ManyToOne` and `@OneToOne` associations.


6. **`enums/`**:
* Strongly typed Java `enum` classes matching database check/varchar values:
* `AuthProvider` (`LOCAL`, `GOOGLE`)
* `ProviderType` (`BANK`, `E_WALLET`)
* `WalletType` (`BANK`, `E_WALLET`, `CASH`, `PIGGY_BANK`)
* `TransactionType` (`EXPENSE`, `INCOME`)
* `TransactionSource` (`MANUAL`, `AI_CHAT`, `RECURRING`, `SPLIT_BILL`)
* `RecurringFrequency` (`DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`)
* `NotificationType` (`RECURRING_DUE`, `BUDGET_ALERT`, `DAILY_REMINDER`, `SYSTEM`)
* `NotificationActionStatus` (`PENDING`, `CONFIRMED`, `SKIPPED`)
* `ChatSender` (`USER`, `BOT`)
* `ChatMode` (`ADVISOR_RAG`, `ADD_TRANSACTION`)
* `SplitMethod` (`EQUAL`, `BY_ITEM`, `CUSTOM`)
* `SplitBillStatus` (`COLLECTING`, `COMPLETED`)
* `ParticipantPaymentStatus` (`PAID_BY_HOST`, `UNPAID`, `PAID_TRANSFER`, `PAID_CASH`)




7. **`mappers/`**:
* Dedicated mapper classes/interfaces (MapStruct or clean manual mappers) converting between `entities` and `dtos`.


8. **`schedulers/`**:
* `RecurringTransactionScheduler`: Scans active `recurring_transactions` where `next_occurrence_date <= CURRENT_DATE`, creates `Notification` (`type = RECURRING_DUE`, `action_status = PENDING`), and advances `next_occurrence_date`.
* `DailyReminderScheduler`: Triggers daily bookkeeping reminders based on `users.daily_reminder_enabled` and `users.daily_reminder_time`.


9. **`security/`**:
* Stateless JWT authentication (`JwtAuthenticationFilter`, `JwtService`) + OAuth2 Google authentication + Public endpoint allowance for `/api/v1/public/split-bills/{shareToken}` so friends can view bill details and VietQR without logging in.


10. **`exceptions/`**:
* `GlobalExceptionHandler` (`@RestControllerAdvice`) returning standardized error responses without leaking internal stack traces or database details.



---

## 3. Database Migration Strategy (`src/main/resources/db/migration/`)

* Managed by **Flyway**.
* Naming convention: `V{version}__{snake_case_description}.sql` (e.g., `V1__init_cozy_ledger_schema.sql`, `V2__seed_master_providers_and_system_categories.sql`).
* **Rule on Seed Data**:
* **ALLOWED in Flyway**: Non-sensitive system master data required for the app to function (`providers` list of Vietnamese banks/e-wallets with Napas `bin_code`, and default system `categories` where `user_id IS NULL`).
* **FORBIDDEN in Flyway**: Real user accounts, admin passwords, PII, or mock transactions (place dev mock data in a `dev` profile initializer if needed).
```
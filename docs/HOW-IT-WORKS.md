# How it works: a beginner's guide

This guide explains the shop from the outside in: the big picture, one order step by step,
then each technique (outbox, ShedLock, idempotency, security, refresh tokens ...), with the
file where it lives. At the end: interview questions with short answers.

---

## 1. The big picture

```
                          Browser (frontend/: HTML + CSS + vanilla JS)
                                         │  HTTPS, one origin
                                         ▼
                                  nginx (production only)
                                         │
                                         ▼
                         API Gateway :8080 (Spring Cloud Gateway)
          checks the JWT, rate-limits per IP (Redis), routes by path
          │            │             │              │                │
     /users/**   /products/**   /orders/**    /payments/**   /notifications/**
          ▼            ▼             ▼              ▼                ▼
       User        Product        Order         Payment        Notification
       :8082        :8081         :8083          :8084            :8085
       MySQL       MongoDB        MySQL        PostgreSQL         MongoDB
       userdb     productdb      order_db      payment_db      notificationdb
          │            ▲  │          ▲  │          ▲  │              ▲
          │            │  └── Kafka ─┘  └─ Kafka ──┘  └── Kafka ─────┘
          └── Kafka (user-events) ──────────────────────────────────┘

   Config Server :8888  every service reads its settings from a private GitLab repo
   Eureka :8761         every service registers here; "lb://order-service" finds it
```

- **One database per service.** No service reads another service's tables. They talk in two
  ways: **HTTP** when an answer is needed right now (Order asks Product for a price), and
  **Kafka events** for everything that can happen a moment later (stock, payment, emails).
- **Kafka topics**, all keyed by `orderId` (so one order's events stay in order), except
  `user-events` (keyed by `userId`): `order-events`, `inventory-events`, `payment-events`,
  `user-events`, each with a `-dlt` dead-letter topic.

## 2. One order, step by step (the saga)

A **saga** is a business process spread over several services, each doing its part in its
own database transaction and announcing the result with an event. There is no distributed
transaction: if a later step fails, earlier steps are undone by more events
("compensation").

```
 User           Order              Product              Payment            Notification
  │ POST /orders  │                    │                    │                    │
  │──────────────►│ save order PENDING │                    │                    │
  │  201 PENDING  │ + OrderEvent in    │                    │                    │
  │◄──────────────│   the outbox       │                    │                    │
  │               │── order-events ───►│ hold stock         │                    │
  │               │    (PENDING)       │                    │                    │
  │               │◄─ inventory-events ┤ RESERVED ─────────►│ create PENDING     │
  │               │ AWAITING_PAYMENT   │                    │ payment (15 min)   │
  │ POST /payments/order/{id}/pay      │                    │                    │
  │────────────────────────────────────────────────────────►│ PENDING→PROCESSING │
  │                                                         │ charge (mock)      │
  │                    200 SUCCESS                          │ → SUCCESS          │
  │◄────────────────────────────────────────────────────────│                    │
  │               │◄──────────── payment-events (SUCCESS) ──┤                    │
  │               │ CONFIRMED          │◄── SUCCESS: holds marked paid           │
  │               │                    │                    │      every event ─►│ save + email
```

| Step | Who | What happens |
|---|---|---|
| 1 | Order | `POST /orders`: asks Product for each price (never trusts prices from the browser), saves the order `PENDING` **and** an `OrderEvent` in the outbox, in one transaction |
| 2 | Product | Gets `PENDING`, **holds** the stock on each product document, sends `RESERVED` (or `REJECTED` when something is short) |
| 3 | Order | `RESERVED` → `AWAITING_PAYMENT`; `REJECTED` → `CANCELLED` (`OUT_OF_STOCK`) |
| 3 | Payment | `RESERVED` → creates a `PENDING` payment, payable for 15 minutes |
| 4 | Payment | User pays: claim `PENDING`→`PROCESSING`, charge the (mock) gateway, then `SUCCESS` or `FAILED`; the `PaymentEvent` goes through Payment's outbox |
| 5 | Order | `SUCCESS` → `CONFIRMED`; `FAILED` → `CANCELLED` (`PAYMENT_FAILED`); `EXPIRED` → `CANCELLED` (`PAYMENT_TIMEOUT`) |
| 5 | Product | `SUCCESS` → holds marked paid (the stock is sold); `FAILED`/`EXPIRED`/`CANCELLED` → holds released (stock back) |
| 6 | Payment | An order that is `CANCELLED` although its payment succeeded is refunded (`REFUNDED`, mock refund) |
| 7 | Notification | Saves one notification per `(orderId, type)` and `EmailSenderJob` emails it |

**Safety nets** (scheduled jobs, so nothing waits forever):

- Order: still `PENDING` 5 minutes after its event reached Kafka → `CANCELLED` (`RESERVATION_TIMEOUT`).
- Payment: still `PENDING` after 15 minutes → `EXPIRED` (Order then cancels, Product releases).
- Order: `AWAITING_PAYMENT` for 60 minutes → `CANCELLED` (if Payment's event never came).
- Payment: stuck in `PROCESSING` for 2 minutes (service died while charging) → finished
  with what the gateway says (`PaymentRecoveryJob`).

**Known limit:** different topics are not ordered against each other. Every consumer checks
the current state first, so nothing breaks, but a user can see both "placed successfully"
and "cancelled" when a cancel and a payment race.

## 3. The outbox pattern

**Problem:** "save the order, then send the event to Kafka" can half-fail. If the service
crashes after the commit and before the send, the event is lost and the order hangs forever.
Sending first and saving second is worse: the event announces an order that may never exist.

**Solution:** write the event **into the same database, in the same transaction** as the
change, and send it later:

1. The service calls `eventPublisher.publishEvent(event)` inside its `@Transactional` method.
2. `*OutboxWriter` (`@TransactionalEventListener(phase = BEFORE_COMMIT)`) saves it as a row
   in `outbox_events` (status `PENDING`), still inside the same transaction. Order and event
   are committed together or not at all.
3. `OutboxPublisher` (every second) reads `PENDING` rows in id order, sends each to Kafka,
   waits for Kafka's acknowledgement, then marks the row `SENT`. If Kafka is down it keeps the
   rows and waits 2, 4, 8 ... up to 30 seconds before trying again.
4. `OutboxCleanupJob` (monthly) deletes old `SENT` rows.

Result: **at-least-once** delivery. A crash between "sent" and "marked SENT" sends the event
again, which is why every consumer must be idempotent (next section).

Where: `kafka/*OutboxWriter`, `kafka/OutboxPublisher`, `kafka/OutboxCleanupJob` in Order,
Payment and User. Product sends while handling a Kafka message, so it sends synchronously
instead (`send().get()`): the incoming message is only acknowledged once the outgoing one is
in Kafka.

## 4. Idempotency: the same message twice must do no harm

Kafka delivers **at least once**: after a crash or rebalance a consumer can get a message
again. Each consumer protects itself in its own way:

| Consumer | How a duplicate is ignored |
|---|---|
| Product (stock holds) | A hold is only added when the product has none for this `orderId` yet (one atomic MongoDB update with that condition) |
| Order | State machine: `SUCCESS` changes only an order that is still `PENDING` / `AWAITING_PAYMENT` |
| Payment | One payment per order: a unique constraint on `order_id` in the database |
| Notification | One notification per `(orderId, type)`: a unique MongoDB index |
| User contacts | Upsert by `userId` |

Messages that can never succeed (unreadable JSON) are not retried: `KafkaErrorConfig` sends
them straight to `<topic>-dlt` with one ERROR log line. Other failures are retried, then go to
the DLT too. Order's `payment-events` consumer uses `@RetryableTopic` (retry topics with
growing delays). See [DEAD-LETTER-TOPICS.md](DEAD-LETTER-TOPICS.md) for how to inspect and replay them.

## 5. ShedLock: one scheduled job at a time

`@Scheduled` runs a method on a timer **in every running copy** of a service. With two copies
of Payment, `PaymentExpiryJob` would run twice at once and, for example, publish two
`EXPIRED` events. `@SchedulerLock(name = "...")` takes a lock row in the `shedlock` table
first (MongoDB collection `shedLock` in Notification); the other copy sees the lock and skips
that run. `lockAtMostFor` frees the lock if the holder crashes.

Jobs: `outboxPublisher`, `outboxCleanupJob`, `paymentExpiryJob`, `paymentRecoveryJob`,
`pendingTimeoutJob`, `paymentTimeoutJob`, `refreshTokenCleanupJob`, `emailSender`.
`HoldCleanupJob` (Product) has no lock because running it twice is harmless.

Where: `config/ShedLockConfig` and the `@SchedulerLock` annotations; the `shedlock` tables are
in each `V1__initial_schema.sql`.

## 6. Paying without charging twice

`PaymentService.pay()` is three short steps instead of one long transaction:

1. **Claim:** `PENDING` → `PROCESSING`, saved and flushed at once. The `@Version` column
   (optimistic locking) lets only one request win: a double click or a second tab gets
   **409** and is never charged.
2. **Charge** the payment gateway, with no database transaction open (a slow gateway does not
   hold database locks).
3. **Complete:** `SUCCESS`/`FAILED` plus the `PaymentEvent` in the outbox, in one transaction.
   If the order was cancelled while the gateway was charging (`orderCancelled` flag), the
   payment becomes `REFUNDED` instead.

If the service dies between 2 and 3, `PaymentRecoveryJob` asks the gateway what happened and
finishes step 3. Where: `service/PaymentService`, `scheduler/PaymentRecoveryJob`,
`scheduler/PaymentExpiryJob`.

## 7. Stock holds

Each product document has a list `holds: [{orderId, quantity, paid, createdAt}]`.

- **Reserve:** one atomic update per item: "if `quantity >= n` and no hold for this order:
  subtract `n` and push a hold". Two orders for the last item cannot both win, and a
  repeated message cannot take the stock twice. If one item is short, the holds already
  taken for this order are given back and the result is `REJECTED`.
- **Paid:** the hold is marked `paid` (kept 7 days so a refund can still give the stock back,
  then `HoldCleanupJob` removes it).
- **Release** (failed, expired, cancelled): "if the hold is still there: remove it and add the
  quantity back", so a second release does nothing.

Where: `service/InventoryService` (Product), tested on a real MongoDB in
`InventoryServiceIntegrationTest`.

## 8. Security

**Login and tokens**

- `POST /users/login` checks the BCrypt password hash and returns a **JWT access token**
  (HS256, 30 minutes, claims: `sub` = email, `userId`, `roles`) and sets the **refresh token**
  cookie (next section).
- 5 wrong passwords in a row lock the account for 15 minutes (**423**).
- The **gateway** checks every JWT (signature and expiry) before routing; open paths are only
  login, register, refresh, logout, `GET /products/**` and health. Bad or missing token:
  **401** JSON with `WWW-Authenticate: Bearer`.
- Each **service checks the JWT again** (`security/JwtAuthFilter`), because the gateway must
  not be the only lock. The user id always comes from the token, never from the URL or a header.
- **Ownership:** someone else's order or payment gives **404**, not 403, so its existence is
  not revealed. Admin-only actions use `@PreAuthorize("hasRole('ADMIN')")` (**403**).

**Rate limiting** (gateway, Redis): per client IP, login/register/refresh 5 per minute,
everything else 20 per second (bursts of 40). Too many: **429** JSON.
Behind nginx, the client IP comes from `X-Forwarded-For`, which nginx overwrites, so it
cannot be faked.

**Browser side**

- **CORS** (development): the gateway allows exactly `http://localhost:5501` with credentials.
  In production pages and API share one origin, so the browser needs no CORS.
- **XSS:** API data is always written with `textContent` (the `el()` helper), never
  `innerHTML`; every page has a strict Content-Security-Policy (scripts only from the site
  and the pinned Bootstrap CDN with SRI hashes; no inline scripts).
- **Secrets** live only in `.env` (Docker) and the private config repo, never in git.

Where: Api_Gateway `security/SecurityConfig` and `ratelimit/`, each service's `security/`,
User `service/UserServiceImpl` (lockout), `frontend/js/ui.js` (`el()`).

## 9. Refresh tokens

The access token lives only 30 minutes. To stay logged in without storing a long-lived token
where JavaScript can read it:

- The **refresh token** is a random 32-byte value in an **HttpOnly, Secure, SameSite=Strict**
  cookie limited to `Path=/users`. JavaScript cannot read it (XSS cannot steal it), and the
  browser only sends it to `/users/...` on the same site.
- The database stores only its **SHA-256 hash** (`refresh_tokens`), so a database leak does not
  leak working tokens.
- **Rotation:** every `POST /users/refresh` uses the token up (atomically, so two refreshes
  with the same token cannot both win) and issues a new one in the same **family**.
- **Reuse detection:** if a token that was already used shows up again, someone has a copy:
  the whole family is revoked and the user must log in again (the thief's copy dies too).
- A token lasts 7 days unused; a login session lasts at most 30 days however often it is
  refreshed. Logout revokes the family and clears the cookie.
- In the browser, `js/api.js` refreshes once on a 401 and retries the request; refreshes are
  shared within a tab and serialized across tabs (`navigator.locks`).

Where: User `service/RefreshTokenService`, `controller/UserController`; tests
`RefreshTokenServiceTest` and `RefreshTokenIntegrationTest`.

## 10. When a service is down

- **Order → Product (HTTP):** `ResilientProductClient` wraps the Feign call in a
  **retry** (3 attempts, 300 ms apart, only for 5xx, no connection or timeout; never for 404)
  inside a **circuit breaker** (if half of the last calls failed, stop calling Product for
  30 s and answer **503** at once). Unknown product: **400**.
- **Kafka down:** the outboxes keep the events in the databases and send them when Kafka is
  back. Nothing is lost; orders just wait.
- **SMTP down:** notifications are saved anyway; `EmailSenderJob` retries each email after 1,
  2, 4, 8, 16 minutes.

## 11. Configuration, discovery, database schema

- **Config Server** serves one `<app-name>.properties` per service from a private GitLab repo
  (HTTP Basic login). Each service's local `application.properties` only knows its name and
  where Config Server is. Environment variables (Docker) override Config Server values.
- **Eureka:** services register by name; the gateway routes to `lb://order-service` and
  Feign calls `PRODUCT-SERVICE` without knowing any address.
- **Flyway** owns the MySQL/PostgreSQL schema: `db/migration/V1__initial_schema.sql`, later
  `V2__...`. Hibernate only checks it (`ddl-auto=validate`), so the schema never changes by
  surprise. MongoDB collections and indexes are created by Spring Data at startup.

## 12. Tests and CI

- **Unit tests** (JUnit + Mockito, no Spring, milliseconds): the decision logic in
  `OrderServiceTest`, `PaymentServiceTest`, `PaymentRecoveryJobTest`,
  `RefreshTokenServiceTest`, `ResilientProductClientTest`.
- **Integration tests** (`*IntegrationTest`): a real database in a throwaway Docker container
  (Testcontainers), with only the database part of Spring started (`@DataJpaTest`,
  `@DataMongoTest`). They prove that Flyway's V1 builds a working schema on an empty
  database, and test the things a mock cannot: atomic stock holds, unique constraints,
  refresh-token reuse detection.
- **CI:** `.github/workflows/ci.yml` runs `./mvnw -B verify` for all eight services on every
  push.

## 13. Where each concept lives

| Concept | Files |
|---|---|
| Order placement, cancel, state changes | `Order_Service/.../service/OrderService.java` |
| Calls to Product (retry, circuit breaker) | `Order_Service/.../client/ResilientProductClient.java`, `config/ResilienceConfig.java` |
| Outbox | `*/kafka/*OutboxWriter.java`, `*/kafka/OutboxPublisher.java`, `*/kafka/OutboxCleanupJob.java` |
| Kafka consumers | `*/kafka/*Consumer.java`; error handling `*/config/KafkaErrorConfig.java` |
| Stock holds | `Product_Service/.../service/InventoryService.java`, `HoldCleanupJob.java` |
| Payment claim, recovery, expiry | `Payment_Service/.../service/PaymentService.java`, `scheduler/` |
| Order safety nets | `Order_Service/.../scheduler/PaymentTimeoutScheduler.java` |
| Emails | `Notification_Service/.../service/EmailSenderJob.java`, `EmailContent.java` |
| ShedLock | `*/config/ShedLockConfig.java`, `@SchedulerLock` on each job |
| JWT, login, lockout | `User_Service/.../service/UserServiceImpl.java`, `security/JwtUtil.java` |
| Refresh tokens | `User_Service/.../service/RefreshTokenService.java`, `controller/UserController.java` |
| Gateway security, rate limit | `Api_Gateway/.../security/SecurityConfig.java`, `ratelimit/` |
| Service security | `*/security/` (same classes in Product, Order, Payment, Notification) |
| Errors | `*/exception/GlobalExceptionHandler.java`, `ErrorResponse.java` |
| Paging | `*/dto/Paging.java` |
| Database schema | `*/src/main/resources/db/migration/` |
| Swagger | `*/config/OpenApiConfig.java`, `Api_Gateway/.../docs/ApiDocsRoutes.java` |
| Frontend | `frontend/js/api.js` (fetch + refresh), `ui.js` (`el()`), `pages/*.js` |
| Production | `compose.prod.yaml`, `nginx/`, `docs/DEPLOY.md` |

---

## Interview questions

**1. Why microservices here, and what do they cost?**
Each service owns its data and can be deployed and scaled alone, and a slow Notification
cannot slow down payments. The cost: no joins or transactions across services, so we need
events, sagas, idempotency and eventual consistency; plus more infrastructure (Kafka, Eureka,
Config Server) and harder debugging.

**2. What is a saga? Choreography or orchestration?**
A business process split into local transactions, each publishing an event that triggers the
next; failures are undone by compensating events. This project uses **choreography**: no
central coordinator, each service reacts to events (Order, Product, Payment).

**3. What is the outbox pattern and why not just send to Kafka after saving?**
Saving and sending are two systems; a crash between them loses the event (or announces
something that was never saved). The outbox writes the event into the same database
transaction, and a publisher sends it afterwards. Delivery becomes at-least-once.

**4. At-least-once means duplicates. How do you handle them?**
Every consumer is idempotent: unique constraints (one payment per order, one notification per
order and type), conditional atomic updates (a hold only if none exists for this order), and
state checks (only a `PENDING` order can become `CONFIRMED`).

**5. Two users buy the last item at the same moment. What happens?**
Product's hold is one atomic MongoDB update with the condition `quantity >= n`. Only one
update matches; the other order gets `REJECTED` → `CANCELLED (OUT_OF_STOCK)`.

**6. A user double-clicks "Pay". Are they charged twice?**
No. The claim `PENDING → PROCESSING` uses optimistic locking (`@Version`); the second request
fails the version check and gets 409 before the gateway is called.

**7. What if Payment crashes after charging but before saving the result?**
The payment stays `PROCESSING`. `PaymentRecoveryJob` finds it after 2 minutes, asks the
gateway for the result and completes it.

**8. Why ShedLock?**
`@Scheduled` runs in every instance. ShedLock uses a lock row in the database so that each
job runs on one instance at a time.

**9. How does authentication work across services?**
User Service issues a signed JWT. The gateway verifies signature and expiry before routing,
and every service verifies it again (defence in depth). The user id comes only from the token.

**10. Why refresh tokens in an HttpOnly cookie instead of localStorage?**
JavaScript cannot read an HttpOnly cookie, so an XSS bug cannot steal it. SameSite=Strict and
`Path=/users` limit where the browser sends it. The short-lived access token lives in
sessionStorage.

**11. What is refresh-token rotation and reuse detection?**
Each refresh token works once and is replaced by a new one. If an old one is presented again,
someone copied it, so the whole session (token family) is revoked.

**12. Why store only a hash of the refresh token?**
Like passwords: a database leak must not give working tokens. SHA-256 is enough because the
token is long and random (no dictionary to guess from).

**13. How do you stop brute-force logins?**
Two layers: the gateway rate-limits login to 5 per minute per IP (Redis), and the account is
locked for 15 minutes after 5 wrong passwords (423).

**14. Why does someone else's order return 404 and not 403?**
403 would confirm that order id exists. 404 reveals nothing.

**15. What is a circuit breaker and why combine it with retry?**
Retry hides short hiccups. A circuit breaker stops calling a service that is clearly down
(fail fast with 503 instead of waiting for timeouts), then tests it again after 30 s. Retry
is inside the breaker's view, so every real attempt counts, and an open breaker is not retried.

**16. What is a dead-letter topic?**
Where a message goes when it cannot be processed (unreadable, or still failing after the
retries), so it does not block the rest of the topic. It is logged with partition and offset
and can be replayed after a fix.

**17. Why Flyway instead of `ddl-auto=update`?**
`update` changes production tables silently, never drops or renames, and leaves no history.
Flyway applies reviewed, versioned SQL files in order and records them; Hibernate only
validates. The existing database was "baselined" at V1 without touching data.

**18. Unit test or integration test, what goes where?**
Unit tests (Mockito) for decision logic: fast and precise. Integration tests (Testcontainers)
for what depends on the real database: atomic updates, constraints, the schema itself.

**19. How do you deploy it?**
One server with Docker Compose (`compose.prod.yaml`): nginx is the only public container,
serves the frontend and proxies the API on one origin, with Let's Encrypt HTTPS. Works on x86
and ARM64. See `docs/DEPLOY.md`.

**20. What would you improve next?**
Monitoring and alerting (Prometheus/Grafana), a real payment provider with webhooks, more
than one instance of each service and a managed database, Kafka with replication, and an
admin view for dead-letter messages.

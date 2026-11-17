# Microshop: an e-commerce backend built as microservices

[![CI](https://github.com/Subash2401/spring-boot-ecommerce-microservices/actions/workflows/ci.yml/badge.svg)](https://github.com/Subash2401/spring-boot-ecommerce-microservices/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A small online shop built as **eight Spring Boot services** with a plain-JavaScript frontend:
browse products, add to a cart, place an order, pay within 15 minutes, and get a notification
and an email for every step. Behind that simple flow are the parts real systems need:
an event-driven **saga** over Kafka, the **outbox pattern**, **idempotent consumers**,
**stock holds** that cannot oversell, **refresh-token rotation**, **rate limiting**, a
**circuit breaker**, **Flyway** migrations, **Testcontainers** tests, **CI**, and a
production setup that runs on **AWS (x86) or Oracle Cloud (ARM64)**.

> Screenshots: to be added
> `docs/screenshots/products.png`, `cart.png`, `pay.png`, `orders.png`,
> `notifications.png`, `admin-products.png`, `swagger.png`

## Architecture

```
                 Browser (frontend/: HTML, CSS, vanilla JS, Bootstrap 5)
                                    │
                 nginx (production: frontend + API on one origin, HTTPS)
                                    │
              API Gateway :8080  (JWT check, per-IP rate limit in Redis, routing)
        ┌──────────┬────────────┬───────────┬─────────────┬──────────────┐
      User      Product       Order       Payment      Notification
      MySQL     MongoDB       MySQL      PostgreSQL      MongoDB + SMTP
        └──────────┴──── Kafka (order / inventory / payment / user events) ┘

      Config Server (settings from a private Git repo)   Eureka (service discovery)
```

| Service | Port | Database | Does |
|---|---|---|---|
| `Config_Server` | 8888 | | Serves every service's settings from a Git repo |
| `Eureka_Server` | 8761 | | Service registry: services find each other by name |
| `Api_Gateway` | 8080 | Redis | Single entry point: JWT check, rate limits, routing, Swagger UI |
| `User_Service` | 8082 | MySQL | Register, login (JWT), refresh tokens, account lockout, first admin |
| `Product_Service` | 8081 | MongoDB | Catalogue (admin CRUD), stock holds for orders |
| `Order_Service` | 8083 | MySQL | Orders, saga state machine, timeouts |
| `Payment_Service` | 8084 | PostgreSQL | Payments: claim, charge (mock gateway), expiry, recovery, refunds |
| `Notification_Service` | 8085 | MongoDB | One notification per order step, HTML emails |

**The order saga** (choreography over Kafka): Order saves `PENDING` → Product holds the stock →
`RESERVED` → Payment opens a 15-minute payment → the user pays → `SUCCESS` → Order
`CONFIRMED`, Product marks the stock sold, Notification emails the user. Every failure path
(out of stock, payment declined, payment window expired, cancel, service crash) ends in a
consistent state, with the stock given back. Step by step in
[docs/HOW-IT-WORKS.md](docs/HOW-IT-WORKS.md).

## Tech stack

| Area | Used |
|---|---|
| Language, framework | Java 21, Spring Boot 4.1, Spring Cloud 2025.1 (Gateway, Config, Eureka, OpenFeign) |
| Data | MySQL 8.4, PostgreSQL 17, MongoDB 8, Redis 7, Flyway, Hibernate 7 |
| Messaging | Apache Kafka 4.3 (KRaft), Spring Kafka, dead-letter topics |
| Security | Spring Security, JWT (jjwt), OAuth2 resource server in the gateway, BCrypt, HttpOnly refresh-token cookie |
| Reliability | Outbox pattern, ShedLock, Resilience4j (retry + circuit breaker), idempotent consumers |
| Frontend | HTML, CSS, vanilla JavaScript (ES modules), Bootstrap 5.3 (pinned with SRI), strict CSP |
| API docs | springdoc-openapi 3 (Swagger UI in the gateway, with JWT "Authorize") |
| Tests | JUnit 6, Mockito, AssertJ, Testcontainers 2 (44 tests) |
| Delivery | Docker (multi-stage builds, Maven cache), Docker Compose, GitHub Actions, nginx, Let's Encrypt |

`BOOT3-VS-BOOT4.md` lists every place where Spring Boot 4 differs from Boot 3 in this project.

## Run it locally

**You need:** Docker Desktop (4 GB of memory for Docker is enough, but start-up is slow),
JDK 21, and VS Code with the Live Server extension (for the frontend).

1. Get the code and create your `.env`:

   ```bash
   git clone https://github.com/Subash2401/spring-boot-ecommerce-microservices.git
   cd spring-boot-ecommerce-microservices
   cp .env.example .env
   ```

   Fill in every value: your own random database passwords and `JWT_SECRET`, Config Server
   and Eureka logins (Eureka password: letters and digits only), Mailpit as the mail server
   (`MAIL_HOST=mailpit`, `MAIL_PORT=1025`, no username or password). For the services'
   settings, point Config Server at the example files in this repository
   ([config-repo-example/](config-repo-example/README.md)):

   ```properties
   CONFIG_GIT_URI=https://github.com/Subash2401/spring-boot-ecommerce-microservices.git
   CONFIG_GIT_SEARCH_PATHS=config-repo-example
   ```

2. Start everything:

   ```bash
   docker compose up -d --build
   docker compose ps          # wait until every container is "healthy" (a few minutes)
   ```

3. Open the `frontend/` folder in VS Code and click **Go Live**, then use
   **http://localhost:5501** (exactly this address: the gateway allows only this origin).
4. Useful pages:
   - Swagger UI (all five APIs): http://localhost:8080/swagger-ui.html
   - Mailpit (the emails the shop sends): http://localhost:8025
   - Eureka dashboard: http://localhost:8761

Run the tests of one service (Docker must be running, for Testcontainers):

```bash
cd Order_Service
./mvnw verify
```

## Deploy

`compose.prod.yaml` runs the same stack on one server: only nginx is public (80/443), HTTPS
from Let's Encrypt, real SMTP, Swagger off. Step-by-step guide for AWS and Oracle Cloud,
with a server checklist, backups and the minimum RAM: [docs/DEPLOY.md](docs/DEPLOY.md).

## Project layout

```
Config_Server/  Eureka_Server/  Api_Gateway/               infrastructure services
User_Service/  Product_Service/  Order_Service/
Payment_Service/  Notification_Service/                    business services
frontend/                                                  the web shop (no build step)
nginx/  compose.prod.yaml                                  production
compose.yaml  mysql-init/  postgres-init/  mongo-init/     local Docker stack
config-repo-example/                                       the services' settings (served by Config Server)
docs/                                                      how it works, deployment, dead-letter topics
.github/workflows/ci.yml                                   CI: build and test every service
```

## Trade-offs (on purpose)

- **Mock payment gateway.** It declines 20% of payments at random, to exercise the failure
  paths. A real provider (with webhooks) would replace `MockPaymentGateway`.
- **Choreography, not orchestration.** Each service reacts to events; simple to add to, but
  the whole flow is only visible in the docs, not in one class.
- **Eventual consistency.** Kafka topics are not ordered against each other, so a user can
  briefly see both "placed" and "cancelled" when a cancel races a payment; the final state
  is always consistent.
- **One server, one Kafka broker** in production: simple and cheap, but a single point of
  failure. No monitoring or alerting yet; the ERROR log lines are the signal.
- **Plain JavaScript frontend** without a framework or build step: easy to read and to
  serve, more code for state and rendering than a framework would need.
- **Unit tests for logic, a few integration tests for the database**, no full end-to-end
  test suite; the end-to-end flows are checked by hand on the running stack.

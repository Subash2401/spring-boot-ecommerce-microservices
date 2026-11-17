# config-repo-example: the services' settings

Config Server serves these files to the services: one `<app-name>.properties` per service,
plus `application.properties`, which every service gets. They are a cleaned copy of the
project's private config repo: every password and secret is a `${...}` placeholder,
filled in from the environment (`.env` in Docker).

## Use them

Config Server reads them from a Git repository. Without one of your own, point it at this
folder in the public repo. In `.env`:

```properties
CONFIG_GIT_URI=https://github.com/Subash2401/spring-boot-ecommerce-microservices.git
CONFIG_GIT_SEARCH_PATHS=config-repo-example
CONFIG_GIT_USERNAME=
GITLAB_TOKEN=
```

(No login is needed for a public repo.) To change settings, copy this folder into a Git
repository of your own and set `CONFIG_GIT_URI` to it (and `CONFIG_GIT_SEARCH_PATHS` to
the folder, or leave it empty if the files are at the root).

## Docker vs. running in an IDE

The `localhost` addresses here are for services started from an IDE. In Docker,
`compose.yaml` overrides the database, Kafka, Mongo, Eureka and mail settings with
environment variables (environment variables win over Config Server), so the same files
work in both.

| File | Placeholders it needs |
|---|---|
| `application.properties` | `EUREKA_USERNAME`, `EUREKA_PASSWORD`, `JWT_SECRET` |
| `user-service.properties` | `USER_DB_PASSWORD` |
| `order-service.properties` | `ORDER_DB_PASSWORD` |
| `payment-service.properties` | `PAYMENT_DB_PASSWORD` |
| `product-service.properties` | `PRODUCT_DB_PASSWORD` |
| `notification-service.properties` | `NOTIFICATION_DB_PASSWORD`, `MAIL_FROM` |
| `api-gateway.properties` | none (Redis login comes from the environment) |

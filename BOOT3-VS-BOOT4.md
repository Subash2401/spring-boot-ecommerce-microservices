# Boot 3 vs Boot 4 — what changed in this project

Project versions: Spring Boot 4.1.1, Spring Cloud 2025.1.3, Java 21.

This file has three parts:

1. Changes that exist **because of Boot 4** (old Boot 3 syntax next to the new one).
2. A **library upgrade** that is not tied to Boot (jjwt).
3. Clean-ups that are **the same on Boot 3 and Boot 4**, listed so they are not mistaken for Boot 4 changes.

## What Boot 4 brings with it

| Library | Boot 3.x | Boot 4.1.1 in this project |
|---|---|---|
| Spring Framework | 6 | 7.0.9 |
| Spring Security | 6 | 7.1.1 |
| Hibernate | 6 | 7.4.5.Final |
| Spring Data JPA | 3 | 4.1.1 |
| Jackson | 2 (`com.fasterxml.jackson`) | 3.1.5 (`tools.jackson`) |
| Spring Kafka | 3 | 4.1.1 |
| Kafka client | 3 | 4.2.1 |
| Tomcat | 10 | 11.0.24 |
| Spring Cloud release | 2025.0 (for Boot 3.5) | 2025.1.3 (its modules are 5.0.x) |
| Spring Cloud Gateway | 4 | 5.0.3 |

The Boot 4.1.1 column is read from `mvn dependency:tree` on Order-Service. The Boot 3.x column gives major versions only and is not taken from a build of this project.

Jackson 2 (2.21.5) is still on the classpath next to Jackson 3. Our code does not use it; it arrives through `spring-cloud-starter-netflix-eureka-client`, which depends on the Netflix Eureka client and on Boot's `spring-boot-jackson2` compatibility module.

---

## Part 1 — Boot 3 → Boot 4 changes

### 1. Kafka dependency

Boot 3:
```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

Boot 4:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-kafka</artifactId>
</dependency>
```

**Why:** Boot 4 moved each technology's auto-configuration into its own module, so the bare `spring-kafka` jar no longer gets auto-configured; the starter brings both.

Where: Order, Payment, Notification.

### 2. Web starter

Boot 3:
```xml
<artifactId>spring-boot-starter-web</artifactId>
```

Boot 4:
```xml
<artifactId>spring-boot-starter-webmvc</artifactId>
```

**Why:** the starter was renamed to say which web stack it is (Spring MVC); the old name still works but is deprecated.

Where: Order, Payment, Notification (User and Product already used it).

### 3. Jackson: `ObjectMapper` → `JsonMapper`

Boot 3:
```java
import com.fasterxml.jackson.databind.ObjectMapper;

private final ObjectMapper objectMapper;
```

Boot 4:
```java
import tools.jackson.databind.json.JsonMapper;

private final JsonMapper jsonMapper;
```

**Why:** Boot 4 uses Jackson 3 by default, and Jackson 3 lives in the `tools.jackson` package; the bean Boot creates is a `JsonMapper`.

Where: User (`CustomAuthenticationEntryPoint`, `CustomAccessDeniedHandler`), Payment and Notification Kafka classes, Order `PaymentEventConsumer`.

Note: annotations such as `@JsonProperty` stay in `com.fasterxml.jackson.annotation` in both versions.

### 4. Jackson: exceptions are unchecked

Boot 3 (Jackson 2):
```java
try {
    event = objectMapper.readValue(message, OrderEvent.class);
} catch (JsonProcessingException e) {   // checked: must catch or declare
    ...
}
```

Boot 4 (Jackson 3):
```java
try {
    event = jsonMapper.readValue(message, OrderEvent.class);
} catch (JacksonException e) {          // unchecked: catching is a choice
    ...
}
```

**Why:** Jackson 3 made its exceptions extend `RuntimeException`, so `readValue` and `writeValueAsString` no longer force a `try/catch`.

Where: the Kafka consumers in Order and Notification.

### 5. Kafka JSON serializer

Boot 3 (Spring Kafka 3):
```properties
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
```

Boot 4 (Spring Kafka 4):
```properties
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JacksonJsonSerializer
```

**Why:** `JsonSerializer` is built on Jackson 2 and is deprecated in Spring Kafka 4; `JacksonJsonSerializer` is the Jackson 3 replacement.

Where: `order-service.properties` in the config repo.

### 6. MongoDB connection property

Boot 3:
```properties
spring.data.mongodb.uri=mongodb://localhost:27017/product_db
```

Boot 4:
```properties
spring.mongodb.uri=mongodb://localhost:27017/product_db
```

**Why:** connection settings belong to the MongoDB driver, not to Spring Data, so Boot 4 moved them out of the `spring.data` prefix. Spring Data settings such as `spring.data.mongodb.auto-index-creation` keep their old prefix.

Where: `product-service.properties` and `notification-service.properties` in the config repo.

### 7. Security auto-configuration package

Boot 3:
```java
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
```

Boot 4:
```java
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
```

**Why:** the same module split as in point 1: security auto-configuration now lives in its own `spring-boot-security` module with its own package.

Where: `OrderServiceApplication` (`@SpringBootApplication(exclude = ...)`).

### 8. Hibernate dialect property

Boot 3 (often seen, already deprecated):
```properties
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect
```

Boot 4:
```properties
# no dialect line at all
```

**Why:** Hibernate detects the dialect from the database connection, and `MySQL8Dialect` no longer exists in Hibernate 7.

Where: the MySQL services (User, Order, Payment) in the config repo.

### 9. Gateway starter and property prefix

This change comes from the Spring Cloud release that goes with Boot 4, not from Boot itself.

Boot 3:
```xml
<artifactId>spring-cloud-starter-gateway</artifactId>
```
```properties
spring.cloud.gateway.routes[0].id=product-service
```

Boot 4:
```xml
<artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>
```
```properties
spring.cloud.gateway.server.webflux.routes[0].id=product-service
```

The same prefix change applies to `default-filters`, `globalcors` and `discovery.locator.enabled`.

**Why:** Gateway now comes in two server flavours (`server-webflux` on Netty, `server-webmvc` on Tomcat), and both the starter and the properties name the one in use. Spring Cloud 2025.0 introduced the new names and deprecated the old ones; 2025.1 removed them. The 2025.1.3 BOM no longer manages `spring-cloud-starter-gateway`, and properties under the old prefix are ignored without an error.

Where: Api_Gateway (`pom.xml`, and `api-gateway.properties` in the config repo).

### 10. OAuth2 resource server starter

Boot 3:
```xml
<artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
```

Boot 4:
```xml
<artifactId>spring-boot-starter-security-oauth2-resource-server</artifactId>
```

**Why:** Boot 4 renamed the OAuth2 starters so their names say they belong to Spring Security (`spring-boot-starter-security-oauth2-client` and `-authorization-server` follow the same rule). The old name still exists in Boot 4.1.1 but its pom describes it as deprecated in favor of the new one.

Where: Api_Gateway `pom.xml`.

---

## Part 2 — Library upgrade, not tied to Boot

### jjwt 0.11.5 → 0.12.6

| 0.11 (deprecated in 0.12) | 0.12 |
|---|---|
| `.setSubject(x)` | `.subject(x)` |
| `.setIssuedAt(x)` | `.issuedAt(x)` |
| `.setExpiration(x)` | `.expiration(x)` |
| `.signWith(key, SignatureAlgorithm.HS256)` | `.signWith(key, Jwts.SIG.HS256)` |
| `Jwts.parserBuilder()` | `Jwts.parser()` |
| `.setSigningKey(key)` | `.verifyWith(key)` |
| `.parseClaimsJws(token)` | `.parseSignedClaims(token)` |
| `.getBody()` | `.getPayload()` |

**Why:** jjwt 0.12 renamed its builder and parser methods and deprecated the old names; the token format is unchanged, so old tokens stay valid.

Where: `JwtUtil` in User, Product, Order, Payment and Notification. Api_Gateway validates tokens with Spring Security's Nimbus decoder instead and has no jjwt.

---

## Part 3 — Same on Boot 3 and Boot 4

These were cleaned up during the audit. None of them is a Boot 4 change.

### Configuration

| Before | After | Why |
|---|---|---|
| `bootstrap.properties` + `spring-cloud-starter-bootstrap` | `spring.config.import=configserver:${CONFIG_SERVER_URL:http://localhost:8888}` in `application.properties` | The import line has been the standard way to reach Config Server since Boot 2.4; bootstrap is the legacy route. |
| Hand-written `KafkaConfig` class with `ProducerFactory`, `KafkaTemplate`, `ConsumerFactory` beans | `spring.kafka.*` properties | Boot builds the same beans from properties, so the class was duplicate code. |
| `@EnableKafka` on the application class | removed | The Kafka starter already switches on `@KafkaListener` support. |
| GitLab token written in `application.properties` | `${GITLAB_TOKEN}` | Secrets come from the environment, never from a file in git. |
| `spring.application.name=Config_Server` | `config-server` | Kebab-case like every other service; underscores are not valid in host names. |

### Code

| Before | After | Why |
|---|---|---|
| `@Autowired` on a field | `private final` field + `@RequiredArgsConstructor` | Constructor injection makes the dependency mandatory and the class easy to test. |
| `@Data` on JPA entities that reference each other | `@Getter` + `@Setter` | `@Data` generates `toString`/`hashCode` that loop between `Order` and `OrderItem` until a stack overflow. |
| `@RequiredArgsConstructor` on an entity | `@NoArgsConstructor` | JPA needs a no-argument constructor; say so directly. |
| `log.info("OrderId: {}" + id)` | `log.info("OrderId: {}", id)` | With `+` the placeholder is never filled. |
| `secret.getBytes()` | `secret.getBytes(StandardCharsets.UTF_8)` | States the charset instead of relying on the JVM default. |
| Own `authenticationProvider()` bean | removed | Spring Security builds it from the `UserDetailsService` and `PasswordEncoder` beans. |
| Test class in `com.subash`, app in `com.subash.eurekaserver` | test moved into `com.subash.eurekaserver` | `@SpringBootTest` only searches the test's package and upward for the application class. |

### Kafka

| Before | After | Why |
|---|---|---|
| `kafkaTemplate.send(...)` then `log.info("published")` | `kafkaTemplate.send(...).whenComplete((result, ex) -> ...)` | `send` returns a `CompletableFuture`; only the callback knows whether the broker accepted the message. |
| Event sent inside the transaction | `ApplicationEventPublisher` + `@TransactionalEventListener(phase = AFTER_COMMIT)` | Consumers must never see an order that is not saved yet. |
| Payment event keyed by `userId` | keyed by `orderId` | Same key as order events, so everything about one order stays in order. |
| No duplicate protection in Notification | `existsByOrderIdAndType` check + unique `@CompoundIndex` | Kafka can deliver a message twice; needs `spring.data.mongodb.auto-index-creation=true`. |
| No concurrency protection on `Order` | `@Version` field | Stops a cancel and a payment result from overwriting each other. |

### Error handling and security

| Before | After | Why |
|---|---|---|
| Payment `ErrorResponse` with `timestamp` | `status` + `message` in every service | One error shape for all services. |
| 500 handler without logging | `log.error("Unexpected error: ", ex)` | An unexpected failure must leave a stack trace. |
| Bad JSON, wrong URL, wrong method, wrong id type → 500 | 400 / 404 / 405 / 400 handlers | Those are caller mistakes, not server errors. `NoResourceFoundException` exists from Boot 3.2. |
| `/error` blocked by security | `/error` in `permitAll()` | The internal error forward arrives without authentication and would hide the real status. |
| Payment blocks `/actuator/health` | `/actuator/**` in `permitAll()` | Health checks must work without a token. |
| `markRead` ignores an unknown id and returns 200 | throws `NotificationNotFoundException` → 404 | The caller should know nothing was updated. |

### Developer tooling

| Before | After | Why |
|---|---|---|
| Manual restart after every change | `spring-boot-devtools` (`runtime`, `optional`) | Restarts the app on save; inactive in a packaged jar. |

---

## Part 4 — Added after the Boot 4 migration

### springdoc-openapi (Swagger)

Boot 3:
```xml
<groupId>org.springdoc</groupId>
<artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
<version>2.8.x</version>
```

Boot 4:
```xml
<groupId>org.springdoc</groupId>
<artifactId>springdoc-openapi-starter-webmvc-api</artifactId>   <!-- services: spec only -->
<artifactId>springdoc-openapi-starter-webflux-ui</artifactId>   <!-- gateway: the UI -->
<version>3.2.0</version>
```

**Why:** springdoc 2.x is built for Spring Boot 3 (Spring Framework 6, Jackson 2); springdoc 3.x is the line for Boot 4 (Framework 7, Jackson 3). The artifact names and the `springdoc.*` properties are the same in both. The `-api` / `-ui` split and the UI in the gateway are a design choice of this project, not a Boot 4 change.

Where: the five business services and Api_Gateway.

### Flyway

Boot 3:
```xml
<artifactId>flyway-core</artifactId>        <!-- org.flywaydb -->
<artifactId>flyway-mysql</artifactId>       <!-- org.flywaydb -->
```

Boot 4:
```xml
<artifactId>spring-boot-starter-flyway</artifactId>   <!-- org.springframework.boot -->
<artifactId>flyway-mysql</artifactId>                 <!-- org.flywaydb; flyway-database-postgresql for Payment -->
```

**Why:** the same module split as in Part 1, point 1: Boot 4 moved Flyway's auto-configuration into its own `spring-boot-flyway` module, so `flyway-core` alone is no longer picked up; the starter brings both. The `spring.flyway.*` properties did not change.

Where: User, Order (MySQL) and Payment (PostgreSQL).

### Test slices and Testcontainers

Boot 3 (Testcontainers 1.x):
```java
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.testcontainers.containers.MySQLContainer;      // artifact org.testcontainers:mysql
static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");
```

Boot 4 (Testcontainers 2.x):
```java
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;   // spring-boot-starter-data-jpa-test
import org.testcontainers.mysql.MySQLContainer;           // artifact org.testcontainers:testcontainers-mysql
static MySQLContainer mysql = new MySQLContainer("mysql:8.4");
```

**Why:** Boot 4 split the test auto-configuration per technology, like the main auto-configuration: `@DataJpaTest` comes from `spring-boot-starter-data-jpa-test`, `@DataMongoTest` from `spring-boot-starter-data-mongodb-test`, each in a new package. Boot 4.1 manages Testcontainers 2.0, which prefixed its artifacts with `testcontainers-`, moved each container class into its own package and dropped the self-type generic. `@ServiceConnection` works the same in both.

Where: the tests of Order, Payment, User (JPA) and Product (MongoDB).

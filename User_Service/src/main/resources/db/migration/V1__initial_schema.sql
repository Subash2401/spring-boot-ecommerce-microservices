-- V1: the userdb schema exactly as it was before Flyway (until then Hibernate's ddl-auto
-- created it, which is why some constraint names look generated).
--
-- On the existing database this file is NOT run: spring.flyway.baseline-on-migrate=true
-- marks the database as "already at version 1" and leaves the data alone.
-- On a new, empty database Flyway runs it and creates everything.
-- Never edit this file after it has been applied: add V2__..., V3__... for changes.

CREATE TABLE users (
  id bigint NOT NULL AUTO_INCREMENT,
  email varchar(255) NOT NULL,
  name varchar(255) NOT NULL,
  password varchar(255) NOT NULL,
  failed_login_attempts int NOT NULL,
  locked_until datetime(6) DEFAULT NULL,
  event_published tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (id),
  UNIQUE KEY UK6dotkott2kjsp8vw4d0m25fb7 (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- One row per role of a user (ADMIN, USER)
CREATE TABLE user_roles (
  user_id bigint NOT NULL,
  role varchar(30) DEFAULT NULL,
  UNIQUE KEY UKkpjrhqbpn9eqscssk9ttp7glu (user_id, role),
  CONSTRAINT FKhfh9dx7w3ubf1co1vdev94g3f FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Refresh tokens: only the SHA-256 hash is stored; family_id groups one login session
CREATE TABLE refresh_tokens (
  id bigint NOT NULL AUTO_INCREMENT,
  created_at datetime(6) NOT NULL,
  expires_at datetime(6) NOT NULL,
  family_expires_at datetime(6) NOT NULL,
  family_id varchar(36) NOT NULL,
  revoked bit(1) NOT NULL,
  token_hash varchar(64) NOT NULL,
  user_id bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY UKo2mlirhldriil2y7krapq4frt (token_hash),
  KEY idx_refresh_family (family_id),
  KEY idx_refresh_family_expires (family_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Outbox: events written in the same transaction as the change, sent to Kafka by OutboxPublisher
CREATE TABLE outbox_events (
  id bigint NOT NULL AUTO_INCREMENT,
  created_at datetime(6) NOT NULL,
  event_key varchar(100) NOT NULL,
  payload text NOT NULL,
  sent_at datetime(6) DEFAULT NULL,
  status varchar(20) NOT NULL,
  topic varchar(100) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_outbox_status_id (status, id),
  CONSTRAINT outbox_events_chk_1 CHECK ((status in ('PENDING', 'SENT')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ShedLock: makes each @Scheduled job run on one instance at a time
-- (was created by mysql-init/03-user-shedlock.sql before Flyway)
CREATE TABLE shedlock (
  name varchar(64) NOT NULL,
  lock_until timestamp(3) NOT NULL,
  locked_at timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  locked_by varchar(255) NOT NULL,
  PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

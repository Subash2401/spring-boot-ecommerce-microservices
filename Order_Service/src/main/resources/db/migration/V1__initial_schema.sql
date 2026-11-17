-- V1: the order_db schema exactly as it was before Flyway (until then Hibernate's ddl-auto
-- created it, which is why some constraint names look generated).
--
-- On the existing database this file is NOT run: spring.flyway.baseline-on-migrate=true
-- marks the database as "already at version 1" and leaves the data alone.
-- On a new, empty database Flyway runs it and creates everything.
-- Never edit this file after it has been applied: add V2__..., V3__... for changes.

CREATE TABLE orders (
  id bigint NOT NULL AUTO_INCREMENT,
  created_at datetime(6) DEFAULT NULL,
  status varchar(30) DEFAULT NULL,
  total_amount decimal(10,2) NOT NULL,
  user_id bigint DEFAULT NULL,
  version bigint NOT NULL,
  cancellation_reason varchar(30) DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_orders_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE order_items (
  id bigint NOT NULL AUTO_INCREMENT,
  price decimal(10,2) NOT NULL,
  product_id varchar(255) DEFAULT NULL,
  product_name varchar(255) DEFAULT NULL,
  quantity int NOT NULL,
  order_id bigint NOT NULL,
  PRIMARY KEY (id),
  KEY FKbioxgbv59vetrxe0ejfubep1w (order_id),
  CONSTRAINT FKbioxgbv59vetrxe0ejfubep1w FOREIGN KEY (order_id) REFERENCES orders (id)
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
-- (was created by mysql-init/02-order-shedlock.sql before Flyway)
CREATE TABLE shedlock (
  name varchar(64) NOT NULL,
  lock_until timestamp(3) NOT NULL,
  locked_at timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  locked_by varchar(255) NOT NULL,
  PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

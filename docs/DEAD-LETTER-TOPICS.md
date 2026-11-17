# Dead-letter topics: finding and replaying failed Kafka messages

Every topic has a `<topic>-dlt` ("dead-letter topic"). A message lands there when it still
fails after its retries, or when it cannot be read at all (bad JSON is never retried).
The consumer then logs one line:

```
ERROR ... Sending message to <topic>-dlt: partition=P offset=O key=K cause=...
```

Order's `payment-events` consumer uses `@RetryableTopic`: the message first goes through
retry topics with growing delays, then to `payment-events-dlt`. Nothing reads the DLTs
automatically; there is no admin UI or alerting, so the ERROR line is the signal.

In production add `-f compose.prod.yaml` after `docker compose` in every command below.

## 1. Find them

```bash
docker compose logs order-service | grep "Sending message to"
```

## 2. Look at one

Take the partition `P` and offset `O` from that log line (in Git Bash on Windows, put
`MSYS_NO_PATHCONV=1` in front of the command):

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:29092 \
  --topic payment-events-dlt --partition P --offset O --max-messages 1 \
  --formatter-property print.key=true --formatter-property print.headers=true
```

The headers include the exception that sent it there.

## 3. Replay it after fixing the cause

Send the same key and value back to the original topic. Every consumer ignores duplicates,
so a replay is safe. Unreadable JSON would only fail again: fix or drop those instead.

```bash
docker compose exec kafka sh -c '/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:29092 \
  --topic payment-events-dlt --partition P --offset O --max-messages 1 \
  --formatter-property print.key=true --formatter-property key.separator="|" \
  | /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka:29092 --topic payment-events \
  --reader-property parse.key=true --reader-property key.separator="|"'
```

# Deploying to one cloud server (AWS or Oracle Cloud)

This puts the whole shop on **one Linux server** with Docker Compose, using
`compose.prod.yaml`. The same steps work on **x86** (AWS EC2) and **ARM64** (Oracle Cloud
Ampere, AWS Graviton): every image exists for both, and our own images are built on the
server itself.

What runs where:

```
 Internet ──► :80 / :443  nginx  (the ONLY public container)
                            ├── /                      frontend files (HTML, CSS, JS)
                            └── /users /products /orders /payments /notifications
                                       └──► api-gateway:8080 ──► the five services
 Internal only (no public port): api-gateway, config-server, eureka, user/product/order/
 payment/notification services, MySQL, PostgreSQL, MongoDB, Kafka, Redis
```

Pages and API share one address (`https://your-domain`), so the browser needs no CORS,
the refresh-token cookie works, and the CSP allows only `'self'`.

---

## 0. What you need first

| Thing | Why |
|---|---|
| A **domain name** (for example `shop.example.com`) | HTTPS certificates are issued for names, not IP addresses |
| An **SMTP account** from a mail provider (host, port 587, user, password, a verified "from" address) | Order emails. Mailpit is for development only |
| The **GitLab token** that Config Server uses (read-only is enough) | The services get their settings from the config repo |
| An **SSH key pair** on your laptop (`ssh-keygen -t ed25519`) | Logging in to the server without passwords |

## 1. How big the server must be

Measured with the whole stack running: about **3.4 GB RAM** in use after start-up
(the containers' limits add up to about 5.7 GB). Java services use more while they start.

| | RAM | CPU | Disk |
|---|---|---|---|
| **Minimum** (works, slow start-up) | 4 GB + 4 GB swap | 2 vCPU | 30 GB |
| **Recommended** | 8 GB | 2–4 vCPU | 40 GB |

Examples:

- **AWS:** `t3.large` (x86, 2 vCPU, 8 GB) or `t4g.large` (ARM Graviton, 2 vCPU, 8 GB, cheaper).
- **Oracle Cloud Always Free:** shape `VM.Standard.A1.Flex` (ARM Ampere), for example
  2 OCPU and 12 GB.

Operating system: **Ubuntu Server 24.04 LTS** (x86 or ARM version to match the server).
MongoDB 8 needs ARMv8.2 or newer on ARM: Oracle Ampere A1 and AWS Graviton are fine.

## 2. Create the server

### AWS (EC2)

1. EC2 → **Launch instance**: Ubuntu Server 24.04 LTS, type `t3.large` or `t4g.large`,
   storage 40 GB gp3.
2. **Key pair:** import your public key (`~/.ssh/id_ed25519.pub`).
3. **Security group** (AWS's firewall), inbound rules:
   - SSH, port 22, source **My IP** only
   - HTTP, port 80, source anywhere (`0.0.0.0/0`)
   - HTTPS, port 443, source anywhere
4. **Elastic IP:** allocate one and associate it with the instance, so the address
   never changes.
5. At your domain registrar, create an **A record**: `shop.example.com` → the Elastic IP.

### Oracle Cloud (OCI)

1. Compute → **Create instance**: image Canonical Ubuntu 24.04, shape
   `VM.Standard.A1.Flex` (Ampere), for example 2 OCPU / 12 GB, boot volume 50 GB.
2. **Add SSH keys:** paste your public key.
3. Networking → your VCN → **Security List**, ingress rules:
   - TCP 22 from **your IP** only (`x.x.x.x/32`)
   - TCP 80 from `0.0.0.0/0`
   - TCP 443 from `0.0.0.0/0`
4. **Oracle's Ubuntu images also block ports inside the server** (iptables). Open 80 and
   443 there too, after logging in (step 3):

   ```bash
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
   sudo netfilter-persistent save
   ```

5. Reserve the public IP (Networking → Reserved public IPs) and create the **A record**
   at your registrar.

## 3. Server checklist (do this once, before installing anything)

Log in: `ssh ubuntu@shop.example.com` (AWS and OCI both use the user `ubuntu`).

- **OS updates, now and automatically later:**

  ```bash
  sudo apt update && sudo apt -y upgrade
  sudo apt -y install unattended-upgrades
  sudo dpkg-reconfigure -plow unattended-upgrades
  ```

- **SSH keys only, no passwords, no root login:**

  ```bash
  printf 'PasswordAuthentication no\nPermitRootLogin no\n' | sudo tee /etc/ssh/sshd_config.d/99-hardening.conf
  sudo systemctl restart ssh
  ```

  Keep your current session open and test a new `ssh` login before closing it.

- **Firewall:** the cloud firewall from step 2 is the main one. On AWS you can add `ufw`
  as a second layer (`sudo ufw allow OpenSSH && sudo ufw allow 80,443/tcp && sudo ufw enable`).
  On Oracle, keep the iptables rules from step 2 instead of ufw. Note: ports that Docker
  publishes bypass ufw, which is why `compose.prod.yaml` publishes only 80 and 443.

- **Swap** (needed on a 4 GB server, harmless on 8 GB):

  ```bash
  sudo fallocate -l 4G /swapfile && sudo chmod 600 /swapfile
  sudo mkswap /swapfile && sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
  ```

- **Optional:** `sudo apt -y install fail2ban` blocks addresses that keep failing SSH logins.

## 4. Install Docker

Docker's official install script (works on x86 and ARM):

```bash
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker ubuntu
```

Log out and in again, then check: `docker version` and `docker compose version`
(Compose v2 is included).

## 5. Get the code and fill in `.env`

```bash
git clone https://github.com/<your-account>/<your-repo>.git ecommerce
cd ecommerce
cp .env.example .env
chmod 600 .env
nano .env
```

Fill in **every** line. Production notes:

| Name | Production value |
|---|---|
| `DOMAIN` | `shop.example.com` (no `https://`) |
| `LETSENCRYPT_EMAIL` | your email, for certificate expiry warnings |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | your SMTP provider (port 587). `MAIL_SMTP_AUTH` / `MAIL_SMTP_STARTTLS` default to `true` |
| every `*_PASSWORD`, `JWT_SECRET` | new random values, never the development ones: `openssl rand -hex 24` (passwords), `openssl rand -base64 48` (JWT secret) |
| `EUREKA_PASSWORD` | letters and digits only (it is part of a URL) |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | the first admin account, created at the first start |
| `GITLAB_TOKEN`, `CONFIG_*` | as in development |

## 6. Start

```bash
docker compose -f compose.prod.yaml up -d --build
```

The first build downloads and compiles everything: 10–20 minutes. Then wait until every
container is `healthy` (several minutes; a service may restart once while MySQL starts):

```bash
docker compose -f compose.prod.yaml ps
```

The shop now answers on `http://shop.example.com` (plain HTTP for the moment: nginx starts
in HTTP-only mode while there is no certificate). The first requests after a start are
slow while Java warms up.

## 7. HTTPS (Let's Encrypt)

Get the certificate (replace the domain and email). By running this you accept the
Let's Encrypt terms of service:

```bash
docker compose -f compose.prod.yaml run --rm certbot certonly --webroot -w /var/www/certbot \
  -d shop.example.com --email you@example.com --agree-tos --no-eff-email
docker compose -f compose.prod.yaml restart nginx
```

At start nginx sees the certificate and switches to HTTPS: `https://shop.example.com`
works, `http://` redirects to it, and browsers are told to use HTTPS only (HSTS).

**Renewal:** certificates last 90 days. Add a daily job (`crontab -e`):

```
0 3 * * * cd /home/ubuntu/ecommerce && docker compose -f compose.prod.yaml run --rm certbot renew --quiet && docker compose -f compose.prod.yaml exec nginx nginx -s reload
```

## 8. Check that it is right

From your laptop:

- `https://shop.example.com` shows the shop; register, log in, order, pay.
- `http://shop.example.com` redirects to `https://`.
- Internal ports are closed:
  `nc -zv -w 3 shop.example.com 8080` (and 3306, 5432, 27017, 9092, 6379, 8761, 8888)
  must all fail.
- An order email arrives (check the notification logs if not:
  `docker compose -f compose.prod.yaml logs notification-service | grep -i mail`).
- Swagger is off: `https://shop.example.com/swagger-ui.html` gives 404.

## 9. Backups

The data is in Docker volumes (`ecommerce-prod_mysql-data`, `..._postgres-data`,
`..._mongo-data`). Back up the databases every night with their own dump tools
(the passwords come from inside the containers, so they never appear in a command line):

```bash
mkdir -p ~/backups
cat > ~/backup.sh <<'EOF'
#!/bin/sh
set -e
cd /home/ubuntu/ecommerce
D=$(date +%F)
docker compose -f compose.prod.yaml exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --databases userdb order_db' \
  | gzip > ~/backups/mysql-$D.sql.gz
docker compose -f compose.prod.yaml exec -T postgres pg_dump -U postgres payment_db \
  | gzip > ~/backups/postgres-$D.sql.gz
docker compose -f compose.prod.yaml exec -T mongo sh -c \
  'mongodump -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --archive --gzip' \
  > ~/backups/mongo-$D.archive.gz
find ~/backups -type f -mtime +7 -delete
EOF
chmod 700 ~/backup.sh
```

Run it at 2:00 every night (`crontab -e`): `0 2 * * * /home/ubuntu/backup.sh`

- **Copy the backups off the server** (a backup on the same disk dies with the disk):
  AWS S3 (`aws s3 sync ~/backups s3://your-bucket/`) or OCI Object Storage.
- **Also** turn on disk snapshots: AWS Data Lifecycle Manager for the EBS volume,
  OCI boot volume backup policy.
- **Test a restore** once, on another machine, before you need it.
- Kafka's data is not backed up: events are short-lived, and the outbox tables (in the
  database backups) hold anything not yet sent. Also keep a copy of `.env` somewhere safe.

## 10. Everyday operations

```bash
docker compose -f compose.prod.yaml ps                       # status
docker compose -f compose.prod.yaml logs -f --tail=100 order-service
git pull && docker compose -f compose.prod.yaml up -d --build   # deploy a new version
```

- A new version rebuilds only the images whose code changed (the Maven cache makes it quick).
- Database changes are Flyway migrations: they run when the service starts. **Take a
  backup before deploying** a version with a new `V*.sql` file.
- Logs are kept as 3 files of 10 MB per container, so they cannot fill the disk.
- Dead-letter messages: `docker compose -f compose.prod.yaml logs | grep "Sending message to"`
  (see [DEAD-LETTER-TOPICS.md](DEAD-LETTER-TOPICS.md)).

## Trade-offs and limits of this setup

- **One server** is one point of failure: if it goes down, the shop is down. Fine for a
  portfolio or a small shop; the next step would be managed databases and several app servers.
- **Kafka has a single broker** (replication factor 1).
- **No monitoring or alerting**: the logs are the signal. A next step: Prometheus/Grafana
  or the cloud provider's monitoring.
- **Same config repo as development.** Production-only values are environment variables in
  `compose.prod.yaml` (they win over the Config Server). A separate production branch or
  repo would keep development edits from ever reaching production.
- **Images are built on the server**, so no image registry is needed; the first build is slow.
- **Behind a cloud load balancer** (instead of nginx facing the internet directly), nginx
  would see the load balancer's address, not the user's. Then add to
  `nginx/snippets/app.conf` the `real_ip` settings (`set_real_ip_from <load balancer range>;`
  `real_ip_header X-Forwarded-For;`), so the per-IP rate limit keeps working.

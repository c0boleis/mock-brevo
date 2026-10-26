# mock-brevo

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Spring Boot 3.3](https://img.shields.io/badge/Spring%20Boot-3.3-green.svg)](https://spring.io/projects/spring-boot)

Local mock of the [Brevo](https://developers.brevo.com/) (ex-Sendinblue) transactional API, designed to stand in for `api.brevo.com` during development and integration testing. Clients swap the base URL; payloads stay identical.

> ⚠️ **Disclaimer.** This project is **not affiliated with, endorsed by, or sponsored by Brevo SAS**. "Brevo" and "Sendinblue" are trademarks of Brevo SAS. This is an independent community tool for local development and integration testing. No Brevo source code is redistributed — every endpoint is re-implemented from the publicly documented API at https://developers.brevo.com/.

## What it does

- Accepts Brevo-shaped HTTP requests on `/v3/**` and returns responses matching the real API contract (verified against the official [`getbrevo/brevo-php`](https://github.com/getbrevo/brevo-php) SDK).
- **Auto-provisions an account per API key** — any non-empty `api-key` header works, each gets its own isolated data (contacts, lists, campaigns, sent emails, …).
- **Persists every sent email** (`POST /v3/smtp/email`) in file-based H2 so you can assert on payloads or inspect them after a test run.
- **Optional SMTP forward** — relays captured emails to a real SMTP catcher (Mailpit, MailHog, …) for visual inspection.
- **Admin web UI** at `http://localhost:8080/` with:
  - live list of accounts + counters
  - last 500 REST calls (request/response headers + body, Monaco editor with JSON folding, copy-to-clipboard)
  - light/dark theme toggle
  - inline form to create faker campaigns per account
  - direct links to the matching Brevo documentation page for each logged endpoint
- **Webhook simulation** — outbound `delivered`, `hard_bounce`, `opened`, `click`, etc. to a client-controlled URL.

See [`ENDPOINTS.md`](ENDPOINTS.md) for the full endpoint coverage matrix.

## Quick start

### Docker (pre-built image)

```bash
docker run --rm -p 8080:8080 ghcr.io/<OWNER>/mock-brevo:latest
```

Then point your Brevo client at `http://localhost:8080` instead of `api.brevo.com`. Any `api-key` value works — the first use provisions the account on the fly.

### Docker Compose

```yaml
services:
  mock-brevo:
    image: ghcr.io/<OWNER>/mock-brevo:latest
    ports: ["8080:8080"]
    volumes: ["brevo-data:/app/data"]
    environment:
      MOCK_SMTP_ENABLED: "true"
      MOCK_SMTP_HOST: mailpit   # name of your SMTP catcher service
      MOCK_SMTP_PORT: "1025"

volumes:
  brevo-data:
```

### From source

Requires JDK 21. Maven is wrapped (`./mvnw`) — no system install needed.

```bash
./mvnw spring-boot:run                    # dev profile, SMTP forward ON (localhost:1025)
./mvnw test                               # run integration/contract tests
./mvnw clean package -DskipTests          # → target/mock-brevo-<version>.jar
java -jar target/mock-brevo-*.jar         # run packaged jar (default profile, SMTP OFF)
```

## Pointing a client at mock-brevo

| Client | Setting |
|---|---|
| `getbrevo/brevo-php` SDK | `Configuration->setHost('http://localhost:8080/v3')` |
| Symfony Mailer | use `brevo+api://KEY@localhost:8080` (custom host requires patching the bridge) or route via SMTP with `MOCK_SMTP_ENABLED=true` |
| `curl` / Postman | replace `https://api.brevo.com` with `http://localhost:8080` |

### Calling a host-running dev server from a Docker container

When mock-brevo runs directly on the host (`./mvnw spring-boot:run` listening on `localhost:8080`) but the client app runs in Docker, the client container can't resolve the host's `localhost`. Use the Docker-managed alias `host.docker.internal` instead.

The client container needs the alias mapped to the host gateway. Add this to your `docker-compose.yml`:

```yaml
services:
  yourapp:
    extra_hosts:
      - "host.docker.internal:host-gateway"
```

Then point the backend at `http://host.docker.internal:8080`. Note that this hostname only works **from inside Docker** — the browser on the host still uses `http://localhost:8080`. If the client also exposes deep-links to mock-brevo's UI (e.g. campaign edit pages), you need two distinct settings: one for backend traffic, one for the rendered URL.

Example for an Enoria-style setup with split API/app URLs:

```env
# .env — consumed by the PHP backend running in Docker
BREVO_API_URL=http://host.docker.internal:8080/v3
# Rendered as a link in the UI, opened by the browser on the host
BREVO_APP_URL=http://localhost:8080
```

If you instead run mock-brevo as a service inside the same Docker Compose project (the more common setup), use the service name and internal port — e.g. `BREVO_API_URL=http://mock-brevo:8080/v3` — and expose it through Traefik or a port mapping for browser access.

## Configuration

All settings are environment variables, sensible defaults for dev:

| Variable | Default | Description |
|---|---|---|
| `MOCK_BREVO_DB_PATH` | `./data/brevo` (host) / `/app/data/brevo` (Docker) | H2 file location |
| `MOCK_STATUS_REVEAL_KEYS` | `true` | Expose raw API keys in `/mock-status` (disable in shared environments) |
| `MOCK_DEFAULT_WEBHOOK_URL` | — | URL to fire outbound webhooks at |
| `MOCK_AUTO_FIRE_DELIVERED` | `false` | Auto-fire `delivered` webhook after each `POST /v3/smtp/email` |
| `MOCK_SMTP_ENABLED` | `false` | Relay captured emails to an SMTP server |
| `MOCK_SMTP_HOST` | `localhost` (dev profile) / `mailcatcher` (Docker) | SMTP host |
| `MOCK_SMTP_PORT` | `1025` | SMTP port (Mailpit default) |
| `MOCK_SMTP_USERNAME` / `MOCK_SMTP_PASSWORD` | — | Optional auth |
| `MOCK_SMTP_STARTTLS` | `false` | STARTTLS toggle |
| `SERVER_PORT` | `8080` | HTTP port |

## Admin routes (not part of the Brevo API)

| Method | Path | Description |
|---|---|---|
| `GET` | `/` | Web UI |
| `GET` | `/mock-status` | All provisioned accounts + counters |
| `GET` | `/mock-status/requests` | Last N REST calls (metadata) |
| `GET` | `/mock-status/requests/{id}` | Full call detail (headers + body) |
| `GET` | `/mock-status/accounts/{apiKey}/emails` | Captured emails for a tenant |
| `POST` | `/mock-status/accounts/{apiKey}/campaigns` | Create a faker campaign |
| `POST` | `/mock-webhooks/fire` | Trigger an outbound Brevo webhook |

## Contributing

- Open an issue describing the endpoint shape you need — bonus points for a link to the Brevo docs page and a sample SDK call.
- PRs welcome. Keep the mock behavior as close as possible to the real API response contract (field names, types, HTTP codes).

## Licence

[MIT](LICENSE) — use freely, attribute if you wish.

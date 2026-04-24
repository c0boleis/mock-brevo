# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and this project aims
to follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html) once it
reaches 1.0.

## [Unreleased]

## [0.2.0] — 2026-04-24

add scripts for: 
- change version
- deploy

## [0.1.0] — 2026-04-24

Initial public release. Scope: cover the Brevo REST endpoints actively used by
the Enoria project and any client relying on the `getbrevo/brevo-php` SDK.

### Added

#### Brevo API surface

- `GET /v3/account` with full payload (`organization_id`, `user_id`,
  `enterprise`, `dateTimePreferences`, plan, address, relay,
  marketingAutomation — all SDK-required fields populated).
- `POST /v3/smtp/email` with payload persistence and returning a synthetic
  `messageId`.
- `GET /v3/senders` with generated IPs.
- `GET/POST /v3/contacts/lists`, `POST /v3/contacts/import` (CSV), `GET
  /v3/contacts/lists/{id}/contacts`, `DELETE /v3/contacts/lists/{id}/contacts`,
  `PUT /v3/contacts/{identifier}`.
- `GET/POST /v3/emailCampaigns` + `POST /v3/emailCampaigns/{id}/sendNow` with
  faker-generated statistics (globalStats, campaignStats, statsByDevice…).
- `GET/POST /v3/smtp/templates` with non-null defaults on all required fields.
- `GET/POST /v3/contacts/folders`.

#### Platform

- Auto-provisioning: any non-empty `api-key` header creates a new tenant on the
  fly with a deterministic `organization_id`, a default sender, and 2 seeded
  faker campaigns.
- Strict account scoping for every persisted entity.
- File-based H2 database, survives restarts.
- Optional SMTP forward relaying captured emails to a real SMTP catcher
  (Mailpit, MailHog…). Activated by default in the `dev` Spring profile.
- Outbound webhook fire endpoint (`POST /mock-webhooks/fire`) for simulating
  `delivered`, `opened`, `click`, `hard_bounce`, etc.

#### Admin web UI

- Single-page UI at `/` with two tabs (accounts / REST calls).
- Light / dark theme toggle with persistence; Monaco Editor syncs its theme.
- Monaco Editor for JSON bodies with folding, read-only, view-state preserved
  across auto-refresh cycles.
- Copy-to-clipboard button on every body.
- Inline form to create faker campaigns per account.
- Direct links to the matching Brevo documentation page for each logged route.
- In-memory ring buffer of the last 500 REST calls with full headers and bodies
  (request body / response body, truncation at 16 KiB).

### Disclaimer

Not affiliated with Brevo SAS. "Brevo" is a trademark of Brevo SAS. Every
endpoint is re-implemented from the publicly documented API contract; no Brevo
source code is redistributed.

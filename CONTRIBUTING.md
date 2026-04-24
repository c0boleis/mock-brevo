# Contributing

Thanks for your interest!

## What a good contribution looks like

The goal of this project is to stay as close as possible to the **real Brevo API response contract**. When reporting or fixing a bug, or adding a new endpoint:

1. **Link to the Brevo reference page** — https://developers.brevo.com/reference/…
2. **Name the SDK version and method** you are calling (e.g. `getbrevo/brevo-php ^4.0`, `TransactionalEmailsApi::sendTransacEmail`).
3. **Show a minimal reproducer**: the request you send + the response you expect vs what you get.

## Development workflow

```bash
./mvnw spring-boot:run        # port 8080, dev profile (SMTP forward to localhost:1025)
./mvnw -B verify              # run tests
./mvnw clean package          # produce target/mock-brevo-*.jar
docker build -t mock-brevo:dev .
```

The UI at `http://localhost:8080/` shows every call your client makes (method, path, headers, request body, response body) — use it to observe the real shape you need to mock.

## Pull request checklist

- `./mvnw -B verify` passes locally.
- If you touched a response DTO, verify field names against the PHP SDK source (`vendor/getbrevo/brevo-php/src/**/Types/*.php`). Watch out for snake_case traps like `organization_id` and `user_id`.
- Keep field defaults non-null for fields typed as `public string $x` in the SDK — the deserializer will TypeError on null.
- Don't introduce new snake_case / camelCase inconsistencies without a source link justifying it.

## What is out of scope

- Real email delivery (use the optional SMTP forward to Mailpit/similar).
- Endpoints we don't already cover, unless a concrete client (e.g. the PHP SDK) needs them. See `ENDPOINTS.md` for the current matrix.

## Licence

By submitting a contribution you agree it is licensed under the project's [MIT License](LICENSE).

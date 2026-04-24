# Brevo API — Priorités d'implémentation pour mock-brevo

Liste priorisée des endpoints à mocker pour qu'Enoria (`/home/adu/git/enoria`) puisse fonctionner contre ce serveur local.

## Contexte

- **Client côté Enoria :** SDK officiel `getbrevo/brevo-php ^4.0` (Guzzle), clé API stockée chiffrée dans `Entite.brevoApiKey` / `EntiteLiee.brevoApiKey`.
- **Base URL réelle :** `https://api.brevo.com/v3`
- **Auth :** header `api-key: <KEY>`. **Un compte = une clé API.** À chaque requête entrante :
  - Si la clé API existe en base → on rattache la requête au compte correspondant.
  - Sinon → on **crée automatiquement** un nouveau compte (avec valeurs par défaut : `firstName`, `lastName`, `email`, `plan[]` générés à partir de la clé) et on le rattache à la requête.
  - Seule une clé absente/vide doit renvoyer **401**. Toute autre clé est valide et provisionne son compte à la volée.
  - Toutes les données (contacts, listes, campagnes, emails envoyés, templates, dossiers, senders) sont **scopées par compte** — aucune fuite entre clés API.
- **Webhooks :** Enoria reçoit les callbacks sur `POST /callback/brevomail/{key}` (clé = `TOKEN_CALLBACK_MAIL`). Le mock doit pouvoir **émettre** ces webhooks vers une URL configurable pour simuler `delivered`, `opened`, `click`, `hard_bounce`, `soft_bounce`, `complaint`, `invalid_email`, `blocked`, `error`, `unsubscribed`.

Tous les chemins ci-dessous sont préfixés par `/v3`. Les réponses doivent respecter **exactement** les noms de champs JSON de Brevo (le SDK désérialise strictement).

---

## P0 — Bloquants (sans eux, Enoria plante au démarrage)

| # | Méthode | Endpoint | SDK | Appelé depuis | Notes |
|---|---------|----------|-----|---------------|-------|
| 1 | `GET` | `/account` | `AccountApi::getAccount` | `ApiBrevoService.php:75` (validation clé), `:322` (dashboard) | Doit renvoyer `firstName`, `lastName`, `email`, `plan[]` avec `type` et `credits` du compte rattaché à la clé. Si la clé est absente/vide → **401**. Si c'est la première fois qu'on voit cette clé, le compte est créé à la volée (voir § Contexte) puis renvoyé. |
| 2 | `POST` | `/smtp/email` | `TransactionalEmailsApi::sendTransacEmail` | `ApiBrevoService.php:224` (envois groupés ToolsController), `:664` (reçus de dons) | **Endpoint le plus appelé.** Accepter `to[]`, `sender`, `subject`, `htmlContent`, `cc`, `bcc`, `attachment`, `replyTo`, `templateId`, `params`. Retourner `{ "messageId": "<uuid>" }`. Persister le payload rendu pour inspection. |
| 3 | `GET` | `/senders` | `AccountApi::getSenders` | `ApiBrevoService.php:348` + nombreux dropdowns | Renvoie `senders[]` avec `name`, `email`, `active` (bool). Enoria filtre sur `active=true`. |

## P1 — Gestion des contacts et listes (flux newsletter)

| # | Méthode | Endpoint | SDK | Appelé depuis | Notes |
|---|---------|----------|-----|---------------|-------|
| 4 | `GET` | `/contacts/lists` | `ContactsApi::getLists` | `ApiBrevoService.php:296` | Retourner `lists[]` avec `id`, `name`, `uniqueSubscribers`. Supporte `limit`/`offset`. |
| 5 | `POST` | `/contacts/import` | `ContactsApi::importContacts` | `ApiBrevoService.php:371`, `CampagneCommunicationService.php:435` | Payload clé : `fileBody` (CSV `EMAIL,PRENOM,NOM`), `listIds[]`, `updateExistingContacts=true`. Retourner `{ "processId": <int> }`. En mock : parser le CSV, matérialiser les contacts synchrones, puis répondre OK. |
| 6 | `GET` | `/contacts/lists/{listId}/contacts` | `ContactsApi::getContactsFromList` | `ApiBrevoService.php:421` (boucle sync par batch de 300) | Pagination `limit`/`offset`. Response : `contacts[]` avec `email`, `emailBlacklisted`. |
| 7 | `DELETE` | `/contacts/lists/{listId}/contacts` | `ContactsApi::removeContactFromList` | `ApiBrevoService.php:387` (désabonnement utilisateur) | Body : `{ "emails": [...] }`. |
| 8 | `PUT` | `/contacts/{email}` | `ContactsApi::updateContact` | `ApiBrevoService.php:405` (toggle blacklist) | Payload : `emailBlacklisted` (bool), `listIds[]`. |

## P2 — Campagnes email (dashboard + envois manuels)

| # | Méthode | Endpoint | SDK | Appelé depuis | Notes |
|---|---------|----------|-----|---------------|-------|
| 9 | `GET` | `/emailCampaigns` | `EmailCampaignsApi::getEmailCampaigns` | `ApiBrevoService.php:257` | Query : `type=classic`, `limit=100`, `offset=0`. Enoria lit `campaigns[].id/name/subject/status/sentDate/sender.email` et `statistics.campaignStats[].delivered`. |
| 10 | `POST` | `/emailCampaigns` | `EmailCampaignsApi::createEmailCampaign` | `ApiBrevoService.php:590`, `CampagneCommunicationService.php` | Payload : `name`, `sender{name,email}`, `templateId`, `subject`, `replyTo`, `recipients.listIds[]`, `inlineImageActivation`, `mirrorActive`, `utmCampaign`, `params` (variables de template, ex. `URL_DON`). |
| 11 | `POST` | `/emailCampaigns/{id}/sendNow` | `EmailCampaignsApi::sendEmailCampaignNow` | `ApiBrevoService.php:621` | Pas de body. Retourner 204. Doit déclencher (asynchrone) les webhooks `delivered` vers Enoria si configuré. |

## P3 — Templates & dossiers (UI secondaire)

| # | Méthode | Endpoint | SDK | Appelé depuis | Notes |
|---|---------|----------|-----|---------------|-------|
| 12 | `GET` | `/smtp/templates` | `TransactionalEmailsApi::getSmtpTemplates` | `ApiBrevoService.php:483` | Query : `templateStatus=true`, `limit=50`. Response : `templates[]` avec au moins `id`, `name`. |
| 13 | `GET` | `/contacts/folders` | `ContactsApi::getFolders` | `ApiBrevoService.php:512` | Query : `limit=20`. Response : `folders[]` avec `id`, `name`. |
| 14 | `POST` | `/contacts/folders` | `ContactsApi::createFolder` | `ApiBrevoService.php:527` (crée « Campagnes de communication Enoria » si absent) | Payload : `{ "name": "..." }`. Response : `{ "id": <int> }`. |

## P4 — Routes d'administration du mock (hors API Brevo)

Ces routes ne sont **pas** des endpoints Brevo — elles servent à inspecter/piloter le mock depuis les tests ou un navigateur. Préfixe conseillé : `/mock-*` ou `/_mock/*` pour éviter toute collision avec un éventuel futur endpoint Brevo.

### `GET /mock-status` — Liste des comptes provisionnés

Retourne la liste des clés API rencontrées et, pour chacune, un résumé du compte associé. Aucun auth requis (endpoint de debug/dev).

Format de réponse proposé :
```json
{
  "accounts": [
    {
      "apiKey": "xkeysib-abc...",
      "apiKeyPreview": "xkeysib-abc…1234",
      "createdAt": "2026-04-24T10:15:00Z",
      "lastSeenAt": "2026-04-24T11:02:13Z",
      "account": { "firstName": "...", "lastName": "...", "email": "..." },
      "counters": {
        "emailsSent": 42,
        "contacts": 1530,
        "lists": 3,
        "campaigns": 1,
        "templates": 2,
        "folders": 1,
        "senders": 2
      }
    }
  ]
}
```

Pour éviter de fuiter les clés en clair dans des logs ou captures d'écran, exposer `apiKeyPreview` (premiers + derniers caractères) à côté de la clé complète, et envisager un flag `MOCK_STATUS_REVEAL_KEYS=false` qui masque `apiKey` en production de test partagée.

### `POST /mock-webhooks/fire` — Émettre un webhook vers Enoria

Pour tester le scoring d'incidents côté Enoria :

- Body : `{ "url": "http://enoria.local/callback/brevomail/<TOKEN>", "event": "...", "email": "...", "reason": "..." }`
- Événements gérés par `CallbackController.php:25` : `delivered`, `unique_opened`, `opened`, `click` (positifs) · `soft_bounce`, `hard_bounce`, `complaint`, `invalid_email`, `blocked`, `error`, `unsubscribed` (négatifs)
- Sécurité Enoria : la clé `TOKEN_CALLBACK_MAIL` est passée en path param/query string ; le mock la transmet telle qu'on la lui donne.

Optionnel : déclencher automatiquement `delivered` quelques secondes après chaque `POST /smtp/email` si une URL webhook par défaut est configurée par compte.

### `GET /mock-status/accounts/{apiKey}/emails` — Inspection des emails envoyés

Liste les emails persistés pour un compte donné (utile pour les assertions de tests). Query : `limit`, `offset`. Réponse : payload complet capturé au moment de l'envoi + `messageId` rendu.

---

## Hors scope (pour l'instant)

Enoria **n'utilise pas** : SMS, WhatsApp, Conversations, Companies, Deals, Inbound Parsing, scores d'attributs contacts, sous-comptes. Ne pas implémenter tant qu'un appel réel n'apparaît pas.

SMTP transport (`brevo+smtp://` via Symfony Mailer) : Enoria a la capacité mais utilise actuellement un SMTP local — **pas prioritaire** pour le mock.

## Ordre de démarrage recommandé

1. Squelette Spring Boot + H2 file + Dockerfile
2. **Modèle `Account` + filtre d'auth** : résout la clé API de la requête, crée le compte si inconnu, expose le compte courant aux controllers (ex. via `HandlerInterceptor` + attribut de requête ou `@ModelAttribute`). Toute entité métier (contact, liste, email, campagne…) porte une FK `account_id`.
3. P0 #1 `GET /account` + `GET /mock-status` → on peut déjà voir les comptes apparaître
4. P0 #2 `POST /smtp/email` + persistance + `GET /mock-status/accounts/{apiKey}/emails` → Enoria peut envoyer, les tests peuvent asserter
5. P0 #3 `GET /senders`
6. P1 (5 endpoints) → flux newsletter opérationnel
7. P2 (3 endpoints) → campagnes
8. P3 (3 endpoints) → finition UI
9. P4 `POST /mock-webhooks/fire` → tests de bout en bout du scoring d'incidents

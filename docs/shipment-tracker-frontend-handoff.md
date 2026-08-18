# Shipment Tracker — Backend Handoff

Backend for the shipment tracker feature is live. This covers what exists, the API contract, and things to know before building the UI. Not a UI spec — no opinions here on layout/components.

## Auth

All `/shipment-tracker/*` endpoints require a Supabase-issued bearer token, same as the rest of this API: `Authorization: Bearer <supabase-access-token>`. Get it the same way the rest of the site already does (Supabase client session after Google OAuth sign-in). No new auth setup needed. `/carriers` does **not** require auth — it's public reference data.

A request without a valid token on a `/shipment-tracker/*` route gets a `401`. A token for a different user never lets you see or modify someone else's shipments (delete on another user's shipment id returns `404`, not `403` — don't rely on getting a 403 to distinguish "not yours" from "doesn't exist," the API deliberately doesn't reveal that distinction).

## Carriers are a fixed, backend-driven list — don't hardcode them

`GET /carriers` returns every valid carrier. **Call this to populate the carrier dropdown/select** rather than hardcoding `["USPS"]` (etc.) in frontend code — the backend is the source of truth, and new carriers show up here automatically as they're added server-side, no frontend deploy required.

```
GET /carriers
```
```json
[
  { "id": 1, "code": "USPS", "displayName": "USPS" }
]
```
Use `id` as the value the create form submits; `displayName` is what to show the user. `code` is an internal identifier, not meant for display (they happen to be the same string today, don't assume that always holds).

Only `USPS` exists today. The dropdown should just render whatever this endpoint returns — no need to special-case "only one option."

## Creating a shipment

```
POST /shipment-tracker
Authorization: Bearer <token>
Content-Type: application/json

{ "trackingNumber": "9400111899223197428490", "carrierId": 1 }
```

Success — `201`, body is the created shipment **with its first status already fetched** (see shape below). The backend scrapes USPS synchronously as part of this call, so expect this request to take a couple seconds — show a loading state, don't assume it's instant like most creates.

Failure cases the UI should handle:
- `400` — bad `carrierId` (not in the `/carriers` list) or blank/missing `trackingNumber`.
- `409` — this exact tracking number + carrier combo already exists for this user. Surface this as "you're already tracking this package," not a generic error — it's an expected, recoverable case, not a bug.

## Listing shipments

```
GET /shipment-tracker
Authorization: Bearer <token>
```
```json
[
  {
    "id": "b3f2...uuid",
    "trackingNumber": "9400111899223197428490",
    "carrier": { "id": 1, "code": "USPS", "displayName": "USPS" },
    "statusText": "In Transit, Arriving Late",
    "lastLocation": "Springfield, IL",
    "estimatedDelivery": "2026-08-20",
    "delivered": false,
    "lastCheckedAt": "2026-08-18T14:02:11Z",
    "createdAt": "2026-08-17T09:15:03Z"
  }
]
```

Ordered newest-created first. Every field except `statusText`/`lastLocation`/`estimatedDelivery` is always present; those three can be `null` — this happens if a scrape fails (e.g. USPS is temporarily unreachable or blocks the request), not just transiently on creation, since creation always attempts a scrape first. **Design the UI to handle a shipment showing no status gracefully** (e.g. "Status unavailable, we'll try again shortly") — this isn't just an edge case, USPS scraping can legitimately fail sometimes.

Status data is refreshed automatically server-side, but **not on every single call** — the backend only re-checks USPS if the cached status is more than ~30 minutes old (and never re-checks once `delivered: true`). So calling this endpoint repeatedly in a short window will return the same cached data, and a request right after creating a shipment or right after another recent refresh won't visibly re-check. This is intentional (avoids hammering USPS), not a bug — don't build a "force refresh" expectation into the UI unless we later add a dedicated endpoint for it (none exists today).

Because of the above, a `GET /shipment-tracker` call can occasionally take a few seconds if several shipments are all stale at once (each stale one gets scraped synchronously, in sequence, before the response comes back) — show a loading state rather than assuming this is always instant.

## Deleting a shipment

```
DELETE /shipment-tracker/{id}
Authorization: Bearer <token>
```
`204` on success. `404` if the id doesn't exist or isn't yours. There's no "archive" or "undo" — deletion is permanent. Once a package is `delivered: true`, deleting is the only way to clear it from the list (there's no separate archive endpoint in v1).

## Things not built yet (don't assume they exist)

- No update/edit endpoint — if a user picks the wrong carrier or fat-fingers a tracking number, the flow is delete + re-create.
- No manual "refresh now" endpoint — refresh only happens automatically per the TTL rule above.
- No pagination on `GET /shipment-tracker` — fine for expected usage (one person's in-flight packages), would need revisiting if that assumption changes.
- Only USPS exists as a carrier right now; the UI shouldn't hardcode assumptions that break when a second carrier is added (e.g. don't assume there's always exactly one carrier option).

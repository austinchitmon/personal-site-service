# Shipment Tracker — Manual Test Checklist

Everything here needs a real Google-sign-in-backed Supabase bearer token, so it couldn't be exercised while building the backend in isolation — this is what to run through once the frontend can produce one. Check these off once the UI is in place (or via curl with a token pulled from a signed-in browser session).

## Core flow

- [ ] `GET /carriers` returns the `USPS` option and the UI dropdown reflects it.
- [ ] Create a shipment with a real USPS tracking number → `201`, and the response already has `statusText`/`lastLocation` populated (confirms the synchronous first scrape actually works against the live USPS page, not just against the graceful-failure path).
- [ ] `GET /shipment-tracker` shows the shipment just created, with the same status data.
- [ ] Delete the shipment → `204`, then confirm it no longer appears in the list.

## Error handling

- [ ] Re-submit the same tracking number + carrier for the same user → `409 Conflict`.
- [ ] Submit a `carrierId` that doesn't exist (e.g. `999`) → `400 Bad Request`.
- [ ] Submit a blank/missing `trackingNumber` → `400 Bad Request`.
- [ ] Call any `/shipment-tracker/*` endpoint with no token, or an expired/garbage token → `401`.
- [ ] Create a shipment as user A, then try to `DELETE` it while authenticated as user B → `404` (not `403`, not `200`).

## USPS scraping — the part that couldn't be verified at all without a live call

- [ ] Confirm `UspsTrackingClient`'s URL and CSS selectors actually work — they were written without inspecting a live USPS response (flagged with a `TODO` comment in the file). Test against a few real tracking numbers in different states:
  - [ ] Pre-shipment / label-created
  - [ ] In-transit
  - [ ] Delivered — confirm the shipment also flips to `delivered: true` and stops refreshing (see TTL section below)
- [ ] Deliberately try a garbage/invalid tracking number and confirm the app degrades gracefully (`statusText: "Unable to retrieve status"`, no `500`) rather than throwing.

## TTL / caching behavior

- [ ] Right after creating a shipment, call `GET /shipment-tracker` again immediately — confirm `lastCheckedAt` does **not** advance (still within the 30-minute TTL, should serve cached data, no new outbound scrape).
- [ ] Temporarily lower `shipment-tracker.tracking-ttl-minutes` in `application.yaml` (or override via env var) to something short like 1 minute, wait past it, call `GET /shipment-tracker` again, and confirm `lastCheckedAt` advances (a real re-scrape happened).
- [ ] For an already-`delivered` shipment, confirm repeated `GET /shipment-tracker` calls never advance `lastCheckedAt` — delivered shipments should never re-scrape.

## Things to watch, not necessarily blockers

- [ ] Check whether USPS blocks or CAPTCHAs the scraper under real traffic (single dev machine testing likely won't trigger this, but worth keeping an eye on once this sees real usage).
- [ ] If several shipments are stale at once, confirm `GET /shipment-tracker` still returns successfully even though it's scraping each one serially (just slower, not broken).

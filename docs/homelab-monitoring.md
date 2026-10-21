# Homelab monitoring

The read-only admin dashboard calls `GET /admin/homelab`. The response combines
the API's own uptime, a bounded check of its pooled PostgreSQL connection, and
a sanitized host snapshot. It never accepts a container ID, command, or file path
from the request and provides no start/stop/exec/log-reading endpoints.

## Authorization

Spring validates the existing Supabase ES256 JWT signature, issuer, audience, and
expiry. Every `/admin` or `/admin/**` endpoint additionally requires
`app_metadata.role == "admin"`. Google sign-in alone is insufficient. User-editable
`user_metadata`, the JWT's top-level `role`, or an unsigned token cannot grant
admin access. The frontend retains Google OAuth and checks the admin role before
loading the admin area and again on child navigation.

This follows the existing JWT trust model: role changes and revocation are subject
to token refresh/expiry. No service-role key, new auth provider, or database schema
is introduced. Monitoring responses use `Cache-Control: no-store`.

`UserSyncFilter` skips only `/admin/homelab` and its child paths. Their JWT checks
still run, but a failure to write `core.users` cannot prevent an admin from seeing
that PostgreSQL is unavailable. All other authenticated routes retain user sync.

## Host collector

Files in `monitoring/` install a small Python 3 collector and systemd timer:

- `/usr/local/lib/chitmon-monitor/collect.py`: root-owned collector code.
- `chitmon-monitor.timer`: every 15 seconds, subject to systemd scheduling.
- `chitmon-monitor.service`: bounded oneshot; no public listener.
- `/var/lib/chitmon-monitor/status.json`: root-owned, mode 644, atomically replaced.
- Container mount: `/app/homelab-monitor`, **read-only directory** mount.
- `HOMELAB_SNAPSHOT_PATH`: defaults to `./monitoring-data/status.json` outside Docker.
- `HOMELAB_MONITOR_DIRECTORY`: optional Compose host-directory override.

The collector uses fixed, read-only Docker CLI commands and `/proc`/filesystem
measurements. Inspection requests only allowlisted fields. It never reads Docker
environment variables, arbitrary labels, logs, mount configuration, or credentials.
The API container is **not** given the Docker socket, privileged mode, host PID
access, or permission to edit the snapshot. The root service is sandboxed with a
read-only filesystem except its output directory, no new privileges, and Unix-only
socket access; root is used for the host Docker socket rather than granting the
web application Docker administration rights.

Measurements include host CPU/load, available memory, swap, root disk, uptime,
all running and stopped containers, healthchecks, restarts, exit codes, image IDs,
published/internal ports, per-container CPU/memory, and cumulative network I/O.
Cloudflare Tunnel and the backend deployment runner are checked separately as
systemd services. This dashboard does not enumerate arbitrary host processes or
discover non-container applications automatically; add fixed service names in
`SERVICES` for those.

CPU is an instantaneous sample. Container CPU can exceed 100% across multiple
logical cores. Container memory uses Docker CLI's cache-adjusted usage, while
host memory uses `MemAvailable`. Stopped containers and missing resource samples
show unknown values, never invented zeros. A Docker failure still yields host
measurements with an explicit inventory warning.

## First deployment

1. On the homelab, review and install the collector from this repository:

   ```sh
   sudo sh monitoring/install.sh
   systemctl status chitmon-monitor.timer
   systemctl show chitmon-monitor.service -p Result -p ExecMainStatus
   ```

   Installation starts the new collector only. It does not restart Docker,
   PostgreSQL, the API, Cloudflare Tunnel, or the GitHub runner.

2. Deploy the backend through its existing main-branch workflow. The snapshot
   directory must exist first; Compose deliberately refuses to create a missing
   source path. Preserve the homelab's separate PostgreSQL loopback override.
3. Deploy the frontend consumer after the protected endpoint is available.
4. With an admin session, open `/admin/homelab`. Check API, database, collector,
   inventory and supporting-service states. Anonymous/invalid-token requests must
   return 401; valid non-admin sessions must return 403.

The collector was installed and verified on 2026-10-21 without restarting existing
services. At verification the API and PostgreSQL containers were healthy, both
supporting systemd services were active, and PostgreSQL remained on
`127.0.0.1:5432`. The backend/Angular rollout is separate from collector installation.

## Failure behavior and debugging

- A missing, invalid, over-1-MiB or unsupported snapshot produces `unavailable`
  collector status while the endpoint still reports API/database checks.
- More than 60 seconds of age, or a future clock skew over 10 seconds, marks host
  data stale. The frontend keeps aging observations even if automatic updates are
  paused and removes current-status styling when observations age or requests fail.
- The database probe uses one worker and the existing pool, starts at most one
  attempt per 10 seconds, and waits at most 2.5 seconds per HTTP caller. Concurrent
  callers share one attempt without serial waiting or an unbounded task queue.
  A stuck pool does not hold the monitoring response indefinitely. Its connection
  is closed when that one attempt eventually finishes.
- Frontend polling is every 15 seconds with an 8-second timeout; overlapping
  refreshes are suppressed and hidden tabs pause automatic requests. Returning
  to the tab refreshes immediately. History is at most 20 distinct fresh host
  samples in memory for that visit, not historical monitoring storage.
- Network failure keeps clearly marked last-known values. 401/403 or sign-out
  clear private dashboard state and stop requests. An access-token refresh through
  the existing auth interceptor is used for each request.

```sh
systemctl status chitmon-monitor.timer chitmon-monitor.service
journalctl -u chitmon-monitor.service --since='1 hour ago'
systemctl status docker cloudflared
cd /opt/personal-site-service
docker compose logs --since=1h personal-site-service
```

Collector problems are visible through snapshot warnings, stale timestamps and
systemd logs. The API logs collector state transitions and database SQL states,
without connection credentials or raw exception messages. The browser logs only
the monitoring endpoint and HTTP status. There is no external alerting or durable
time-series database in this iteration.

## Local development and tests

The development Compose override mounts `./monitoring-data` instead. Its contents
are ignored by Git except `.gitkeep`; use a sanitized snapshot or leave it empty
to exercise the unavailable-collector view. No sample credentials or production
snapshot is committed. Do not deploy the development PostgreSQL port override.

```sh
sh ./mvnw '-Dtest=Resume*Test,Homelab*Test,DatabaseHealthProbeTest' test
python3 -m unittest discover -s monitoring/tests -v
```

Security tests use fresh ES256 keys and a local JWKS server against the real JWT
decoder and Spring filter chain. They cover valid admin access, non-admin denial,
forged metadata/signatures, expiry, issuer/audience, CORS preflight, and independence
from database user sync. No live Supabase account or database is needed for CI.

To remove the collector later, stop/disable its timer before removing its unit
and script files. Revert/remove the backend mount before deleting the host data
directory; do not stop the Docker daemon or database as part of that operation.

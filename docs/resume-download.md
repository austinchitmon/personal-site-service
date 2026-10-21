# Resume download

`GET /files/resume.pdf` serves one configured PDF anonymously through the existing
`api.chitmon.com` Cloudflare Tunnel. It does not serve a directory or accept a
filename from the request. The response uses `application/pdf`, an attachment
filename of `Austin-Chitmon-Resume.pdf`, and `Cache-Control: no-store` on both
success and failure. `HEAD` is also supported by Spring MVC.

## Storage and deployment

- Homelab file: `/srv/chitmon/public/resume.pdf`.
- Container mount: the entire directory at `/app/public-files`, read-only.
- Container configuration: `RESUME_FILE_PATH=/app/public-files/resume.pdf`.
- Optional host-directory override: `RESUME_HOST_DIRECTORY` in the Compose environment.
- Local IDE default: `./public-files/resume.pdf`; the development Compose override
  mounts the same local directory. PDFs in this directory are ignored by Git.

Create the homelab directory before the first deployment; Compose intentionally
fails if the source directory is missing. The non-root application user needs
read/traverse permissions: directory mode `755` and PDF mode `644` work.
The production PostgreSQL loopback override must remain unchanged.

The backend must be deployed before the frontend switches to the new URL.
The existing Supabase PDF can remain available for rollback; this feature does
not delete or modify it. No tunnel or DNS change is required.

The initial PDF was copied from the currently public Supabase-backed download
on 2026-10-21: 83,629 bytes, SHA-256
`afdf6a4a3bb0bf74f5884528b8f9c8fb8d0420cee0573e394eeb99cba7f37935`.
Read-only access was verified using the production image's application user.
This stages the file; it does not by itself deploy the new endpoint.

## Replace the resume

Upload a temporary file, then rename it in the same directory so visitors never
receive a partially uploaded PDF. For example, from the machine holding the new PDF:

```sh
scp resume.pdf homelab:/srv/chitmon/public/resume.pdf.new
ssh homelab 'chmod 644 /srv/chitmon/public/resume.pdf.new && mv /srv/chitmon/public/resume.pdf.new /srv/chitmon/public/resume.pdf'
```

The application reads the file on each request, and the directory mount sees the
new file immediately. No container restart, website build, or database change is
needed. Files must be nonempty, start with `%PDF-`, and be at most 10 MiB. The
header check guards against accidental HTML/empty uploads; it is not full PDF validation.

After deployment, verify the public endpoint:

```sh
curl -fI https://api.chitmon.com/files/resume.pdf
curl -f https://api.chitmon.com/files/resume.pdf -o downloaded-resume.pdf
```

## Debugging

Every resume request receives an `X-Request-Id` reference. The controller logs
missing, inaccessible, oversized, or invalid files at ERROR with the reference,
configured server path, and exception. It returns a generic 503 problem response
with the same reference; filesystem details stay in server logs. A request filter
also logs exceptions while delivering the response, including connection/write
failures after the controller returns. PDF contents, auth tokens, and visitor
identities are not included in these logs.

View the running backend's logs:

```sh
ssh homelab 'cd /opt/personal-site-service && docker compose logs --since=1h --timestamps personal-site-service'
```

Search for `Resume download failed`, `Resume response delivery failed`, or the
reference from the browser console. Docker rotates the application's JSON logs
at 10 MB with three files; container recreation removes that container's logs.
This iteration does not add an external log service or automated alerts.

The frontend logs failed requests, timeouts, non-PDF responses, and browser
download handoff failures in the browser console and offers a retry message.
An unreachable tunnel/server cannot log that request on the backend; inspect
the browser status/error and `journalctl -u cloudflared` in that case.

Tests:

```sh
./mvnw '-Dtest=Resume*Test' test
```

These cover success/headers, replacement without service restart, missing and
unreadable files, invalid/empty/oversized PDFs, HEAD metadata, nonpublic filenames,
and correlated response-delivery errors. They do not require a database. A PR
workflow runs these checks with Java 21.

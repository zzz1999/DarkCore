# EntityReskin End-to-End Testing Runbook

Goal: run the full "backend (web) ↔ Paper plugin ↔ Fabric client" chain locally, verifying that after an entity is given an identifier the client downloads and renders the re-skinned appearance, while un-skinned entities fall back to vanilla.

> Everything verifiable by compilation is green; client rendering (`EntityRenderDispatcherMixin`) and Bukkit→Fabric payload reception were **verified on real hardware on 2026-06-12** — an armor stand was successfully re-skinned to the downloaded cube and played a self-rotating idle animation. This runbook is the reproduction procedure.

## 0. Prerequisites

- JDK 21 (confirm with `java -version`). The three artifacts in this repo are already built:
  - Plugin: `server/build/libs/EntityReskin-server-0.1.0-SNAPSHOT.jar`
  - Backend: `web/build/libs/web-0.1.0-SNAPSHOT.jar`
  - Client: `client/build/libs/EntityReskin-client-0.1.0-SNAPSHOT.jar`
  - Rebuild: `./gradlew :shared:publishToMavenLocal :server:build :web:bootJar` and `client/gradlew.bat -p client build`
- A **Paper 1.21.11** server (must match the client version).
- Sample resources (all under `sample-assets/`): `entityreskin_cube.geo.json` (an 8×8×8 cube), `entityreskin_cube.png` (a 16×16 texture), `entityreskin_cube.animation.json` (`animation.entityreskin.idle`, a slow rotation about the Y axis, easy to confirm by eye). **Geometry, texture, and animation are all required** (enforced by the backend's `AppearanceService`; a missing one returns 400).

Command examples use PowerShell + `curl.exe` (bundled with Windows 10+); JSON is wrapped in single quotes to avoid escaping.

---

## 1. Start the backend (development mode)

```powershell
# From the repo root; development defaults to H2 + dev-mode (codes printed to the console, no real email sent).
# ENTITYRESKIN_RECHARGE_SECRET: the local recharge secret. Downloads are metered; a new account has a 0 balance,
# so without a recharge every download returns 402. It must therefore be set for local testing
# (in production a real payment webhook holds it; never use this dev value there).
$env:ENTITYRESKIN_RECHARGE_SECRET = 'dev-local-recharge'
./gradlew :web:bootRun
```

Listens on `http://localhost:8080`. Development defaults: no production secrets required, no captcha, verification codes printed to the log. **Keep this window open to watch the log** (codes appear here).

> After changing backend code you must **restart bootRun** for it to take effect (bootRun does not hot-reload). For example, the `SecurityConfig` change that permits ERROR dispatch — so real status codes such as 401/402/403 pass through to the plugin/client instead of being masked as an empty-body 403 — only loads after a restart.

---

## 2. Prepare data over REST

> **Windows PowerShell note**: do not send JSON with `curl.exe` — PowerShell's quote and long-line handling is unreliable (the JSON is echoed verbatim, or a long command is truncated around 110 characters). Use PowerShell's built-in `Invoke-RestMethod`, and store long URLs/JSON in **short variables** first, pasting line by line:
> ```powershell
> $u='http://localhost:8080/api/auth/register'
> $b='{"email":"tester@qq.com","password":"Test123456"}'
> Invoke-RestMethod $u -Method Post -ContentType application/json -Body $b
> ```
> Rewrite every JSON-posting step below this way; **only "upload assets" keeps using `curl.exe -F`** (file uploads have no JSON-quoting problem).

### 2.1 Register + verify + log in

```powershell
# Register (the email domain must be QQ/NetEase; the password must be >= 10 chars with upper, lower, and a digit)
curl.exe -s -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" -d '{"email":"tester@qq.com","password":"Test123456"}'
# → {"status":"verification_sent"}; the code is in the bootRun console:  [DEV] verification code for tester@qq.com is 123456

curl.exe -s -X POST http://localhost:8080/api/auth/verify -H "Content-Type: application/json" -d '{"email":"tester@qq.com","code":"123456"}'

curl.exe -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"email":"tester@qq.com","password":"Test123456"}'
# → {"token":"<JWT>","expiresInMinutes":1440}. Save the token:
$T = "Bearer <paste the JWT above>"
```

### 2.2 Upload assets (geometry + texture + animation)

Uploads are multipart; `curl.exe -F` has no JSON-quoting problem, so use it directly:

```powershell
curl.exe -s -X POST http://localhost:8080/api/assets -H "Authorization: $T" -F "kind=geometry" -F "file=@sample-assets/entityreskin_cube.geo.json"
# → {"sha256":"<GEO_SHA>", ...}

curl.exe -s -X POST http://localhost:8080/api/assets -H "Authorization: $T" -F "kind=texture" -F "file=@sample-assets/entityreskin_cube.png"
# → {"sha256":"<TEX_SHA>", ...}

curl.exe -s -X POST http://localhost:8080/api/assets -H "Authorization: $T" -F "kind=animation" -F "file=@sample-assets/entityreskin_cube.animation.json"
# → {"sha256":"<ANI_SHA>", ...}
```

### 2.3 Register a server (get a token)

```powershell
curl.exe -s -X POST http://localhost:8080/api/servers -H "Authorization: $T" -H "Content-Type: application/json" -d '{"name":"local-test"}'
# → {"id":1,"name":"local-test","token":"<SERVER_TOKEN>", ...}. Note the id and token.
```

### 2.4 Create an appearance entry

Fill in all three of the `geometry`/`animation`/`texture` shas; `geometryName` is the identifier inside the geometry file, and `defaultAnimation` must match the animation name defined in the animation file (here `animation.entityreskin.idle`). Use `Invoke-RestMethod` (JSON cannot go through `curl.exe -d`):

```powershell
$u='http://localhost:8080/api/servers/1/appearances'
$b='{"identifier":"entityreskin:cube","displayName":"cube","geometryName":"geometry.entityreskin_cube","defaultAnimation":"animation.entityreskin.idle","resources":{"geometry":"<GEO_SHA>","animation":"<ANI_SHA>","texture":"<TEX_SHA>"}}'
Invoke-RestMethod $u -Method Post -ContentType application/json -Headers @{Authorization=$T} -Body $b
```

> All three resource kinds are required (`AppearanceService.REQUIRED_KINDS`). Prefer an ASCII `displayName`; PowerShell 5.1's `Invoke-RestMethod` mangles a non-ASCII request body into `?` (this affects only the display name, not rendering).

### 2.5 Recharge the account (downloads are metered)

The download endpoint charges the server owner's balance; a new account has a 0 balance, so without a recharge downloads return **402** (which the player sees as resources failing to load and falling back to vanilla). Use the `ENTITYRESKIN_RECHARGE_SECRET` you set before bootRun to simulate a payment (here crediting 1 GB):

```powershell
$u='http://localhost:8080/api/billing/recharge'
$b='{"email":"tester@qq.com","creditBytes":1073741824}'
Invoke-RestMethod $u -Method Post -ContentType application/json -Headers @{'X-Recharge-Secret'='dev-local-recharge'} -Body $b
# → {"email":"tester@qq.com","balanceBytes":1073741824}
```

The manifest should now be ready: `Invoke-RestMethod "http://localhost:8080/api/manifest/sha256?srv=<SERVER_TOKEN>"` should return `{"sha256":"..."}`. You can further take any `path` from the manifest, append it to `http://localhost:8080/`, and `curl.exe -o` to download it — expect 200 with a byte hash matching the sha (a 402 before recharging).

---

## 3. Configure and start the Paper plugin

1. Put `EntityReskin-server-0.1.0-SNAPSHOT.jar` into Paper's `plugins/`, start once to generate `plugins/EntityReskin/config.yml`, then stop the server.
2. Edit `plugins/EntityReskin/config.yml`:
   ```yaml
   backend:
     base-url: "http://localhost:8080/"   # the local backend
     server-token: "<SERVER_TOKEN>"        # from step 2.3
     poll-interval-seconds: 30
   ```
3. The backend address is set in the previous step's `config.yml` → `backend.base-url` (use `http://localhost:8080/` locally); it can also be overridden with a **Paper server JVM argument**:
   ```
   -Dentityreskin.backend.base-url=http://localhost:8080/
   ```
   (Add this `-D` after `java` and before `-jar` in the start script.)
4. Start Paper (**1.21.11**). The console should show the EntityReskin startup log. In-game, run `/entityreskin status`:
   - It should show "Resource backend… Manifest hash: <first 12>..." (meaning the plugin fetched the manifest hash from the local backend).
   - If it shows "not configured" → the token is empty or the base-url is wrong.

---

## 4. Start the Fabric client

**Development (easiest; auto-loads EntityReskin + GeckoLib + Fabric API):**
```powershell
client\gradlew.bat -p client runClient
```
This launches a 1.21.11 dev client with this mod and its dependencies installed.

**Or a real client**: a 1.21.11 Fabric client with `mods/` containing Fabric API `0.141.4+1.21.11` and `EntityReskin-client-0.1.0-SNAPSHOT.jar` (GeckoLib is bundled into that jar, no separate install needed).

Once the client is up, **Multiplayer → Direct Connect → `localhost`** (connecting to the Paper server from step 3).

---

## 5. Run the test

Run on Paper as an OP. Use an **armor stand** as the subject — it stands still, does not burn in daylight, and has no AI to wander, so it is easy to observe (an armor stand is a `LivingEntity`, and the renderer takes it over too):
```
/gamemode creative
/summon minecraft:armor_stand ~ ~ ~
/entityreskin set nearest entityreskin:cube
```
(You can also use `/entityreskin set @e[type=armor_stand,limit=1] entityreskin:cube` or an entity UUID.)

Expected: that armor stand is replaced with the downloaded cube appearance (correct texture) and **slowly rotates about the Y axis** (the idle animation); other entities remain vanilla.

---

## 6. Verification checklist

In the client log (`runClient` console / `.minecraft/logs/latest.log`), you should see, in order:
- `EntityReskin client initialising; control channel 'entityreskin:main'.`
- `handshake complete; server protocol 1, capabilities ...` (✅ confirms Bukkit payload reception works)
- `manifest source set: http://localhost:8080/...`
- `appearance assigned: ... -> entityreskin:cube`
- `appearance resources ready: entityreskin:cube` (✅ download + verify + bake succeeded)

In-game:
- ✅ The `set` armor stand renders as the cube appearance (correct texture) and rotates slowly (idle animation playing).
- ✅ Entities that were not `set` remain vanilla (selective replacement + fallback).
- ✅ In the instant between `set` and download completion, it shows vanilla first, then switches once downloaded.
- ✅ No crashes throughout, and no per-frame error spam.

On the server: `/entityreskin status` shows "handshaked clients" > 0.

---

## 7. Troubleshooting (the two most likely runtime issues)

**A. The handshake log never appears (`handshake complete` not printed)** → the client did not receive the server's plugin message. This is the recorded "Fabric may drop Bukkit-origin payloads" risk. Check:
- Confirm both Paper and the client are 1.21.11 and that the client actually connected.
- If Fabric is indeed dropping third-party payloads, add a Mixin that intercepts `ClientPacketListener`'s custom-payload handling (feeding the raw `entityreskin:main` bytes straight into `ControlChannel`).

**B. The entity does not change appearance (but handshake/download logs are normal)** → the render Mixin did not take effect. Check the client startup log for Mixin application errors:
- `Mixin apply ... failed` / `Critical injection failure: getRenderer ...` → the `getRenderer` injection point did not bind; adjust the `@Inject` method descriptor.
- `@Local` cannot find `Context` → adjust the `onResourceManagerReload` injection point (target after the `Context` constructor with `@At`, or use an explicit `@Local(ordinal=...)`).

**C. Download failure (`appearance resources failed`)** → check the reason:
- `rejected` → the SSRF same-host check: confirm the plugin base-url override and the backend's `ENTITYRESKIN_PUBLIC_BASE_URL` are both `localhost:8080` (same host).
- `hash mismatch` → an asset was changed or the manifest hash is inconsistent; re-upload / rebuild the appearance.
- `download failed` → the backend is down or the URL is unreachable.

---

## 8. Optional server-side experience checks

In `config.yml`:
```yaml
preload: ["entityreskin:cube"]            # prefetch on join
preload-failure:
  policy: "KICK"                          # kick and name the appearance on preload failure
preload-progress-bar:
  enabled: true                           # a BossBar progress bar on join
  notify-text: true                       # a "Preloading…" chat notice
```
Rejoin the server to see the progress bar and chat notice; set one asset's sha wrong to verify the KICK naming.

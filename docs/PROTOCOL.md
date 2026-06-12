# EntityReskin Control Protocol (PROTOCOL)

A version-independent client ↔ server control protocol. **Independent of the Minecraft version**: one packet format covers 1.13 through the latest release, with a zero-NMS server. Resource bytes **do not travel on this channel** — they are transferred over HTTPS only (see [MANIFEST.md](MANIFEST.md)).

The implementation lives in the `shared` module under `io.github.zzz1999.entityreskin.protocol.*` and is shared by the client and server to rule out protocol drift.

## Transport

- **Carrier**: Minecraft plugin messaging (custom payload).
- **Channel**: the namespaced channel `entityreskin:main` (1.13+ requires the namespaced form; the plugin and client register the same name).
- **Size limits** (vanilla hard limits, which the design respects): S2C ≤ 1 MB, C2S ≤ 32 KB. Every packet here is small metadata, far below the limits; **resources are never inlined**.

## Primitive encoding

| Type | Encoding |
|---|---|
| VarInt | LEB128, 7 bits + continuation bit, up to 5 bytes (any 32-bit int; negative values take 5 bytes) |
| String | VarInt length (UTF-8 byte count) + UTF-8 bytes |
| UUID | two longs (high, low), big-endian |
| int / long | fixed-length big-endian (4 / 8 bytes) |
| float | written as an int via `Float.floatToIntBits` |
| bytes | VarInt length + raw bytes |
| boolean | one byte (0/1) |

## Frame format

```
[ VarInt packetId ][ packet body ... ]
```

`PacketCodec.encode/decode` handles framing and dispatch. An unknown packetId → `ProtocolException`; the caller **degrades gracefully** (falls back to the vanilla appearance) and must not crash.

## Packet table

| id | Name | Direction | Fields (in write order) |
|---|---|---|---|
| 0x01 | HANDSHAKE_REQUEST | C→S | VarInt protocolVersion, long capabilities |
| 0x02 | HANDSHAKE_RESPONSE | S→C | VarInt protocolVersion, long serverCapabilities |
| 0x03 | SET_MANIFEST_SOURCE | S→C | String baseUrl, String manifestPath, bytes manifestSha256 (32B) |
| 0x04 | PRELOAD | S→C | VarInt count, count×(String identifier) |
| 0x10 | SET_IDENTIFIER | S→C | VarInt entityId, UUID entityUuid, String identifier, boolean hasScale, [float scaleHint] |
| 0x11 | CLEAR_IDENTIFIER | S→C | VarInt entityId, UUID entityUuid |
| 0x20 | RESOURCE_READY | C→S | String identifier |
| 0x21 | RESOURCE_ERROR | C→S | String identifier, VarInt reasonCode, String message |
| 0x30 | PLAY_ANIMATION | S→C | (reserved for phase 2, not yet implemented) |

`RESOURCE_ERROR.reasonCode`: 0=UNKNOWN, 1=UNKNOWN_IDENTIFIER, 2=DOWNLOAD_FAILED, 3=HASH_MISMATCH, 4=TOO_LARGE, 5=SECURITY_REJECTED, 6=PARSE_FAILED.

## Capabilities (long bitset)

`GEOMETRY=1<<0`, `ANIMATION=1<<1`, `ANIMATION_CONTROLLER=1<<2`, `RENDER_CONTROLLER=1<<3`, `MOLANG=1<<4`. Exchanged at handshake; both sides take the intersection to negotiate the available features.

## Handshake and a typical sequence

```
client → HANDSHAKE_REQUEST(protocolVersion, caps)
server → HANDSHAKE_RESPONSE(protocolVersion, serverCaps)
server → SET_MANIFEST_SOURCE(baseUrl, manifestPath, manifestSha256)
        client fetches and verifies the manifest over HTTPS
server → SET_IDENTIFIER(entityId, uuid, identifier[, scale])   // command / API / re-sent on join
        client looks up the manifest → checks cache → downloads + verifies if missing → renders
client → RESOURCE_READY(identifier)        // success
client → RESOURCE_ERROR(identifier, code)  // failure; that entity falls back to vanilla
server → CLEAR_IDENTIFIER(entityId, uuid)  // cancel the re-skin
```

- **Protocol version**: `ProtocolVersion.CURRENT`. On a mismatch both sides degrade rather than force a parse.
- **Players without the mod** never receive the custom payload and simply see the vanilla appearance (graceful degradation).
- **entityId**: the network entity id, used by the client for immediate lookup (`world.getEntityById`). The sentinel `-1` (`SetIdentifier.UNKNOWN_ENTITY_ID`) means the entity is not currently loaded on the server (e.g. the full sync after a handshake); the client then applies by `entityUuid` when the entity comes into view.
- **entityUuid**: the stable key. The client keeps its own `UUID → identifier` map from it, re-applying the skin automatically as the entity enters and leaves view, with no re-send from the server.
- **Full sync after handshake**: on a successful handshake the server sends the client every `SET_IDENTIFIER` in the registry, one by one (using the `-1` sentinel for unloaded entities). The client then handles entity load/unload from its own map, and the server only sends incrementally on set/clear and manifest-hash changes.
- **Preload (PRELOAD, 0x04)**: an operator can list identifiers under `preload` in the plugin's `config.yml`; after the handshake and manifest source are sent, the server sends `PRELOAD`, and the client prefetches those appearances' resources into memory, avoiding a download pause when the entity first appears. Unlisted appearances are still fetched on demand.

## Plugin ↔ resource backend

The server plugin and the resource backend (the `web` module) communicate over HTTPS, outside the Minecraft protocol.

- **Binding**: the operator registers a "server" on the website to obtain a `serverToken`, then fills it into the plugin's `config.yml`: `backend.base-url` (e.g. `http://localhost:8080/`) and `server-token`.
- **Manifest-hash polling**: at startup and periodically afterwards (5 minutes by default) the plugin requests `GET {backend.base-url}api/manifest/sha256?srv={serverToken}`, which responds `{"sha256": "<64-char lowercase hex>"}`. While the server has no appearance entries, both the manifest and hash endpoints return 404; the plugin treats this as "nothing to serve yet" and does not send `SET_MANIFEST_SOURCE`.
- **Two distinct baseUrls**: `SET_MANIFEST_SOURCE.baseUrl` is the backend address configured in the plugin (used to build the manifest download URL); the `baseUrl` field inside the manifest JSON is the prefix for resource `path`s, generated by the backend. The two may differ (e.g. resources served from a separate CDN domain). When building resource URLs the client always uses the `baseUrl` inside the manifest JSON.
- **Re-pinning**: when the hash changes (the operator updated resources, or the signed-URL rolling window flipped) the plugin re-sends `SET_MANIFEST_SOURCE` to online players, with `baseUrl = backend.base-url`, `manifestPath = "api/manifest?srv={serverToken}"`, and `manifestSha256 = the polled hash`. The backend generates the manifest deterministically per rolling half-window (see MANIFEST.md), keeping the bytes stable within a window so the hash can be pinned.
- **Token-exposure note**: the `serverToken` is delivered to players as part of the manifest URL (players need it to download). The damage from a leak is bounded by that server's own rate tier; the token can be reset at any time from the dashboard, and a reset immediately invalidates all old signed URLs.

## Reserved packet ids

`0x31 PLAY_PARTICLE` and `0x32 PLAY_SOUND` (capabilities `PARTICLE=1<<5`, `SOUND=1<<6`) are reserved for a later phase of particles and sound; the current protocol version neither implements nor sends them.

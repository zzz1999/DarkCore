package io.github.zzz1999.entityreskin.client.download;

import io.github.zzz1999.entityreskin.client.render.InMemoryAssetStore;
import io.github.zzz1999.entityreskin.protocol.manifest.Manifest;
import io.github.zzz1999.entityreskin.protocol.manifest.ManifestEntry;
import io.github.zzz1999.entityreskin.protocol.manifest.Manifests;
import io.github.zzz1999.entityreskin.protocol.manifest.ResourceFile;
import io.github.zzz1999.entityreskin.protocol.manifest.ResourceKind;
import io.github.zzz1999.entityreskin.protocol.packet.ResourceError;
import io.github.zzz1999.entityreskin.protocol.packet.SetManifestSource;
import software.bernie.geckolib.cache.model.BakedGeoModel;
import software.bernie.geckolib.loading.object.BakedAnimations;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;

/**
 * Downloads an appearance's resources over HTTPS into memory, verifies their integrity, and bakes
 * the geometry and animation with {@link InMemoryAssetStore}. Nothing is written to disk.
 *
 * <p>Security boundaries: the manifest is verified byte-for-byte against the SHA-256 pinned in the
 * {@link SetManifestSource} handshake (defeating a tampered/substituted manifest); each resource is
 * verified against the SHA-256 declared in the (now-trusted) manifest; transport is HTTPS-only
 * (loopback HTTP allowed for local development, enforced by {@link Manifests}); redirects are not
 * followed (avoids cross-host redirection); and every response is read under a hard byte cap so a
 * malicious or runaway body cannot exhaust memory.</p>
 */
public final class AssetDownloader {

    /** A prepared appearance: baked model, optional baked animations, and raw texture bytes. */
    public record Prepared(BakedGeoModel model, BakedAnimations animations, byte[] textureBytes) {
    }

    /** Carries a {@link ResourceError} reason code so the client can report a precise failure. */
    public static final class DownloadException extends RuntimeException {
        private final int reasonCode;

        public DownloadException(int reasonCode, String message) {
            super(message);
            this.reasonCode = reasonCode;
        }

        public DownloadException(int reasonCode, String message, Throwable cause) {
            super(message, cause);
            this.reasonCode = reasonCode;
        }

        public int reasonCode() {
            return reasonCode;
        }
    }

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final int SHA256_LENGTH = 32;

    private final HttpClient http;
    private final long maxResourceBytes;
    private final long maxManifestBytes;

    public AssetDownloader(long maxResourceBytes, long maxManifestBytes) {
        this.http = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.maxResourceBytes = maxResourceBytes;
        this.maxManifestBytes = maxManifestBytes;
    }

    /**
     * Fetches, verifies, and bakes the resources for one appearance {@code identifier}. Blocking;
     * call from a background thread. Throws {@link DownloadException} on any failure.
     */
    public Prepared prepare(SetManifestSource source, String identifier, String playerName) {
        if (!Manifests.isAllowedBaseUrl(source.baseUrl())) {
            throw new DownloadException(ResourceError.REASON_SECURITY_REJECTED, "manifest baseUrl is not HTTPS");
        }
        if (!Manifests.isSafeRelativePath(source.manifestPath())) {
            throw new DownloadException(ResourceError.REASON_SECURITY_REJECTED, "unsafe manifest path");
        }
        if (source.manifestSha256().length != SHA256_LENGTH) {
            throw new DownloadException(ResourceError.REASON_SECURITY_REJECTED, "pinned manifest hash is not 32 bytes");
        }
        byte[] manifestBytes = fetch(source.baseUrl() + source.manifestPath(), maxManifestBytes);
        if (!Arrays.equals(sha256(manifestBytes), source.manifestSha256())) {
            throw new DownloadException(ResourceError.REASON_HASH_MISMATCH, "manifest hash mismatch");
        }

        Manifest manifest;
        try {
            // Manifests.parse re-validates HTTPS baseUrl, safe relative paths, and hash formats.
            manifest = Manifests.parse(new String(manifestBytes, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new DownloadException(ResourceError.REASON_PARSE_FAILED, "manifest invalid: " + e.getMessage(), e);
        }
        // The resource host must match the hash-pinned handshake host: a server must not be able to
        // redirect the client's downloads at an arbitrary (e.g. internal/loopback) host via the
        // manifest's own baseUrl field.
        if (!sameHost(source.baseUrl(), manifest.getBaseUrl())) {
            throw new DownloadException(ResourceError.REASON_SECURITY_REJECTED,
                    "manifest baseUrl host differs from the handshake source");
        }
        ManifestEntry entry = manifest.getEntry(identifier);
        if (entry == null) {
            throw new DownloadException(ResourceError.REASON_UNKNOWN_IDENTIFIER, "no manifest entry: " + identifier);
        }

        byte[] geometry = fetchResource(manifest, entry, ResourceKind.GEOMETRY, playerName, true);
        byte[] texture = fetchResource(manifest, entry, ResourceKind.TEXTURE, playerName, true);
        byte[] animation = fetchResource(manifest, entry, ResourceKind.ANIMATION, playerName, false);

        try {
            BakedGeoModel model = InMemoryAssetStore.bakeGeometry(geometry);
            BakedAnimations animations = animation == null ? null : InMemoryAssetStore.bakeAnimations(animation);
            return new Prepared(model, animations, texture);
        } catch (RuntimeException e) {
            throw new DownloadException(ResourceError.REASON_PARSE_FAILED, "could not bake assets: " + e.getMessage(), e);
        }
    }

    private byte[] fetchResource(Manifest manifest, ManifestEntry entry, String kind, String playerName, boolean required) {
        ResourceFile file = entry.getResource(kind);
        if (file == null) {
            if (required) {
                throw new DownloadException(ResourceError.REASON_PARSE_FAILED, "appearance is missing its " + kind);
            }
            return null;
        }
        if (file.getSize() > maxResourceBytes) {
            throw new DownloadException(ResourceError.REASON_TOO_LARGE,
                    kind + " declares " + file.getSize() + " bytes, over the " + maxResourceBytes + " cap");
        }
        String url = manifest.getBaseUrl() + file.getPath();
        if (playerName != null && !playerName.isEmpty()) {
            // Append the player name for download statistics, choosing the correct query separator
            // rather than assuming the path already carries one.
            String separator = url.indexOf('?') >= 0 ? "&" : "?";
            url = url + separator + "player=" + URLEncoder.encode(playerName, StandardCharsets.UTF_8);
        }
        byte[] bytes = fetch(url, maxResourceBytes);
        if (!toHex(sha256(bytes)).equals(file.getSha256())) {
            throw new DownloadException(ResourceError.REASON_HASH_MISMATCH, kind + " hash mismatch");
        }
        return bytes;
    }

    private byte[] fetch(String url, long maxBytes) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "EntityReskin-Client")
                    .GET()
                    .build();
        } catch (IllegalArgumentException e) {
            throw new DownloadException(ResourceError.REASON_DOWNLOAD_FAILED, "invalid URL: " + e.getMessage(), e);
        }
        long deadlineNanos = System.nanoTime() + REQUEST_TIMEOUT.toNanos();
        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new DownloadException(ResourceError.REASON_DOWNLOAD_FAILED, "HTTP " + response.statusCode());
            }
            try (InputStream in = response.body()) {
                return readBounded(in, maxBytes, deadlineNanos);
            }
        } catch (IOException e) {
            throw new DownloadException(ResourceError.REASON_DOWNLOAD_FAILED, "download failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DownloadException(ResourceError.REASON_DOWNLOAD_FAILED, "download interrupted", e);
        }
    }

    private static byte[] readBounded(InputStream in, long maxBytes, long deadlineNanos) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            // Wall-clock bound on the body read so a slow-drip response (bytes under the size cap but
            // trickled out over a long time) cannot pin the download thread indefinitely.
            if (System.nanoTime() > deadlineNanos) {
                throw new DownloadException(ResourceError.REASON_DOWNLOAD_FAILED, "download exceeded time budget");
            }
            total += read;
            if (total > maxBytes) {
                throw new DownloadException(ResourceError.REASON_TOO_LARGE, "response exceeds " + maxBytes + " bytes");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    /** Whether two URLs target the same host (case-insensitive); false if either is unparseable. */
    private static boolean sameHost(String a, String b) {
        try {
            String hostA = URI.create(a).getHost();
            String hostB = URI.create(b).getHost();
            return hostA != null && hostA.equalsIgnoreCase(hostB);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}

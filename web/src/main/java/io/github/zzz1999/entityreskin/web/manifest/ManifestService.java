package io.github.zzz1999.entityreskin.web.manifest;

import io.github.zzz1999.entityreskin.protocol.manifest.Manifest;
import io.github.zzz1999.entityreskin.protocol.manifest.ManifestEntry;
import io.github.zzz1999.entityreskin.protocol.manifest.Manifests;
import io.github.zzz1999.entityreskin.protocol.manifest.ResourceFile;
import io.github.zzz1999.entityreskin.web.appearance.AppearanceEntry;
import io.github.zzz1999.entityreskin.web.appearance.AppearanceEntryRepository;
import io.github.zzz1999.entityreskin.web.asset.Asset;
import io.github.zzz1999.entityreskin.web.asset.AssetRepository;
import io.github.zzz1999.entityreskin.web.security.UrlSigner;
import io.github.zzz1999.entityreskin.web.server.GameServer;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates per-server manifests with signed download URLs, deterministically per rolling
 * half-window so the manifest SHA-256 pinned via {@code SET_MANIFEST_SOURCE} stays stable.
 *
 * <p>Window scheme: {@code window = epochSeconds / (ttl/2)}, {@code expiry = (window + 2) * (ttl/2)}.
 * Signed URLs are therefore valid for between half of the TTL and the full TTL, and within one
 * half-window the manifest bytes (sorted entries, no timestamps) are byte-identical. The Bukkit
 * plugin polls {@code GET /api/manifest/sha256} and re-pins on change (content edits or window
 * rollover).</p>
 */
@Service
public class ManifestService {

    public record ManifestDocument(String json, String sha256Hex) {
    }

    private record CachedManifest(long window, ManifestDocument document) {
    }

    private static final Logger log = LoggerFactory.getLogger(ManifestService.class);
    private static final Gson GSON = new Gson();

    private final AppearanceEntryRepository appearances;
    private final AssetRepository assets;
    private final UrlSigner urlSigner;
    private final Clock clock;
    private final String publicBaseUrl;
    private final long halfWindowSeconds;
    private final Map<Long, CachedManifest> cache = new ConcurrentHashMap<>();

    public ManifestService(AppearanceEntryRepository appearances,
                           AssetRepository assets,
                           UrlSigner urlSigner,
                           Clock clock,
                           @Value("${entityreskin.public-base-url}") String publicBaseUrl,
                           @Value("${entityreskin.download.url-ttl-minutes}") long urlTtlMinutes) {
        if (urlTtlMinutes < 2) {
            throw new IllegalStateException("entityreskin.download.url-ttl-minutes must be at least 2");
        }
        this.appearances = appearances;
        this.assets = assets;
        this.urlSigner = urlSigner;
        this.clock = clock;
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl : publicBaseUrl + "/";
        this.halfWindowSeconds = urlTtlMinutes * 60 / 2;
    }

    /** Returns the current manifest for the server, cached per rolling half-window. */
    public ManifestDocument document(GameServer server) {
        long window = clock.instant().getEpochSecond() / halfWindowSeconds;
        CachedManifest cached = cache.get(server.getId());
        if (cached != null && cached.window() == window) {
            return cached.document();
        }
        ManifestDocument document = generate(server, window);
        cache.put(server.getId(), new CachedManifest(window, document));
        return document;
    }

    /** Drops the cached manifest after appearance edits or a token reset. */
    public void invalidate(long serverId) {
        cache.remove(serverId);
    }

    private ManifestDocument generate(GameServer server, long window) {
        long expiresAt = (window + 2) * halfWindowSeconds;
        TreeMap<String, ManifestEntry> entries = new TreeMap<>();
        for (AppearanceEntry appearance : appearances.findByGameServerId(server.getId())) {
            TreeMap<String, ResourceFile> resources = new TreeMap<>();
            boolean complete = true;
            for (Map.Entry<String, String> resource : new TreeMap<>(appearance.getResources()).entrySet()) {
                String sha256 = resource.getValue();
                Asset asset = assets.findById(sha256).orElse(null);
                if (asset == null) {
                    log.warn("appearance {} on server {} references missing asset {}; entry skipped",
                            appearance.getIdentifier(), server.getId(), sha256);
                    complete = false;
                    break;
                }
                String signature = urlSigner.sign(sha256, expiresAt, server.getToken());
                String path = "download/" + sha256
                        + "?exp=" + expiresAt
                        + "&srv=" + server.getToken()
                        + "&sig=" + signature;
                resources.put(resource.getKey(), new ResourceFile(path, sha256, asset.getSize()));
            }
            if (complete) {
                entries.put(appearance.getIdentifier(), new ManifestEntry(
                        appearance.getDisplayName(),
                        appearance.getGeometryName(),
                        appearance.getDefaultAnimation(),
                        appearance.getRenderControllerEntry(),
                        resources));
            }
        }
        if (entries.isEmpty()) {
            // Shared validation (Manifests.parse) rejects manifests without entries, so serving
            // one would break the client; the plugin treats 404 as "nothing to announce yet".
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "server has no appearance entries");
        }
        String json = GSON.toJson(new Manifest(Manifests.SUPPORTED_FORMAT_VERSION, publicBaseUrl, entries));
        return new ManifestDocument(json, sha256Hex(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

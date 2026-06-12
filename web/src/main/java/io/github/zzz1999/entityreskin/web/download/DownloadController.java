package io.github.zzz1999.entityreskin.web.download;

import io.github.zzz1999.entityreskin.web.billing.BillingService;
import io.github.zzz1999.entityreskin.web.security.UrlSigner;
import io.github.zzz1999.entityreskin.web.server.GameServer;
import io.github.zzz1999.entityreskin.web.server.GameServerRepository;
import io.github.zzz1999.entityreskin.web.stats.DownloadStatsRegistry;
import io.github.zzz1999.entityreskin.web.stats.IpHasher;
import io.github.zzz1999.entityreskin.web.storage.BlobStorage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.regex.Pattern;

/**
 * Streams content-addressed blobs to clients holding a valid signed URL. Validation order:
 * server token, expiry, signature, then blob existence — so unauthenticated probes learn nothing
 * about which blobs exist. The server owner's balance is charged before streaming (402 when
 * exhausted). Bytes are served as an opaque attachment (never inline) and throttled through the
 * server's token bucket. Each download is recorded for the owner's live statistics; the player
 * name is self-reported by the client and the IP is reduced to a salted hash prefix.
 */
@RestController
public class DownloadController {

    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-f]{64}$");
    private static final int CHUNK_BYTES = 64 * 1024;
    private static final int MAX_PLAYER_NAME = 32;

    private final GameServerRepository servers;
    private final UrlSigner urlSigner;
    private final BlobStorage storage;
    private final TokenBucketRegistry buckets;
    private final BillingService billingService;
    private final DownloadStatsRegistry statsRegistry;
    private final IpHasher ipHasher;
    private final Clock clock;

    public DownloadController(GameServerRepository servers, UrlSigner urlSigner, BlobStorage storage,
                              TokenBucketRegistry buckets, BillingService billingService,
                              DownloadStatsRegistry statsRegistry, IpHasher ipHasher, Clock clock) {
        this.servers = servers;
        this.urlSigner = urlSigner;
        this.storage = storage;
        this.buckets = buckets;
        this.billingService = billingService;
        this.statsRegistry = statsRegistry;
        this.ipHasher = ipHasher;
        this.clock = clock;
    }

    @GetMapping("/download/{sha256}")
    public ResponseEntity<StreamingResponseBody> download(
            @PathVariable String sha256,
            @RequestParam("exp") long expiresAtEpochSeconds,
            @RequestParam("srv") String serverToken,
            @RequestParam("sig") String signature,
            @RequestParam(value = "player", required = false) String playerName,
            HttpServletRequest httpRequest) {
        if (!SHA256_PATTERN.matcher(sha256).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid blob hash");
        }
        GameServer server = servers.findByToken(serverToken).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unknown server token"));
        if (clock.instant().getEpochSecond() > expiresAtEpochSeconds) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "download URL expired");
        }
        if (!urlSigner.verify(sha256, expiresAtEpochSeconds, serverToken, signature)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "invalid signature");
        }
        Path blob = storage.locate(sha256).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such blob"));
        long size;
        try {
            size = Files.size(blob);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "blob unreadable");
        }
        // Bill the traffic to the server owner before streaming; deny when the balance is gone.
        if (!billingService.chargeTraffic(server.getOwnerEmail(), size)) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "server owner balance exhausted");
        }
        statsRegistry.record(server.getId(), size, sanitizePlayerName(playerName),
                ipHasher.hashPrefix(clientIp(httpRequest)));

        TokenBucket bucket = buckets.bucketFor(server);
        StreamingResponseBody body = output -> {
            try (InputStream input = Files.newInputStream(blob)) {
                byte[] buffer = new byte[CHUNK_BYTES];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    try {
                        bucket.consumeBlocking(read);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("download interrupted", e);
                    }
                    output.write(buffer, 0, read);
                }
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(size)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + sha256 + "\"")
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma >= 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    private static String sanitizePlayerName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim().replaceAll("\\p{Cntrl}", "");
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > MAX_PLAYER_NAME ? trimmed.substring(0, MAX_PLAYER_NAME) : trimmed;
    }
}

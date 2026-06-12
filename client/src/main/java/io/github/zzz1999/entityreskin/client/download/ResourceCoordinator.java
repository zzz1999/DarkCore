package io.github.zzz1999.entityreskin.client.download;

import io.github.zzz1999.entityreskin.client.net.ControlChannel;
import io.github.zzz1999.entityreskin.client.session.ClientSession;
import io.github.zzz1999.entityreskin.protocol.packet.ResourceError;
import io.github.zzz1999.entityreskin.protocol.packet.ResourceReady;
import io.github.zzz1999.entityreskin.protocol.packet.SetManifestSource;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Drives resource preparation: when the server assigns or preloads an appearance, this fetches and
 * bakes its resources off-thread (one virtual thread per request), then applies the result on the
 * client thread and acknowledges the server. In-flight identifiers are de-duplicated, and an
 * appearance already present in the in-memory store is skipped. All state lives in the
 * {@link ClientSession} and is dropped on disconnect.
 */
public final class ResourceCoordinator {

    /** Per-resource and per-manifest byte caps, defending against oversized or runaway downloads. */
    private static final long MAX_RESOURCE_BYTES = 16L * 1024 * 1024;
    private static final long MAX_MANIFEST_BYTES = 2L * 1024 * 1024;

    private static final Logger LOGGER = LoggerFactory.getLogger("EntityReskin");

    private final ClientSession session;
    private final ControlChannel channel;
    private final AssetDownloader downloader = new AssetDownloader(MAX_RESOURCE_BYTES, MAX_MANIFEST_BYTES);
    private final Executor backgroundExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    /** Bumped on every disconnect; a download whose epoch no longer matches is dropped. */
    private final AtomicInteger generation = new AtomicInteger();

    public ResourceCoordinator(ClientSession session, ControlChannel channel) {
        this.session = session;
        this.channel = channel;
    }

    /**
     * Ensures the resources for {@code identifier} are downloaded and baked into the session store.
     * Idempotent and non-blocking: returns immediately if the appearance is already present or a
     * download is already in flight, otherwise schedules one.
     */
    public void ensure(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return;
        }
        SetManifestSource source = session.manifestSource();
        if (source == null) {
            return;
        }
        if (session.assets().contains(identifier) || !inFlight.add(identifier)) {
            return;
        }
        String playerName = currentPlayerName();
        int epoch = generation.get();
        backgroundExecutor.execute(() -> download(source, identifier, playerName, epoch));
    }

    /**
     * Invalidates in-flight downloads from the previous connection and clears the de-duplication
     * set. Called on disconnect so a late-completing download neither writes into the next session
     * nor acknowledges on a dead/other connection, and so a reconnect can re-drive every request.
     */
    public void reset() {
        generation.incrementAndGet();
        inFlight.clear();
    }

    private void download(SetManifestSource source, String identifier, String playerName, int epoch) {
        try {
            AssetDownloader.Prepared prepared = downloader.prepare(source, identifier, playerName);
            apply(identifier, epoch, () -> {
                session.assets().put(identifier, prepared.model(), prepared.animations(), prepared.textureBytes());
                channel.send(new ResourceReady(identifier));
                LOGGER.info("appearance resources ready: {}", identifier);
            });
        } catch (AssetDownloader.DownloadException e) {
            LOGGER.warn("appearance resources failed ({}): {} - {}", e.reasonCode(), identifier, e.getMessage());
            apply(identifier, epoch, () -> channel.send(new ResourceError(identifier, e.reasonCode(), reasonText(e.reasonCode()))));
        } catch (Throwable t) {
            LOGGER.warn("appearance resources failed unexpectedly: {}", identifier, t);
            apply(identifier, epoch, () -> channel.send(new ResourceError(identifier, ResourceError.REASON_UNKNOWN, "error")));
        }
    }

    /**
     * Runs the completion {@code action} on the client thread, but only if the connection has not
     * changed since the download was requested (epoch check). The de-duplication entry is always
     * cleared afterward, so a send that throws cannot wedge the identifier.
     */
    private void apply(String identifier, int epoch, Runnable action) {
        Minecraft.getInstance().execute(() -> {
            if (epoch != generation.get()) {
                return;
            }
            try {
                action.run();
            } finally {
                inFlight.remove(identifier);
            }
        });
    }

    private static String currentPlayerName() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getUser() != null ? minecraft.getUser().getName() : null;
    }

    /**
     * A fixed, non-sensitive message per reason code. The detailed cause is logged locally only and
     * never forwarded to the (untrusted) server, which would otherwise leak request URLs, internal
     * hostnames, and exception text (an SSRF/error oracle).
     */
    private static String reasonText(int reasonCode) {
        return switch (reasonCode) {
            case ResourceError.REASON_UNKNOWN_IDENTIFIER -> "unknown identifier";
            case ResourceError.REASON_DOWNLOAD_FAILED -> "download failed";
            case ResourceError.REASON_HASH_MISMATCH -> "hash mismatch";
            case ResourceError.REASON_TOO_LARGE -> "too large";
            case ResourceError.REASON_SECURITY_REJECTED -> "rejected";
            case ResourceError.REASON_PARSE_FAILED -> "parse failed";
            default -> "error";
        };
    }
}

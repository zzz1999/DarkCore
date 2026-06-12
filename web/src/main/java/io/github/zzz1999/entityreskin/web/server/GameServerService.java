package io.github.zzz1999.entityreskin.web.server;

import io.github.zzz1999.entityreskin.web.appearance.AppearanceEntryRepository;
import io.github.zzz1999.entityreskin.web.download.TokenBucketRegistry;
import io.github.zzz1999.entityreskin.web.manifest.ManifestService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

/**
 * Server registration and token lifecycle. Ownership is always checked against the JWT
 * principal; foreign or unknown server ids yield 404 so that ids of other accounts' servers
 * are not confirmable.
 */
@Service
public class GameServerService {

    private static final int TOKEN_BYTES = 32;

    private final GameServerRepository servers;
    private final AppearanceEntryRepository appearances;
    private final ManifestService manifestService;
    private final TokenBucketRegistry bucketRegistry;
    private final long defaultBytesPerSecond;
    private final SecureRandom random = new SecureRandom();

    public GameServerService(GameServerRepository servers,
                             AppearanceEntryRepository appearances,
                             ManifestService manifestService,
                             TokenBucketRegistry bucketRegistry,
                             @Value("${entityreskin.download.default-bytes-per-second}") long defaultBytesPerSecond) {
        this.servers = servers;
        this.appearances = appearances;
        this.manifestService = manifestService;
        this.bucketRegistry = bucketRegistry;
        this.defaultBytesPerSecond = defaultBytesPerSecond;
    }

    public GameServer create(String ownerEmail, String name) {
        return servers.save(new GameServer(name, randomToken(), ownerEmail, defaultBytesPerSecond));
    }

    public List<GameServer> listOwned(String ownerEmail) {
        return servers.findByOwnerEmailOrderByCreatedAtAsc(ownerEmail);
    }

    /** Returns the server if it exists and belongs to the given account; otherwise 404. */
    public GameServer requireOwned(String ownerEmail, long serverId) {
        return servers.findById(serverId)
                .filter(s -> s.getOwnerEmail().equals(ownerEmail))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such server"));
    }

    /** Replaces the token, revoking all outstanding signed URLs and the cached manifest. */
    @Transactional
    public GameServer resetToken(String ownerEmail, long serverId) {
        GameServer server = requireOwned(ownerEmail, serverId);
        server.setToken(randomToken());
        servers.save(server);
        manifestService.invalidate(serverId);
        return server;
    }

    @Transactional
    public void delete(String ownerEmail, long serverId) {
        GameServer server = requireOwned(ownerEmail, serverId);
        appearances.deleteByGameServerId(serverId);
        servers.delete(server);
        manifestService.invalidate(serverId);
        bucketRegistry.remove(serverId);
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}

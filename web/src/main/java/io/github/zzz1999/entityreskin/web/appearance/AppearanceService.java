package io.github.zzz1999.entityreskin.web.appearance;

import io.github.zzz1999.entityreskin.protocol.Identifiers;
import io.github.zzz1999.entityreskin.protocol.manifest.ResourceKind;
import io.github.zzz1999.entityreskin.web.asset.Asset;
import io.github.zzz1999.entityreskin.web.asset.AssetRepository;
import io.github.zzz1999.entityreskin.web.manifest.ManifestService;
import io.github.zzz1999.entityreskin.web.server.GameServer;
import io.github.zzz1999.entityreskin.web.server.GameServerService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Validates and persists appearance entries. Every referenced asset must already exist with a
 * matching kind, so a manifest can never advertise a blob the backend does not hold.
 */
@Service
public class AppearanceService {

    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    private static final Set<String> REQUIRED_KINDS = Set.of(
            ResourceKind.GEOMETRY, ResourceKind.ANIMATION, ResourceKind.TEXTURE);
    private static final Set<String> ALLOWED_KINDS = Set.of(
            ResourceKind.GEOMETRY,
            ResourceKind.ANIMATION,
            ResourceKind.TEXTURE,
            ResourceKind.ANIMATION_CONTROLLERS,
            ResourceKind.RENDER_CONTROLLERS,
            ResourceKind.PARTICLE,
            ResourceKind.SOUND);

    private final AppearanceEntryRepository appearances;
    private final AssetRepository assets;
    private final GameServerService gameServerService;
    private final ManifestService manifestService;

    public AppearanceService(AppearanceEntryRepository appearances, AssetRepository assets,
                             GameServerService gameServerService, ManifestService manifestService) {
        this.appearances = appearances;
        this.assets = assets;
        this.gameServerService = gameServerService;
        this.manifestService = manifestService;
    }

    @Transactional
    public AppearanceEntry createOrReplace(String ownerEmail, long serverId, AppearanceDefinition definition) {
        GameServer server = gameServerService.requireOwned(ownerEmail, serverId);
        validate(ownerEmail, definition);
        Map<String, String> resources = new TreeMap<>(definition.resources());

        AppearanceEntry entry = appearances
                .findByGameServerIdAndIdentifier(serverId, definition.identifier())
                .orElse(null);
        if (entry == null) {
            entry = new AppearanceEntry(server, definition.identifier(), definition.displayName(),
                    definition.geometryName(), definition.defaultAnimation(),
                    definition.renderControllerEntry(), resources);
        } else {
            entry.setDisplayName(definition.displayName());
            entry.setGeometryName(definition.geometryName());
            entry.setDefaultAnimation(definition.defaultAnimation());
            entry.setRenderControllerEntry(definition.renderControllerEntry());
            entry.setResources(resources);
        }
        AppearanceEntry saved = appearances.save(entry);
        manifestService.invalidate(serverId);
        return saved;
    }

    public List<AppearanceEntry> list(String ownerEmail, long serverId) {
        gameServerService.requireOwned(ownerEmail, serverId);
        return appearances.findByGameServerId(serverId);
    }

    @Transactional
    public void delete(String ownerEmail, long serverId, long appearanceId) {
        gameServerService.requireOwned(ownerEmail, serverId);
        AppearanceEntry entry = appearances.findById(appearanceId)
                .filter(e -> Objects.equals(e.getGameServer().getId(), serverId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such appearance entry"));
        appearances.delete(entry);
        manifestService.invalidate(serverId);
    }

    private void validate(String ownerEmail, AppearanceDefinition definition) {
        if (!Identifiers.isValid(definition.identifier())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "identifier must match namespace:name using lowercase letters, digits, '_', '.', '-', '/'");
        }
        Map<String, String> resources = definition.resources();
        for (String required : REQUIRED_KINDS) {
            if (!resources.containsKey(required)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing required resource: " + required);
            }
        }
        for (Map.Entry<String, String> resource : resources.entrySet()) {
            String kind = resource.getKey();
            String sha256 = resource.getValue();
            if (!ALLOWED_KINDS.contains(kind)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid resource kind: " + kind);
            }
            if (sha256 == null || !SHA256_PATTERN.matcher(sha256).matches()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "resource '" + kind + "' must reference a lowercase-hex SHA-256");
            }
            Asset asset = assets.findById(sha256).orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "resource '" + kind + "' references an unknown asset"));
            if (!asset.getKind().equals(kind)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "resource '" + kind + "' references an asset uploaded as kind '" + asset.getKind() + "'");
            }
            // Ownership boundary: an account may only publish assets it uploaded itself.
            // Content-addressed deduplication keeps a single owner per blob, so identical
            // bytes uploaded by another account remain attributed to the first uploader.
            if (!asset.getOwnerEmail().equals(ownerEmail)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "resource '" + kind + "' references an asset owned by another account");
            }
        }
    }
}

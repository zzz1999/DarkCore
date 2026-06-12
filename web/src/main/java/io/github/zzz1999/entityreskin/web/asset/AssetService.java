package io.github.zzz1999.entityreskin.web.asset;

import io.github.zzz1999.entityreskin.protocol.manifest.ResourceKind;
import io.github.zzz1999.entityreskin.web.storage.BlobStorage;
import io.github.zzz1999.entityreskin.web.storage.StoredBlob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles asset uploads. SECURITY: the backend treats every upload as opaque bytes — it hashes
 * and stores them and never parses their contents (so a malicious model/animation/MoLang file
 * cannot exploit a backend parser). The accepted {@code kind} is validated against the shared
 * {@link ResourceKind} allowlist.
 */
@Service
public class AssetService {

    private static final Set<String> ALLOWED_KINDS = Set.of(
            ResourceKind.GEOMETRY,
            ResourceKind.ANIMATION,
            ResourceKind.TEXTURE,
            ResourceKind.ANIMATION_CONTROLLERS,
            ResourceKind.RENDER_CONTROLLERS,
            ResourceKind.PARTICLE,
            ResourceKind.SOUND);

    private final BlobStorage storage;
    private final AssetRepository assets;
    private final long userQuotaBytes;

    /**
     * Serializes the quota check and the subsequent store per account, so concurrent uploads
     * cannot jointly exceed the quota. Adequate for a single-instance deployment; a database
     * level reservation would replace it when the backend scales horizontally.
     */
    private final ConcurrentHashMap<String, Object> uploadLocks = new ConcurrentHashMap<>();

    public AssetService(BlobStorage storage, AssetRepository assets,
                        @Value("${entityreskin.upload.user-quota-bytes}") long userQuotaBytes) {
        this.storage = storage;
        this.assets = assets;
        this.userQuotaBytes = userQuotaBytes;
    }

    public Asset upload(String ownerEmail, String kind, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty file");
        }
        if (!ALLOWED_KINDS.contains(kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid kind: " + kind);
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to read upload");
        }
        Object lock = uploadLocks.computeIfAbsent(ownerEmail, key -> new Object());
        synchronized (lock) {
            if (assets.totalSizeByOwner(ownerEmail) + data.length > userQuotaBytes) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "storage quota exceeded");
            }
            StoredBlob blob;
            try {
                blob = storage.store(data);
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to store blob");
            }
            return assets.findById(blob.sha256()).orElseGet(() ->
                    assets.save(new Asset(
                            blob.sha256(),
                            blob.size(),
                            kind,
                            file.getContentType(),
                            ownerEmail,
                            sanitizeFilename(file.getOriginalFilename()))));
        }
    }

    /** Strips any client-supplied path; the result is for display only (never used as a path). */
    static String sanitizeFilename(String name) {
        if (name == null) {
            return null;
        }
        String base = name.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        if (base.length() > 120) {
            base = base.substring(0, 120);
        }
        return base.isBlank() ? null : base;
    }
}

package io.github.zzz1999.entityreskin.web.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Content-addressed blob store. Files are addressed by the SHA-256 the backend computes itself,
 * never by any client-supplied name (no path traversal). The backend treats blobs as opaque
 * bytes and never parses their contents.
 */
public interface BlobStorage {

    /** Stores bytes addressed by their SHA-256; idempotent (re-storing identical bytes is a no-op). */
    StoredBlob store(byte[] data) throws IOException;

    /** Whether a blob with the given lowercase-hex SHA-256 exists. */
    boolean exists(String sha256);

    /**
     * Locates a stored blob for streaming, if present. Filesystem-specific; this returns a
     * {@link Path} for the local MVP backend and would be revisited for object storage.
     */
    Optional<Path> locate(String sha256);
}

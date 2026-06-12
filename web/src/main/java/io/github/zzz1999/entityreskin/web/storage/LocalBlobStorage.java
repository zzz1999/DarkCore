package io.github.zzz1999.entityreskin.web.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/**
 * Local filesystem content-addressed storage: {@code <root>/<first-2-hex>/<full-sha256>}. The
 * path is derived solely from the hash we compute, so an upload can never escape the root or
 * collide with a traversal payload. Storage is the chosen MVP backend; the {@link BlobStorage}
 * abstraction lets this be swapped for object storage (OSS/COS/S3) later.
 */
@Component
public class LocalBlobStorage implements BlobStorage {

    private final Path root;

    public LocalBlobStorage(@Value("${entityreskin.storage.root}") String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
    }

    @Override
    public StoredBlob store(byte[] data) throws IOException {
        String sha = sha256Hex(data);
        Path target = pathFor(sha);
        if (Files.exists(target)) {
            return new StoredBlob(sha, data.length, true);
        }
        Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(target.getParent(), "upload-", ".tmp");
        try {
            Files.write(tmp, data);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (FileAlreadyExistsException | AtomicMoveNotSupportedException e) {
                // A concurrent writer created the target, or the filesystem lacks atomic move;
                // the content is identical (same hash), so replacing it is safe.
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
        return new StoredBlob(sha, data.length, false);
    }

    @Override
    public boolean exists(String sha256) {
        return Files.exists(pathFor(sha256));
    }

    @Override
    public Optional<Path> locate(String sha256) {
        Path path = pathFor(sha256);
        return Files.exists(path) ? Optional.of(path) : Optional.empty();
    }

    private Path pathFor(String sha256) {
        // sha256 is lowercase hex computed by us; safe to use as a path segment.
        return root.resolve(sha256.substring(0, 2)).resolve(sha256);
    }

    static String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

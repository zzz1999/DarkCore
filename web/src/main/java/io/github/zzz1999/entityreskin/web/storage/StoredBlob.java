package io.github.zzz1999.entityreskin.web.storage;

/** Result of storing a blob: its content hash, byte size, and whether it already existed. */
public record StoredBlob(String sha256, long size, boolean alreadyExisted) {
}

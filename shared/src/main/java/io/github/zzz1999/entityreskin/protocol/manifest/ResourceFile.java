package io.github.zzz1999.entityreskin.protocol.manifest;

/**
 * One downloadable resource: a content-addressed CDN path (relative to the manifest baseUrl),
 * its expected lowercase-hex SHA-256, and its byte size (so the client can reject oversized
 * downloads before fetching). Fields are populated by Gson from the manifest JSON.
 */
public final class ResourceFile {

    private String path;
    private String sha256;
    private long size;

    public ResourceFile() {
    }

    public ResourceFile(String path, String sha256, long size) {
        this.path = path;
        this.sha256 = sha256;
        this.size = size;
    }

    public String getPath() {
        return path;
    }

    public String getSha256() {
        return sha256;
    }

    public long getSize() {
        return size;
    }
}

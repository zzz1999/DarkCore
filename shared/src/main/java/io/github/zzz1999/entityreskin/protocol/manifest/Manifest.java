package io.github.zzz1999.entityreskin.protocol.manifest;

import java.util.Map;

/**
 * The resource manifest: maps appearance identifiers to their resource definitions. Fetched by
 * the client over HTTPS from {@code baseUrl + manifestPath} and verified against the SHA-256
 * pinned in {@link io.github.zzz1999.entityreskin.protocol.packet.SetManifestSource}. Populated by Gson; use
 * {@link Manifests#parse(String)} to parse + validate.
 */
public final class Manifest {

    private int formatVersion;
    private String baseUrl;
    private Map<String, ManifestEntry> entries;

    public Manifest() {
    }

    public Manifest(int formatVersion, String baseUrl, Map<String, ManifestEntry> entries) {
        this.formatVersion = formatVersion;
        this.baseUrl = baseUrl;
        this.entries = entries;
    }

    public int getFormatVersion() {
        return formatVersion;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public Map<String, ManifestEntry> getEntries() {
        return entries;
    }

    /** Returns the entry for the given identifier, or {@code null} if absent. */
    public ManifestEntry getEntry(String identifier) {
        return entries == null ? null : entries.get(identifier);
    }
}

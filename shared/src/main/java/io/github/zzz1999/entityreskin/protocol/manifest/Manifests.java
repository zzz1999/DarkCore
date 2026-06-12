package io.github.zzz1999.entityreskin.protocol.manifest;

import io.github.zzz1999.entityreskin.protocol.ProtocolException;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.Map;

/**
 * Parsing and validation for {@link Manifest}. Validation is a security boundary: it rejects
 * malformed manifests, bad hashes, and unsafe (path-traversal / absolute) resource paths before
 * any download happens.
 */
public final class Manifests {

    public static final int SUPPORTED_FORMAT_VERSION = 1;

    private static final int SHA256_HEX_LENGTH = 64;
    private static final Gson GSON = new Gson();

    private Manifests() {
    }

    /** Parses and validates a manifest from JSON, or throws {@link ProtocolException}. */
    public static Manifest parse(String json) {
        Manifest manifest;
        try {
            manifest = GSON.fromJson(json, Manifest.class);
        } catch (JsonSyntaxException e) {
            throw new ProtocolException("invalid manifest JSON", e);
        }
        validate(manifest);
        return manifest;
    }

    public static void validate(Manifest manifest) {
        if (manifest == null) {
            throw new ProtocolException("manifest is null/empty");
        }
        if (manifest.getFormatVersion() != SUPPORTED_FORMAT_VERSION) {
            throw new ProtocolException("unsupported manifest formatVersion: " + manifest.getFormatVersion());
        }
        if (!isAllowedBaseUrl(manifest.getBaseUrl())) {
            throw new ProtocolException("baseUrl must use HTTPS (HTTP is permitted for loopback only): "
                    + manifest.getBaseUrl());
        }
        Map<String, ManifestEntry> entries = manifest.getEntries();
        if (entries == null || entries.isEmpty()) {
            throw new ProtocolException("manifest has no entries");
        }
        for (Map.Entry<String, ManifestEntry> e : entries.entrySet()) {
            String identifier = e.getKey();
            ManifestEntry entry = e.getValue();
            if (entry == null) {
                throw new ProtocolException("null entry for identifier: " + identifier);
            }
            Map<String, ResourceFile> resources = entry.getResources();
            if (resources == null || resources.isEmpty()) {
                throw new ProtocolException("entry has no resources: " + identifier);
            }
            for (Map.Entry<String, ResourceFile> r : resources.entrySet()) {
                String kind = r.getKey();
                ResourceFile file = r.getValue();
                if (file == null) {
                    throw new ProtocolException("null resource '" + kind + "' in " + identifier);
                }
                validateSha256(file.getSha256(), identifier, kind);
                if (file.getSize() < 0) {
                    throw new ProtocolException("negative size in " + identifier + "/" + kind);
                }
                if (!isSafeRelativePath(file.getPath())) {
                    throw new ProtocolException("unsafe resource path in " + identifier + "/" + kind
                            + ": " + file.getPath());
                }
            }
        }
    }

    /**
     * Resource downloads must not be downgradable to plaintext HTTP; loopback addresses are
     * exempt so local development backends remain usable.
     */
    public static boolean isAllowedBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return false;
        }
        String lower = baseUrl.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("https://")
                || lower.startsWith("http://localhost:")
                || lower.startsWith("http://localhost/")
                || lower.startsWith("http://127.0.0.1:")
                || lower.startsWith("http://127.0.0.1/");
    }

    /**
     * A safe relative path: non-empty, no backslashes, not absolute, no drive/scheme colon, and
     * no {@code ..} segment. Defense in depth for URL construction against traversal or
     * scheme-injection through manifest paths.
     */
    public static boolean isSafeRelativePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        if (path.indexOf('\\') >= 0) {
            return false;
        }
        if (path.startsWith("/")) {
            return false;
        }
        if (path.indexOf(':') >= 0) {
            return false;
        }
        String[] parts = path.split("/");
        for (String part : parts) {
            if ("..".equals(part)) {
                return false;
            }
        }
        return true;
    }

    private static void validateSha256(String sha, String identifier, String kind) {
        if (sha == null || sha.length() != SHA256_HEX_LENGTH) {
            throw new ProtocolException("invalid sha256 length in " + identifier + "/" + kind);
        }
        for (int i = 0; i < sha.length(); i++) {
            char c = sha.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                throw new ProtocolException("sha256 must be lowercase hex in " + identifier + "/" + kind);
            }
        }
    }
}

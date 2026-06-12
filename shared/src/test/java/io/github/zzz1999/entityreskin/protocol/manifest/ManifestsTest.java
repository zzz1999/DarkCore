package io.github.zzz1999.entityreskin.protocol.manifest;

import io.github.zzz1999.entityreskin.protocol.ProtocolException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestsTest {

    /** A syntactically valid 64-char lowercase-hex SHA-256. */
    private static final String VALID_HASH =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private static String validManifestJson() {
        return "{"
                + "\"formatVersion\":1,"
                + "\"baseUrl\":\"https://cdn.example.com/entityreskin/\","
                + "\"entries\":{"
                + "\"entityreskin:dragon_red\":{"
                + "\"displayName\":\"红龙\","
                + "\"geometryName\":\"geometry.dragon_red\","
                + "\"defaultAnimation\":\"animation.dragon_red.idle\","
                + "\"resources\":{"
                + "\"geometry\":{\"path\":\"blobs/ab/" + VALID_HASH + ".geo\",\"sha256\":\"" + VALID_HASH + "\",\"size\":184320},"
                + "\"animation\":{\"path\":\"blobs/cd/" + VALID_HASH + ".anim\",\"sha256\":\"" + VALID_HASH + "\",\"size\":65536},"
                + "\"texture\":{\"path\":\"blobs/ef/" + VALID_HASH + ".png\",\"sha256\":\"" + VALID_HASH + "\",\"size\":262144}"
                + "}"
                + "}"
                + "}"
                + "}";
    }

    @Test
    void parsesValidManifest() {
        Manifest m = Manifests.parse(validManifestJson());
        assertEquals(1, m.getFormatVersion());
        assertEquals("https://cdn.example.com/entityreskin/", m.getBaseUrl());
        ManifestEntry entry = m.getEntry("entityreskin:dragon_red");
        assertNotNull(entry);
        assertEquals("红龙", entry.getDisplayName());
        assertEquals("geometry.dragon_red", entry.getGeometryName());
        assertEquals("animation.dragon_red.idle", entry.getDefaultAnimation());
        ResourceFile geo = entry.getResource(ResourceKind.GEOMETRY);
        assertNotNull(geo);
        assertEquals(VALID_HASH, geo.getSha256());
        assertEquals(184320L, geo.getSize());
    }

    @Test
    void rejectsUnsafePath() {
        String json = validManifestJson().replace("blobs/ab/", "../../../etc/");
        assertThrows(ProtocolException.class, () -> Manifests.parse(json));
    }

    @Test
    void rejectsPlainHttpBaseUrl() {
        String json = validManifestJson().replace("https://cdn.example.com", "http://cdn.example.com");
        assertThrows(ProtocolException.class, () -> Manifests.parse(json));
    }

    @Test
    void permitsLoopbackHttpBaseUrlForDevelopment() {
        String json = validManifestJson()
                .replace("https://cdn.example.com/entityreskin/", "http://localhost:8080/");
        assertEquals("http://localhost:8080/", Manifests.parse(json).getBaseUrl());
    }

    @Test
    void rejectsWrongFormatVersion() {
        String json = validManifestJson().replaceFirst("\"formatVersion\":1", "\"formatVersion\":2");
        assertThrows(ProtocolException.class, () -> Manifests.parse(json));
    }

    @Test
    void rejectsBadHash() {
        String json = validManifestJson()
                .replaceFirst("\"sha256\":\"" + VALID_HASH + "\"", "\"sha256\":\"deadbeef\"");
        assertThrows(ProtocolException.class, () -> Manifests.parse(json));
    }

    @Test
    void rejectsInvalidJson() {
        assertThrows(ProtocolException.class, () -> Manifests.parse("{ not valid json "));
    }

    @Test
    void safeRelativePathChecks() {
        assertTrue(Manifests.isSafeRelativePath("blobs/ab/file.geo"));
        assertFalse(Manifests.isSafeRelativePath("../secret"));
        assertFalse(Manifests.isSafeRelativePath("a/../b"));
        assertFalse(Manifests.isSafeRelativePath("/etc/passwd"));
        assertFalse(Manifests.isSafeRelativePath("C:\\windows"));
        assertFalse(Manifests.isSafeRelativePath("a\\b"));
        assertFalse(Manifests.isSafeRelativePath(""));
        assertFalse(Manifests.isSafeRelativePath(null));
        assertFalse(Manifests.isSafeRelativePath("http://evil.example/x"));
    }
}

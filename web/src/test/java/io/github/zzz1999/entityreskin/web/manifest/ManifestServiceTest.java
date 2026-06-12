package io.github.zzz1999.entityreskin.web.manifest;

import io.github.zzz1999.entityreskin.protocol.manifest.Manifest;
import io.github.zzz1999.entityreskin.protocol.manifest.ManifestEntry;
import io.github.zzz1999.entityreskin.protocol.manifest.Manifests;
import io.github.zzz1999.entityreskin.protocol.manifest.ResourceFile;
import io.github.zzz1999.entityreskin.protocol.manifest.ResourceKind;
import io.github.zzz1999.entityreskin.web.appearance.AppearanceEntry;
import io.github.zzz1999.entityreskin.web.appearance.AppearanceEntryRepository;
import io.github.zzz1999.entityreskin.web.asset.Asset;
import io.github.zzz1999.entityreskin.web.asset.AssetRepository;
import io.github.zzz1999.entityreskin.web.security.UrlSigner;
import io.github.zzz1999.entityreskin.web.server.GameServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManifestServiceTest {

    private static final String SIGNING_SECRET = "unit-test-signing-secret-0123456789abcdef";
    private static final String GEOMETRY_SHA =
            "1111111111111111111111111111111111111111111111111111111111111111";
    private static final String ANIMATION_SHA =
            "2222222222222222222222222222222222222222222222222222222222222222";
    private static final String TEXTURE_SHA =
            "3333333333333333333333333333333333333333333333333333333333333333";

    /** TTL 1440 minutes: half-window 43200 s. Epoch 1,000,000 lies in window 23. */
    private static final long FIXED_EPOCH_SECONDS = 1_000_000L;
    private static final long EXPECTED_EXPIRY = 25L * 43_200;

    private final UrlSigner signer = new UrlSigner(SIGNING_SECRET, new MockEnvironment());

    private static GameServer serverWithId(long id) {
        return new GameServer("测试服", "ab".repeat(32), "owner@example.com", 1_048_576) {
            @Override
            public Long getId() {
                return id;
            }
        };
    }

    private static AppearanceEntry appearance(GameServer server, String identifier) {
        return new AppearanceEntry(server, identifier, "红龙", "geometry.dragon_red",
                "animation.dragon_red.idle", null, Map.of(
                ResourceKind.GEOMETRY, GEOMETRY_SHA,
                ResourceKind.ANIMATION, ANIMATION_SHA,
                ResourceKind.TEXTURE, TEXTURE_SHA));
    }

    private static AssetRepository assetsContainingAll() {
        AssetRepository assets = mock(AssetRepository.class);
        when(assets.findById(anyString())).thenAnswer(invocation -> Optional.of(
                new Asset(invocation.getArgument(0), 1024, ResourceKind.GEOMETRY, null,
                        "owner@example.com", null)));
        return assets;
    }

    private static ManifestService service(AppearanceEntryRepository appearances, AssetRepository assets,
                                           UrlSigner signer, long epochSeconds) {
        Clock clock = Clock.fixed(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
        return new ManifestService(appearances, assets, signer, clock, "https://cdn.example.com", 1440);
    }

    @Test
    void manifestIsByteIdenticalWithinAHalfWindowAndPassesSharedValidation() {
        GameServer server = serverWithId(7);
        AppearanceEntryRepository appearances = mock(AppearanceEntryRepository.class);
        when(appearances.findByGameServerId(7L)).thenReturn(List.of(appearance(server, "entityreskin:dragon_red")));
        AssetRepository assets = assetsContainingAll();

        ManifestService first = service(appearances, assets, signer, FIXED_EPOCH_SECONDS);
        ManifestService second = service(appearances, assets, signer, FIXED_EPOCH_SECONDS + 3600);

        ManifestService.ManifestDocument documentA = first.document(server);
        ManifestService.ManifestDocument documentB = second.document(server);
        assertEquals(documentA.json(), documentB.json());
        assertEquals(documentA.sha256Hex(), documentB.sha256Hex());

        Manifest manifest = Manifests.parse(documentA.json());
        assertEquals("https://cdn.example.com/", manifest.getBaseUrl());
        ManifestEntry entry = manifest.getEntry("entityreskin:dragon_red");
        assertNotNull(entry);
        ResourceFile geometry = entry.getResource(ResourceKind.GEOMETRY);
        assertNotNull(geometry);
        assertEquals(GEOMETRY_SHA, geometry.getSha256());
        assertEquals(1024, geometry.getSize());
        String expectedSignature = signer.sign(GEOMETRY_SHA, EXPECTED_EXPIRY, server.getToken());
        assertEquals("download/" + GEOMETRY_SHA + "?exp=" + EXPECTED_EXPIRY
                + "&srv=" + server.getToken() + "&sig=" + expectedSignature, geometry.getPath());
    }

    @Test
    void differentHalfWindowsProduceDifferentExpiries() {
        GameServer server = serverWithId(7);
        AppearanceEntryRepository appearances = mock(AppearanceEntryRepository.class);
        when(appearances.findByGameServerId(7L)).thenReturn(List.of(appearance(server, "entityreskin:dragon_red")));
        AssetRepository assets = assetsContainingAll();

        ManifestService current = service(appearances, assets, signer, FIXED_EPOCH_SECONDS);
        ManifestService nextWindow = service(appearances, assets, signer, FIXED_EPOCH_SECONDS + 43_200);
        assertNotEquals(current.document(server).sha256Hex(), nextWindow.document(server).sha256Hex());
    }

    @Test
    void appearanceReferencingMissingAssetIsSkipped() {
        GameServer server = serverWithId(7);
        AppearanceEntryRepository appearances = mock(AppearanceEntryRepository.class);
        when(appearances.findByGameServerId(7L)).thenReturn(List.of(
                appearance(server, "entityreskin:dragon_red"),
                new AppearanceEntry(server, "entityreskin:broken", null, "geometry.broken",
                        "animation.broken.idle", null, Map.of(ResourceKind.GEOMETRY,
                        "4444444444444444444444444444444444444444444444444444444444444444"))));
        AssetRepository assets = mock(AssetRepository.class);
        when(assets.findById(anyString())).thenAnswer(invocation -> {
            String sha = invocation.getArgument(0);
            if (sha.startsWith("4444")) {
                return Optional.empty();
            }
            return Optional.of(new Asset(sha, 1024, ResourceKind.GEOMETRY, null, "owner@example.com", null));
        });

        Manifest manifest = Manifests.parse(
                service(appearances, assets, signer, FIXED_EPOCH_SECONDS).document(server).json());
        assertNotNull(manifest.getEntry("entityreskin:dragon_red"));
        assertNull(manifest.getEntry("entityreskin:broken"));
    }

    @Test
    void invalidationForcesRegenerationWithinTheSameWindow() {
        GameServer server = serverWithId(7);
        AppearanceEntryRepository appearances = mock(AppearanceEntryRepository.class);
        AppearanceEntry original = appearance(server, "entityreskin:dragon_red");
        AppearanceEntry renamed = appearance(server, "entityreskin:dragon_blue");
        when(appearances.findByGameServerId(7L)).thenReturn(List.of(original), List.of(renamed));
        AssetRepository assets = assetsContainingAll();

        ManifestService manifestService = service(appearances, assets, signer, FIXED_EPOCH_SECONDS);
        String beforeEdit = manifestService.document(server).json();
        assertEquals(beforeEdit, manifestService.document(server).json(), "second read must hit the cache");
        manifestService.invalidate(7);
        String afterEdit = manifestService.document(server).json();
        assertTrue(afterEdit.contains("entityreskin:dragon_blue"));
        assertNotEquals(beforeEdit, afterEdit);
    }
}

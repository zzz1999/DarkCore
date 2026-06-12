package io.github.zzz1999.entityreskin.web;

import io.github.zzz1999.entityreskin.protocol.manifest.Manifest;
import io.github.zzz1999.entityreskin.protocol.manifest.ManifestEntry;
import io.github.zzz1999.entityreskin.protocol.manifest.Manifests;
import io.github.zzz1999.entityreskin.protocol.manifest.ResourceKind;
import io.github.zzz1999.entityreskin.web.account.User;
import io.github.zzz1999.entityreskin.web.account.UserRepository;
import io.github.zzz1999.entityreskin.web.security.UrlSigner;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end contract test for the download chain: register a server, upload assets, define an
 * appearance, fetch the per-server manifest (validated with the shared parser, exactly as the
 * client mod will), download a blob through its signed URL, and verify every rejection path.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:entityreskin-flow;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "entityreskin.storage.root=build/test-blobs-flow",
        "entityreskin.jwt.secret=test-secret-0123456789abcdef0123456789abcdef0123456789",
        "entityreskin.signing.secret=test-signing-secret-0123456789abcdef0123456789",
        "entityreskin.email.dev-mode=true",
        "entityreskin.public-base-url=https://cdn.example.com/"
})
class DownloadFlowIntegrationTest {

    private static final Gson GSON = new Gson();
    private static final String EMAIL = "owner@example.com";
    private static final String PASSWORD = "password123";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UrlSigner urlSigner;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void fullDownloadChain() throws Exception {
        // An enabled account (created directly; the verification flow is covered elsewhere).
        User user = new User(EMAIL, passwordEncoder.encode(PASSWORD));
        user.setEnabled(true);
        user.setBalanceBytes(10_000_000);
        users.save(user);

        String jwt = login();

        byte[] geometryBytes = "geometry test payload".getBytes(StandardCharsets.UTF_8);
        String geometrySha = upload(jwt, ResourceKind.GEOMETRY, geometryBytes);
        String animationSha = upload(jwt, ResourceKind.ANIMATION,
                "animation test payload".getBytes(StandardCharsets.UTF_8));
        String textureSha = upload(jwt, ResourceKind.TEXTURE,
                "texture test payload".getBytes(StandardCharsets.UTF_8));

        // Register a server and define an appearance referencing the uploaded assets.
        JsonObject server = postJson(jwt, "/api/servers", Map.of("name", "集成测试服"));
        long serverId = server.get("id").getAsLong();
        String serverToken = server.get("token").getAsString();

        postJson(jwt, "/api/servers/" + serverId + "/appearances", Map.of(
                "identifier", "entityreskin:test_dragon",
                "displayName", "测试龙",
                "geometryName", "geometry.test_dragon",
                "defaultAnimation", "animation.test_dragon.idle",
                "resources", Map.of(
                        ResourceKind.GEOMETRY, geometrySha,
                        ResourceKind.ANIMATION, animationSha,
                        ResourceKind.TEXTURE, textureSha)));

        // The manifest must satisfy the exact validation the client mod applies.
        byte[] manifestBytes = mockMvc.perform(get("/api/manifest").param("srv", serverToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        Manifest manifest = Manifests.parse(new String(manifestBytes, StandardCharsets.UTF_8));
        ManifestEntry entry = manifest.getEntry("entityreskin:test_dragon");
        assertNotNull(entry);
        String geometryPath = entry.getResource(ResourceKind.GEOMETRY).getPath();
        assertTrue(geometryPath.startsWith("download/" + geometrySha + "?exp="));
        assertEquals(geometryBytes.length, entry.getResource(ResourceKind.GEOMETRY).getSize());

        // The hash endpoint must report the SHA-256 of the exact manifest bytes served.
        String hashBody = mockMvc.perform(get("/api/manifest/sha256").param("srv", serverToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String reportedSha = JsonParser.parseString(hashBody).getAsJsonObject().get("sha256").getAsString();
        assertEquals(sha256Hex(manifestBytes), reportedSha);

        // Download through the signed URL and verify headers and bytes.
        MvcResult pending = mockMvc.perform(get("/" + geometryPath))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(content().bytes(geometryBytes));

        // The download is recorded in the owner's live statistics.
        String statsBody = mockMvc.perform(get("/api/stats/servers/" + serverId)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonObject stats = JsonParser.parseString(statsBody).getAsJsonObject();
        assertTrue(stats.get("downloads").getAsLong() >= 1);
        assertTrue(stats.get("totalBytes").getAsLong() >= geometryBytes.length);

        // Rejection paths: tampered signature, expired URL, unknown token, unknown blob.
        mockMvc.perform(get("/" + tamperLastCharacter(geometryPath)))
                .andExpect(status().isForbidden());

        String expiredSignature = urlSigner.sign(geometrySha, 1000L, serverToken);
        mockMvc.perform(get("/download/" + geometrySha)
                        .param("exp", "1000").param("srv", serverToken).param("sig", expiredSignature))
                .andExpect(status().isForbidden());

        String foreignToken = "0".repeat(64);
        mockMvc.perform(get("/download/" + geometrySha)
                        .param("exp", String.valueOf(Long.MAX_VALUE)).param("srv", foreignToken)
                        .param("sig", urlSigner.sign(geometrySha, Long.MAX_VALUE, foreignToken)))
                .andExpect(status().isUnauthorized());

        String absentSha = "f".repeat(64);
        mockMvc.perform(get("/download/" + absentSha)
                        .param("exp", String.valueOf(Long.MAX_VALUE)).param("srv", serverToken)
                        .param("sig", urlSigner.sign(absentSha, Long.MAX_VALUE, serverToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/manifest").param("srv", foreignToken))
                .andExpect(status().isUnauthorized());

        // A server without appearance entries yields 404 on both manifest endpoints
        // (the shared parser rejects empty manifests, so none is served).
        JsonObject emptyServer = postJson(jwt, "/api/servers", Map.of("name", "空服务器"));
        String emptyToken = emptyServer.get("token").getAsString();
        mockMvc.perform(get("/api/manifest").param("srv", emptyToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/manifest/sha256").param("srv", emptyToken))
                .andExpect(status().isNotFound());

        // Another account cannot publish assets it does not own (cross-account reference).
        User other = new User("other@example.com", passwordEncoder.encode(PASSWORD));
        other.setEnabled(true);
        users.save(other);
        String otherJwt = loginAs("other@example.com");
        JsonObject otherServer = postJson(otherJwt, "/api/servers", Map.of("name", "他人服务器"));
        mockMvc.perform(post("/api/servers/" + otherServer.get("id").getAsLong() + "/appearances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GSON.toJson(Map.of(
                                "identifier", "entityreskin:stolen_dragon",
                                "geometryName", "geometry.stolen",
                                "defaultAnimation", "animation.stolen.idle",
                                "resources", Map.of(
                                        ResourceKind.GEOMETRY, geometrySha,
                                        ResourceKind.ANIMATION, animationSha,
                                        ResourceKind.TEXTURE, textureSha))))
                        .header("Authorization", "Bearer " + otherJwt))
                .andExpect(status().isForbidden());
    }

    private String login() throws Exception {
        return loginAs(EMAIL);
    }

    private String loginAs(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GSON.toJson(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonParser.parseString(body).getAsJsonObject().get("token").getAsString();
    }

    private String upload(String jwt, String kind, byte[] bytes) throws Exception {
        String body = mockMvc.perform(multipart("/api/assets")
                        .file(new MockMultipartFile("file", kind + ".bin", null, bytes))
                        .param("kind", kind)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonParser.parseString(body).getAsJsonObject().get("sha256").getAsString();
    }

    private JsonObject postJson(String jwt, String path, Object payload) throws Exception {
        String body = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GSON.toJson(payload))
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonParser.parseString(body).getAsJsonObject();
    }

    private static String tamperLastCharacter(String path) {
        char last = path.charAt(path.length() - 1);
        char replacement = last == 'a' ? 'b' : 'a';
        return path.substring(0, path.length() - 1) + replacement;
    }

    private static String sha256Hex(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
}

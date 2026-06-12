package io.github.zzz1999.entityreskin.web.manifest;

import io.github.zzz1999.entityreskin.web.server.GameServer;
import io.github.zzz1999.entityreskin.web.server.GameServerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Serves per-server manifests. These endpoints are public in the security filter chain;
 * authorization is the server token itself ({@code srv}), which also selects the server whose
 * appearances are served. The hash endpoint is polled by the Bukkit plugin to detect content
 * changes and signing-window rollovers (see ManifestService).
 */
@RestController
@RequestMapping("/api/manifest")
public class ManifestController {

    private static final MediaType JSON_UTF8 =
            new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);

    private final ManifestService manifestService;
    private final GameServerRepository servers;

    public ManifestController(ManifestService manifestService, GameServerRepository servers) {
        this.manifestService = manifestService;
        this.servers = servers;
    }

    @GetMapping
    public ResponseEntity<String> manifest(@RequestParam("srv") String serverToken) {
        ManifestService.ManifestDocument document = manifestService.document(requireServer(serverToken));
        return ResponseEntity.ok().contentType(JSON_UTF8).body(document.json());
    }

    @GetMapping("/sha256")
    public Map<String, String> manifestSha256(@RequestParam("srv") String serverToken) {
        ManifestService.ManifestDocument document = manifestService.document(requireServer(serverToken));
        return Map.of("sha256", document.sha256Hex());
    }

    private GameServer requireServer(String token) {
        return servers.findByToken(token).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unknown server token"));
    }
}

package io.github.zzz1999.entityreskin.web.server;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Authenticated management of the account's registered servers. */
@RestController
@RequestMapping("/api/servers")
public class GameServerController {

    private final GameServerService gameServerService;

    public GameServerController(GameServerService gameServerService) {
        this.gameServerService = gameServerService;
    }

    @PostMapping
    public ServerResponse create(@Valid @RequestBody CreateServerRequest request, Authentication authentication) {
        return ServerResponse.of(gameServerService.create(authentication.getName(), request.name()));
    }

    @GetMapping
    public List<ServerResponse> list(Authentication authentication) {
        return gameServerService.listOwned(authentication.getName()).stream()
                .map(ServerResponse::of)
                .toList();
    }

    @PostMapping("/{serverId}/reset-token")
    public ServerResponse resetToken(@PathVariable long serverId, Authentication authentication) {
        return ServerResponse.of(gameServerService.resetToken(authentication.getName(), serverId));
    }

    @DeleteMapping("/{serverId}")
    public Map<String, String> delete(@PathVariable long serverId, Authentication authentication) {
        gameServerService.delete(authentication.getName(), serverId);
        return Map.of("status", "deleted");
    }

    public record CreateServerRequest(@NotBlank @Size(max = 64) String name) {
    }

    public record ServerResponse(Long id, String name, String token, long bytesPerSecond, String createdAt) {

        static ServerResponse of(GameServer server) {
            return new ServerResponse(server.getId(), server.getName(), server.getToken(),
                    server.getBytesPerSecond(), server.getCreatedAt().toString());
        }
    }
}

package io.github.zzz1999.entityreskin.web.appearance;

import jakarta.validation.Valid;
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

/**
 * Authenticated management of a server's appearance entries. POST has create-or-replace
 * semantics keyed by (server, identifier).
 */
@RestController
@RequestMapping("/api/servers/{serverId}/appearances")
public class AppearanceController {

    private final AppearanceService appearanceService;

    public AppearanceController(AppearanceService appearanceService) {
        this.appearanceService = appearanceService;
    }

    @PostMapping
    public AppearanceResponse createOrReplace(@PathVariable long serverId,
                                              @Valid @RequestBody AppearanceDefinition definition,
                                              Authentication authentication) {
        return AppearanceResponse.of(
                appearanceService.createOrReplace(authentication.getName(), serverId, definition));
    }

    @GetMapping
    public List<AppearanceResponse> list(@PathVariable long serverId, Authentication authentication) {
        return appearanceService.list(authentication.getName(), serverId).stream()
                .map(AppearanceResponse::of)
                .toList();
    }

    @DeleteMapping("/{appearanceId}")
    public Map<String, String> delete(@PathVariable long serverId, @PathVariable long appearanceId,
                                      Authentication authentication) {
        appearanceService.delete(authentication.getName(), serverId, appearanceId);
        return Map.of("status", "deleted");
    }

    public record AppearanceResponse(Long id, String identifier, String displayName, String geometryName,
                                     String defaultAnimation, String renderControllerEntry,
                                     Map<String, String> resources, String createdAt) {

        static AppearanceResponse of(AppearanceEntry entry) {
            return new AppearanceResponse(entry.getId(), entry.getIdentifier(), entry.getDisplayName(),
                    entry.getGeometryName(), entry.getDefaultAnimation(), entry.getRenderControllerEntry(),
                    entry.getResources(), entry.getCreatedAt().toString());
        }
    }
}

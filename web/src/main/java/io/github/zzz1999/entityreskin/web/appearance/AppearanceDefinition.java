package io.github.zzz1999.entityreskin.web.appearance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Request body for creating or replacing an appearance entry. {@code resources} maps a resource
 * kind (see {@link io.github.zzz1999.entityreskin.protocol.manifest.ResourceKind}) to the SHA-256 of a
 * previously uploaded asset.
 */
public record AppearanceDefinition(
        @NotBlank @Size(max = 128) String identifier,
        @Size(max = 64) String displayName,
        @NotBlank @Size(max = 128) String geometryName,
        @NotBlank @Size(max = 128) String defaultAnimation,
        @Size(max = 128) String renderControllerEntry,
        @NotEmpty Map<String, String> resources) {
}

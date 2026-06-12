package io.github.zzz1999.entityreskin.protocol.manifest;

import java.util.Map;

/**
 * One appearance identifier's definition: which geometry/animation to use and the set of
 * resource files (keyed by {@link ResourceKind}) needed to render it. {@code defaultAnimation}
 * is used in the MVP before animation controllers exist; the two {@code *Controllers} resource
 * kinds are optional and only consumed in later phases.
 */
public final class ManifestEntry {

    private String displayName;
    private String geometryName;
    private String defaultAnimation;
    private String renderControllerEntry;
    private Map<String, ResourceFile> resources;

    public ManifestEntry() {
    }

    public ManifestEntry(String displayName, String geometryName, String defaultAnimation,
                         String renderControllerEntry, Map<String, ResourceFile> resources) {
        this.displayName = displayName;
        this.geometryName = geometryName;
        this.defaultAnimation = defaultAnimation;
        this.renderControllerEntry = renderControllerEntry;
        this.resources = resources;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getGeometryName() {
        return geometryName;
    }

    public String getDefaultAnimation() {
        return defaultAnimation;
    }

    public String getRenderControllerEntry() {
        return renderControllerEntry;
    }

    public Map<String, ResourceFile> getResources() {
        return resources;
    }

    /** Returns the resource of the given {@link ResourceKind}, or {@code null} if absent. */
    public ResourceFile getResource(String kind) {
        return resources == null ? null : resources.get(kind);
    }
}

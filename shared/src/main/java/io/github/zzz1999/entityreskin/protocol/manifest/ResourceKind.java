package io.github.zzz1999.entityreskin.protocol.manifest;

/** Keys for the {@code resources} map of a {@link ManifestEntry}. */
public final class ResourceKind {

    public static final String GEOMETRY = "geometry";
    public static final String ANIMATION = "animation";
    public static final String TEXTURE = "texture";
    public static final String ANIMATION_CONTROLLERS = "animation_controllers";
    public static final String RENDER_CONTROLLERS = "render_controllers";

    /** Reserved for a later phase: Bedrock particle effect definitions. */
    public static final String PARTICLE = "particle";

    /** Reserved for a later phase: sound files and sound definitions. */
    public static final String SOUND = "sound";

    private ResourceKind() {
    }
}

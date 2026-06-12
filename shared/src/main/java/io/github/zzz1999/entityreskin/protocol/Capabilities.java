package io.github.zzz1999.entityreskin.protocol;

/**
 * Capability bit flags exchanged in the handshake so client and server can negotiate
 * which features are mutually supported (a higher-version client may understand controllers
 * that an older server never sends, and vice versa).
 */
public final class Capabilities {

    public static final long GEOMETRY = 1L << 0;
    public static final long ANIMATION = 1L << 1;
    public static final long ANIMATION_CONTROLLER = 1L << 2;
    public static final long RENDER_CONTROLLER = 1L << 3;
    public static final long MOLANG = 1L << 4;

    /** Reserved for a later phase: Bedrock particle effect playback. */
    public static final long PARTICLE = 1L << 5;

    /** Reserved for a later phase: sound playback. */
    public static final long SOUND = 1L << 6;

    public static boolean has(long set, long capability) {
        return (set & capability) == capability;
    }

    private Capabilities() {
    }
}

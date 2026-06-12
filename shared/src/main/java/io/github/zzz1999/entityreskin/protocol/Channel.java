package io.github.zzz1999.entityreskin.protocol;

/**
 * Plugin messaging channel name. Servers in the supported range (1.13 and newer) require a
 * namespaced channel name; the plugin and the client mod register this same name.
 */
public final class Channel {

    /** Namespaced plugin messaging channel (required since 1.13). */
    public static final String MODERN = "entityreskin:main";

    private Channel() {
    }
}

package io.github.zzz1999.entityreskin.client.appearance;

import net.minecraft.resources.Identifier;
import software.bernie.geckolib.constant.dataticket.DataTicket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bridges EntityReskin appearance identifiers (free-form strings such as {@code entityreskin:dragon_red})
 * to the {@link Identifier} keys GeckoLib uses internally, and carries the appearance assigned to
 * the entity currently being rendered via a {@link DataTicket} on the render state.
 *
 * <p>An appearance string cannot be used as an {@code Identifier} path directly (it may contain a
 * namespace colon), so each appearance is mapped to a stable, path-safe synthetic Identifier with a
 * reverse lookup. The synthetic Identifier serves both as GeckoLib's per-model cache key and as the
 * runtime texture id.</p>
 */
public final class AppearanceKeys {

    /** Render-state ticket carrying the appearance identifier assigned to the entity being rendered. */
    public static final DataTicket<String> APPEARANCE = DataTicket.create("entityreskin:appearance", String.class);

    private static final Map<String, Identifier> BY_APPEARANCE = new ConcurrentHashMap<>();
    private static final Map<Identifier, String> BY_KEY = new ConcurrentHashMap<>();
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private AppearanceKeys() {
    }

    /** Returns the stable synthetic Identifier for an appearance string, allocating one if needed. */
    public static Identifier keyFor(String appearance) {
        return BY_APPEARANCE.computeIfAbsent(appearance, ignored -> {
            Identifier key = Identifier.fromNamespaceAndPath("entityreskin", "appearance/" + COUNTER.getAndIncrement());
            BY_KEY.put(key, appearance);
            return key;
        });
    }

    /** The appearance string for a previously-allocated key, or {@code null} if unknown. */
    public static String appearanceForKey(Identifier key) {
        return BY_KEY.get(key);
    }

    /** Drops all appearance-to-key mappings; call on disconnect, after textures are released. */
    public static void clearAll() {
        BY_APPEARANCE.clear();
        BY_KEY.clear();
    }
}

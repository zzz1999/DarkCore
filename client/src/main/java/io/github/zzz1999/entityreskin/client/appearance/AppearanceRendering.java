package io.github.zzz1999.entityreskin.client.appearance;

/**
 * Activation point for EntityReskin's entity rendering. Deliberately a no-op for now: Fabric's
 * {@code EntityRendererRegistry} replaces a renderer for ALL instances of an entity type, which
 * would turn every vanilla entity of that type into a broken/invisible render whenever it has no
 * EntityReskin appearance (GeckoLib resolves a non-null render type from the texture id but a null baked
 * model, so the model still tries to render and fails). Selective, per-entity replacement that falls
 * back to vanilla requires a mixin into the entity render dispatcher; until that lands, the
 * renderer/model/animatable classes stay dormant and no vanilla rendering is altered. Asset download
 * and baking still run, so appearances are in memory and ready for the dispatcher mixin.
 */
public final class AppearanceRendering {

    private AppearanceRendering() {
    }

    public static void register() {
        // No global renderer replacement (see class note).
    }
}

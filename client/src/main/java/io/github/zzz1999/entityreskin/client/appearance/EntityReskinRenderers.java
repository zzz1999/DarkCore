package io.github.zzz1999.entityreskin.client.appearance;

import io.github.zzz1999.entityreskin.client.render.ClientRuntime;
import io.github.zzz1999.entityreskin.client.session.ClientSession;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Holds the single shared {@link AppearanceEntityRenderer} and decides, per entity, whether the
 * entity-render-dispatcher mixin should divert rendering to it. Only living entities with a fully
 * downloaded-and-baked appearance are diverted; every other entity keeps its vanilla renderer, which
 * is the graceful fallback the policy requires. The renderer is (re)built whenever the dispatcher
 * reloads and hands us a fresh {@link EntityRendererProvider.Context}.
 */
public final class EntityReskinRenderers {

    private static volatile AppearanceEntityRenderer<LivingEntity> renderer;

    private EntityReskinRenderers() {
    }

    /**
     * The shared appearance renderer, or {@code null} before the first resource reload. Used by the
     * dispatcher mixin's render-state overload, where only the (already EntityReskin-extracted) render
     * state is available and the entity is not.
     */
    public static EntityRenderer<?, ?> appearanceRenderer() {
        return renderer;
    }

    /** Called by the dispatcher mixin after a resource reload, with the freshly built context. */
    public static void onContext(EntityRendererProvider.Context context) {
        AppearanceGeoModel model = new AppearanceGeoModel();
        // A single shared animatable serves every reskinned living entity; per-entity animation
        // state is keyed internally by the entity id. The replacing type is informational here.
        AppearanceAnimatable animatable = new AppearanceAnimatable(EntityType.ZOMBIE, "idle");
        renderer = new AppearanceEntityRenderer<>(context, model, animatable);
    }

    /**
     * The EntityReskin renderer for {@code entity} if it is a living entity with a ready appearance,
     * otherwise {@code null} so the dispatcher falls back to the vanilla renderer.
     */
    public static EntityRenderer<?, ?> rendererFor(Entity entity) {
        AppearanceEntityRenderer<LivingEntity> current = renderer;
        if (current == null || !(entity instanceof LivingEntity)) {
            return null;
        }
        String appearance = ClientRuntime.appearanceFor(entity.getUUID());
        if (appearance == null) {
            return null;
        }
        ClientSession session = ClientRuntime.session();
        if (session == null || session.assets().model(appearance) == null) {
            // Assigned but not yet downloaded/baked: render vanilla until the asset is ready.
            return null;
        }
        return current;
    }
}

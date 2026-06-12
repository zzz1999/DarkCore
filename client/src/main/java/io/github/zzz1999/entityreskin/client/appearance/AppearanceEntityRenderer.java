package io.github.zzz1999.entityreskin.client.appearance;

import io.github.zzz1999.entityreskin.client.render.ClientRuntime;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;

/**
 * Replaces a vanilla living entity's rendering with a downloaded GeckoLib appearance. The model and
 * animatable are shared (registered once); the per-entity appearance is captured into the render
 * state from the entity's assigned identifier in {@link #addRenderData}, where the model reads it
 * back. Entities with no assigned/ready appearance resolve to no geometry; selective replacement
 * with a vanilla fallback for arbitrary entities is a follow-on render-dispatcher mixin.
 */
public final class AppearanceEntityRenderer<E extends LivingEntity>
        extends GeoReplacedEntityRenderer<AppearanceAnimatable, E, LivingEntityRenderState> {

    public AppearanceEntityRenderer(EntityRendererProvider.Context context,
                                    GeoModel<AppearanceAnimatable> model, AppearanceAnimatable animatable) {
        super(context, model, animatable);
    }

    /**
     * Publishes the appearance being extracted so {@link AppearanceGeoModel#getBakedAnimation} can
     * resolve it during the animation processing that runs inside this call (that hook has no
     * render-state parameter). Wrapping the whole extraction is necessary because animation
     * resolution happens before {@link #addRenderData}.
     */
    @Override
    public void extractRenderState(E entity, LivingEntityRenderState renderState, float partialTick) {
        String appearance = entity != null ? ClientRuntime.appearanceFor(entity.getUUID()) : null;
        AppearanceGeoModel.EXTRACTING_APPEARANCE.set(appearance);
        try {
            super.extractRenderState(entity, renderState, partialTick);
        } finally {
            AppearanceGeoModel.EXTRACTING_APPEARANCE.remove();
        }
    }

    @Override
    public void addRenderData(AppearanceAnimatable animatable, E relatedObject,
                              LivingEntityRenderState renderState, float partialTick) {
        super.addRenderData(animatable, relatedObject, renderState, partialTick);
        if (relatedObject != null) {
            String appearance = ClientRuntime.appearanceFor(relatedObject.getUUID());
            if (appearance != null) {
                renderState.addGeckolibData(AppearanceKeys.APPEARANCE, appearance);
            }
        }
    }
}

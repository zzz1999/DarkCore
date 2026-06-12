package io.github.zzz1999.entityreskin.client.mixin;

import io.github.zzz1999.entityreskin.client.appearance.AppearanceKeys;
import io.github.zzz1999.entityreskin.client.appearance.EntityReskinRenderers;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * Per-instance selective entity-renderer replacement. A living entity that has a fully
 * downloaded-and-baked EntityReskin appearance is diverted to the shared GeckoLib renderer; every other
 * entity keeps its vanilla renderer, so un-reskinned (and not-yet-ready) entities render normally.
 * The freshly built renderer context is captured on each resource reload so the EntityReskin renderer
 * can be (re)constructed.
 *
 * <p><b>Runtime validation required:</b> Minecraft render mixins cannot be verified by compilation.
 * A live Paper + client test must confirm: (1) an entity assigned a ready appearance draws the
 * downloaded model/texture; (2) un-reskinned entities of the same and other types draw vanilla;
 * (3) an assigned-but-still-downloading entity draws vanilla until the asset arrives; (4) no crash
 * or log spam. If the {@code @Local} context capture or the {@code getRenderer} target does not bind,
 * adjust the injection point.</p>
 */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Inject(
            method = "getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
            at = @At("HEAD"),
            cancellable = true)
    private <T extends Entity> void entityreskin$selectAppearanceRenderer(
            T entity, CallbackInfoReturnable<EntityRenderer<? super T, ?>> cir) {
        EntityRenderer<?, ?> renderer = EntityReskinRenderers.rendererFor(entity);
        if (renderer != null) {
            @SuppressWarnings("unchecked")
            EntityRenderer<? super T, ?> typed = (EntityRenderer<? super T, ?>) renderer;
            cir.setReturnValue(typed);
        }
    }

    /**
     * The submit path resolves the renderer from the already-extracted render state via the
     * {@code getRenderer(EntityRenderState)} overload (a direct entity-type to renderer map lookup),
     * which bypasses the entity overload above. Without this, a state extracted by the EntityReskin
     * renderer (a generic {@link net.minecraft.client.renderer.entity.state.LivingEntityRenderState}
     * carrying the appearance ticket) would be submitted through the vanilla renderer registered for
     * the entity's type, whose render-state subtype it is not — a fatal {@code ClassCastException}.
     * A render state carrying the EntityReskin appearance ticket was extracted by this renderer and must
     * be submitted through it too.
     */
    @Inject(
            method = "getRenderer(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
            at = @At("HEAD"),
            cancellable = true)
    private <S extends EntityRenderState> void entityreskin$selectAppearanceRendererForState(
            S entityRenderState, CallbackInfoReturnable<EntityRenderer<?, ? super S>> cir) {
        if (entityRenderState instanceof GeoRenderState geoState
                && geoState.hasGeckolibData(AppearanceKeys.APPEARANCE)) {
            EntityRenderer<?, ?> renderer = EntityReskinRenderers.appearanceRenderer();
            if (renderer != null) {
                @SuppressWarnings("unchecked")
                EntityRenderer<?, ? super S> typed = (EntityRenderer<?, ? super S>) renderer;
                cir.setReturnValue(typed);
            }
        }
    }

    @Inject(method = "onResourceManagerReload", at = @At("TAIL"))
    private void entityreskin$captureContext(
            ResourceManager resourceManager, CallbackInfo ci, @Local EntityRendererProvider.Context context) {
        EntityReskinRenderers.onContext(context);
    }
}

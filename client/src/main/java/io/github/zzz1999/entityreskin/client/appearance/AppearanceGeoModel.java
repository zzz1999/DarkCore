package io.github.zzz1999.entityreskin.client.appearance;

import io.github.zzz1999.entityreskin.client.render.ClientRuntime;
import io.github.zzz1999.entityreskin.client.render.TextureRegistry;
import io.github.zzz1999.entityreskin.client.session.ClientSession;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.cache.animation.Animation;
import software.bernie.geckolib.cache.model.BakedGeoModel;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * A {@link GeoModel} backed by the in-memory {@link io.github.zzz1999.entityreskin.client.render.InMemoryAssetStore},
 * shared by the replaced-entity renderer. It resolves the appearance assigned to the entity being
 * rendered (carried on the render state by {@link AppearanceKeys#APPEARANCE}) and serves the baked
 * geometry/animation and runtime texture for it, overriding GeckoLib's resource-pack-backed loaders
 * so nothing is read from disk.
 */
public final class AppearanceGeoModel extends GeoModel<AppearanceAnimatable> {

    /** Returned when no appearance is resolved; the geometry lookup then yields null (no render). */
    private static final Identifier MISSING = Identifier.fromNamespaceAndPath("entityreskin", "appearance/none");

    /**
     * The appearance being extracted on the current render thread. {@link AppearanceEntityRenderer}
     * sets this around state extraction, because {@link #getBakedAnimation} runs during that
     * extraction (animation processing) but receives no render-state parameter, so it has no other
     * way to know which appearance's in-memory animations to resolve.
     */
    static final ThreadLocal<String> EXTRACTING_APPEARANCE = new ThreadLocal<>();

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        String appearance = renderState.getGeckolibData(AppearanceKeys.APPEARANCE);
        return appearance == null ? MISSING : AppearanceKeys.keyFor(appearance);
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        String appearance = renderState.getGeckolibData(AppearanceKeys.APPEARANCE);
        if (appearance == null) {
            return MISSING;
        }
        Identifier key = AppearanceKeys.keyFor(appearance);
        ClientSession session = ClientRuntime.session();
        if (session != null) {
            byte[] png = session.assets().textureBytes(appearance);
            if (png != null && TextureRegistry.register(key, png)) {
                // Lazy GPU upload on the render thread (this method runs during the render pass).
                return key;
            }
        }
        // No appearance, no texture bytes, or an undecodable texture: avoid returning a key with no
        // backing texture (which would render the missing-texture). The selective-replacement mixin
        // is what ultimately prevents un-ready entities from reaching here.
        return MISSING;
    }

    @Override
    public Identifier getAnimationResource(AppearanceAnimatable animatable) {
        return MISSING;
    }

    @Override
    public BakedGeoModel getBakedModel(Identifier location) {
        ClientSession session = ClientRuntime.session();
        String appearance = AppearanceKeys.appearanceForKey(location);
        return (session != null && appearance != null) ? session.assets().model(appearance) : null;
    }

    @Override
    public @Nullable Animation getBakedAnimation(AppearanceAnimatable animatable, String name) {
        String appearance = EXTRACTING_APPEARANCE.get();
        if (appearance == null) {
            return null;
        }
        ClientSession session = ClientRuntime.session();
        if (session == null) {
            return null;
        }
        BakedAnimations baked = session.assets().animations(appearance);
        if (baked == null) {
            return null;
        }
        Animation animation = baked.animations().get(name);
        if (animation == null && baked.animations().size() == 1) {
            // The shared controller plays a fixed logical name (e.g. "idle"); a downloaded appearance
            // may name its sole looping animation differently (e.g. "animation.entityreskin.idle"). With
            // a single animation this is unambiguous, so play it. Selecting among multiple animations
            // (animation controllers / state machines) is a later phase.
            animation = baked.animations().values().iterator().next();
        }
        return animation;
    }
}

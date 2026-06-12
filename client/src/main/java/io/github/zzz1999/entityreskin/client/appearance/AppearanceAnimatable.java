package io.github.zzz1999.entityreskin.client.appearance;

import net.minecraft.world.entity.EntityType;
import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A standalone GeoAnimatable used to render a downloaded appearance in place of a vanilla entity.
 * It must NOT be an {@link net.minecraft.world.entity.Entity} (GeoReplacedEntityRenderer rejects an
 * Entity animatable), so it is a lightweight singleton shared across all replaced entities of a
 * type; per-entity animation state is keyed internally by the entity's network id. The single
 * looping idle controller plays the named animation if the appearance provides it.
 */
public final class AppearanceAnimatable implements GeoReplacedEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final EntityType<?> replacing;
    private final RawAnimation idle;

    public AppearanceAnimatable(EntityType<?> replacing, String idleAnimationName) {
        this.replacing = replacing;
        this.idle = RawAnimation.begin().thenLoop(idleAnimationName);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<AppearanceAnimatable>("idle", 0,
                state -> state.setAndContinue(this.idle)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public EntityType<?> getReplacingEntityType() {
        return this.replacing;
    }
}

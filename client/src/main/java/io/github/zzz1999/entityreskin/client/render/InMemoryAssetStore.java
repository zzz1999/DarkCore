package io.github.zzz1999.entityreskin.client.render;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import software.bernie.geckolib.cache.GeckoLibResources;
import software.bernie.geckolib.cache.model.BakedGeoModel;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bakes Bedrock-format geometry and animation JSON that was downloaded into memory into GeckoLib's
 * render-ready objects, without writing anything to disk or to a resource pack.
 *
 * <p>This is the core of the runtime/in-memory asset spike. GeckoLib's public loading pipeline is
 * reused directly — {@link GeckoLibResources#GSON} for parsing, {@link GeometryTree#fromModel} and
 * {@link BakedModelFactory} for geometry, and the same {@code GSON} for animations — so no resource
 * pack, resource-manager reload, reflection, or mixin is required. A custom {@code GeoModel} then
 * resolves these baked objects by overriding {@code getBakedModel}/{@code getBakedAnimation},
 * bypassing GeckoLib's resource-manager-backed cache. Everything is held in memory and dropped on
 * disconnect, consistent with the memory-only asset policy.</p>
 */
public final class InMemoryAssetStore {

    private record Baked(BakedGeoModel model, BakedAnimations animations, byte[] textureBytes) {
    }

    private final Map<String, Baked> byIdentifier = new ConcurrentHashMap<>();

    /** Bakes a {@code .geo.json} body into a render-ready model using GeckoLib's own pipeline. */
    public static BakedGeoModel bakeGeometry(byte[] geoJson) {
        JsonObject json = parse(geoJson);
        Model model = GeckoLibResources.GSON.fromJson(json, Model.class);
        return BakedModelFactory.getForNamespace("entityreskin").constructGeoModel(GeometryTree.fromModel(model));
    }

    /** Bakes an {@code .animation.json} body into render-ready animations. */
    public static BakedAnimations bakeAnimations(byte[] animationJson) {
        JsonObject json = parse(animationJson);
        return GeckoLibResources.GSON.fromJson(GsonHelper.getAsJsonObject(json, "animations"), BakedAnimations.class);
    }

    public void put(String identifier, BakedGeoModel model, BakedAnimations animations, byte[] textureBytes) {
        byIdentifier.put(identifier, new Baked(model, animations, textureBytes));
    }

    public BakedGeoModel model(String identifier) {
        Baked baked = byIdentifier.get(identifier);
        return baked == null ? null : baked.model();
    }

    public BakedAnimations animations(String identifier) {
        Baked baked = byIdentifier.get(identifier);
        return baked == null ? null : baked.animations();
    }

    /** Raw PNG bytes for the appearance's texture, registered to the GPU lazily by the renderer. */
    public byte[] textureBytes(String identifier) {
        Baked baked = byIdentifier.get(identifier);
        return baked == null ? null : baked.textureBytes();
    }

    public boolean contains(String identifier) {
        return byIdentifier.containsKey(identifier);
    }

    public void clearAll() {
        byIdentifier.clear();
    }

    private static JsonObject parse(byte[] bytes) {
        return GsonHelper.parse(new String(bytes, StandardCharsets.UTF_8));
    }
}

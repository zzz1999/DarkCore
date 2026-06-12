package io.github.zzz1999.entityreskin.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers in-memory PNG bytes as runtime GPU textures, addressable by {@link Identifier}. Must be
 * called on the render thread (the {@link DynamicTexture} constructor uploads to the GPU). Textures
 * are tracked so they can all be released on disconnect, consistent with the memory-only policy.
 */
public final class TextureRegistry {

    private static final Set<Identifier> REGISTERED = ConcurrentHashMap.newKeySet();

    private TextureRegistry() {
    }

    /**
     * Decodes and registers the texture if not already present. Returns whether the texture is
     * available (true if it was registered now or earlier; false if the PNG could not be decoded).
     */
    public static boolean register(Identifier id, byte[] png) {
        if (REGISTERED.contains(id)) {
            return true;
        }
        try {
            NativeImage image = NativeImage.read(png);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
            REGISTERED.add(id);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Releases every registered texture (frees GPU memory and closes the NativeImage). */
    public static void releaseAll() {
        var textureManager = Minecraft.getInstance().getTextureManager();
        for (Identifier id : REGISTERED) {
            textureManager.release(id);
        }
        REGISTERED.clear();
    }
}

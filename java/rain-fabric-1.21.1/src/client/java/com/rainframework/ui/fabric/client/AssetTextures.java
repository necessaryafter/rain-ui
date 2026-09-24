package com.rainframework.ui.fabric.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.rainframework.ui.client.content.ContentStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Images from the content cache, uploaded as textures. By the time a screen opens, the client session has checked
 * each image's header against the contract, so only files within the limits reach the decoder.
 */
final class AssetTextures {
    private static final int GIF_MINIMUM_DELAY_MS = 20;

    private final ContentStore store;
    private final Map<String, @Nullable Frames> loaded = new HashMap<>();

    AssetTextures(ContentStore store) {
        this.store = store;
    }

    /** The texture to draw now; animated images advance with the wall clock. Null when the image cannot be loaded. */
    @Nullable ResourceLocation texture(String hash, long nowMillis) {
        final var frames = loaded.computeIfAbsent(hash, this::load);
        if (frames == null) {
            return null;
        }

        return frames.at(nowMillis);
    }

    void releaseAll() {
        final var textures = Minecraft.getInstance().getTextureManager();
        for (final var frames : loaded.values()) {
            if (frames != null) {
                frames.ids.forEach(textures::release);
            }
        }

        loaded.clear();
    }

    private @Nullable Frames load(String hash) {
        final var bytes = store.read(hash);
        if (bytes == null) {
            return null;
        }

        final var buffer = MemoryUtil.memAlloc(bytes.length);
        try {
            buffer.put(bytes).flip();
            return isGif(bytes) ? loadGif(hash, buffer) : loadStill(hash, buffer);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    private @Nullable Frames loadStill(String hash, ByteBuffer encoded) {
        try (final var stack = MemoryStack.stackPush()) {
            final var width = stack.mallocInt(1);
            final var height = stack.mallocInt(1);
            final var channels = stack.mallocInt(1);

            final var pixels = STBImage.stbi_load_from_memory(encoded, width, height, channels, 4);
            if (pixels == null) {
                return null;
            }

            try {
                final var id = upload(hash, 0, pixels, 0, width.get(0), height.get(0));
                return new Frames(List.of(id), new int[]{0}, width.get(0), height.get(0));
            } finally {
                STBImage.stbi_image_free(pixels);
            }
        }
    }

    // stb returns every frame stacked in one buffer plus a delay array it allocated, which is freed with it.
    private @Nullable Frames loadGif(String hash, ByteBuffer encoded) {
        try (final var stack = MemoryStack.stackPush()) {
            final var delays = stack.mallocPointer(1);
            final var width = stack.mallocInt(1);
            final var height = stack.mallocInt(1);
            final var count = stack.mallocInt(1);
            final var channels = stack.mallocInt(1);

            final var pixels = STBImage.stbi_load_gif_from_memory(encoded, delays, width, height, count, channels, 4);
            if (pixels == null) {
                return null;
            }

            try {
                final var frameCount = count.get(0);
                final var frameBytes = width.get(0) * height.get(0) * 4;
                final var delayValues = MemoryUtil.memIntBuffer(delays.get(0), frameCount);
                final var ids = new ArrayList<ResourceLocation>();
                final var durations = new int[frameCount];

                for (int frame = 0; frame < frameCount; frame++) {
                    ids.add(upload(hash, frame, pixels, frame * frameBytes, width.get(0), height.get(0)));
                    durations[frame] = Math.max(GIF_MINIMUM_DELAY_MS, delayValues.get(frame));
                }

                return new Frames(ids, durations, width.get(0), height.get(0));
            } finally {
                STBImage.nstbi_image_free(delays.get(0));
                STBImage.stbi_image_free(pixels);
            }
        }
    }

    // NativeImage stores ABGR in this version, while stb gives RGBA bytes.
    private static ResourceLocation upload(String hash, int frame, ByteBuffer rgba, int offset, int width, int height) {
        final var image = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final var index = offset + (y * width + x) * 4;
                final var red = rgba.get(index) & 0xff;
                final var green = rgba.get(index + 1) & 0xff;
                final var blue = rgba.get(index + 2) & 0xff;
                final var alpha = rgba.get(index + 3) & 0xff;

                image.setPixelRGBA(x, y, (alpha << 24) | (blue << 16) | (green << 8) | red);
            }
        }

        final var id = ResourceLocation.fromNamespaceAndPath("rain-ui", "asset/" + hash + "/" + frame);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        return id;
    }

    private static boolean isGif(byte[] bytes) {
        return bytes.length > 3 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F';
    }

    private record Frames(List<ResourceLocation> ids, int[] durations, int width, int height) {

        ResourceLocation at(long nowMillis) {
            if (ids.size() == 1) {
                return ids.getFirst();
            }

            var total = 0L;
            for (final var duration : durations) {
                total += duration;
            }

            var remaining = nowMillis % total;
            for (int i = 0; i < ids.size(); i++) {
                if (remaining < durations[i]) {
                    return ids.get(i);
                }

                remaining -= durations[i];
            }

            return ids.getLast();
        }
    }
}

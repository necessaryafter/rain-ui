package com.rainframework.ui.client.content;

import com.rainframework.ui.protocol.AssetInfo;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Reads an asset's type, dimensions and frame count from its header, without decoding it. The client compares this
 * with the contract before handing the bytes to an image or font decoder, which is the riskiest code a server can
 * reach. Mirrors packages/cli/src/assets.ts.
 */
public final class AssetHeaders {
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

    private AssetHeaders() {
    }

    /** Whether the bytes are the type the contract declared, with the same dimensions and frames. */
    public static boolean matches(byte[] bytes, AssetInfo declared) {
        final var actual = read(bytes);
        if (actual == null || !actual.type().equals(declared.type())) {
            return false;
        }

        return Objects.equals(actual.width(), declared.width())
                && Objects.equals(actual.height(), declared.height())
                && Objects.equals(actual.frames(), declared.frames());
    }

    /** The detected header, with {@code bytes} left null; null when the file is not a supported format. */
    public static @Nullable AssetInfo read(byte[] bytes) {
        final var png = readPng(bytes);
        if (png != null) {
            return png;
        }

        final var jpeg = readJpeg(bytes);
        if (jpeg != null) {
            return jpeg;
        }

        final var gif = readGif(bytes);
        return gif != null ? gif : readFont(bytes);
    }

    // The first chunk of a PNG is always IHDR, which starts with the width and height.
    private static @Nullable AssetInfo readPng(byte[] bytes) {
        if (bytes.length < 24 || !Arrays.equals(bytes, 0, 8, PNG_SIGNATURE, 0, 8)) {
            return null;
        }

        if (!new String(bytes, 12, 4, StandardCharsets.US_ASCII).equals("IHDR")) {
            return null;
        }

        final var buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        final var width = Integer.toUnsignedLong(buffer.getInt(16));
        final var height = Integer.toUnsignedLong(buffer.getInt(20));

        return image("image/png", width, height, null);
    }

    // Walks the marker segments until a start-of-frame. C4, C8 and CC share the SOF range but are other segments.
    private static @Nullable AssetInfo readJpeg(byte[] bytes) {
        if (bytes.length < 4 || (bytes[0] & 0xff) != 0xff || (bytes[1] & 0xff) != 0xd8) {
            return null;
        }

        var offset = 2;
        while (offset + 4 <= bytes.length) {
            if ((bytes[offset] & 0xff) != 0xff) {
                return null;
            }

            final var marker = bytes[offset + 1] & 0xff;
            if (marker == 0xff) {
                offset++;
                continue;
            }

            if (marker == 0x01 || (marker >= 0xd0 && marker <= 0xd8)) {
                offset += 2;
                continue;
            }

            if (marker >= 0xc0 && marker <= 0xcf && marker != 0xc4 && marker != 0xc8 && marker != 0xcc) {
                if (offset + 9 > bytes.length) {
                    return null;
                }

                return image("image/jpeg", unsignedShort(bytes, offset + 7), unsignedShort(bytes, offset + 5), null);
            }

            if (marker == 0xda) {
                return null;
            }

            offset += 2 + unsignedShort(bytes, offset + 2);
        }

        return null;
    }

    // Counts frames by walking the blocks to the trailer: one image descriptor per frame.
    private static @Nullable AssetInfo readGif(byte[] bytes) {
        if (bytes.length < 13) {
            return null;
        }

        final var version = new String(bytes, 0, 6, StandardCharsets.US_ASCII);
        if (!version.equals("GIF87a") && !version.equals("GIF89a")) {
            return null;
        }

        final var width = littleShort(bytes, 6);
        final var height = littleShort(bytes, 8);
        var offset = 13 + colorTableSize(bytes[10]);
        var frames = 0L;

        while (offset < bytes.length) {
            switch (bytes[offset] & 0xff) {
                case 0x3b -> {
                    return frames > 0 ? image("image/gif", width, height, frames) : null;
                }
                case 0x2c -> {
                    if (offset + 10 > bytes.length) {
                        return null;
                    }

                    frames++;
                    offset += 10 + colorTableSize(bytes[offset + 9]) + 1;
                }
                case 0x21 -> offset += 2;
                default -> {
                    return null;
                }
            }

            final var next = skipSubBlocks(bytes, offset);
            if (next < 0) {
                return null;
            }

            offset = next;
        }

        return null;
    }

    private static int colorTableSize(byte flags) {
        return (flags & 0x80) != 0 ? 3 * (1 << ((flags & 0x07) + 1)) : 0;
    }

    private static int skipSubBlocks(byte[] bytes, int offset) {
        var current = offset;
        while (current < bytes.length) {
            final var size = bytes[current] & 0xff;
            if (size == 0) {
                return current + 1;
            }

            current += size + 1;
        }

        return -1;
    }

    // An sfnt font starts with its version tag: 0x00010000 or "true" for TrueType outlines, "OTTO" for CFF.
    private static @Nullable AssetInfo readFont(byte[] bytes) {
        if (bytes.length < 12) {
            return null;
        }

        final var tag = new String(bytes, 0, 4, StandardCharsets.ISO_8859_1);
        if (tag.equals("OTTO")) {
            return new AssetInfo("font/otf", null, null, null, null);
        }

        if (tag.equals("true") || ByteBuffer.wrap(bytes).getInt(0) == 0x00010000) {
            return new AssetInfo("font/ttf", null, null, null, null);
        }

        return null;
    }

    private static @Nullable AssetInfo image(String type, long width, long height, @Nullable Long frames) {
        if (width == 0 || height == 0) {
            return null;
        }

        return new AssetInfo(type, null, width, height, frames);
    }

    private static long unsignedShort(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    private static long littleShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }
}

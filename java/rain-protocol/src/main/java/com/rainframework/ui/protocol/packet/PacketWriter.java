package com.rainframework.ui.protocol.packet;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Writes the Rain wire format. Invalid values throw IllegalArgumentException: they are a bug on the sending side. */
public final class PacketWriter {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    /** Unsigned LEB128, the same VarInt Minecraft uses; only non-negative values are allowed. */
    public PacketWriter writeVarInt(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("VarInt must not be negative: " + value);
        }

        var remaining = value;
        while ((remaining & ~0x7f) != 0) {
            out.write((remaining & 0x7f) | 0x80);
            remaining >>>= 7;
        }

        out.write(remaining);
        return this;
    }

    public PacketWriter writeString(String value, int maxBytes) {
        final var utf8 = value.getBytes(StandardCharsets.UTF_8);
        if (utf8.length > maxBytes) {
            throw new IllegalArgumentException("String of " + utf8.length + " bytes is over the limit of " + maxBytes);
        }

        writeVarInt(utf8.length);
        out.writeBytes(utf8);
        return this;
    }

    public PacketWriter writeBytes(byte[] bytes) {
        out.writeBytes(bytes);
        return this;
    }

    public byte[] toByteArray() {
        return out.toByteArray();
    }
}

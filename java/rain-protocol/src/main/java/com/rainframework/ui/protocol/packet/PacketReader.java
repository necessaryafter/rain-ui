package com.rainframework.ui.protocol.packet;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Reads the Rain wire format from untrusted bytes. Every length is checked against the field's limit and against what
 * is left in the packet before anything is allocated.
 */
public final class PacketReader {
    private static final int MAX_VARINT_BYTES = 5;

    private final byte[] bytes;
    private int position = 0;

    public PacketReader(byte[] bytes) {
        this.bytes = bytes;
    }

    public int readVarInt() throws PacketDecodeException {
        int value = 0;

        for (int i = 0; i < MAX_VARINT_BYTES; i++) {
            final var current = readByte();
            value |= (current & 0x7f) << (7 * i);

            if ((current & 0x80) == 0) {
                // The fifth byte only has room for the top 4 bits of an int; anything above would wrap negative.
                if (i == MAX_VARINT_BYTES - 1 && (current & 0xf8) != 0) {
                    throw new PacketDecodeException("VarInt does not fit a non-negative int");
                }

                return value;
            }
        }

        throw new PacketDecodeException("VarInt is longer than " + MAX_VARINT_BYTES + " bytes");
    }

    public String readString(int maxBytes) throws PacketDecodeException {
        final var length = readVarInt();
        if (length > maxBytes) {
            throw new PacketDecodeException("String of " + length + " bytes is over the limit of " + maxBytes);
        }

        final var utf8 = ByteBuffer.wrap(readBytes(length));
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(utf8)
                    .toString();
        } catch (CharacterCodingException e) {
            throw new PacketDecodeException("String is not valid UTF-8");
        }
    }

    public byte[] readBytes(int length) throws PacketDecodeException {
        if (length > remaining()) {
            throw new PacketDecodeException("Packet ends " + (length - remaining()) + " bytes early");
        }

        final var read = new byte[length];
        System.arraycopy(bytes, position, read, 0, length);
        position += length;
        return read;
    }

    public int remaining() {
        return bytes.length - position;
    }

    private int readByte() throws PacketDecodeException {
        if (remaining() < 1) {
            throw new PacketDecodeException("Packet ends early");
        }

        return bytes[position++] & 0xff;
    }
}

package com.rainframework.ui.protocol.packet;

public final class PacketWriter {

    /** Unsigned LEB128, the same VarInt Minecraft uses; only non-negative values are allowed. */
    public PacketWriter writeVarInt(int value) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public byte[] toByteArray() {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}

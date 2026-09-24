package com.rainframework.ui.protocol.packet;

public interface PacketCodec<T> {

    void encode(T packet, PacketWriter writer);

    T decode(PacketReader reader) throws PacketDecodeException;
}

package com.rainframework.ui.protocol.packet;

import java.util.function.BiConsumer;

public interface PacketCodec<T> {

    void encode(T packet, PacketWriter writer);

    T decode(PacketReader reader) throws PacketDecodeException;

    static <T> PacketCodec<T> of(BiConsumer<T, PacketWriter> encoder, Decoder<T> decoder) {
        return new PacketCodec<>() {

            @Override
            public void encode(T packet, PacketWriter writer) {
                encoder.accept(packet, writer);
            }

            @Override
            public T decode(PacketReader reader) throws PacketDecodeException {
                return decoder.decode(reader);
            }
        };
    }

    @FunctionalInterface
    interface Decoder<T> {

        T decode(PacketReader reader) throws PacketDecodeException;
    }
}

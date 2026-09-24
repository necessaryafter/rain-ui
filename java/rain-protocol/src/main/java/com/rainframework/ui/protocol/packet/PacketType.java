package com.rainframework.ui.protocol.packet;

/** A packet sent as its own custom payload, identified by {@code id}; its bytes carry no type header. */
public record PacketType<T>(String id, PacketCodec<T> codec) {

    public byte[] encode(T packet) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Fails if the bytes do not hold exactly one packet: trailing bytes are an error, not ignored. */
    public T decode(byte[] bytes) throws PacketDecodeException {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}

package com.rainframework.ui.protocol.packet;

/** A packet sent as its own custom payload, identified by {@code id}; its bytes carry no type header. */
public record PacketType<T>(String id, PacketCodec<T> codec) {

    public byte[] encode(T packet) {
        final var writer = new PacketWriter();
        codec.encode(packet, writer);

        return writer.toByteArray();
    }

    /** Fails if the bytes do not hold exactly one packet: trailing bytes are an error, not ignored. */
    public T decode(byte[] bytes) throws PacketDecodeException {
        final var reader = new PacketReader(bytes);
        final var packet = codec.decode(reader);

        if (reader.remaining() > 0) {
            throw new PacketDecodeException(reader.remaining() + " trailing bytes after " + id);
        }

        return packet;
    }
}

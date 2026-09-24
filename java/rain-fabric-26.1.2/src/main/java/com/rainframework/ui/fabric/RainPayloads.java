package com.rainframework.ui.fabric;

import com.rainframework.ui.protocol.packet.PacketDecodeException;
import com.rainframework.ui.protocol.packet.PacketType;
import com.rainframework.ui.protocol.packet.RainPackets;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Maps every Rain packet type to a custom payload type and registers them with Fabric. */
public final class RainPayloads {
    private static final Map<PacketType<?>, CustomPacketPayload.Type<RainPayload>> TYPES = new ConcurrentHashMap<>();

    private static final List<PacketType<?>> CLIENTBOUND = List.of(
            RainPackets.SERVER_HELLO,
            RainPackets.OPEN_SCREEN,
            RainPackets.UPDATE_SCREEN,
            RainPackets.INTERACTION_REJECTED,
            RainPackets.CLOSE_SCREEN);

    private static final List<PacketType<?>> SERVERBOUND = List.of(
            RainPackets.CLIENT_HELLO,
            RainPackets.INTERACT,
            RainPackets.SCREEN_CLOSED,
            RainPackets.SCREEN_FAILED);

    private RainPayloads() {
    }

    public static void register() {
        for (final var packet : CLIENTBOUND) {
            PayloadTypeRegistry.clientboundPlay().register(type(packet), codec(type(packet)));
        }

        for (final var packet : SERVERBOUND) {
            PayloadTypeRegistry.serverboundPlay().register(type(packet), codec(type(packet)));
        }
    }

    public static CustomPacketPayload.Type<RainPayload> type(PacketType<?> packet) {
        return TYPES.computeIfAbsent(packet, key -> {
            final var id = key.id().split(":", 2);
            return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(id[0], id[1]));
        });
    }

    public static <T> RainPayload encode(PacketType<T> packet, T value) {
        return new RainPayload(type(packet), packet.encode(value));
    }

    public static <T> T decode(PacketType<T> packet, RainPayload payload) throws PacketDecodeException {
        return packet.decode(payload.bytes());
    }

    // Vanilla already caps a custom payload's size, so the whole remaining buffer is the packet.
    private static StreamCodec<RegistryFriendlyByteBuf, RainPayload> codec(CustomPacketPayload.Type<RainPayload> type) {
        return StreamCodec.of(
                (buffer, payload) -> buffer.writeBytes(payload.bytes()),
                buffer -> {
                    final var bytes = new byte[buffer.readableBytes()];
                    buffer.readBytes(bytes);
                    return new RainPayload(type, bytes);
                });
    }
}

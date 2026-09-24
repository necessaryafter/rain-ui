package com.rainframework.ui.protocol.packet;

import com.rainframework.ui.protocol.Limits;

/**
 * Every Rain packet, each sent as its own custom payload under {@link PacketType#id()}. Server to client first, then
 * client to server.
 */
public final class RainPackets {
    public static final PacketType<ServerHello> SERVER_HELLO = new PacketType<>(
            "rain:server_hello",
            PacketCodec.of(
                    (packet, writer) -> {
                        writer.writeVarInt(packet.protocolVersion());
                        PacketFields.writeAssetBaseUrl(writer, packet.assetBaseUrl());
                    },
                    reader -> new ServerHello(reader.readVarInt(), PacketFields.readAssetBaseUrl(reader))));

    public static final PacketType<OpenScreen> OPEN_SCREEN = new PacketType<>(
            "rain:open_screen",
            PacketCodec.of(
                    (packet, writer) -> {
                        writer.writeVarInt(packet.instanceId());
                        PacketFields.writeId(writer, packet.screenId());
                        PacketFields.writeHash(writer, packet.contractHash());
                        writer.writeVarInt(packet.revision());
                        writer.writeString(packet.propertiesJson(), Limits.MAX_PROPERTIES_BYTES);
                    },
                    reader -> new OpenScreen(
                            reader.readVarInt(),
                            PacketFields.readId(reader),
                            PacketFields.readHash(reader),
                            reader.readVarInt(),
                            reader.readString(Limits.MAX_PROPERTIES_BYTES))));

    public static final PacketType<UpdateScreen> UPDATE_SCREEN = new PacketType<>(
            "rain:update_screen",
            PacketCodec.of(
                    (packet, writer) -> writer
                            .writeVarInt(packet.instanceId())
                            .writeVarInt(packet.revision())
                            .writeString(packet.propertiesJson(), Limits.MAX_PROPERTIES_BYTES),
                    reader -> new UpdateScreen(
                            reader.readVarInt(),
                            reader.readVarInt(),
                            reader.readString(Limits.MAX_PROPERTIES_BYTES))));

    public static final PacketType<InteractionRejected> INTERACTION_REJECTED = new PacketType<>(
            "rain:interaction_rejected",
            PacketCodec.of(
                    (packet, writer) -> writer.writeVarInt(packet.instanceId()).writeVarInt(packet.revision()),
                    reader -> new InteractionRejected(reader.readVarInt(), reader.readVarInt())));

    public static final PacketType<CloseScreen> CLOSE_SCREEN = new PacketType<>(
            "rain:close_screen",
            PacketCodec.of(
                    (packet, writer) -> writer.writeVarInt(packet.instanceId()),
                    reader -> new CloseScreen(reader.readVarInt())));

    public static final PacketType<ClientHello> CLIENT_HELLO = new PacketType<>(
            "rain:client_hello",
            PacketCodec.of(
                    (packet, writer) -> writer.writeVarInt(packet.protocolVersion()),
                    reader -> new ClientHello(reader.readVarInt())));

    public static final PacketType<Interact> INTERACT = new PacketType<>(
            "rain:interact",
            PacketCodec.of(
                    (packet, writer) -> {
                        writer.writeVarInt(packet.instanceId()).writeVarInt(packet.revision());
                        PacketFields.writeId(writer, packet.actionId());
                        writer.writeString(packet.payloadJson(), Limits.MAX_PAYLOAD_BYTES);
                    },
                    reader -> new Interact(
                            reader.readVarInt(),
                            reader.readVarInt(),
                            PacketFields.readId(reader),
                            reader.readString(Limits.MAX_PAYLOAD_BYTES))));

    public static final PacketType<ScreenClosed> SCREEN_CLOSED = new PacketType<>(
            "rain:screen_closed",
            PacketCodec.of(
                    (packet, writer) -> writer.writeVarInt(packet.instanceId()),
                    reader -> new ScreenClosed(reader.readVarInt())));

    public static final PacketType<ScreenFailed> SCREEN_FAILED = new PacketType<>(
            "rain:screen_failed",
            PacketCodec.of(
                    (packet, writer) -> {
                        writer.writeVarInt(packet.instanceId());
                        PacketFields.writeReason(writer, packet.reason());
                    },
                    reader -> new ScreenFailed(reader.readVarInt(), PacketFields.readReason(reader))));

    private RainPackets() {
    }
}

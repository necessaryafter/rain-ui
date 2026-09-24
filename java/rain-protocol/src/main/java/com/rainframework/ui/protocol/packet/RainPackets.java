package com.rainframework.ui.protocol.packet;

public final class RainPackets {
    public static final PacketType<ServerHello> SERVER_HELLO = new PacketType<>("rain:server_hello", null);
    public static final PacketType<OpenScreen> OPEN_SCREEN = new PacketType<>("rain:open_screen", null);
    public static final PacketType<UpdateScreen> UPDATE_SCREEN = new PacketType<>("rain:update_screen", null);
    public static final PacketType<InteractionRejected> INTERACTION_REJECTED =
            new PacketType<>("rain:interaction_rejected", null);
    public static final PacketType<CloseScreen> CLOSE_SCREEN = new PacketType<>("rain:close_screen", null);

    public static final PacketType<ClientHello> CLIENT_HELLO = new PacketType<>("rain:client_hello", null);
    public static final PacketType<Interact> INTERACT = new PacketType<>("rain:interact", null);
    public static final PacketType<ScreenClosed> SCREEN_CLOSED = new PacketType<>("rain:screen_closed", null);
    public static final PacketType<ScreenFailed> SCREEN_FAILED = new PacketType<>("rain:screen_failed", null);

    private RainPackets() {
    }
}

package com.rainframework.ui.client.session;

import com.rainframework.ui.protocol.packet.PacketType;

public interface ServerConnection {

    <T> void send(PacketType<T> type, T packet);
}

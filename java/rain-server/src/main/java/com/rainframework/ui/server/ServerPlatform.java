package com.rainframework.ui.server;

import com.rainframework.ui.protocol.packet.PacketType;

import java.util.concurrent.Executor;

/** What the version module provides to the server core. */
public interface ServerPlatform {

    <T> void send(PlayerRef player, PacketType<T> type, T packet);

    /** The server thread; interaction events are always fired on it. */
    Executor mainThread();

    /** Encodes an item with the vanilla network codec of this version, as base64 (decisions.md §8). */
    String encodeItem(Object item);

    void debug(String message);

    void warn(String message);
}

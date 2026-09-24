package com.rainframework.ui.fabric;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** One Rain packet as a custom payload: the bytes are the Rain wire format, decoded by rain-protocol. */
public record RainPayload(Type<RainPayload> type, byte[] bytes) implements CustomPacketPayload {
}

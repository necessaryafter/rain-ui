package com.rainframework.ui.fabric;

import com.rainframework.ui.protocol.packet.PacketType;
import com.rainframework.ui.server.PlayerRef;
import com.rainframework.ui.server.ServerPlatform;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.Base64;
import java.util.concurrent.Executor;

public final class FabricServerPlatform implements ServerPlatform {
    private final MinecraftServer server;
    private final Logger logger;

    public FabricServerPlatform(MinecraftServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Override
    public <T> void send(PlayerRef player, PacketType<T> type, T packet) {
        final var target = server.getPlayerList().getPlayer(player.id());
        if (target == null) {
            return;
        }

        ServerPlayNetworking.send(target, RainPayloads.encode(type, packet));
    }

    @Override
    public Executor mainThread() {
        return server;
    }

    // The same codec vanilla uses for container slots, with this server's registries (decisions.md §8).
    @Override
    public String encodeItem(Object item) {
        if (!(item instanceof ItemStack stack)) {
            throw new IllegalArgumentException("putItem expects an ItemStack, got " + item.getClass().getName());
        }

        final var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        try {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack);

            final var bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return Base64.getEncoder().encodeToString(bytes);
        } finally {
            buffer.release();
        }
    }

    @Override
    public void debug(String message) {
        logger.debug(message);
    }

    @Override
    public void warn(String message) {
        logger.warn(message);
    }
}

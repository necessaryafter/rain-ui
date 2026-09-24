package com.rainframework.ui;

import com.rainframework.ui.fabric.FabricServerPlatform;
import com.rainframework.ui.fabric.RainConfig;
import com.rainframework.ui.fabric.RainPayloads;
import com.rainframework.ui.protocol.packet.PacketDecodeException;
import com.rainframework.ui.protocol.packet.PacketType;
import com.rainframework.ui.protocol.packet.RainPackets;
import com.rainframework.ui.server.ContractRegistry;
import com.rainframework.ui.server.PlayerRef;
import com.rainframework.ui.server.RainServer;
import com.rainframework.ui.server.http.ContentHttpServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;

public final class RainUIServer implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("rain-ui");

    private @Nullable ContentHttpServer http;

    @Override
    public void onInitialize() {
        RainPayloads.register();

        receive(RainPackets.CLIENT_HELLO, (player, packet) -> RainUI.server().onClientHello(player, packet));
        receive(RainPackets.INTERACT, (player, packet) -> RainUI.server().onInteract(player, packet));
        receive(RainPackets.SCREEN_CLOSED, (player, packet) -> RainUI.server().onScreenClosed(player, packet));
        receive(RainPackets.SCREEN_FAILED, (player, packet) -> RainUI.server().onScreenFailed(player, packet));

        ServerLifecycleEvents.SERVER_STARTING.register(this::start);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> stop());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                RainUI.server().onPlayerJoin(RainUI.player(handler.getPlayer())));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                RainUI.server().onPlayerLeave(RainUI.player(handler.getPlayer())));
    }

    // The player always comes from the connection, never from the packet (spec §3.7).
    private static <T> void receive(PacketType<T> type, BiConsumer<PlayerRef, T> handler) {
        ServerPlayNetworking.registerGlobalReceiver(RainPayloads.type(type), (payload, context) -> {
            try {
                handler.accept(RainUI.player(context.player()), RainPayloads.decode(type, payload));
            } catch (PacketDecodeException e) {
                LOGGER.debug("Dropped a malformed {} from {}: {}", type.id(), context.player().getName().getString(),
                        e.getMessage());
            }
        });
    }

    private void start(MinecraftServer server) {
        final var directory = FabricLoader.getInstance().getConfigDir().resolve("rain-ui");

        try {
            final var config = RainConfig.load(directory.resolve("rain-ui.properties"));
            final var registry = loadRegistry(directory.resolve("dist"));

            http = config.httpEnabled()
                    ? ContentHttpServer.start(new InetSocketAddress(config.httpBind(), config.httpPort()), registry)
                    : null;

            RainUI.start(RainServer.builder()
                    .platform(new FabricServerPlatform(server, LOGGER))
                    .registry(registry)
                    .assetBaseUrl(assetBaseUrl(config, server))
                    .adapters(RainUI.adapters())
                    .interactions(RainUI.interactions())
                    .build());

            LOGGER.info("Rain UI loaded {} screen(s) from {}", registry.screens().size(), directory.resolve("dist"));
        } catch (IOException e) {
            throw new UncheckedIOException("Rain UI could not start", e);
        }
    }

    private void stop() {
        if (http != null) {
            http.close();
            http = null;
        }

        RainUI.stop();
    }

    // A server with no dist/ yet still starts, with no screens, so the mod can be installed before any build.
    private static ContractRegistry loadRegistry(Path dist) throws IOException {
        if (!Files.exists(dist.resolve("manifest.json"))) {
            LOGGER.warn("No Rain UI build found at {}; run rain build and copy dist/ there", dist);
            Files.createDirectories(dist);
            Files.writeString(dist.resolve("manifest.json"), "{\"schemaVersion\":0,\"screens\":{}}");
        }

        return ContractRegistry.load(dist);
    }

    // Without asset-base-url the built-in server is used at the address players reach this server on.
    private static String assetBaseUrl(RainConfig config, MinecraftServer server) {
        if (!config.assetBaseUrl().isEmpty()) {
            return config.assetBaseUrl();
        }

        final var localIp = server.getLocalIp();
        final var host = localIp == null || localIp.isEmpty() ? "127.0.0.1" : localIp;
        if (host.equals("127.0.0.1")) {
            LOGGER.warn("Rain UI serves assets on http://127.0.0.1:{}, which only local players can reach; "
                    + "set asset-base-url in config/rain-ui/rain-ui.properties", config.httpPort());
        }

        return "http://" + host + ":" + config.httpPort();
    }
}

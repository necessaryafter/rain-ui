package com.rainframework.ui;

import com.rainframework.ui.client.content.ConsentStore;
import com.rainframework.ui.client.content.ContentFetcher;
import com.rainframework.ui.client.content.ContentStore;
import com.rainframework.ui.client.session.ClientSession;
import com.rainframework.ui.client.session.ServerConnection;
import com.rainframework.ui.fabric.RainPayloads;
import com.rainframework.ui.fabric.client.FabricClientPlatform;
import com.rainframework.ui.protocol.packet.PacketDecodeException;
import com.rainframework.ui.protocol.packet.PacketType;
import com.rainframework.ui.protocol.packet.RainPackets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

public final class RainUIClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("rain-ui");
    private static final long CACHE_BYTES = 512L * 1024 * 1024;

    private final ExecutorService downloads = Executors.newCachedThreadPool(runnable -> {
        final var thread = new Thread(runnable, "rain-ui-download");
        thread.setDaemon(true);
        return thread;
    });

    private @Nullable ClientSession session;

    @Override
    public void onInitializeClient() {
        final var directory = FabricLoader.getInstance().getGameDir().resolve("rain-ui");
        final var store = new ContentStore(directory.resolve("cache"), CACHE_BYTES);
        final var consent = new ConsentStore(directory.resolve("consent.json"));
        final var fetcher = new ContentFetcher(downloads);

        receive(RainPackets.SERVER_HELLO, ClientSession::onServerHello);
        receive(RainPackets.OPEN_SCREEN, ClientSession::onOpenScreen);
        receive(RainPackets.UPDATE_SCREEN, ClientSession::onUpdateScreen);
        receive(RainPackets.INTERACTION_REJECTED, ClientSession::onInteractionRejected);
        receive(RainPackets.CLOSE_SCREEN, ClientSession::onCloseScreen);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            final var platform = new FabricClientPlatform(store);
            session = ClientSession.builder()
                    .platform(platform)
                    .connection(new ServerConnection() {

                        @Override
                        public <T> void send(PacketType<T> type, T packet) {
                            ClientPlayNetworking.send(RainPayloads.encode(type, packet));
                        }
                    })
                    .store(store)
                    .fetcher(fetcher)
                    .consent(consent)
                    .build();
            platform.attach(session);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> session = null);
    }

    private <T> void receive(PacketType<T> type, BiConsumer<ClientSession, T> handler) {
        ClientPlayNetworking.registerGlobalReceiver(RainPayloads.type(type), (payload, context) -> {
            if (session == null) {
                return;
            }

            try {
                handler.accept(session, RainPayloads.decode(type, payload));
            } catch (PacketDecodeException e) {
                LOGGER.debug("Dropped a malformed {} from the server: {}", type.id(), e.getMessage());
            }
        });
    }
}

package com.rainframework.ui;

import com.rainframework.ui.server.Adapter;
import com.rainframework.ui.server.Adapters;
import com.rainframework.ui.server.Event;
import com.rainframework.ui.server.InteractionEvent;
import com.rainframework.ui.server.PlayerRef;
import com.rainframework.ui.server.Properties;
import com.rainframework.ui.server.RainServer;
import com.rainframework.ui.server.ScreenInstance;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * The API plugins use. Adapters and interaction handlers can be registered at any time; screens can be opened once
 * the server has started.
 */
public final class RainUI {
    // Plugins register adapters and handlers during mod init, before any server exists, so these outlive servers.
    private static final Adapters ADAPTERS = new Adapters();
    private static final Event<InteractionEvent> INTERACTIONS = new Event<>();

    private static volatile @Nullable RainServer server;

    private RainUI() {
    }

    public static <T> void registerAdapter(Class<T> type, Adapter<? super T> adapter) {
        ADAPTERS.register(type, adapter);
    }

    public static Event<InteractionEvent> interactions() {
        return INTERACTIONS;
    }

    /** Null when the player has no compatible Rain client; see {@link RainServer#open}. */
    public static @Nullable ScreenInstance open(ServerPlayer player, String screenId, Properties properties) {
        return server().open(player(player), screenId, properties);
    }

    public static boolean update(ScreenInstance instance, Properties properties) {
        return server().update(instance, properties);
    }

    public static void close(ScreenInstance instance) {
        server().close(instance);
    }

    public static List<ScreenInstance> openInstances(String screenId) {
        return server().openInstances(screenId);
    }

    public static PlayerRef player(ServerPlayer player) {
        return new PlayerRef(player.getUUID(), player.getGameProfile().getName());
    }

    public static RainServer server() {
        return Objects.requireNonNull(server, "Rain UI screens can only be opened after the server started");
    }

    static Adapters adapters() {
        return ADAPTERS;
    }

    static void start(RainServer started) {
        server = started;
    }

    static void stop() {
        server = null;
    }
}

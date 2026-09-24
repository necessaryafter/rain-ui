package com.rainframework.ui.fabric.client;

import com.rainframework.ui.client.content.ContentStore;
import com.rainframework.ui.client.screen.ScreenController;
import com.rainframework.ui.client.session.ClientPlatform;
import com.rainframework.ui.client.session.ClientSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class FabricClientPlatform implements ClientPlatform {
    private final ContentStore store;
    private @Nullable ClientSession session;

    public FabricClientPlatform(ContentStore store) {
        this.store = store;
    }

    public void attach(ClientSession attached) {
        session = attached;
    }

    @Override
    public Executor mainThread() {
        return Minecraft.getInstance();
    }

    @Override
    public void openScreen(ScreenController controller) {
        if (session == null) {
            return;
        }

        Minecraft.getInstance().setScreen(new RainScreen(controller, session, new AssetTextures(store)));
    }

    @Override
    public void closeScreen(int instanceId) {
        if (Minecraft.getInstance().screen instanceof RainScreen screen && screen.instanceId() == instanceId) {
            screen.closeFromServer();
        }
    }

    // Same shape as the vanilla resource pack prompt: the host is shown, the answer is remembered by the session.
    @Override
    public CompletableFuture<Boolean> askConsent(String origin, long bytes) {
        final var answer = new CompletableFuture<Boolean>();
        final var minecraft = Minecraft.getInstance();
        final var previous = minecraft.screen;
        final var size = bytes < 0 ? "" : " (" + Math.max(1, bytes / 1024) + " KiB)";

        minecraft.setScreen(new ConfirmScreen(
                accepted -> {
                    minecraft.setScreen(previous);
                    answer.complete(accepted);
                },
                Component.literal("Rain UI"),
                Component.literal("Este servidor quer baixar interfaces de " + origin + size + ". Permitir?")));
        return answer;
    }

    @Override
    public void warn(String message) {
        RainUIClientLog.warn(message);
    }
}

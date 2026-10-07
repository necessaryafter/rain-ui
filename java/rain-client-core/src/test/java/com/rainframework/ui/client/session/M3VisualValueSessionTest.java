package com.rainframework.ui.client.session;

import com.rainframework.ui.client.content.ConsentStore;
import com.rainframework.ui.client.content.ContentStore;
import com.rainframework.ui.client.screen.ScreenController;
import com.rainframework.ui.protocol.packet.OpenScreen;
import com.rainframework.ui.protocol.packet.PacketType;
import com.rainframework.ui.protocol.packet.ScreenFailed;
import com.rainframework.ui.protocol.packet.ScreenFailureReason;
import com.rainframework.ui.protocol.packet.UpdateScreen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class M3VisualValueSessionTest {
    private static final String CONTRACT = """
            {"schemaVersion":0,"id":"test:visual","properties":{"width":{"kind":"int"}},"actions":{},
             "root":{"type":"box","props":{"width":{"$bind":"width"}},"children":[]}}
            """;

    @TempDir
    Path cache;

    @Test
    void rejectsAnInvalidResolvedWidthOnUpdate() throws Exception {
        final var store = new ContentStore(cache.resolve("content"), 1024 * 1024);
        final var bytes = CONTRACT.getBytes(StandardCharsets.UTF_8);
        final var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        store.write(hash, bytes);

        final var platform = new FakePlatform();
        final var connection = new FakeConnection();
        final var session = ClientSession.builder()
                .platform(platform)
                .connection(connection)
                .store(store)
                .consent(new ConsentStore(cache.resolve("consent.json")))
                .build();

        session.onOpenScreen(new OpenScreen(1, "test:visual", hash, 0, "{\"width\":10}"));
        assertNotNull(platform.opened);

        session.onUpdateScreen(new UpdateScreen(1, 1, "{\"width\":-4}"));

        assertEquals(List.of(new ScreenFailed(1, ScreenFailureReason.INVALID_PROPERTIES)), connection.packets);
        assertEquals(1, platform.closedId);
        assertNull(session.current());
    }

    private static final class FakePlatform implements ClientPlatform {
        ScreenController opened;
        int closedId = -1;

        @Override
        public Executor mainThread() {
            return Runnable::run;
        }

        @Override
        public void openScreen(ScreenController controller) {
            opened = controller;
        }

        @Override
        public void closeScreen(int instanceId) {
            closedId = instanceId;
        }

        @Override
        public CompletableFuture<Boolean> askConsent(String origin, long bytes) {
            throw new AssertionError("cached contracts must not request downloads");
        }

        @Override
        public void warn(String message) {
        }
    }

    private static final class FakeConnection implements ServerConnection {
        final List<Object> packets = new ArrayList<>();

        @Override
        public <T> void send(PacketType<T> type, T packet) {
            packets.add(packet);
        }
    }
}

package com.rainframework.ui.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rainframework.ui.protocol.packet.ClientHello;
import com.rainframework.ui.protocol.packet.CloseScreen;
import com.rainframework.ui.protocol.packet.Interact;
import com.rainframework.ui.protocol.packet.InteractionRejected;
import com.rainframework.ui.protocol.packet.OpenScreen;
import com.rainframework.ui.protocol.packet.PacketType;
import com.rainframework.ui.protocol.packet.RainPackets;
import com.rainframework.ui.protocol.packet.RainProtocol;
import com.rainframework.ui.protocol.packet.ScreenClosed;
import com.rainframework.ui.protocol.packet.ScreenFailed;
import com.rainframework.ui.protocol.packet.ScreenFailureReason;
import com.rainframework.ui.protocol.packet.ServerHello;
import com.rainframework.ui.protocol.packet.UpdateScreen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RainServerTest {
    private static final PlayerRef ASH = new PlayerRef(UUID.randomUUID(), "Ash");
    private static final PlayerRef MISTY = new PlayerRef(UUID.randomUUID(), "Misty");

    record ShopItem(String id, String name, int price, String icon, boolean available) {
    }

    @TempDir
    Path dist;

    private final FakePlatform platform = new FakePlatform();
    private final AtomicLong clock = new AtomicLong();
    private final AtomicInteger purchases = new AtomicInteger();
    private RainServer server;

    @BeforeEach
    void start() throws Exception {
        server = RainServer.builder()
                .platform(platform)
                .registry(ContractRegistry.load(TestDist.create(dist, "shop", "gts")))
                .assetBaseUrl("https://cdn.example.com/rain")
                .nanoClock(clock::get)
                .build();

        server.getAdapters().register(ShopItem.class, (item, out) -> out
                .put("id", item.id())
                .put("name", item.name())
                .put("priceLabel", item.price() + " ₽")
                .putItem("icon", item.icon())
                .put("locked", !item.available()));

        // The shop from the spec: buying succeeds while the item is in stock.
        server.getInteractions().subscribe(event -> {
            if (!event.getActionId().equals("shop:buy")) {
                return;
            }

            if (!event.getPayload().getString("itemId").equals("pikachu")) {
                event.setResult(InteractionResult.reject());
                return;
            }

            purchases.incrementAndGet();
            event.setResult(InteractionResult.update(shop(false)));
        });

        join(ASH);
        join(MISTY);
        platform.sent.clear();
    }

    @Test
    void greetsAJoiningPlayerWithTheProtocolVersionAndAssetBaseUrl() {
        final var player = new PlayerRef(UUID.randomUUID(), "Brock");

        server.onPlayerJoin(player);

        assertEquals(new ServerHello(RainProtocol.VERSION, "https://cdn.example.com/rain"), platform.last().packet());
    }

    @Test
    void doesNotOpenScreensForAPlayerWithoutACompatibleClient() {
        final var vanilla = new PlayerRef(UUID.randomUUID(), "Gary");
        server.onPlayerJoin(vanilla);

        assertNull(server.open(vanilla, "shop:main", shop(true)));

        final var outdated = new PlayerRef(UUID.randomUUID(), "Oak");
        server.onPlayerJoin(outdated);
        server.onClientHello(outdated, new ClientHello(RainProtocol.VERSION + 1));

        assertNull(server.open(outdated, "shop:main", shop(true)));
    }

    @Test
    void opensTheShopWithTheItemsTheServerSends() {
        final var instance = server.open(ASH, "shop:main", shop(true));

        assertNotNull(instance);
        final var open = assertInstanceOf(OpenScreen.class, platform.last().packet());
        assertEquals("shop:main", open.screenId());
        assertEquals(instance.getContractHash(), open.contractHash());
        assertEquals(0, open.revision());
        assertTrue(open.propertiesJson().contains("\"priceLabel\":\"1250 ₽\""));
        assertTrue(open.propertiesJson().contains("\"icon\":\"" + encoded("pikachu-item") + "\""));
    }

    @Test
    void failsOnTheServerWhenPropertiesHaveAnUndeclaredKey() {
        final var properties = Properties.builder().add("items", List.of()).add("balance", 10).build();

        final var error = assertThrows(
                InvalidPropertiesException.class,
                () -> server.open(ASH, "shop:main", properties));

        assertEquals("balance", error.getError().getPath());
        assertTrue(platform.sent.isEmpty(), "nothing is sent for invalid properties");
    }

    @Test
    void opensWithPropertiesTakenFromJson() throws Exception {
        final var json = new ObjectMapper().readTree("""
                { "items": [ { "id": "a", "name": "A", "priceLabel": "1", "icon": "AA==", "locked": false } ] }
                """);

        assertNotNull(server.open(ASH, "shop:main", Properties.fromJson(json)));
        assertThrows(InvalidPropertiesException.class, () -> server.open(ASH, "shop:main",
                Properties.fromJson(new ObjectMapper().readTree("{\"items\": 3}"))));
    }

    @Test
    void failsWhenAValueHasNoAdapter() {
        final var properties = Properties.builder().add("items", List.of(new Object())).build();

        assertThrows(IllegalArgumentException.class, () -> server.open(ASH, "shop:main", properties));
    }

    @Test
    void completesAPurchase() {
        final var instance = server.open(ASH, "shop:main", shop(true));

        buy(ASH, instance.getId(), 0, "pikachu");

        assertEquals(1, purchases.get());
        final var update = assertInstanceOf(UpdateScreen.class, platform.last().packet());
        assertEquals(1, update.revision());
        assertTrue(update.propertiesJson().contains("\"locked\":true"));
    }

    @Test
    void rejectsAStaleRevisionAndResendsTheCurrentState() {
        final var instance = server.open(ASH, "shop:main", shop(true));
        server.update(instance, shop(false));
        platform.sent.clear();

        buy(ASH, instance.getId(), 0, "pikachu");

        assertEquals(0, purchases.get());
        assertEquals(new InteractionRejected(instance.getId(), 0), platform.sent.get(0).packet());
        final var resent = assertInstanceOf(UpdateScreen.class, platform.sent.get(1).packet());
        assertEquals(1, resent.revision());
    }

    @Test
    void buysOnceOnADoubleClick() {
        final var instance = server.open(ASH, "shop:main", shop(true));

        buy(ASH, instance.getId(), 0, "pikachu");
        buy(ASH, instance.getId(), 0, "pikachu");

        assertEquals(1, purchases.get());
    }

    @Test
    void rejectsWhenNoHandlerSetsAResultSoTheClientLeavesItsPendingState() throws Exception {
        final var instance = server.open(ASH, "gts:listings", gtsPage());
        platform.sent.clear();

        server.onInteract(ASH, new Interact(instance.getId(), 0, "gts:open", "{\"listingId\":\"a1\"}"));

        assertEquals(new InteractionRejected(instance.getId(), 0), platform.last().packet());
    }

    @Test
    void rejectsAnInteractionWithAnotherPlayersInstance() {
        final var mistyScreen = server.open(MISTY, "shop:main", shop(true));

        buy(ASH, mistyScreen.getId(), 0, "pikachu");

        assertEquals(0, purchases.get());
        assertInstanceOf(InteractionRejected.class, platform.last().packet());
    }

    @Test
    void rejectsAPayloadOutsideTheSchemaBeforeTheEvent() {
        final var instance = server.open(ASH, "shop:main", shop(true));
        final var fired = new AtomicInteger();
        server.getInteractions().subscribe(event -> fired.incrementAndGet());

        server.onInteract(ASH, new Interact(instance.getId(), 0, "shop:buy", "{\"itemId\":42}"));
        server.onInteract(ASH, new Interact(instance.getId(), 0, "shop:buy", "{\"itemId\":\"a\",\"price\":0}"));

        assertEquals(0, fired.get());
        assertInstanceOf(InteractionRejected.class, platform.last().packet());
    }

    @Test
    void rejectsAnActionTheScreenDoesNotDeclare() {
        final var instance = server.open(ASH, "shop:main", shop(true));

        server.onInteract(ASH, new Interact(instance.getId(), 0, "shop:steal", "{\"itemId\":\"pikachu\"}"));

        assertEquals(0, purchases.get());
        assertInstanceOf(InteractionRejected.class, platform.last().packet());
    }

    @Test
    void limitsInteractionsToTenPerSecondPerPlayer() {
        final var instance = server.open(ASH, "shop:main", shop(true));
        final var handled = new AtomicInteger();
        server.getInteractions().subscribe(event -> handled.incrementAndGet());

        for (int i = 0; i < 11; i++) {
            buy(ASH, instance.getId(), 0, "ditto");
        }

        assertEquals(10, handled.get());
        assertEquals(11, platform.count(RainPackets.INTERACTION_REJECTED), "the handler rejects ten, the limit one");

        clock.addAndGet(1_000_000_000L);
        platform.sent.clear();
        buy(ASH, instance.getId(), 0, "pikachu");
        assertEquals(1, purchases.get());
    }

    @Test
    void rejectsWhenAHandlerThrows() {
        final var instance = server.open(ASH, "shop:main", shop(true));
        server.getInteractions().subscribe(event -> {
            throw new IllegalStateException("economy offline");
        });

        buy(ASH, instance.getId(), 0, "pikachu");

        assertInstanceOf(InteractionRejected.class, platform.last().packet());
    }

    @Test
    void closesTheScreenWhenTheHandlerAsks() {
        final var instance = server.open(ASH, "shop:main", shop(true));
        server.getInteractions().subscribe(event -> event.setResult(InteractionResult.close()));

        buy(ASH, instance.getId(), 0, "ditto");

        assertEquals(new CloseScreen(instance.getId()), platform.last().packet());
        assertFalse(instance.isOpen());
    }

    @Test
    void pushesServerInitiatedUpdatesToEveryOpenInstance() {
        final var ash = server.open(ASH, "shop:main", shop(true));
        final var misty = server.open(MISTY, "shop:main", shop(true));

        for (final var instance : server.openInstances("shop:main")) {
            assertTrue(server.update(instance, shop(false)));
        }

        assertEquals(List.of(ash, misty).size(), server.openInstances("shop:main").size());
        assertEquals(1, ash.getRevision());
        assertEquals(1, misty.getRevision());
    }

    @Test
    void stopsTrackingAScreenThePlayerClosedOrFailedToOpen() {
        final var closed = server.open(ASH, "shop:main", shop(true));
        server.onScreenClosed(ASH, new ScreenClosed(closed.getId()));

        assertFalse(closed.isOpen());
        assertFalse(server.update(closed, shop(false)));

        final var failed = server.open(ASH, "shop:main", shop(true));
        server.onScreenFailed(ASH, new ScreenFailed(failed.getId(), ScreenFailureReason.CONSENT_DENIED));

        assertFalse(failed.isOpen());
        assertTrue(server.openInstances("shop:main").isEmpty());
    }

    @Test
    void replacesThePreviousScreenAndNeverReusesAnInstanceId() {
        final var first = server.open(ASH, "shop:main", shop(true));
        final var second = server.open(ASH, "shop:main", shop(true));

        assertFalse(first.isOpen());
        assertTrue(second.isOpen());
        assertTrue(second.getId() > first.getId());

        buy(ASH, first.getId(), 0, "pikachu");
        assertEquals(0, purchases.get());
    }

    private void join(PlayerRef player) {
        server.onPlayerJoin(player);
        server.onClientHello(player, new ClientHello(RainProtocol.VERSION));
    }

    private void buy(PlayerRef player, int instanceId, int revision, String itemId) {
        server.onInteract(player, new Interact(instanceId, revision, "shop:buy", "{\"itemId\":\"" + itemId + "\"}"));
    }

    private static Properties shop(boolean available) {
        return Properties.builder()
                .add("items", List.of(new ShopItem("pikachu", "Pikachu", 1250, "pikachu-item", available)))
                .build();
    }

    private static Properties gtsPage() {
        return Properties.builder()
                .add("pageLabel", "1/1")
                .add("isFirstPage", true)
                .add("isLastPage", true)
                .add("listings", List.of())
                .build();
    }

    private static String encoded(String item) {
        return Base64.getEncoder().encodeToString(item.getBytes(StandardCharsets.UTF_8));
    }

    private record Sent(PlayerRef player, PacketType<?> type, Object packet) {
    }

    // Runs server-thread tasks immediately and encodes an item as the base64 of its name.
    private static final class FakePlatform implements ServerPlatform {
        final List<Sent> sent = new ArrayList<>();

        @Override
        public <T> void send(PlayerRef player, PacketType<T> type, T packet) {
            sent.add(new Sent(player, type, packet));
        }

        @Override
        public Executor mainThread() {
            return Runnable::run;
        }

        @Override
        public String encodeItem(Object item) {
            return encoded(item.toString());
        }

        @Override
        public void debug(String message) {
        }

        @Override
        public void warn(String message) {
        }

        Sent last() {
            return sent.getLast();
        }

        long count(PacketType<?> type) {
            return sent.stream().filter(entry -> entry.type() == type).count();
        }
    }
}

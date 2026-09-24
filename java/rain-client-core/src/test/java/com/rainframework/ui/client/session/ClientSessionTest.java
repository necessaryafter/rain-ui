package com.rainframework.ui.client.session;

import com.rainframework.ui.client.content.ConsentStore;
import com.rainframework.ui.client.content.ContentFetcher;
import com.rainframework.ui.client.content.ContentStore;
import com.rainframework.ui.client.screen.ScreenController;
import com.rainframework.ui.protocol.packet.CloseScreen;
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
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.rainframework.ui.client.TestContracts.MONOSPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientSessionTest {
    private static final String PROPERTIES = "{\"title\":\"GTS\"}";

    @TempDir
    Path cache;

    private final Map<String, byte[]> served = new ConcurrentHashMap<>();
    private final AtomicInteger requests = new AtomicInteger();
    private final FakePlatform platform = new FakePlatform();
    private final FakeConnection connection = new FakeConnection();

    private HttpServer http;
    private ClientSession session;
    private byte[] banner;
    private String bannerHash;

    @BeforeEach
    void start() throws Exception {
        http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/", exchange -> {
            requests.incrementAndGet();
            final var name = exchange.getRequestURI().getPath().substring(1);
            final var body = served.get(name);
            if (name.startsWith("redirect/")) {
                exchange.getResponseHeaders().add("Location", "http://example.com/");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
                return;
            }

            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        http.start();

        banner = Files.readAllBytes(Path.of(System.getProperty("user.dir"),
                "packages/cli/test/fixtures/assets-basic/images/banner.png"));
        bannerHash = sha256(banner);
        served.put(bannerHash, banner);

        final var executor = Executors.newCachedThreadPool();
        session = ClientSession.builder()
                .platform(platform)
                .connection(connection)
                .store(new ContentStore(cache.resolve("content"), 1024 * 1024))
                .fetcher(new ContentFetcher(executor))
                .consent(new ConsentStore(cache.resolve("consent.json")))
                .build();

        session.onServerHello(new ServerHello(RainProtocol.VERSION, "http://127.0.0.1:" + http.getAddress().getPort()));
        connection.sent.clear();
    }

    @AfterEach
    void stop() {
        http.stop(0);
    }

    @Test
    void answersTheServerHelloWithTheClientVersion() {
        session.onServerHello(new ServerHello(RainProtocol.VERSION, "http://127.0.0.1:1"));

        assertEquals(RainPackets.CLIENT_HELLO, connection.sent.peek().type());
    }

    @Test
    void downloadsTheContractAndAssetsAfterConsentAndOpensTheScreen() throws Exception {
        final var contractHash = serveContract(bannerHash, 32, 16);

        session.onOpenScreen(new OpenScreen(1, "test:banner", contractHash, 0, PROPERTIES));

        final var screen = platform.awaitOpened();
        assertEquals(1, screen.getInstanceId());
        assertEquals(1, platform.consentAsks.get());

        screen.layout(MONOSPACE, 400, 300);
        assertTrue(screen.draw(0, 0).size() > 1);
    }

    @Test
    void asksForConsentOncePerOriginAndUsesTheCacheAfterwards() throws Exception {
        final var contractHash = serveContract(bannerHash, 32, 16);

        session.onOpenScreen(new OpenScreen(1, "test:banner", contractHash, 0, PROPERTIES));
        platform.awaitOpened();
        final var requestsAfterFirstOpen = requests.get();

        session.onOpenScreen(new OpenScreen(2, "test:banner", contractHash, 0, PROPERTIES));
        assertEquals(2, platform.awaitOpened().getInstanceId());

        assertEquals(1, platform.consentAsks.get());
        assertEquals(requestsAfterFirstOpen, requests.get(), "a cached contract and asset are not downloaded again");
    }

    @Test
    void downloadsNothingWhenThePlayerRefuses() throws Exception {
        platform.allow = false;
        final var contractHash = serveContract(bannerHash, 32, 16);

        session.onOpenScreen(new OpenScreen(1, "test:banner", contractHash, 0, PROPERTIES));

        assertEquals(new ScreenFailed(1, ScreenFailureReason.CONSENT_DENIED), connection.awaitFailure());
        assertEquals(0, requests.get());
    }

    @Test
    void discardsContentWhoseHashDoesNotMatch() throws Exception {
        final var contractHash = serveContract(bannerHash, 32, 16);
        served.put(contractHash, "{\"tampered\":true}".getBytes(StandardCharsets.UTF_8));

        session.onOpenScreen(new OpenScreen(1, "test:banner", contractHash, 0, PROPERTIES));

        assertEquals(new ScreenFailed(1, ScreenFailureReason.HASH_MISMATCH), connection.awaitFailure());
        assertNull(platform.opened.peek());
    }

    @Test
    void refusesPropertiesThatDoNotMatchTheContract() throws Exception {
        final var contractHash = serveContract(bannerHash, 32, 16);

        session.onOpenScreen(new OpenScreen(1, "test:banner", contractHash, 0, "{\"title\":3}"));

        assertEquals(new ScreenFailed(1, ScreenFailureReason.INVALID_PROPERTIES), connection.awaitFailure());
    }

    @Test
    void refusesAnInvalidContractEvenWhenItsHashMatches() throws Exception {
        final var contract = "{\"schemaVersion\":0,\"id\":\"test:bad\",\"properties\":{},\"actions\":{},"
                + "\"root\":{\"type\":\"marquee\",\"props\":{},\"children\":[]}}";
        final var bytes = contract.getBytes(StandardCharsets.UTF_8);
        served.put(sha256(bytes), bytes);

        session.onOpenScreen(new OpenScreen(1, "test:bad", sha256(bytes), 0, "{}"));

        assertEquals(new ScreenFailed(1, ScreenFailureReason.INVALID_CONTRACT), connection.awaitFailure());
    }

    @Test
    void refusesAnAssetWhoseHeaderDoesNotMatchTheContract() throws Exception {
        final var contractHash = serveContract(bannerHash, 16, 16);

        session.onOpenScreen(new OpenScreen(1, "test:banner", contractHash, 0, PROPERTIES));

        assertEquals(new ScreenFailed(1, ScreenFailureReason.DECODE_FAILED), connection.awaitFailure());
    }

    @Test
    void doesNotFollowRedirects() throws Exception {
        session.onServerHello(new ServerHello(RainProtocol.VERSION,
                "http://127.0.0.1:" + http.getAddress().getPort() + "/redirect"));
        connection.sent.clear();

        session.onOpenScreen(new OpenScreen(1, "test:banner", "a".repeat(64), 0, PROPERTIES));

        assertEquals(new ScreenFailed(1, ScreenFailureReason.DOWNLOAD_FAILED), connection.awaitFailure());
    }

    @Test
    void appliesUpdatesAndRejectionsToTheOpenScreen() throws Exception {
        session.onOpenScreen(new OpenScreen(1, "test:banner", serveContract(bannerHash, 32, 16), 0, PROPERTIES));
        final var screen = platform.awaitOpened();

        session.onUpdateScreen(new UpdateScreen(1, 5, "{\"title\":\"Leilões\"}"));
        assertEquals(5, screen.getRevision());

        session.onInteractionRejected(new InteractionRejected(1, 5));
        assertFalse(screen.isPending());
    }

    @Test
    void failsAndClosesOnAnInvalidUpdate() throws Exception {
        session.onOpenScreen(new OpenScreen(1, "test:banner", serveContract(bannerHash, 32, 16), 0, PROPERTIES));
        platform.awaitOpened();

        session.onUpdateScreen(new UpdateScreen(1, 5, "{\"title\":null}"));

        assertEquals(new ScreenFailed(1, ScreenFailureReason.INVALID_PROPERTIES), connection.awaitFailure());
        assertEquals(1, platform.closed.poll(5, TimeUnit.SECONDS));
        assertNull(session.current());
    }

    @Test
    void closesWhenTheServerAsksAndReportsWhenThePlayerCloses() throws Exception {
        final var contractHash = serveContract(bannerHash, 32, 16);
        session.onOpenScreen(new OpenScreen(1, "test:banner", contractHash, 0, PROPERTIES));
        platform.awaitOpened();

        session.onCloseScreen(new CloseScreen(1));
        assertEquals(1, platform.closed.poll(5, TimeUnit.SECONDS));

        session.onOpenScreen(new OpenScreen(2, "test:banner", contractHash, 0, PROPERTIES));
        platform.awaitOpened();
        session.onClosedByPlayer();

        final var sent = connection.sent.poll(5, TimeUnit.SECONDS);
        assertNotNull(sent);
        assertEquals(new ScreenClosed(2), sent.packet());
    }

    @Test
    void forgetsTheServerOnReset() throws Exception {
        session.onOpenScreen(new OpenScreen(1, "test:banner", serveContract(bannerHash, 32, 16), 0, PROPERTIES));
        platform.awaitOpened();

        session.reset();

        assertNull(session.current());
        assertEquals(1, platform.closed.poll(5, TimeUnit.SECONDS));

        session.onOpenScreen(new OpenScreen(2, "test:banner", "b".repeat(64), 0, PROPERTIES));
        assertEquals(new ScreenFailed(2, ScreenFailureReason.DOWNLOAD_FAILED), connection.awaitFailure());
    }

    // A screen that is still downloading when a newer one arrives is dropped instead of opened over it.
    @Test
    void ignoresAnOlderScreenThatFinishesLoadingLate() throws Exception {
        final var contractHash = serveContract(bannerHash, 32, 16);
        platform.holdConsent = new CompletableFuture<>();

        session.onOpenScreen(new OpenScreen(1, "test:banner", contractHash, 0, PROPERTIES));
        session.onOpenScreen(new OpenScreen(2, "test:banner", contractHash, 0, PROPERTIES));
        platform.holdConsent.complete(true);

        assertEquals(2, platform.awaitOpened().getInstanceId());
        assertNull(platform.opened.poll(500, TimeUnit.MILLISECONDS));
    }

    private String serveContract(String assetHash, int width, int height) throws Exception {
        final var contract = """
                {"schemaVersion":0,"id":"test:banner","properties":{"title":{"kind":"string"}},"actions":{},\
                "assets":{"%s":{"type":"image/png","bytes":%d,"width":%d,"height":%d}},\
                "root":{"type":"column","props":{},"children":[\
                {"type":"text","props":{"value":{"$bind":"title"}},"children":[]},\
                {"type":"image","props":{"src":{"$asset":"%s"}},"children":[]}]}}"""
                .formatted(assetHash, banner.length, width, height, assetHash);
        final var bytes = contract.getBytes(StandardCharsets.UTF_8);
        final var hash = sha256(bytes);

        served.put(hash, bytes);
        return hash;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static final class FakePlatform implements ClientPlatform {
        final LinkedBlockingQueue<ScreenController> opened = new LinkedBlockingQueue<>();
        final LinkedBlockingQueue<Integer> closed = new LinkedBlockingQueue<>();
        final AtomicInteger consentAsks = new AtomicInteger();
        final CopyOnWriteArrayList<String> warnings = new CopyOnWriteArrayList<>();
        volatile boolean allow = true;
        volatile CompletableFuture<Boolean> holdConsent;

        @Override
        public Executor mainThread() {
            return Runnable::run;
        }

        @Override
        public void openScreen(ScreenController controller) {
            opened.add(controller);
        }

        @Override
        public void closeScreen(int instanceId) {
            closed.add(instanceId);
        }

        @Override
        public CompletableFuture<Boolean> askConsent(String origin, long bytes) {
            consentAsks.incrementAndGet();
            return holdConsent != null ? holdConsent : CompletableFuture.completedFuture(allow);
        }

        @Override
        public void warn(String message) {
            warnings.add(message);
        }

        ScreenController awaitOpened() throws InterruptedException {
            final var screen = opened.poll(5, TimeUnit.SECONDS);
            assertNotNull(screen, "screen did not open; warnings: " + warnings);
            return screen;
        }
    }

    private record Sent(PacketType<?> type, Object packet) {
    }

    private static final class FakeConnection implements ServerConnection {
        final LinkedBlockingQueue<Sent> sent = new LinkedBlockingQueue<>();

        @Override
        public <T> void send(PacketType<T> type, T packet) {
            sent.add(new Sent(type, packet));
        }

        ScreenFailed awaitFailure() throws InterruptedException {
            final var next = sent.poll(5, TimeUnit.SECONDS);
            assertNotNull(next, "no packet was sent");
            return assertInstanceOf(ScreenFailed.class, next.packet());
        }
    }
}

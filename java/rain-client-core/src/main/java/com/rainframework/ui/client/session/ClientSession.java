package com.rainframework.ui.client.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rainframework.ui.client.content.AssetHeaders;
import com.rainframework.ui.client.content.ConsentStore;
import com.rainframework.ui.client.content.ContentFetcher;
import com.rainframework.ui.client.content.ContentLoadException;
import com.rainframework.ui.client.content.ContentStore;
import com.rainframework.ui.client.screen.ScreenController;
import com.rainframework.ui.protocol.Contract;
import com.rainframework.ui.protocol.ContractParser;
import com.rainframework.ui.protocol.Limits;
import com.rainframework.ui.protocol.ParseException;
import com.rainframework.ui.protocol.packet.ClientHello;
import com.rainframework.ui.protocol.packet.CloseScreen;
import com.rainframework.ui.protocol.packet.Interact;
import com.rainframework.ui.protocol.packet.InteractionRejected;
import com.rainframework.ui.protocol.packet.OpenScreen;
import com.rainframework.ui.protocol.packet.RainPackets;
import com.rainframework.ui.protocol.packet.RainProtocol;
import com.rainframework.ui.protocol.packet.ScreenClosed;
import com.rainframework.ui.protocol.packet.ScreenFailed;
import com.rainframework.ui.protocol.packet.ScreenFailureReason;
import com.rainframework.ui.protocol.packet.ServerHello;
import com.rainframework.ui.protocol.packet.UpdateScreen;
import com.rainframework.ui.protocol.validation.ContractValidator;
import com.rainframework.ui.protocol.validation.PropertiesValidator;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.LongSupplier;

/**
 * The client side of one connection to a Rain server. Packet handlers run on the main thread; content loads run on
 * other threads and come back through {@link ClientPlatform#mainThread()}. At most one Rain screen is open at a time,
 * and a newer {@code OpenScreen} wins over one still loading.
 */
public final class ClientSession {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ClientPlatform platform;
    private final ServerConnection connection;
    private final ContentStore store;
    private final ContentFetcher fetcher;
    private final ConsentStore consent;
    private final LongSupplier nanoClock;
    private final ContractValidator contractValidator = new ContractValidator();
    private final PropertiesValidator propertiesValidator = new PropertiesValidator();

    private @Nullable String assetBaseUrl;
    private @Nullable ScreenController current;
    private int latestOpening = -1;

    @Builder
    private ClientSession(
            ClientPlatform platform,
            ServerConnection connection,
            ContentStore store,
            ContentFetcher fetcher,
            ConsentStore consent,
            @Nullable LongSupplier nanoClock
    ) {
        this.platform = platform;
        this.connection = connection;
        this.store = store;
        this.fetcher = fetcher;
        this.consent = consent;
        this.nanoClock = nanoClock == null ? System::nanoTime : nanoClock;
    }

    public void onServerHello(ServerHello hello) {
        if (hello.protocolVersion() != RainProtocol.VERSION) {
            platform.warn("Server speaks Rain protocol " + hello.protocolVersion() + ", this client "
                    + RainProtocol.VERSION + "; its screens may not open");
        }

        assetBaseUrl = hello.assetBaseUrl();
        connection.send(RainPackets.CLIENT_HELLO, new ClientHello(RainProtocol.VERSION));
    }

    public void onOpenScreen(OpenScreen packet) {
        latestOpening = packet.instanceId();

        loadContract(packet.contractHash())
                .thenCompose(contract -> prepare(contract, packet))
                .whenCompleteAsync(
                        (controller, error) -> finishOpening(packet, controller, error),
                        platform.mainThread());
    }

    public void onUpdateScreen(UpdateScreen packet) {
        final var screen = currentScreen(packet.instanceId());
        if (screen == null) {
            return;
        }

        final var properties = validProperties(screen.getContract(), packet.propertiesJson());
        if (properties == null) {
            fail(packet.instanceId(), ScreenFailureReason.INVALID_PROPERTIES, "Update has invalid properties");
            closeCurrent();
            return;
        }

        screen.update(packet.revision(), properties);
    }

    public void onInteractionRejected(InteractionRejected packet) {
        final var screen = currentScreen(packet.instanceId());
        if (screen != null) {
            screen.onRejected();
        }
    }

    public void onCloseScreen(CloseScreen packet) {
        if (currentScreen(packet.instanceId()) != null) {
            closeCurrent();
        }
    }

    /** Called by the version module for a click on the open screen. */
    public void click(double x, double y) {
        if (current == null) {
            return;
        }

        final Interact interact = current.click(x, y);
        if (interact != null) {
            connection.send(RainPackets.INTERACT, interact);
        }
    }

    /** Called by the version module when the player closes the screen (ESC). */
    public void onClosedByPlayer() {
        if (current == null) {
            return;
        }

        connection.send(RainPackets.SCREEN_CLOSED, new ScreenClosed(current.getInstanceId()));
        current = null;
    }

    public @Nullable ScreenController current() {
        return current;
    }

    private @Nullable ScreenController currentScreen(int instanceId) {
        return current != null && current.getInstanceId() == instanceId ? current : null;
    }

    private void closeCurrent() {
        if (current == null) {
            return;
        }

        platform.closeScreen(current.getInstanceId());
        current = null;
    }

    private CompletableFuture<Contract> loadContract(String hash) {
        return content(hash, Limits.MAX_CONTRACT_BYTES, -1).thenApply(bytes -> {
            try {
                final var contract = new ContractParser().parse(new String(bytes, StandardCharsets.UTF_8));
                final var result = contractValidator.validate(contract);
                if (!result.isValid()) {
                    throw new ContentLoadException(ScreenFailureReason.INVALID_CONTRACT,
                            result.getError().getCode() + " at " + result.getError().getPath());
                }

                return contract;
            } catch (ParseException e) {
                throw new ContentLoadException(ScreenFailureReason.INVALID_CONTRACT, e.getMessage());
            }
        });
    }

    // Properties are checked before any asset is downloaded, so a bad screen costs no bandwidth.
    private CompletableFuture<ScreenController> prepare(Contract contract, OpenScreen packet) {
        final var properties = validProperties(contract, packet.propertiesJson());
        if (properties == null) {
            return CompletableFuture.failedFuture(new ContentLoadException(
                    ScreenFailureReason.INVALID_PROPERTIES,
                    "Properties do not match the contract"));
        }

        return loadAssets(contract).thenApply(ignored -> new ScreenController(
                packet.instanceId(),
                packet.screenId(),
                contract,
                packet.revision(),
                properties,
                nanoClock));
    }

    private CompletableFuture<Void> loadAssets(Contract contract) {
        var missingBytes = 0L;
        for (final var entry : contract.assets().entrySet()) {
            if (!store.contains(entry.getKey())) {
                missingBytes += entry.getValue().bytes();
            }
        }

        final var loads = new ArrayList<CompletableFuture<byte[]>>();
        for (final var entry : contract.assets().entrySet()) {
            final var info = entry.getValue();

            loads.add(content(entry.getKey(), info.bytes(), missingBytes).thenApply(bytes -> {
                if (!AssetHeaders.matches(bytes, info)) {
                    throw new ContentLoadException(ScreenFailureReason.DECODE_FAILED,
                            entry.getKey() + " is not the " + info.type() + " the contract declares");
                }

                return bytes;
            }));
        }

        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new));
    }

    // Cached content never needs consent; downloading from the server's host does, asked once per origin.
    private CompletableFuture<byte[]> content(String hash, long maxBytes, long consentBytes) {
        final var cached = store.read(hash);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        final var baseUrl = assetBaseUrl;
        if (baseUrl == null) {
            return CompletableFuture.failedFuture(
                    new ContentLoadException(ScreenFailureReason.DOWNLOAD_FAILED, "The server sent no asset base URL"));
        }

        return consent(baseUrl, consentBytes)
                .thenCompose(ignored -> fetcher.fetch(baseUrl, hash, maxBytes))
                .thenApply(bytes -> {
                    store.write(hash, bytes);
                    return bytes;
                });
    }

    private CompletableFuture<Void> consent(String baseUrl, long bytes) {
        final var origin = ConsentStore.originOf(baseUrl);
        final var decided = consent.decision(origin);
        if (decided != null) {
            return decided ? CompletableFuture.completedFuture(null) : denied(origin);
        }

        return platform.askConsent(origin, bytes)
                .thenComposeAsync(allowed -> {
                    consent.remember(origin, allowed);
                    return allowed ? CompletableFuture.<Void>completedFuture(null) : denied(origin);
                }, platform.mainThread());
    }

    private static CompletableFuture<Void> denied(String origin) {
        return CompletableFuture.failedFuture(
                new ContentLoadException(ScreenFailureReason.CONSENT_DENIED, "Downloads from " + origin + " refused"));
    }

    private void finishOpening(OpenScreen packet, @Nullable ScreenController controller, @Nullable Throwable error) {
        if (packet.instanceId() != latestOpening) {
            return;
        }

        if (error != null) {
            final var cause = error instanceof CompletionException && error.getCause() != null
                    ? error.getCause()
                    : error;
            final var reason = cause instanceof ContentLoadException load
                    ? load.getReason()
                    : ScreenFailureReason.OTHER;
            fail(packet.instanceId(), reason, String.valueOf(cause.getMessage()));
            return;
        }

        closeCurrent();
        current = controller;
        platform.openScreen(controller);
    }

    private void fail(int instanceId, ScreenFailureReason reason, String detail) {
        platform.warn("Rain screen " + instanceId + " failed (" + reason + "): " + detail);
        connection.send(RainPackets.SCREEN_FAILED, new ScreenFailed(instanceId, reason));
    }

    private @Nullable JsonNode validProperties(Contract contract, String json) {
        if (!propertiesValidator.validate(contract, json).isValid()) {
            return null;
        }

        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}

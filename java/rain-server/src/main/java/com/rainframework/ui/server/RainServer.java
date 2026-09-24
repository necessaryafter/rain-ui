package com.rainframework.ui.server;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rainframework.ui.protocol.Contract;
import com.rainframework.ui.protocol.TypeSchema;
import com.rainframework.ui.protocol.packet.ClientHello;
import com.rainframework.ui.protocol.packet.CloseScreen;
import com.rainframework.ui.protocol.packet.Interact;
import com.rainframework.ui.protocol.packet.InteractionRejected;
import com.rainframework.ui.protocol.packet.OpenScreen;
import com.rainframework.ui.protocol.packet.RainPackets;
import com.rainframework.ui.protocol.packet.RainProtocol;
import com.rainframework.ui.protocol.packet.ScreenClosed;
import com.rainframework.ui.protocol.packet.ScreenFailed;
import com.rainframework.ui.protocol.packet.ServerHello;
import com.rainframework.ui.protocol.packet.UpdateScreen;
import com.rainframework.ui.protocol.payload.Payload;
import com.rainframework.ui.protocol.validation.PayloadValidator;
import com.rainframework.ui.protocol.validation.PropertiesValidator;
import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * The server side of Rain: opens screens, pushes updates and dispatches interactions. Packet handlers may be called
 * from the network thread; interaction events are always fired on {@link ServerPlatform#mainThread()}.
 */
public final class RainServer {
    public static final int INTERACTIONS_PER_SECOND = 10;

    private final ServerPlatform platform;
    private final ContractRegistry registry;
    private final String assetBaseUrl;
    private final LongSupplier nanoClock;
    private final PropertiesValidator propertiesValidator = new PropertiesValidator();
    private final PayloadValidator payloadValidator = new PayloadValidator();
    private final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();

    @Getter
    private final Adapters adapters;
    @Getter
    private final Event<InteractionEvent> interactions;

    private final PropertyEncoder encoder;

    @Builder
    private RainServer(
            ServerPlatform platform,
            ContractRegistry registry,
            String assetBaseUrl,
            @Nullable Adapters adapters,
            @Nullable Event<InteractionEvent> interactions,
            @Nullable LongSupplier nanoClock
    ) {
        this.platform = platform;
        this.registry = registry;
        this.assetBaseUrl = assetBaseUrl;
        this.adapters = adapters == null ? new Adapters() : adapters;
        this.interactions = interactions == null ? new Event<>() : interactions;
        this.nanoClock = nanoClock == null ? System::nanoTime : nanoClock;
        this.encoder = new PropertyEncoder(this.adapters, platform);
    }

    public void onPlayerJoin(PlayerRef player) {
        sessions.put(player.id(), new PlayerSession(new RateLimiter(INTERACTIONS_PER_SECOND, nanoClock)));
        platform.send(player, RainPackets.SERVER_HELLO, new ServerHello(RainProtocol.VERSION, assetBaseUrl));
    }

    public void onPlayerLeave(PlayerRef player) {
        final var session = sessions.remove(player.id());
        if (session != null && session.open != null) {
            session.open.setOpen(false);
        }
    }

    public void onClientHello(PlayerRef player, ClientHello hello) {
        final var session = sessions.get(player.id());
        if (session == null) {
            return;
        }

        session.clientVersion = hello.protocolVersion();
        if (hello.protocolVersion() != RainProtocol.VERSION) {
            platform.warn(player.name() + " runs Rain protocol " + hello.protocolVersion() + ", the server "
                    + RainProtocol.VERSION + "; Rain screens will not open for them");
        }
    }

    /**
     * Opens a screen for the player, closing the one they had open. Returns null, and logs, when the player has no
     * compatible Rain client. Throws {@link InvalidPropertiesException} before sending anything when the properties do
     * not match the contract, and IllegalArgumentException for an unknown screen.
     */
    public @Nullable ScreenInstance open(PlayerRef player, String screenId, Properties properties) {
        final var screen = registry.screen(screenId);
        if (screen == null) {
            throw new IllegalArgumentException("No Rain screen " + screenId + " was loaded");
        }

        final var session = sessions.get(player.id());
        if (session == null || session.clientVersion != RainProtocol.VERSION) {
            platform.warn("Cannot open " + screenId + " for " + player.name() + ": no compatible Rain client");
            return null;
        }

        final var json = encodeValid(screen.contract(), screenId, properties);
        if (session.open != null) {
            session.open.setOpen(false);
        }

        final var instance = new ScreenInstance(session.nextInstanceId++, player, screen, json);
        session.open = instance;

        platform.send(player, RainPackets.OPEN_SCREEN, new OpenScreen(
                instance.getId(),
                screenId,
                screen.hash(),
                instance.getRevision(),
                json.toString()));
        return instance;
    }

    /** Pushes new properties to an open screen and bumps its revision. Returns false when the screen is closed. */
    public boolean update(ScreenInstance instance, Properties properties) {
        if (!instance.isOpen()) {
            return false;
        }

        sendUpdate(instance, encodeValid(instance.getContract(), instance.getScreenId(), properties));
        return true;
    }

    public void close(ScreenInstance instance) {
        if (!instance.isOpen()) {
            return;
        }

        instance.setOpen(false);
        platform.send(instance.getPlayer(), RainPackets.CLOSE_SCREEN, new CloseScreen(instance.getId()));
    }

    /** An item as the version module encodes it for properties, for code that builds properties as raw JSON. */
    public String encodeItem(Object item) {
        return platform.encodeItem(item);
    }

    public Set<String> screenIds() {
        return registry.screens().keySet();
    }

    /** Every open instance of a screen, e.g. to push a new bid to everyone looking at an auction. */
    public List<ScreenInstance> openInstances(String screenId) {
        final var instances = new ArrayList<ScreenInstance>();
        for (final var session : sessions.values()) {
            final var open = session.open;
            if (open != null && open.isOpen() && open.getScreenId().equals(screenId)) {
                instances.add(open);
            }
        }

        return instances;
    }

    public void onScreenClosed(PlayerRef player, ScreenClosed packet) {
        final var instance = ownedOpenInstance(player, packet.instanceId());
        if (instance != null) {
            instance.setOpen(false);
        }
    }

    public void onScreenFailed(PlayerRef player, ScreenFailed packet) {
        final var instance = ownedOpenInstance(player, packet.instanceId());
        if (instance == null) {
            return;
        }

        instance.setOpen(false);
        platform.warn(instance.getScreenId() + " failed to open for " + player.name() + ": " + packet.reason());
    }

    /**
     * Checks an interaction in the order of spec §7.2, each failure answered with a rejection and logged at debug
     * level, then fires the event on the server thread and sends exactly one response.
     */
    public void onInteract(PlayerRef player, Interact packet) {
        final var session = sessions.get(player.id());
        if (session == null) {
            return;
        }

        if (!session.rateLimiter.tryAcquire()) {
            reject(player, packet, "rate limit exceeded");
            return;
        }

        final var instance = ownedOpenInstance(player, packet.instanceId());
        if (instance == null) {
            reject(player, packet, "instance " + packet.instanceId() + " is not open for this player");
            return;
        }

        // A stale revision means the player clicked on something that has changed since; they get the current state.
        if (packet.revision() != instance.getRevision()) {
            reject(player, packet, "revision " + packet.revision() + " is not " + instance.getRevision());
            platform.send(player, RainPackets.UPDATE_SCREEN, new UpdateScreen(
                    instance.getId(),
                    instance.getRevision(),
                    instance.getProperties().toString()));
            return;
        }

        final var schema = instance.getContract().actions().get(packet.actionId());
        if (schema == null) {
            reject(player, packet, "action " + packet.actionId() + " is not declared by " + instance.getScreenId());
            return;
        }

        final var contract = instance.getContract();
        final var validation = payloadValidator.validate(contract, packet.actionId(), packet.payloadJson());
        if (!validation.isValid()) {
            final var error = validation.getError();
            reject(player, packet, "payload " + error.getCode() + " at " + error.getPath());
            return;
        }

        // Accessors read object fields, so an action declared with a non-object schema gets an empty payload.
        final var payload = TypeSchema.unwrap(schema) instanceof TypeSchema.ObjectType object
                ? Payload.of(object, packet.payloadJson())
                : Payload.of(new TypeSchema.ObjectType(Map.of()), "{}");
        final var event = new InteractionEvent(player, instance, packet.actionId(), payload);

        platform.mainThread().execute(() -> dispatch(event));
    }

    private void dispatch(InteractionEvent event) {
        final var instance = event.getInstance();

        try {
            interactions.fire(event);
        } catch (RuntimeException e) {
            platform.warn("A Rain interaction handler for " + event.getActionId() + " failed: " + e);
            event.setResult(InteractionResult.reject());
        }

        // The screen may have been closed or replaced while the event was queued for the server thread.
        if (!instance.isOpen()) {
            return;
        }

        respond(instance, event.getResult() == null ? InteractionResult.reject() : event.getResult());
    }

    private void respond(ScreenInstance instance, InteractionResult result) {
        switch (result) {
            case InteractionResult.Update update -> respondWithUpdate(instance, update.properties());
            case InteractionResult.Close ignored -> close(instance);
            case InteractionResult.Reject ignored -> platform.send(
                    instance.getPlayer(),
                    RainPackets.INTERACTION_REJECTED,
                    new InteractionRejected(instance.getId(), instance.getRevision()));
        }
    }

    // Invalid properties from a handler are a plugin bug: the player still gets an answer so the screen unlocks.
    private void respondWithUpdate(ScreenInstance instance, Properties properties) {
        try {
            sendUpdate(instance, encodeValid(instance.getContract(), instance.getScreenId(), properties));
        } catch (InvalidPropertiesException | IllegalArgumentException e) {
            platform.warn(e.getMessage());
            platform.send(instance.getPlayer(), RainPackets.INTERACTION_REJECTED,
                    new InteractionRejected(instance.getId(), instance.getRevision()));
        }
    }

    private void sendUpdate(ScreenInstance instance, ObjectNode json) {
        instance.setRevision(instance.getRevision() + 1);
        instance.setProperties(json);

        platform.send(instance.getPlayer(), RainPackets.UPDATE_SCREEN, new UpdateScreen(
                instance.getId(),
                instance.getRevision(),
                json.toString()));
    }

    private void reject(PlayerRef player, Interact packet, String reason) {
        platform.debug("Rejected interaction from " + player.name() + ": " + reason);
        platform.send(player, RainPackets.INTERACTION_REJECTED,
                new InteractionRejected(packet.instanceId(), packet.revision()));
    }

    private ObjectNode encodeValid(Contract contract, String screenId, Properties properties) {
        final var json = encoder.encode(properties);
        final var result = propertiesValidator.validate(contract, json.toString());
        if (!result.isValid()) {
            throw new InvalidPropertiesException(screenId, result.getError());
        }

        return json;
    }

    private @Nullable ScreenInstance ownedOpenInstance(PlayerRef player, int instanceId) {
        final var session = sessions.get(player.id());
        if (session == null || session.open == null) {
            return null;
        }

        final var open = session.open;
        return open.getId() == instanceId && open.isOpen() ? open : null;
    }

    private static final class PlayerSession {
        private final RateLimiter rateLimiter;
        private int clientVersion = -1;
        private int nextInstanceId = 1;
        private @Nullable ScreenInstance open;

        private PlayerSession(RateLimiter rateLimiter) {
            this.rateLimiter = rateLimiter;
        }
    }
}

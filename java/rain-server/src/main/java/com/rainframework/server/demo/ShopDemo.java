package com.rainframework.server.demo;

import com.rainframework.server.api.PlayerRef;
import com.rainframework.server.api.Properties;
import com.rainframework.server.api.RainServer;
import com.rainframework.server.api.ScreenInstance;
import com.rainframework.server.api.event.Event;
import com.rainframework.server.api.event.InteractionEvent;
import com.rainframework.server.api.event.InteractionResult;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashSet;
import java.util.Set;

/**
 * The handler behind the shop sample opened with {@code /rain open shop:main}. It is deliberately separate from the
 * public API: real plugins subscribe to {@link RainServer#getInteractions()} and own their purchase state.
 */
public final class ShopDemo {
    public static final String SCREEN_ID = "shop:main";
    private final Set<ScreenInstance> instances = new HashSet<>();

    public ShopDemo(Event<InteractionEvent> interactions) {
        interactions.subscribe(this::handle);
    }

    public ScreenInstance open(RainServer server, PlayerRef player, Properties properties) {
        final var instance = server.open(player, SCREEN_ID, properties);
        if (instance != null) {
            instances.add(instance);
        }
        return instance;
    }

    private void handle(InteractionEvent event) {
        final var instance = event.getInstance();
        if (!instances.contains(instance) || !event.getActionId().equals("shop:buy")) {
            return;
        }

        final var itemId = event.getPayload().getString("itemId");
        final var updated = lock(instance.getProperties(), itemId);
        if (updated == null) {
            event.setResult(InteractionResult.reject());
            return;
        }

        event.setResult(InteractionResult.update(Properties.fromJson(updated)));
    }

    private static ObjectNode lock(ObjectNode properties, String itemId) {
        final var copy = properties.deepCopy();
        if (!(copy.get("items") instanceof ArrayNode items)) {
            return null;
        }

        for (final var node : items) {
            if (node instanceof ObjectNode item && item.path("id").asText().equals(itemId) && !item.path("locked").asBoolean()) {
                item.put("locked", true);
                return copy;
            }
        }

        return null;
    }
}

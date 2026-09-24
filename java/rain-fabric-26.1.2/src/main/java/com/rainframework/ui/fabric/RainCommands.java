package com.rainframework.ui.fabric;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rainframework.ui.RainUI;
import com.rainframework.ui.server.InvalidPropertiesException;
import com.rainframework.ui.server.Properties;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code /rain open <screen>} previews a screen with the sample properties in
 * {@code config/rain-ui/samples/<namespace>/<screen>.json}. In a sample, {@code {"$item": "minecraft:diamond"}} (with an
 * optional {@code "count"}) stands for an item, since real item values are encoded per server.
 */
public final class RainCommands {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path samples;

    public RainCommands(Path samples) {
        this.samples = samples;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rain")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("open")
                        .then(Commands.argument("screen", StringArgumentType.greedyString())
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(RainUI.server().screenIds(), builder))
                                .executes(this::open))));
    }

    private int open(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final var source = context.getSource();
        final var player = source.getPlayerOrException();
        final var screenId = StringArgumentType.getString(context, "screen");
        final var sample = samples.resolve(screenId.replace(':', '/') + ".json");

        try {
            final var json = Files.exists(sample) ? MAPPER.readTree(sample.toFile()) : MAPPER.createObjectNode();
            if (RainUI.open(player, screenId, Properties.fromJson(withItems(json))) == null) {
                source.sendFailure(Component.literal("You have no compatible Rain UI client installed"));
                return 0;
            }

            return 1;
        } catch (IOException | InvalidPropertiesException | IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage() + " (sample: " + sample + ")"));
            return 0;
        }
    }

    private static JsonNode withItems(JsonNode node) {
        if (node instanceof ObjectNode object && object.has("$item")) {
            return MAPPER.getNodeFactory().textNode(encodeItem(object));
        }

        if (node instanceof ObjectNode object) {
            object.properties().forEach(entry -> entry.setValue(withItems(entry.getValue())));
            return object;
        }

        if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                array.set(i, withItems(array.get(i)));
            }
        }

        return node;
    }

    private static String encodeItem(ObjectNode sample) {
        final var id = Identifier.parse(sample.get("$item").asText());
        final var item = BuiltInRegistries.ITEM.getOptional(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown item " + id));

        return RainUI.server().encodeItem(new ItemStack(item, sample.path("count").asInt(1)));
    }
}

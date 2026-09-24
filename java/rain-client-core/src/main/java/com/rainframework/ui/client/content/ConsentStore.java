package com.rainframework.ui.client.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * The player's answer to "may this server make you download from this host", remembered per origin
 * (scheme, host and port), both when allowed and when refused.
 */
public final class ConsentStore {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path file;
    private final ObjectNode decisions;

    public ConsentStore(Path file) {
        this.file = file;
        this.decisions = load(file);
    }

    public static String originOf(String url) {
        final var uri = URI.create(url);
        final var port = uri.getPort() == -1 ? "" : ":" + uri.getPort();

        return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost().toLowerCase(Locale.ROOT) + port;
    }

    public synchronized @Nullable Boolean decision(String origin) {
        final var value = decisions.get(origin);
        return value == null || !value.isBoolean() ? null : value.asBoolean();
    }

    public synchronized void remember(String origin, boolean allowed) {
        decisions.put(origin, allowed);

        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(decisions));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // A missing or unreadable file means nothing was decided yet, so the player is asked again.
    private static ObjectNode load(Path file) {
        try {
            if (Files.isRegularFile(file) && MAPPER.readTree(file.toFile()) instanceof ObjectNode object) {
                return object;
            }
        } catch (IOException ignored) {
            // Falls through to an empty set of decisions.
        }

        return MAPPER.createObjectNode();
    }
}

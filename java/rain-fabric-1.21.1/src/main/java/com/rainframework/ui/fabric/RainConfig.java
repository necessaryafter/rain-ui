package com.rainframework.ui.fabric;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** {@code config/rain-ui/rain-ui.properties}, written with defaults on first start. */
public record RainConfig(String assetBaseUrl, boolean httpEnabled, String httpBind, int httpPort) {
    private static final String DEFAULTS = """
            # Where players download screens and assets from: GET <asset-base-url>/<sha256>.
            # Leave empty to use the built-in HTTP server below; set it when a CDN or your own host serves dist/.
            asset-base-url=
            # The built-in HTTP server that serves config/rain-ui/dist by hash.
            http-enabled=true
            http-bind=0.0.0.0
            http-port=25580
            """;

    public static RainConfig load(Path file) throws IOException {
        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, DEFAULTS);
        }

        final var properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        }

        return new RainConfig(
                properties.getProperty("asset-base-url", "").trim(),
                Boolean.parseBoolean(properties.getProperty("http-enabled", "true").trim()),
                properties.getProperty("http-bind", "0.0.0.0").trim(),
                Integer.parseInt(properties.getProperty("http-port", "25580").trim()));
    }
}

package com.rainframework.ui.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Writes a dist/ directory like rain build does, from schema fixtures. */
final class TestDist {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestDist() {
    }

    static Path create(Path directory, String... fixtures) throws Exception {
        final ObjectNode manifest = MAPPER.createObjectNode().put("schemaVersion", 0);
        final var screens = manifest.putObject("screens");

        for (final var fixture : fixtures) {
            final var bytes = Files.readAllBytes(Path.of(System.getProperty("user.dir"),
                    "schema/fixtures/valid", fixture + ".json"));
            final var id = MAPPER.readTree(bytes).get("id").asText();
            final var file = id.replace(':', '/') + ".json";

            Files.createDirectories(directory.resolve(file).getParent());
            Files.write(directory.resolve(file), bytes);
            screens.putObject(id).put("file", file).put("sha256", sha256(bytes));
        }

        Files.writeString(directory.resolve("manifest.json"), manifest.toString());
        return directory;
    }

    static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}

package com.rainframework.ui.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rainframework.ui.protocol.Contract;
import com.rainframework.ui.protocol.ContractParser;
import com.rainframework.ui.protocol.ParseException;
import com.rainframework.ui.protocol.validation.ContractValidator;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The screens a server can open, loaded once from the {@code dist/} directory that {@code rain build} writes. Every
 * contract is checked against its manifest hash and validated at load, so a broken build fails at startup instead of
 * on a player's screen. It also serves that content by hash for the HTTP server.
 */
public final class ContractRegistry {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern HASH_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    private final Map<String, LoadedScreen> screens;
    private final Map<String, byte[]> contractsByHash;
    private final Path assetsDirectory;

    private ContractRegistry(Map<String, LoadedScreen> screens, Map<String, byte[]> contractsByHash, Path assets) {
        this.screens = Map.copyOf(screens);
        this.contractsByHash = Map.copyOf(contractsByHash);
        this.assetsDirectory = assets;
    }

    public record LoadedScreen(String id, String hash, Contract contract) {
    }

    public static ContractRegistry load(Path dist) throws IOException {
        final var manifest = MAPPER.readTree(dist.resolve("manifest.json").toFile());
        final var screens = new HashMap<String, LoadedScreen>();
        final var contracts = new HashMap<String, byte[]>();

        for (final var entry : manifest.path("screens").properties()) {
            final var id = entry.getKey();
            final var hash = entry.getValue().path("sha256").asText();
            final var bytes = Files.readAllBytes(dist.resolve(entry.getValue().path("file").asText()));

            if (!sha256(bytes).equals(hash)) {
                throw new IOException("Contract for " + id + " does not match its manifest hash; rebuild dist/");
            }

            screens.put(id, new LoadedScreen(id, hash, parse(id, bytes)));
            contracts.put(hash, bytes);
        }

        return new ContractRegistry(screens, contracts, dist.resolve("assets"));
    }

    public @Nullable LoadedScreen screen(String id) {
        return screens.get(id);
    }

    public Map<String, LoadedScreen> screens() {
        return screens;
    }

    /** A contract or asset by hash, as served over HTTP; null when this server does not have it. */
    public byte @Nullable [] content(String hash) {
        if (!HASH_PATTERN.matcher(hash).matches()) {
            return null;
        }

        final var contract = contractsByHash.get(hash);
        if (contract != null) {
            return contract;
        }

        final var asset = assetsDirectory.resolve(hash);
        try {
            return Files.isRegularFile(asset) ? Files.readAllBytes(asset) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static Contract parse(String id, byte[] bytes) throws IOException {
        try {
            final var contract = new ContractParser().parse(new String(bytes, StandardCharsets.UTF_8));
            final var result = new ContractValidator().validate(contract);
            if (!result.isValid()) {
                throw new IOException("Contract " + id + " is invalid: " + result.getError().getCode() + " at "
                        + result.getError().getPath());
            }

            return contract;
        } catch (ParseException e) {
            throw new IOException("Contract " + id + " could not be parsed: " + e.getMessage(), e);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JVM ships SHA-256", e);
        }
    }
}

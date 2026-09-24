package com.rainframework.ui.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContractRegistryTest {

    @TempDir
    Path dist;

    @Test
    void loadsEveryScreenInTheManifest() throws Exception {
        final var registry = ContractRegistry.load(TestDist.create(dist, "shop", "gts"));

        assertEquals(2, registry.screens().size());
        assertNotNull(registry.screen("shop:main"));
    }

    @Test
    void servesContractsAndAssetsByHash() throws Exception {
        final var registry = ContractRegistry.load(TestDist.create(dist, "shop"));
        final var shop = registry.screen("shop:main");
        final var asset = "asset".getBytes();
        Files.createDirectories(dist.resolve("assets"));
        Files.write(dist.resolve("assets").resolve(TestDist.sha256(asset)), asset);

        assertArrayEquals(Files.readAllBytes(dist.resolve("shop/main.json")), registry.content(shop.hash()));
        assertArrayEquals(asset, registry.content(TestDist.sha256(asset)));
        assertNull(registry.content("0".repeat(64)));
        assertNull(registry.content("../manifest.json"));
    }

    @Test
    void refusesAContractThatDoesNotMatchItsManifestHash() throws Exception {
        TestDist.create(dist, "shop");
        Files.writeString(dist.resolve("shop/main.json"), "{}");

        assertThrows(IOException.class, () -> ContractRegistry.load(dist));
    }
}

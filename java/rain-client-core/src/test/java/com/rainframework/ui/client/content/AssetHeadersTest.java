package com.rainframework.ui.client.content;

import com.rainframework.ui.protocol.AssetInfo;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Uses the same files as the CLI's asset tests, so the Java and TypeScript readers agree.
class AssetHeadersTest {

    @Test
    void readsPngGifAndTtfHeaders() throws Exception {
        assertEquals(new AssetInfo("image/png", null, 32L, 16L, null), AssetHeaders.read(file("images/banner.png")));
        assertEquals(new AssetInfo("image/gif", null, 16L, 16L, 4L), AssetHeaders.read(file("images/spinner.gif")));
        assertEquals(new AssetInfo("font/ttf", null, null, null, null), AssetHeaders.read(file("fonts/title.ttf")));
    }

    @Test
    void rejectsFilesItCannotRead() throws Exception {
        final var broken = Path.of(System.getProperty("user.dir"),
                "packages/cli/test/fixtures/assets-corrupt/images/broken.png");

        assertNull(AssetHeaders.read(Files.readAllBytes(broken)));
    }

    @Test
    void matchesOnlyTheDeclaredTypeAndDimensions() throws Exception {
        final var banner = file("images/banner.png");

        assertTrue(AssetHeaders.matches(banner, new AssetInfo("image/png", 94L, 32L, 16L, null)));
        assertFalse(AssetHeaders.matches(banner, new AssetInfo("image/png", 94L, 16L, 16L, null)));
        assertFalse(AssetHeaders.matches(banner, new AssetInfo("image/jpeg", 94L, 32L, 16L, null)));
    }

    static byte[] file(String name) throws Exception {
        return Files.readAllBytes(Path.of(System.getProperty("user.dir"),
                "packages/cli/test/fixtures/assets-basic", name));
    }
}

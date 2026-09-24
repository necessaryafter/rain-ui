package com.rainframework.ui.client.content;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentStoreTest {

    @TempDir
    Path directory;

    @Test
    void storesAndReadsContentByHash() {
        final var store = new ContentStore(directory, 1024);
        final var bytes = "contrato".getBytes(StandardCharsets.UTF_8);

        store.write(Hashes.sha256(bytes), bytes);

        assertArrayEquals(bytes, store.read(Hashes.sha256(bytes)));
    }

    @Test
    void refusesToStoreBytesUnderAHashTheyDoNotMatch() {
        final var store = new ContentStore(directory, 1024);

        assertThrows(IllegalArgumentException.class, () -> store.write("0".repeat(64), new byte[]{1, 2, 3}));
    }

    @Test
    void discardsAFileWhoseBytesChangedOnDisk() throws Exception {
        final var store = new ContentStore(directory, 1024);
        final var bytes = "contrato".getBytes(StandardCharsets.UTF_8);
        final var hash = Hashes.sha256(bytes);
        store.write(hash, bytes);

        Files.writeString(directory.resolve(hash), "adulterado");

        assertNull(store.read(hash));
        assertFalse(Files.exists(directory.resolve(hash)));
    }

    @Test
    void ignoresNamesThatAreNotHashes() {
        final var store = new ContentStore(directory, 1024);

        assertNull(store.read("../../options.txt"));
        assertFalse(store.contains("../../options.txt"));
    }

    @Test
    void evictsTheLeastRecentlyUsedContentPastTheLimit() throws Exception {
        final var store = new ContentStore(directory, 20);
        final var old = "aaaaaaaaaa".getBytes(StandardCharsets.UTF_8);
        final var recent = "bbbbbbbbbb".getBytes(StandardCharsets.UTF_8);
        final var incoming = "cccccccccc".getBytes(StandardCharsets.UTF_8);

        store.write(Hashes.sha256(old), old);
        store.write(Hashes.sha256(recent), recent);
        Files.setLastModifiedTime(directory.resolve(Hashes.sha256(old)), FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(directory.resolve(Hashes.sha256(recent)), FileTime.fromMillis(2_000));

        store.write(Hashes.sha256(incoming), incoming);

        assertFalse(store.contains(Hashes.sha256(old)));
        assertTrue(store.contains(Hashes.sha256(recent)));
        assertTrue(store.contains(Hashes.sha256(incoming)));
    }
}

package com.rainframework.ui.client.content;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * The disk cache of downloaded contracts and assets, one file per hash, shared across servers since the content is
 * addressed by its hash. Least recently used files are evicted past {@code maxBytes}.
 */
public final class ContentStore {
    private final Path directory;
    private final long maxBytes;

    public ContentStore(Path directory, long maxBytes) {
        this.directory = directory;
        this.maxBytes = maxBytes;
    }

    /** The content for a hash, or null when absent; a file whose bytes no longer match its hash is deleted. */
    public synchronized byte @Nullable [] read(String hash) {
        if (!Hashes.isHash(hash)) {
            return null;
        }

        final var file = directory.resolve(hash);
        if (!Files.isRegularFile(file)) {
            return null;
        }

        try {
            final var bytes = Files.readAllBytes(file);
            if (!Hashes.sha256(bytes).equals(hash)) {
                Files.deleteIfExists(file);
                return null;
            }

            Files.setLastModifiedTime(file, FileTime.from(Instant.now()));
            return bytes;
        } catch (IOException e) {
            return null;
        }
    }

    public synchronized boolean contains(String hash) {
        return Hashes.isHash(hash) && Files.isRegularFile(directory.resolve(hash));
    }

    public synchronized void write(String hash, byte[] bytes) {
        if (!Hashes.isHash(hash) || !Hashes.sha256(bytes).equals(hash)) {
            throw new IllegalArgumentException("Refusing to store content under a hash it does not match: " + hash);
        }

        try {
            Files.createDirectories(directory);

            // Written to a temporary file first so a crash never leaves a truncated file under a valid hash.
            final var temporary = Files.createTempFile(directory, hash, ".part");
            Files.write(temporary, bytes);
            Files.move(
                    temporary,
                    directory.resolve(hash),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            evict(hash);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void evict(String keep) throws IOException {
        try (final Stream<Path> files = Files.list(directory)) {
            final var entries = files
                    .filter(file -> Hashes.isHash(file.getFileName().toString()))
                    .sorted(Comparator.comparing(ContentStore::lastModified))
                    .toList();

            var total = 0L;
            for (final var file : entries) {
                total += Files.size(file);
            }

            for (final var file : entries) {
                if (total <= maxBytes) {
                    return;
                }

                if (file.getFileName().toString().equals(keep)) {
                    continue;
                }

                total -= Files.size(file);
                Files.deleteIfExists(file);
            }
        }
    }

    private static FileTime lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }
}

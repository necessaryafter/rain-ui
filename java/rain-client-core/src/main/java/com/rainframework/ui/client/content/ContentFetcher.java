package com.rainframework.ui.client.content;

import com.rainframework.ui.protocol.packet.ScreenFailureReason;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * Downloads content by hash from the server's asset base URL. Redirects are never followed, no cookies or credentials
 * are sent, the body is cut off at the size the contract declared, and the bytes must hash to what was asked for.
 */
public final class ContentFetcher {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient client;

    public ContentFetcher(Executor executor) {
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(CONNECT_TIMEOUT)
                .executor(executor)
                .build();
    }

    public CompletableFuture<byte[]> fetch(String assetBaseUrl, String hash, long maxBytes) {
        if (!Hashes.isHash(hash)) {
            return CompletableFuture.failedFuture(
                    new ContentLoadException(ScreenFailureReason.INVALID_CONTRACT, "Not a content hash: " + hash));
        }

        final var request = HttpRequest.newBuilder(URI.create(assetBaseUrl + "/" + hash))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "rain-ui")
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenApply(response -> read(response, hash, maxBytes))
                .exceptionallyCompose(error -> CompletableFuture.failedFuture(asLoadFailure(error, hash)));
    }

    private static byte[] read(HttpResponse<InputStream> response, String hash, long maxBytes) {
        try (final var body = response.body()) {
            if (response.statusCode() != 200) {
                throw new ContentLoadException(
                        ScreenFailureReason.DOWNLOAD_FAILED,
                        "HTTP " + response.statusCode() + " for " + hash);
            }

            // One byte past the limit is enough to know the host sent more than the contract declared.
            final var bytes = body.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes + 1));
            if (bytes.length > maxBytes) {
                throw new ContentLoadException(
                        ScreenFailureReason.DOWNLOAD_FAILED,
                        hash + " is larger than the " + maxBytes + " bytes declared");
            }

            if (!Hashes.sha256(bytes).equals(hash)) {
                throw new ContentLoadException(
                        ScreenFailureReason.HASH_MISMATCH,
                        "Downloaded bytes do not match " + hash);
            }

            return bytes;
        } catch (IOException e) {
            throw new ContentLoadException(ScreenFailureReason.DOWNLOAD_FAILED, "Reading " + hash + " failed: " + e);
        }
    }

    private static Throwable asLoadFailure(Throwable error, String hash) {
        final var cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        if (cause instanceof ContentLoadException) {
            return cause;
        }

        return new ContentLoadException(
                ScreenFailureReason.DOWNLOAD_FAILED,
                "Downloading " + hash + " failed: " + cause);
    }
}

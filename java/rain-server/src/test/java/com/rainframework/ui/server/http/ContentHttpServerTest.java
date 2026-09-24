package com.rainframework.ui.server.http;

import com.rainframework.ui.server.ContractRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ContentHttpServerTest {

    @TempDir
    Path dist;

    private ContentHttpServer server;
    private final HttpClient client = HttpClient.newHttpClient();
    private byte[] asset;
    private String hash;

    @BeforeEach
    void start() throws Exception {
        Files.writeString(dist.resolve("manifest.json"), "{\"schemaVersion\":0,\"screens\":{}}");
        asset = "image bytes".getBytes();
        hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(asset));
        Files.createDirectories(dist.resolve("assets"));
        Files.write(dist.resolve("assets").resolve(hash), asset);
        Files.writeString(dist.resolve("secret.txt"), "not content");

        server = ContentHttpServer.start(new InetSocketAddress("127.0.0.1", 0), ContractRegistry.load(dist));
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void servesContentByHashAsImmutable() throws Exception {
        final var response = get("/" + hash);

        assertEquals(200, response.statusCode());
        assertArrayEquals(asset, response.body());
        assertEquals(
                "public, max-age=31536000, immutable",
                response.headers().firstValue("Cache-Control").orElseThrow());
    }

    @Test
    void answersNotFoundForAnythingElse() throws Exception {
        assertEquals(404, get("/" + "0".repeat(64)).statusCode());
        assertEquals(404, get("/secret.txt").statusCode());
        assertEquals(404, get("/..%2Fsecret.txt").statusCode());
        assertEquals(404, get("/").statusCode());
    }

    @Test
    void onlyAllowsReading() throws Exception {
        final var request = HttpRequest.newBuilder(uri("/" + hash))
                .POST(HttpRequest.BodyPublishers.ofString("x"))
                .build();

        assertEquals(405, client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
    }

    private HttpResponse<byte[]> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.port() + path);
    }
}

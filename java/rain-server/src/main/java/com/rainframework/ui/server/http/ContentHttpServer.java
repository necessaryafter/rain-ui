package com.rainframework.ui.server.http;

import com.rainframework.ui.server.ContractRegistry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The base content host: {@code GET /<sha256>} returns a contract or asset this server loaded. Anything else is a 404,
 * so the path can never reach other files. Servers with their own host (nginx, a CDN) leave this off and point
 * {@code assetBaseUrl} there instead.
 */
public final class ContentHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor;
    private final ContractRegistry registry;

    private ContentHttpServer(HttpServer server, ExecutorService executor, ContractRegistry registry) {
        this.server = server;
        this.executor = executor;
        this.registry = registry;
    }

    public static ContentHttpServer start(InetSocketAddress address, ContractRegistry registry) throws IOException {
        final var server = HttpServer.create(address, 0);
        final var executor = Executors.newFixedThreadPool(4, runnable -> {
            final var thread = new Thread(runnable, "rain-content-http");
            thread.setDaemon(true);
            return thread;
        });

        final var content = new ContentHttpServer(server, executor, registry);
        server.createContext("/", content::handle);
        server.setExecutor(executor);
        server.start();
        return content;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            final var method = exchange.getRequestMethod();
            if (!method.equals("GET") && !method.equals("HEAD")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            final var bytes = registry.content(exchange.getRequestURI().getPath().substring(1));
            if (bytes == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            // Content never changes under a hash, so clients and proxies may keep it forever.
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");

            if (method.equals("HEAD")) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}

package com.rainframework.ui.client.session;

import com.rainframework.ui.client.screen.ScreenController;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** What the version module provides to the client session. Every call is made on the main (render) thread. */
public interface ClientPlatform {

    /** Runs a task on the main thread; downloads complete on other threads and hop back through this. */
    Executor mainThread();

    void openScreen(ScreenController controller);

    void closeScreen(int instanceId);

    /**
     * Asks the player whether this server may make them download from {@code origin}. {@code bytes} is the total to
     * download, or -1 when not known yet (the first contract of a server).
     */
    CompletableFuture<Boolean> askConsent(String origin, long bytes);

    void warn(String message);
}

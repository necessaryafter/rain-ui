package com.rainframework.ui.server;

import java.util.function.LongSupplier;

/** A token bucket: up to {@code perSecond} interactions in a burst, refilled at {@code perSecond} per second. */
final class RateLimiter {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final int perSecond;
    private final LongSupplier nanoClock;
    private double tokens;
    private long lastRefill;

    RateLimiter(int perSecond, LongSupplier nanoClock) {
        this.perSecond = perSecond;
        this.nanoClock = nanoClock;
        this.tokens = perSecond;
        this.lastRefill = nanoClock.getAsLong();
    }

    synchronized boolean tryAcquire() {
        final var now = nanoClock.getAsLong();
        tokens = Math.min(perSecond, tokens + (double) (now - lastRefill) * perSecond / NANOS_PER_SECOND);
        lastRefill = now;

        if (tokens < 1) {
            return false;
        }

        tokens--;
        return true;
    }
}

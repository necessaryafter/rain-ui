package com.rainframework.ui.server;

/** The one answer the server gives to an interaction. */
public sealed interface InteractionResult {

    static InteractionResult reject() {
        return Reject.INSTANCE;
    }

    /** Replaces the screen's properties and bumps its revision, so any click sent before this is rejected. */
    static InteractionResult update(Properties properties) {
        return new Update(properties);
    }

    static InteractionResult close() {
        return Close.INSTANCE;
    }

    enum Reject implements InteractionResult {
        INSTANCE
    }

    enum Close implements InteractionResult {
        INSTANCE
    }

    record Update(Properties properties) implements InteractionResult {
    }
}

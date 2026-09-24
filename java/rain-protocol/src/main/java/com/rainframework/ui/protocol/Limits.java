package com.rainframework.ui.protocol;

/**
 * Limit constants for Rain UI contracts (spec §5.5).
 * These are the single source of truth — mirrored in @rain-ui/core on the TS side.
 */
public final class Limits {
    public static final int MAX_CONTRACT_BYTES = 256 * 1024;
    public static final int MAX_NODES = 2000;
    public static final int MAX_DEPTH = 32;
    public static final int MAX_ACTIONS = 256;
    public static final int MAX_STRING_LENGTH = 1024;
    public static final int MAX_PROPERTIES_BYTES = 256 * 1024;
    public static final int MAX_LIST_ELEMENTS = 1000;
    public static final int MAX_PAYLOAD_BYTES = 8 * 1024;
    public static final int MAX_PAYLOAD_DEPTH = 8;
    public static final int MAX_JSON_DEPTH = 128;
    public static final long MAX_ASSET_BYTES = 8L * 1024 * 1024;
    public static final int MAX_ASSETS = 256;
    public static final long MAX_TOTAL_ASSET_BYTES = 64L * 1024 * 1024;
    public static final int MAX_IMAGE_DIMENSION = 4096;
    public static final int MAX_GIF_FRAMES = 512;
    public static final long MAX_GIF_DECODED_BYTES = 128L * 1024 * 1024;

    private Limits() {
        // static only
    }
}

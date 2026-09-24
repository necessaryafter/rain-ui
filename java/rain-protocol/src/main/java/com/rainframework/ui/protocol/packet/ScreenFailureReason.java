package com.rainframework.ui.protocol.packet;

/**
 * Sent as its name. A name this version does not know, from a newer client, decodes as {@link #OTHER} so the server
 * still frees the instance instead of dropping the packet.
 */
public enum ScreenFailureReason {
    DOWNLOAD_FAILED,
    CONSENT_DENIED,
    HASH_MISMATCH,
    INVALID_CONTRACT,
    INVALID_PROPERTIES,
    DECODE_FAILED,
    OTHER
}

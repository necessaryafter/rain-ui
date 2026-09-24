package com.rainframework.ui.protocol.packet;

/** {@code contractHash} is lowercase hex in the API; on the wire it is the 32 raw bytes of the sha256. */
public record OpenScreen(int instanceId, String screenId, String contractHash, int revision, String propertiesJson) {
}

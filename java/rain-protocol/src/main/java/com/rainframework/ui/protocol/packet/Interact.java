package com.rainframework.ui.protocol.packet;

public record Interact(int instanceId, int revision, String actionId, String payloadJson) {
}

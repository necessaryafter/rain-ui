package com.rainframework.ui.protocol.packet;

public record ScreenFailed(int instanceId, ScreenFailureReason reason) {
}

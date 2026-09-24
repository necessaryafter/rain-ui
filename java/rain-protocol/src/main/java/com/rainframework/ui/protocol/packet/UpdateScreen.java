package com.rainframework.ui.protocol.packet;

public record UpdateScreen(int instanceId, int revision, String propertiesJson) {
}

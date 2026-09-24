package com.rainframework.ui.protocol.packet;

public record ServerHello(int protocolVersion, String assetBaseUrl) {
}

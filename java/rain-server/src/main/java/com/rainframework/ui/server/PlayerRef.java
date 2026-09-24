package com.rainframework.ui.server;

import java.util.UUID;

/** A player as the core sees it; the version module maps its own player type to this, taken from the connection. */
public record PlayerRef(UUID id, String name) {
}

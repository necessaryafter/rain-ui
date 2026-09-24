package com.rainframework.ui.fabric.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RainUIClientLog {
    private static final Logger LOGGER = LoggerFactory.getLogger("rain-ui");

    private RainUIClientLog() {
    }

    static void warn(String message) {
        LOGGER.warn(message);
    }
}

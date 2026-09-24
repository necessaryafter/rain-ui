package com.rainframework.ui.client.layout;

import org.jspecify.annotations.Nullable;

/** Text metrics from the platform's font renderer; {@code fontHash} is null for the default Minecraft font. */
public interface Measurer {

    int textWidth(String text, @Nullable String fontHash);

    int lineHeight(@Nullable String fontHash);
}

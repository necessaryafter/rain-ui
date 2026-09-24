package com.rainframework.ui.server;

import org.jspecify.annotations.Nullable;

/** Where an adapter writes the fields of the object it converts. A null value writes JSON null. */
public interface PropertyWriter {

    PropertyWriter put(String key, @Nullable Object value);

    /** Writes an item through the version module's vanilla codec. */
    PropertyWriter putItem(String key, @Nullable Object item);
}

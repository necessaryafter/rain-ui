package com.rainframework.ui.protocol;

import java.util.Map;

public record Contract(
    int schemaVersion,
    String id,
    Map<String, TypeSchema> properties,
    Map<String, TypeSchema> actions,
    ComponentNode root,
    int sourceBytes) {}

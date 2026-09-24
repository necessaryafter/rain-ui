package com.rainframework.ui.protocol;

import java.util.Map;

public record Contract(
        int schemaVersion,
        String id,
        Map<String, TypeSchema> properties,
        Map<String, TypeSchema> actions,
        ComponentNode root,
        Map<String, AssetInfo> assets,
        Map<String, String> assetNames,
        int sourceBytes) {
}

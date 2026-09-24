package com.rainframework.ui.protocol;

/**
 * An entry of the contract's asset table, keyed by the file's sha256. Fields that are absent or of the wrong JSON type
 * are null, so the validator reports them with a stable code instead of the parser failing on the first one.
 */
public record AssetInfo(String type, Long bytes, Long width, Long height, Long frames) {
}

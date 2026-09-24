package com.rainframework.ui.protocol.packet;

import com.rainframework.ui.protocol.Limits;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/** Field types shared by several packets, with the rules they must satisfy on both ends of the wire. */
final class PacketFields {
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_-]+:[a-z0-9_/-]+$");
    private static final Pattern HASH_PATTERN = Pattern.compile("^[0-9a-f]{64}$");
    private static final int HASH_BYTES = 32;

    private PacketFields() {
    }

    static void writeId(PacketWriter writer, String id) {
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Not a namespace:path id: " + id);
        }

        writer.writeString(id, Limits.MAX_ID_LENGTH);
    }

    static String readId(PacketReader reader) throws PacketDecodeException {
        final var id = reader.readString(Limits.MAX_ID_LENGTH);
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new PacketDecodeException("Not a namespace:path id: " + id);
        }

        return id;
    }

    static void writeHash(PacketWriter writer, String hash) {
        if (!HASH_PATTERN.matcher(hash).matches()) {
            throw new IllegalArgumentException("Not a lowercase hex sha256: " + hash);
        }

        writer.writeBytes(HexFormat.of().parseHex(hash));
    }

    static String readHash(PacketReader reader) throws PacketDecodeException {
        return HexFormat.of().formatHex(reader.readBytes(HASH_BYTES));
    }

    static void writeAssetBaseUrl(PacketWriter writer, String url) {
        final var error = assetBaseUrlError(url);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }

        writer.writeString(url, Limits.MAX_ASSET_BASE_URL_LENGTH);
    }

    // The client builds download URLs as <base>/<hash>, so a query or fragment would end up in front of the hash, and
    // credentials would be sent to whatever host the server named.
    static String readAssetBaseUrl(PacketReader reader) throws PacketDecodeException {
        final var url = reader.readString(Limits.MAX_ASSET_BASE_URL_LENGTH);

        final var error = assetBaseUrlError(url);
        if (error != null) {
            throw new PacketDecodeException(error);
        }

        return stripTrailingSlashes(url);
    }

    static void writeReason(PacketWriter writer, ScreenFailureReason reason) {
        writer.writeString(reason.name(), Limits.MAX_REASON_LENGTH);
    }

    static ScreenFailureReason readReason(PacketReader reader) throws PacketDecodeException {
        final var name = reader.readString(Limits.MAX_REASON_LENGTH);

        for (final var reason : ScreenFailureReason.values()) {
            if (reason.name().equals(name)) {
                return reason;
            }
        }

        return ScreenFailureReason.OTHER;
    }

    private static String assetBaseUrlError(String url) {
        if (url.getBytes(StandardCharsets.UTF_8).length > Limits.MAX_ASSET_BASE_URL_LENGTH) {
            return "Asset base URL is over " + Limits.MAX_ASSET_BASE_URL_LENGTH + " bytes";
        }

        final URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return "Asset base URL is not a valid URI: " + url;
        }

        final var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return "Asset base URL must be http or https: " + url;
        }

        if (uri.getHost() == null || uri.getRawUserInfo() != null) {
            return "Asset base URL must name a host and carry no credentials: " + url;
        }

        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            return "Asset base URL must not have a query or fragment: " + url;
        }

        return null;
    }

    private static String stripTrailingSlashes(String url) {
        var end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }

        return url.substring(0, end);
    }
}

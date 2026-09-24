package com.rainframework.ui.client.content;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

final class Hashes {
    private static final Pattern HASH_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    private Hashes() {
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JVM ships SHA-256", e);
        }
    }

    // Hashes come from the server and become file names and URL paths, so anything but 64 hex digits is refused.
    static boolean isHash(String value) {
        return HASH_PATTERN.matcher(value).matches();
    }
}

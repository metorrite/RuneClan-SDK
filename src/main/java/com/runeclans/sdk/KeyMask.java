package com.runeclans.sdk;

/** Shows a key in logs as {@code rsh_…abcd} (the last four letters only). */
final class KeyMask {
    private KeyMask() {}

    static String mask(String key) {
        if (key == null || key.length() < 12) return "(none)";
        return key.substring(0, 4) + "…" + key.substring(key.length() - 4);
    }
}

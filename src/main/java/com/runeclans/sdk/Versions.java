package com.runeclans.sdk;

/** {@code major.minor.patch[-pre]} versions, compared the way the hub compares them (a pre-release sorts before its release). */
final class Versions {
    private Versions() {}

    static int compare(String a, String b) {
        int[] x = numbers(a);
        int[] y = numbers(b);
        for (int i = 0; i < 3; i++) if (x[i] != y[i]) return Integer.compare(x[i], y[i]);
        String pa = pre(a);
        String pb = pre(b);
        if (pa.isEmpty() || pb.isEmpty()) return Boolean.compare(pa.isEmpty(), pb.isEmpty());
        return pa.compareTo(pb);
    }

    private static int[] numbers(String v) {
        String core = v.contains("-") ? v.substring(0, v.indexOf('-')) : v;
        String[] parts = core.split("\\.");
        if (parts.length != 3) throw new IllegalArgumentException("not a version: " + v);
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }

    private static String pre(String v) {
        return v.contains("-") ? v.substring(v.indexOf('-') + 1).toLowerCase(java.util.Locale.ROOT) : "";
    }
}

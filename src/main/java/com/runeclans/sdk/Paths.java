package com.runeclans.sdk;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fills a route's {@code {name}} parts, each one encoded. */
final class Paths {
    private static final Pattern PART = Pattern.compile("\\{([A-Za-z0-9_]+)}");

    private Paths() {}

    static String fill(Route route, Map<String, String> values) {
        Matcher m = PART.matcher(route.path());
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = values.get(m.group(1));
            if (v == null || v.isEmpty()) throw new IllegalArgumentException(route + " needs " + m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(enc(v)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}

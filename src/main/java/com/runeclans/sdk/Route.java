package com.runeclans.sdk;

import java.util.List;
import java.util.Objects;

/**
 * One hub operation: its HTTP method, path template ({@code /v1/clans/{clanId}}), the scopes of which the key needs one (empty: any key),
 * whether it takes an API key, the media type of its answer and the generated type of a JSON answer (null when the spec names none).
 * {@link Routes} lists every public one.
 */
public record Route(String method, String path, List<String> scopes, boolean keyed, String mediaType, Class<?> responseType) {
    public Route {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(path, "path");
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }

    /** A route not in {@link Routes} (a newer hub operation this SDK doesn't list yet). Takes the key, needs no scope, answers JSON. */
    public static Route of(String method, String path) {
        return new Route(method, path, List.of(), true, "application/json", null);
    }

    /**
     * True when the hub can't have acted on a call that failed in transit, or acting twice changes nothing (GET, HEAD, PUT), so the SDK
     * may send it again after a lost connection or a 502/503/504. POST and DELETE are never sent again blind (a repeated DELETE would
     * answer 404 for something that worked). A 429 or 426 is different: the hub refused before doing anything, so any call is retried
     * after a 429.
     */
    public boolean safeToRepeat() {
        return method.equals("GET") || method.equals("HEAD") || method.equals("PUT");
    }

    @Override
    public String toString() {
        return method + " " + path;
    }
}

package com.runeclans.sdk;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Sends one HTTP request. The SDK's default is {@link JdkTransport} (one shared JDK {@code HttpClient}); tests pass a
 * {@link com.runeclans.sdk.testing.FakeTransport} instead, so nothing touches the network.
 */
public interface Transport extends AutoCloseable {
    /** Sends the request and returns the answer, whatever its status. Throws only when no answer came back. */
    Response send(Request request) throws IOException, InterruptedException;

    @Override
    default void close() {}

    /** One request. {@code body} is null for none. */
    record Request(String method, URI uri, Map<String, String> headers, byte[] body, Duration timeout) {
        public Request {
            headers = Map.copyOf(headers);
        }

        /** A header by name, ignoring case, or null. */
        public String header(String name) {
            for (Map.Entry<String, String> e : headers.entrySet()) if (e.getKey().equalsIgnoreCase(name)) return e.getValue();
            return null;
        }

        @Override
        public String toString() {
            return method + " " + uri; // never the headers: they carry the key
        }
    }

    /** One answer. Header names are matched without case. */
    record Response(int status, Map<String, List<String>> headers, byte[] body) {
        public Response {
            TreeMap<String, List<String>> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            if (headers != null) headers.forEach((k, v) -> { if (k != null) copy.put(k.toLowerCase(Locale.ROOT), List.copyOf(v)); });
            headers = copy;
            body = body == null ? new byte[0] : body;
        }

        /** The first value of a header, or null. */
        public String header(String name) {
            List<String> values = headers.get(name);
            return values == null || values.isEmpty() ? null : values.get(0);
        }
    }
}

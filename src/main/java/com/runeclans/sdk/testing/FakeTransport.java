package com.runeclans.sdk.testing;

import com.runeclans.sdk.Transport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A {@link Transport} for tests: no network. Queue answers with {@link #answer} (served in order), or give a {@link #handler} that
 * answers from the request; {@link #requests()} records what was sent. A queued {@link IOException} stands for a lost connection.
 *
 * <pre>{@code
 * FakeTransport hub = new FakeTransport().answer(200, "{\"appId\":\"a\",\"scopes\":[]}");
 * RuneClanClient c = RuneClan.connect(RuneClanConfig.builder().apiKey(key).transport(hub).build());
 * }</pre>
 */
public final class FakeTransport implements Transport {
    private final Deque<Object> queue = new ArrayDeque<>();
    private final List<Request> requests = new ArrayList<>();
    private Function<Request, Response> handler;

    /** Queues a JSON answer. */
    public synchronized FakeTransport answer(int status, String json) {
        return answer(status, json, Map.of());
    }

    /** Queues a JSON answer with headers (e.g. {@code RateLimit-Remaining}, {@code Retry-After}). */
    public synchronized FakeTransport answer(int status, String json, Map<String, String> headers) {
        Map<String, List<String>> h = new LinkedHashMap<>();
        h.put("Content-Type", List.of("application/json"));
        headers.forEach((k, v) -> h.put(k, List.of(v)));
        queue.add(new Response(status, h, json == null ? new byte[0] : json.getBytes(StandardCharsets.UTF_8)));
        return this;
    }

    /** Queues a lost connection. */
    public synchronized FakeTransport fail(IOException e) {
        queue.add(e);
        return this;
    }

    /** Answers every request with nothing queued. */
    public synchronized FakeTransport handler(Function<Request, Response> handler) {
        this.handler = handler;
        return this;
    }

    /** What was sent, oldest first. */
    public synchronized List<Request> requests() {
        return List.copyOf(requests);
    }

    public synchronized Request last() {
        return requests.isEmpty() ? null : requests.get(requests.size() - 1);
    }

    @Override
    public synchronized Response send(Request request) throws IOException {
        requests.add(request);
        Object next = queue.poll();
        if (next instanceof IOException e) throw e;
        if (next instanceof Response r) return r;
        if (handler != null) return handler.apply(request);
        throw new IllegalStateException("FakeTransport: no answer queued for " + request);
    }
}

package com.runeclans.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A call to any {@link Route}, for an operation that has no friendly method yet:
 *
 * <pre>{@code
 * EventStandingsAnswer s = hub.route(Routes.GET_EVENTS_EVENT_ID_STANDINGS).path("eventId", id).as(EventStandingsAnswer.class).complete();
 * }</pre>
 *
 * Every call still goes through the handshake's scope check, pacing and 429 handling.
 */
public final class RouteCall {
    private static final Set<String> OWN_HEADERS = Set.of("authorization", "x-runeclan-client", "user-agent", "content-type", "accept");
    private final RuneClanClient client;
    private final Route route;
    private final Map<String, String> path = new LinkedHashMap<>();
    private final Map<String, String> query = new LinkedHashMap<>();
    private final Map<String, String> headers = new LinkedHashMap<>();
    private Object body;

    RouteCall(RuneClanClient client, Route route) {
        this.client = client;
        this.route = route;
        if (client.guildId() != null) headers.put("X-Guild-Id", client.guildId());
    }

    /** Fills {@code {name}} in the route's path (encoded for you). */
    public RouteCall path(String name, String value) {
        path.put(name, value);
        return this;
    }

    /** Adds {@code ?name=value}; a null value is left out. */
    public RouteCall query(String name, Object value) {
        if (value != null) query.put(name, String.valueOf(value));
        return this;
    }

    /** Adds a header. The SDK's own (Authorization, X-RuneClan-Client, User-Agent, Content-Type, Accept) can't be replaced. */
    public RouteCall header(String name, String value) {
        if (OWN_HEADERS.contains(name.toLowerCase(java.util.Locale.ROOT))) throw new IllegalArgumentException(name + " is set by the SDK");
        if (value != null) headers.put(name, value);
        return this;
    }

    /** The Discord server the call is for ({@code X-Guild-Id}). */
    public RouteCall guild(String guildId) {
        return header("X-Guild-Id", guildId);
    }

    /** The JSON body: a record, a Map or a {@link JsonNode}. */
    public RouteCall body(Object body) {
        this.body = body;
        return this;
    }

    /**
     * Marks this write as safe to send again: adds an {@code Idempotency-Key} (a new UUID, kept for every retry of this call), so the hub
     * answers a repeat with the first answer instead of doing it twice, and the SDK may then retry it after a lost connection or a 5xx.
     * Needs a hub that honours the header: a hub without it ignores the key, and a
     * resend there can do the write twice. Leave it off until your hub supports it.
     */
    public RouteCall idempotent() {
        return idempotencyKey(UUID.randomUUID().toString());
    }

    /** As {@link #idempotent()} with your own key (1 to 255 visible ASCII letters; reuse it only for the same write). */
    public RouteCall idempotencyKey(String key) {
        if (key == null || !key.matches("[\\x21-\\x7E]{1,255}")) throw new IllegalArgumentException("an Idempotency-Key is 1 to 255 visible ASCII letters");
        headers.put(Core.IDEMPOTENCY_KEY, key);
        return this;
    }

    public <T> ApiAction<T> as(Class<T> type) {
        Snapshot s = snapshot();
        return client.action(() -> client.core().decode(s.send(), type));
    }

    public <T> ApiAction<T> as(TypeReference<T> type) {
        Snapshot s = snapshot();
        return client.action(() -> client.core().decode(s.send(), type));
    }

    /** The answer as a JSON tree. */
    public ApiAction<JsonNode> asJson() {
        return as(JsonNode.class);
    }

    /** The answer's bytes (pictures, calendars, CSV). */
    public ApiAction<byte[]> asBytes() {
        Snapshot s = snapshot();
        return client.action(() -> s.send().body());
    }

    /** Sends the call and ignores the answer. */
    public ApiAction<Void> send() {
        Snapshot s = snapshot();
        return client.action(() -> {
            s.send();
            return null;
        });
    }

    /** As {@link #as(Class)}, with a 404 as an empty {@link Optional}. */
    public <T> ApiAction<Optional<T>> asOptional(Class<T> type) {
        Snapshot s = snapshot();
        return client.action(() -> {
            try {
                return Optional.ofNullable(client.core().decode(s.send(), type));
            } catch (RuneClanException e) {
                if (e.isNotFound() && !e.local()) return Optional.empty();
                throw e;
            }
        });
    }

    private Snapshot snapshot() {
        String filled = Paths.fill(route, path);
        byte[] bytes = client.core().json(body);
        return new Snapshot(client.core(), route, filled, java.util.Collections.unmodifiableMap(new LinkedHashMap<>(query)), Map.copyOf(headers), bytes);
    }

    /** The call as it was when {@code as(...)} was asked for, so later changes to this builder don't change a queued action. */
    private record Snapshot(Core core, Route route, String path, Map<String, String> query, Map<String, String> headers, byte[] body) {
        Transport.Response send() {
            return core.send(route, path, new LinkedHashMap<>(query), headers, body, true);
        }
    }
}

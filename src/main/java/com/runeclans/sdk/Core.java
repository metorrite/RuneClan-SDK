package com.runeclans.sdk;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runeclans.sdk.model.Me;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/** What every view of one client shares: the transport, the handshake, the pacer and the executor. */
final class Core {
    static final ObjectMapper JSON = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    static final System.Logger LOG = System.getLogger("com.runeclans.sdk");
    /** After a handshake that got no answer, the next try waits this long (an outage must not double every call). */
    static final Duration HANDSHAKE_RETRY = Duration.ofSeconds(60);
    private static final long DEFAULT_WINDOW_SECONDS = 60;
    static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    final RuneClanConfig config;
    final Transport transport;
    final Ticker ticker;
    final RateLimitPacer pacer;
    final Executor executor;
    private final ExecutorService ownExecutor;
    private final String userAgent;
    private final Object handshakeLock = new Object();
    private volatile Me handshake;
    private volatile long windowSeconds = DEFAULT_WINDOW_SECONDS;
    private long lastHandshakeTry = Long.MIN_VALUE;
    private final Set<String> loggedNotices = new HashSet<>();

    Core(RuneClanConfig config) {
        this.config = config;
        this.transport = config.transport();
        this.ticker = config.ticker();
        this.pacer = new RateLimitPacer(ticker);
        if (config.executor() != null) {
            this.executor = config.executor();
            this.ownExecutor = null;
        } else {
            this.ownExecutor = defaultExecutor();
            this.executor = ownExecutor;
        }
        String ua = RuneClanClient.CLIENT_NAME + "/" + RuneClanClient.VERSION + " (Java " + Runtime.version().feature() + ")";
        this.userAgent = config.userAgentSuffix() == null ? ua : ua + " " + config.userAgentSuffix();
    }

    /** Virtual threads where the JDK has them (21+), else a cached pool of daemon threads. */
    private static ExecutorService defaultExecutor() {
        try {
            return (ExecutorService) Executors.class.getMethod("newVirtualThreadPerTaskExecutor").invoke(null);
        } catch (ReflectiveOperationException e) {
            return Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "runeclan-sdk");
                t.setDaemon(true);
                return t;
            });
        }
    }

    Me handshake() {
        return handshake;
    }

    /** Asks the hub {@code GET /v1/me} and keeps the answer: scopes, tier, limits, SDK versions and notices. */
    Me refreshHandshake() {
        synchronized (handshakeLock) {
            lastHandshakeTry = ticker.nanos();
            Transport.Response r;
            try {
                r = send(Routes.GET_ME, "/v1/me", null, Map.of(), null, false);
            } catch (RuneClanException e) {
                // an interrupt is not an outage: the next call may try the handshake again at once
                if ("interrupted".equals(e.code())) lastHandshakeTry = Long.MIN_VALUE;
                throw e;
            }
            Me me = decode(r, Me.class);
            if (me == null) throw new RuneClanException(r.status(), "bad_answer", "The RuneClan hub's handshake answer was empty.", null, null, false, null);
            apply(me);
            // the handshake's own answer already counts against the bucket: apply its headers now that the bucket exists
            pacer.observe(intHeader(r, "RateLimit-Limit"), intHeader(r, "RateLimit-Remaining"), windowSeconds);
            return me;
        }
    }

    /** The handshake if it is missing and due; an unreachable hub only logs (the call itself then reports it). */
    private void ensureHandshake() {
        if (!config.handshake() || handshake != null) return;
        synchronized (handshakeLock) {
            if (handshake != null) return;
            long now = ticker.nanos();
            if (lastHandshakeTry != Long.MIN_VALUE && now - lastHandshakeTry < HANDSHAKE_RETRY.toNanos()) return;
            try {
                refreshHandshake();
            } catch (RuneClanException e) {
                if (transientFailure(e)) {
                    LOG.log(Level.WARNING, "RuneClan SDK: the start-up handshake got no answer ({0}); calls go out unpaced until it works.", e.code());
                } else {
                    throw e;
                }
            }
        }
    }

    /** No answer, a 429 or a 5xx: worth trying again later, not a reason to stop the bot. An interrupt is passed on. */
    static boolean transientFailure(RuneClanException e) {
        if ("interrupted".equals(e.code())) return false;
        return e.status() == 0 || e.status() == 429 || e.status() >= 500;
    }

    private void apply(Me me) {
        Me.RateLimit rl = me.rateLimit();
        if (rl != null && rl.perMinute() != null) {
            long window = rl.windowSeconds() == null || rl.windowSeconds() <= 0 ? DEFAULT_WINDOW_SECONDS : rl.windowSeconds();
            windowSeconds = window;
            pacer.configure(rl.perMinute(), rl.burst() == null ? rl.perMinute() : rl.burst(), window);
        }
        Me.Sdk sdk = me.sdk();
        if (sdk != null && sdk.minVersion() != null) {
            try {
                if (Versions.compare(RuneClanClient.VERSION, sdk.minVersion()) < 0) {
                    LOG.log(Level.ERROR, "RuneClan SDK: the hub no longer serves {0} {1} (oldest served: {2}). Update to {3}.",
                            RuneClanClient.CLIENT_NAME, RuneClanClient.VERSION, sdk.minVersion(), sdk.latest());
                }
            } catch (RuntimeException e) {
                // a typo in the hub's data must never stop a bot from starting
                LOG.log(Level.WARNING, "RuneClan SDK: the hub''s minVersion \"{0}\" isn''t a version; ignored.", sdk.minVersion());
            }
        }
        if (me.notices() != null) {
            for (Me.NoticesItem n : me.notices()) {
                String key = n.code() + "|" + n.message();
                synchronized (loggedNotices) {
                    if (!loggedNotices.add(key)) continue;
                }
                LOG.log("warning".equals(n.level()) ? Level.WARNING : Level.INFO, "RuneClan hub: {0}", n.message());
            }
        }
        handshake = me;
    }

    /** True when the handshake says the key may call this route (or no handshake is known yet). */
    boolean allowed(Route route) {
        Me me = handshake;
        if (route.scopes().isEmpty() || me == null || me.scopes() == null) return true;
        for (String s : route.scopes()) if (me.scopes().contains(s)) return true;
        return false;
    }

    /**
     * Sends one call: scope check, pacing, the request, then 429 / outage handling. Returns any 2xx answer; throws {@link RuneClanException}
     * for the rest.
     */
    Transport.Response send(Route route, String path, Map<String, String> query, Map<String, String> extraHeaders, byte[] body, boolean withHandshake) {
        if (withHandshake && route.keyed()) ensureHandshake();
        if (!allowed(route)) {
            throw new RuneClanException(403, "missing_scope", "This key doesn't have the scope " + String.join(" or ", route.scopes())
                    + " that " + route + " needs. Ask RuneClans for it on your app's page.", null, null, true, null);
        }
        URI uri = URI.create(config.baseUrl() + path + queryString(query));
        Map<String, String> headers = new LinkedHashMap<>(extraHeaders);
        headers.put("User-Agent", userAgent);
        headers.put("X-RuneClan-Client", RuneClanClient.CLIENT_NAME + "/" + RuneClanClient.VERSION);
        headers.put("Accept", route.mediaType() == null ? "application/json" : route.mediaType() + (route.mediaType().equals("application/json") ? "" : ", application/json"));
        if (route.keyed()) headers.put("Authorization", "Bearer " + config.apiKey());
        if (body != null) headers.put("Content-Type", "application/json");
        Transport.Request request = new Transport.Request(route.method(), uri, headers, body, config.requestTimeout());
        // a write that carries an Idempotency-Key is answered once by the hub however often it arrives, so it may go again too
        boolean repeatable = route.safeToRepeat() || request.header(IDEMPOTENCY_KEY) != null;

        int attempt = 0;
        while (true) {
            attempt++;
            Transport.Response r;
            try {
                if (route.keyed()) pacer.acquire();
                r = transport.send(request);
            } catch (IOException e) {
                if (repeatable && attempt < config.maxAttempts()) {
                    pause(backoff(attempt));
                    continue;
                }
                if (e instanceof java.net.http.HttpTimeoutException && !(e instanceof java.net.http.HttpConnectTimeoutException)) {
                    throw new RuneClanException(0, "timeout", "The RuneClan hub didn't answer " + route + " in time. It may still have done it.", null, null, false, e);
                }
                throw new RuneClanException(0, "unreachable", "The RuneClan hub couldn't be reached (" + e.getClass().getSimpleName() + ").", null, null, false, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuneClanException(0, "interrupted", "Interrupted while calling the RuneClan hub.", null, null, true, e);
            }
            if (route.keyed()) pacer.observe(intHeader(r, "RateLimit-Limit"), intHeader(r, "RateLimit-Remaining"), windowSeconds);
            int status = r.status();
            if (status == 429) {
                Integer ra = intHeader(r, "Retry-After");
                long wait = ra == null ? 1 : Math.max(0, ra);
                // The hub's own 429 (its JSON rate_limited answer) comes from the key check, before the route does anything, so any
                // call may go again. A 429 from something in between (a proxy) proves nothing: only repeatable calls go again then.
                boolean again = repeatable || hubRateLimited(r);
                if (again && attempt < config.maxAttempts() && Duration.ofSeconds(wait).compareTo(config.maxRetryWait()) <= 0) {
                    // only a wait the SDK actually does holds back other calls; a refused long wait is the caller's to handle
                    pacer.blockFor(wait);
                    if (!route.keyed()) pause(Duration.ofSeconds(wait));
                    continue;
                }
                throw error(r, route);
            }
            if (status == 409 && repeatable && request.header(IDEMPOTENCY_KEY) != null && attempt < config.maxAttempts() && inProgress(r)) {
                Integer ra = intHeader(r, "Retry-After");
                pause(Duration.ofSeconds(ra == null ? 1 : Math.min(ra, config.maxRetryWait().toSeconds())));
                continue;
            }
            if ((status == 502 || status == 503 || status == 504) && repeatable && attempt < config.maxAttempts()) {
                Integer ra = intHeader(r, "Retry-After");
                Duration wait = ra != null && Duration.ofSeconds(ra).compareTo(config.maxRetryWait()) <= 0 ? Duration.ofSeconds(ra) : backoff(attempt);
                pause(wait);
                continue;
            }
            if (status >= 200 && status < 300) return r;
            throw error(r, route);
        }
    }

    /** A 429 the hub's key check gave: JSON with error rate_limited. */
    private static boolean hubRateLimited(Transport.Response r) {
        try {
            JsonNode n = r.body().length == 0 ? null : JSON.readTree(r.body());
            return n != null && "rate_limited".equals(n.path("error").asText());
        } catch (IOException e) {
            return false;
        }
    }

    /** 409 idempotency_in_progress: the first try with this key is still running on the hub. */
    private static boolean inProgress(Transport.Response r) {
        try {
            JsonNode n = r.body().length == 0 ? null : JSON.readTree(r.body());
            return n != null && "idempotency_in_progress".equals(n.path("error").asText());
        } catch (IOException e) {
            return false;
        }
    }

    private void pause(Duration d) {
        try {
            ticker.sleep(d);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuneClanException(0, "interrupted", "Interrupted while waiting to call the RuneClan hub again.", null, null, true, e);
        }
    }

    /** 0.5 s, 1 s, 2 s, ... (at most 8 s), each plus up to a quarter more so many bots don't come back at the same moment. */
    static Duration backoff(int attempt) {
        long base = Math.min(8000, 500L << Math.min(attempt - 1, 4));
        return Duration.ofMillis(base + ThreadLocalRandom.current().nextLong(base / 4 + 1));
    }

    private RuneClanException error(Transport.Response r, Route route) {
        JsonNode err = null;
        try {
            if (r.body().length > 0) err = JSON.readTree(r.body());
        } catch (IOException ignored) {
            // not JSON (a proxy's page): keep the status only
        }
        String code = err != null && err.hasNonNull("error") ? err.get("error").asText() : "http_" + r.status();
        String message = err != null && err.hasNonNull("message") ? err.get("message").asText() : "The RuneClan hub answered " + r.status() + " to " + route + ".";
        Integer retry = intHeader(r, "Retry-After");
        if (retry == null && err != null && err.has("retryAfter") && err.get("retryAfter").canConvertToInt()) retry = err.get("retryAfter").asInt();
        if (r.status() == 426) LOG.log(Level.ERROR, "RuneClan SDK: {0}", message);
        return new RuneClanException(r.status(), code, message, retry, err, false, null);
    }

    static Integer intHeader(Transport.Response r, String name) {
        String v = r.header(name);
        if (v == null) return null;
        v = v.strip();
        return v.matches("\\d{1,9}") ? Integer.valueOf(v) : null;
    }

    <T> T decode(Transport.Response r, Class<T> type) {
        if (type == Void.class) return null;
        try {
            if (r.body().length == 0) return null;
            return JSON.readValue(r.body(), type);
        } catch (IOException e) {
            throw new RuneClanException(r.status(), "bad_answer", "The RuneClan hub's answer couldn't be read as " + type.getSimpleName() + ".", null, null, false, e);
        }
    }

    <T> T decode(Transport.Response r, com.fasterxml.jackson.core.type.TypeReference<T> type) {
        try {
            if (r.body().length == 0) return null;
            return JSON.readValue(r.body(), type);
        } catch (IOException e) {
            throw new RuneClanException(r.status(), "bad_answer", "The RuneClan hub's answer couldn't be read.", null, null, false, e);
        }
    }

    byte[] json(Object body) {
        if (body == null) return null;
        try {
            return JSON.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new IllegalArgumentException("The body couldn't be written as JSON: " + e.getMessage(), e);
        }
    }

    private static String queryString(Map<String, String> query) {
        if (query == null || query.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : query.entrySet()) {
            if (e.getValue() == null) continue;
            sb.append(sb.length() == 0 ? '?' : '&').append(Paths.enc(e.getKey())).append('=').append(Paths.enc(e.getValue()));
        }
        return sb.toString();
    }

    void close() {
        if (ownExecutor != null) ownExecutor.shutdown();
        try {
            transport.close();
        } catch (Exception ignored) {
            // nothing to do
        }
    }

    static List<String> list(List<String> l) {
        return l == null ? List.of() : l;
    }
}

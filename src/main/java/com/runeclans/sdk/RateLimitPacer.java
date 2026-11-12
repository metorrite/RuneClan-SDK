package com.runeclans.sdk;

import java.time.Duration;

/**
 * The SDK's local copy of the key's rate limit: a token bucket that holds {@code burst} tokens and refills {@code perMinute} tokens over
 * {@code windowSeconds}, set from the handshake. Every call takes a token first; with none left the call waits for one instead of being
 * sent and refused with a 429. The hub's {@code RateLimit-Remaining} header on every answer pulls the local count down when other copies
 * of the bot share the key, and a 429 empties it until {@code Retry-After} has passed.
 * <p>
 * This is a courtesy that saves 429s, not a rule: the hub checks every limit on its own. Thread-safe; waiting happens outside the lock,
 * and each caller reserves its token before it waits, so callers are served in order.
 */
public final class RateLimitPacer {
    private final Ticker ticker;
    private double capacity;
    private double perNano;
    private double tokens;
    private long last;
    private long blockedUntil;
    private boolean known;

    public RateLimitPacer(Ticker ticker) {
        this.ticker = ticker;
        this.last = ticker.nanos();
        this.blockedUntil = last;
    }

    /** Sets the limit from the handshake: up to {@code burst} tokens, {@code perWindow} more every {@code windowSeconds}. */
    public synchronized void configure(long perWindow, long burst, long windowSeconds) {
        if (perWindow <= 0 || windowSeconds <= 0) return;
        refill();
        double cap = Math.max(1, burst > 0 ? burst : perWindow);
        tokens = known ? Math.min(tokens, cap) : cap;
        capacity = cap;
        perNano = perWindow / (windowSeconds * 1e9);
        known = true;
    }

    /** True once a handshake has set the limit (before that, calls are not paced). */
    public synchronized boolean known() {
        return known;
    }

    /** Tokens left now (for tests and diagnostics). */
    public synchronized double tokens() {
        refill();
        return tokens;
    }

    /**
     * Reserves one token and returns how long the caller must wait before sending (zero when a token is free). The caller then waits
     * with {@link #acquire()} or its own sleep.
     */
    public synchronized Duration reserve() {
        long now = ticker.nanos();
        long wait = Math.max(0, blockedUntil - now);
        if (!known) return Duration.ofNanos(wait);
        refill();
        tokens -= 1;
        if (tokens >= 0) return Duration.ofNanos(wait);
        long refillWait = (long) Math.ceil(-tokens / perNano);
        return Duration.ofNanos(Math.max(wait, refillWait));
    }

    /** Takes a token, waiting for one if needed. */
    public void acquire() throws InterruptedException {
        Duration d = reserve();
        if (!d.isZero()) ticker.sleep(d);
    }

    /**
     * Corrects the local count from an answer's headers. {@code remaining} (RateLimit-Remaining) only ever lowers it: other copies sharing
     * the key spend the same bucket, while our own reservations may not have reached the hub yet. {@code limit} (RateLimit-Limit, the
     * per-window rate) updates the refill rate when the hub changed the key's tier.
     */
    public synchronized void observe(Integer limit, Integer remaining, long windowSeconds) {
        if (!known) return;
        refill();
        if (limit != null && limit > 0 && windowSeconds > 0) perNano = limit / (windowSeconds * 1e9);
        if (remaining != null && remaining >= 0 && remaining < tokens) tokens = remaining;
    }

    /** A 429: no calls until {@code retryAfterSeconds} have passed. */
    public synchronized void blockFor(long retryAfterSeconds) {
        long until = ticker.nanos() + Math.max(0, retryAfterSeconds) * 1_000_000_000L;
        if (until > blockedUntil) blockedUntil = until;
        if (known) {
            refill();
            tokens = Math.min(tokens, 0);
        }
    }

    private void refill() {
        long now = ticker.nanos();
        long elapsed = now - last;
        last = now;
        if (known && elapsed > 0) tokens = Math.min(capacity, tokens + elapsed * perNano);
    }
}

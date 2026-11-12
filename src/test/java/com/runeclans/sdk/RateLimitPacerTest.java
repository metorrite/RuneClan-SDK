package com.runeclans.sdk;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The local token bucket against a fake clock. */
class RateLimitPacerTest {
    private final FakeTicker clock = new FakeTicker();
    private final RateLimitPacer pacer = new RateLimitPacer(clock);

    @Test
    void unknownLimitNeverWaits() {
        assertFalse(pacer.known());
        for (int i = 0; i < 1000; i++) assertEquals(Duration.ZERO, pacer.reserve());
    }

    @Test
    void burstThenOneTokenPerSecondAtSixtyAMinute() {
        pacer.configure(60, 3, 60);
        assertEquals(Duration.ZERO, pacer.reserve());
        assertEquals(Duration.ZERO, pacer.reserve());
        assertEquals(Duration.ZERO, pacer.reserve());
        assertEquals(1000, pacer.reserve().toMillis(), "the 4th waits one refill");
        assertEquals(2000, pacer.reserve().toMillis(), "the 5th queues behind the 4th");
        clock.advance(Duration.ofSeconds(2));
        assertEquals(1000, pacer.reserve().toMillis(), "two tokens came back and both were already reserved");
    }

    @Test
    void refillStopsAtTheBurst() {
        pacer.configure(60, 2, 60);
        clock.advance(Duration.ofHours(1));
        assertEquals(2.0, pacer.tokens(), 1e-9);
        pacer.reserve();
        pacer.reserve();
        assertTrue(pacer.reserve().toMillis() > 0);
    }

    @Test
    void theHubsRemainingOnlyLowersTheCount() {
        pacer.configure(60, 10, 60);
        pacer.observe(60, 2, 60);
        assertEquals(2.0, pacer.tokens(), 1e-9);
        pacer.observe(60, 50, 60);
        assertEquals(2.0, pacer.tokens(), 1e-9, "a higher remaining (another bucket's view) never adds tokens");
        pacer.reserve();
        pacer.reserve();
        assertEquals(1000, pacer.reserve().toMillis());
    }

    @Test
    void aNewLimitHeaderChangesTheRefillRate() {
        pacer.configure(60, 1, 60);
        pacer.reserve();
        pacer.observe(600, null, 60);
        assertEquals(100, pacer.reserve().toMillis(), "600 a minute = one every 100 ms");
    }

    @Test
    void a429BlocksUntilRetryAfter() {
        pacer.configure(3000, 100, 60);
        pacer.blockFor(5);
        assertEquals(5000, pacer.reserve().toMillis());
        clock.advance(Duration.ofSeconds(5));
        assertEquals(Duration.ZERO, pacer.reserve(), "free again after Retry-After");
    }

    @Test
    void a429BlocksEvenBeforeAHandshake() {
        pacer.blockFor(3);
        assertEquals(3000, pacer.reserve().toMillis());
        clock.advance(Duration.ofSeconds(3));
        assertEquals(Duration.ZERO, pacer.reserve());
    }

    @Test
    void reconfiguringKeepsSpentTokensSpent() {
        pacer.configure(60, 5, 60);
        for (int i = 0; i < 5; i++) pacer.reserve();
        pacer.configure(600, 50, 60);
        assertTrue(pacer.tokens() < 1, "a second handshake must not refill the bucket");
    }
}

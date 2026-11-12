package com.runeclans.sdk;

import java.time.Duration;

/** The SDK's clock and its way to wait, so tests can run rate-limit waits instantly. */
public interface Ticker {
    /** A monotonic time in nanoseconds (like {@link System#nanoTime()}). */
    long nanos();

    /** Waits this long (may return early when interrupted, by throwing). */
    void sleep(Duration d) throws InterruptedException;

    Ticker SYSTEM = new Ticker() {
        @Override
        public long nanos() {
            return System.nanoTime();
        }

        @Override
        public void sleep(Duration d) throws InterruptedException {
            if (!d.isNegative() && !d.isZero()) Thread.sleep(d.toMillis(), (int) (d.toNanos() % 1_000_000));
        }
    };
}

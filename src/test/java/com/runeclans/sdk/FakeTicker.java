package com.runeclans.sdk;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** A clock that only moves when someone sleeps (or the test advances it), and remembers every sleep. */
final class FakeTicker implements Ticker {
    long now = 1_000_000_000_000L;
    final List<Duration> sleeps = new ArrayList<>();

    @Override
    public synchronized long nanos() {
        return now;
    }

    @Override
    public synchronized void sleep(Duration d) {
        sleeps.add(d);
        now += d.toNanos();
    }

    synchronized void advance(Duration d) {
        now += d.toNanos();
    }

    synchronized Duration slept() {
        return sleeps.stream().reduce(Duration.ZERO, Duration::plus);
    }
}

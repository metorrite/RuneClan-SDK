package com.runeclans.sdk;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * One call, not sent yet; like JDA's {@code RestAction}. {@link #complete()} sends it and waits (fine on a virtual thread or your own
 * worker), {@link #submit()} gives a {@link CompletableFuture}, and {@link #queue} runs callbacks, so a JDA listener never blocks the
 * gateway thread. Sending again sends a new call each time.
 */
public final class ApiAction<T> {
    private static final System.Logger LOG = System.getLogger("com.runeclans.sdk");
    private final Supplier<T> call;
    private final Executor executor;

    ApiAction(Supplier<T> call, Executor executor) {
        this.call = call;
        this.executor = executor;
    }

    /** An action that answers {@code value} without a call (fakes and tests). */
    public static <T> ApiAction<T> of(T value) {
        return new ApiAction<>(() -> value, Runnable::run);
    }

    /** Sends the call and waits for the answer. Throws {@link RuneClanException} when it didn't work. */
    public T complete() {
        return call.get();
    }

    /** Sends the call on the SDK's executor. The future fails with a {@link RuneClanException} when it didn't work. */
    public CompletableFuture<T> submit() {
        return CompletableFuture.supplyAsync(call, executor);
    }

    /** Sends the call on the SDK's executor and hands the answer, or the failure, to the callbacks (either may be null). */
    public void queue(Consumer<? super T> success, Consumer<? super Throwable> failure) {
        submit().whenComplete((value, error) -> {
            if (error != null) {
                Throwable cause = error instanceof java.util.concurrent.CompletionException && error.getCause() != null ? error.getCause() : error;
                if (failure != null) {
                    try {
                        failure.accept(cause);
                    } catch (RuntimeException e) {
                        LOG.log(System.Logger.Level.WARNING, "RuneClan SDK: a queue() failure callback threw " + e.getClass().getSimpleName(), e);
                    }
                } else {
                    // like JDA's RestAction: a failure nobody handles is logged, never lost
                    LOG.log(System.Logger.Level.WARNING, "RuneClan SDK: a queued call failed: " + describe(cause));
                }
            } else if (success != null) {
                try {
                    success.accept(value);
                } catch (RuntimeException e) {
                    LOG.log(System.Logger.Level.WARNING, "RuneClan SDK: a queue() success callback threw " + e.getClass().getSimpleName(), e);
                }
            }
        });
    }

    private static String describe(Throwable t) {
        if (t instanceof RuneClanException e) return e.status() + " " + e.code();
        return t.getClass().getSimpleName();
    }

    /** As {@link #queue(Consumer, Consumer)}; a failure is logged as a warning (status and code only). */
    public void queue(Consumer<? super T> success) {
        queue(success, null);
    }

    /** The same call with its answer turned into something else. */
    public <R> ApiAction<R> map(Function<? super T, ? extends R> f) {
        Objects.requireNonNull(f);
        return new ApiAction<>(() -> f.apply(call.get()), executor);
    }
}

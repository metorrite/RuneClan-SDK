package com.runeclans.sdk;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A call that didn't work. {@link #code()} is the hub's error code (see {@code x-runeclan-error-codes} in {@code spec/openapi.json}, e.g.
 * {@code missing_scope}, {@code rate_limited}, {@code sdk_too_old}), or one of the SDK's own:
 * <ul>
 *   <li>{@code unreachable}: the hub couldn't be reached (status 0)</li>
 *   <li>{@code interrupted}: the thread was interrupted while waiting (status 0)</li>
 *   <li>{@code missing_scope} with {@link #local()} true: the handshake says the key lacks the scope, so the call was never sent</li>
 *   <li>{@code bad_answer}: the hub answered something the SDK couldn't read</li>
 * </ul>
 * The message is written for a person. It never holds the API key.
 */
public class RuneClanException extends RuntimeException {
    private final int status;
    private final String code;
    private final Integer retryAfter;
    private final transient JsonNode body;
    private final boolean local;

    public RuneClanException(int status, String code, String message, Integer retryAfter, JsonNode body, boolean local, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.retryAfter = retryAfter;
        this.body = body;
        this.local = local;
    }

    public RuneClanException(int status, String code, String message) {
        this(status, code, message, null, null, false, null);
    }

    /** The HTTP status, or 0 when no answer came back. */
    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    /** Seconds the hub asked to wait (429 {@code Retry-After}), or null. */
    public Integer retryAfter() {
        return retryAfter;
    }

    /** The hub's whole error answer (some add fields, e.g. {@code minVersion} and {@code latest} on a 426), or null. */
    public JsonNode body() {
        return body;
    }

    /** True when the SDK refused the call itself and nothing was sent to the hub. */
    public boolean local() {
        return local;
    }

    public boolean isNotFound() {
        return status == 404;
    }

    public boolean isRateLimited() {
        return status == 429;
    }

    @Override
    public String toString() {
        return "RuneClanException[" + status + " " + code + (local ? ", not sent" : "") + "]: " + getMessage();
    }
}

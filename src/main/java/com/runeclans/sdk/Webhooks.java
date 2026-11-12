package com.runeclans.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.runeclans.sdk.model.Webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Your app's webhooks ({@code webhooks:manage}, at most 5), and {@link #verify} for the deliveries. */
public final class Webhooks {
    private final RuneClanClient c;

    Webhooks(RuneClanClient c) {
        this.c = c;
    }

    /** Adds a webhook (https only). The answer holds {@code secret}, shown only now: store it. {@code kinds} empty or null for all. */
    public ApiAction<Webhook> create(String url, List<String> kinds) {
        return c.route(Routes.POST_WEBHOOKS).body(Map.of("url", url, "kinds", kinds == null ? List.of() : kinds)).as(Webhook.class);
    }

    public ApiAction<JsonNode> list() {
        return c.route(Routes.GET_WEBHOOKS).asJson();
    }

    public ApiAction<Void> delete(String webhookId) {
        return c.route(Routes.DELETE_WEBHOOKS_ID).path("id", webhookId).send();
    }

    /** Sends a test delivery. */
    public ApiAction<JsonNode> ping(String webhookId) {
        return c.route(Routes.POST_WEBHOOKS_ID_PING).path("id", webhookId).asJson();
    }

    /** Turns a webhook the hub paused (after failures) back on. */
    public ApiAction<JsonNode> resume(String webhookId) {
        return c.route(Routes.POST_WEBHOOKS_ID_RESUME).path("id", webhookId).asJson();
    }

    /**
     * Checks a delivery's {@code X-Hub-Signature} ({@code t=<unix>,v1=<hex>}) against its raw body, refusing deliveries older than
     * {@code toleranceSeconds}. Compares in constant time.
     */
    public static boolean verify(String secret, String signatureHeader, String rawBody, long toleranceSeconds, long nowSeconds) {
        if (secret == null || secret.isEmpty() || signatureHeader == null || rawBody == null) return false;
        String t = null;
        String v1 = null;
        for (String part : signatureHeader.split(",")) {
            String p = part.strip();
            if (p.startsWith("t=")) t = p.substring(2);
            else if (p.startsWith("v1=")) v1 = p.substring(3);
        }
        if (t == null || v1 == null || !t.matches("\\d{1,12}") || Math.abs(nowSeconds - Long.parseLong(t)) > toleranceSeconds) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = HexFormat.of().formatHex(mac.doFinal((t + "." + rawBody).getBytes(StandardCharsets.UTF_8))).getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(expected, v1.getBytes(StandardCharsets.US_ASCII));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 isn't available in this Java", e);
        }
    }

    /** {@link #verify(String, String, String, long, long)} with 5 minutes of tolerance and the current time. */
    public static boolean verify(String secret, String signatureHeader, String rawBody) {
        return verify(secret, signatureHeader, rawBody, 300, System.currentTimeMillis() / 1000);
    }
}

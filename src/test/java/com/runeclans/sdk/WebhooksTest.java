package com.runeclans.sdk;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhooksTest {
    @Test
    void signaturesAndTheirAge() throws Exception {
        String secret = "whsec_" + "s".repeat(43);
        String body = "{\"id\":\"1\",\"kind\":\"ping\",\"data\":{}}";
        long t = 1_800_000_000L;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sig = "t=" + t + ",v1=" + HexFormat.of().formatHex(mac.doFinal((t + "." + body).getBytes(StandardCharsets.UTF_8)));
        assertTrue(Webhooks.verify(secret, sig, body, 300, t + 10));
        assertFalse(Webhooks.verify(secret, sig, body + " ", 300, t + 10));
        assertFalse(Webhooks.verify(secret, sig, body, 300, t + 301), "too old");
        assertFalse(Webhooks.verify(secret, sig, body, 300, t - 301), "from the future");
        assertFalse(Webhooks.verify(secret, "v1=abc", body, 300, t));
        assertFalse(Webhooks.verify(null, sig, body, 300, t));
    }

    @Test
    void grantLinks() {
        assertEquals("https://link.example.org/grant?app=a%20b", RuneClanClient.grantUrl("https://link.example.org/", "a b"));
    }
}

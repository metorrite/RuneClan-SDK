import com.runeclans.sdk.Webhooks;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Receive webhook deliveries with nothing but the JDK. The hub signs every delivery (X-Hub-Signature: t=...,v1=...);
 * Webhooks.verify checks it against the raw body and refuses old ones. Add the webhook once with
 * hub.webhooks().create("https://your.host/runeclan", null) and store the secret it answers (shown only once).
 *
 * <pre>
 * WEBHOOK_SECRET=... java -cp ... WebhookReceiver.java
 * </pre>
 */
public class WebhookReceiver {
    public static void main(String[] args) throws IOException {
        String secret = System.getenv("WEBHOOK_SECRET");
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/runeclan", exchange -> handle(exchange, secret));
        server.start();
        System.out.println("Listening on http://localhost:8080/runeclan");
    }

    static void handle(HttpExchange exchange, String secret) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String signature = exchange.getRequestHeaders().getFirst("X-Hub-Signature");
        int status = accept(secret, signature, body) ? 204 : 401;
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    /** True when the delivery is real and fresh; then act on it (it is one feed entry as JSON). */
    static boolean accept(String secret, String signature, String body) {
        if (!Webhooks.verify(secret, signature, body)) return false;   // 5 minutes of tolerance, constant-time compare
        System.out.println("Delivery: " + body);
        return true;
    }
}

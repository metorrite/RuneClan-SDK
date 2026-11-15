package com.runeclans.sdk;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The real HTTP path once, against a local server: headers out, headers and body back. */
class JdkTransportTest {
    private HttpServer server;
    private final List<String> seen = new ArrayList<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            seen.add(ex.getRequestMethod() + " " + ex.getRequestURI() + " | " + ex.getRequestHeaders().getFirst("X-RuneClan-Client")
                    + " | " + new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String path = ex.getRequestURI().getPath();
            byte[] body = (path.equals("/v1/me") ? ClientTest.me("\"guilds:write\"", 60, 10) : "{\"guildId\":\"g\",\"name\":\"Clan\"}").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.getResponseHeaders().set("RateLimit-Remaining", "9");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void aRealRoundTrip() {
        try (RuneClanClient c = RuneClan.connect(RuneClanConfig.builder().apiKey(ClientTest.KEY).env(n -> null)
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build())) {
            String guild = "5".repeat(18);
            assertEquals("Clan", c.guilds().register(guild, "Clan").complete().name());
            assertEquals("GET /v1/me | runeclan-sdk-java/" + RuneClanClient.VERSION + " | ", seen.get(0));
            assertEquals("PUT /v1/guilds/" + guild + " | runeclan-sdk-java/" + RuneClanClient.VERSION + " | {\"name\":\"Clan\"}", seen.get(1));
        }
    }
}

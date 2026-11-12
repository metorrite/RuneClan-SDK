package com.runeclans.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runeclans.sdk.model.Me;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The committed spec copy, the generated route table and the version agree. */
class SpecTest {
    private static final Set<String> METHODS = Set.of("get", "put", "post", "delete", "patch");

    private static JsonNode spec() throws Exception {
        return new ObjectMapper().readTree(Files.readString(Path.of("spec", "openapi.json")));
    }

    @Test
    void everyPublicOperationHasARouteAndNothingElseIsInTheSpec() throws Exception {
        Set<String> inSpec = new HashSet<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = spec().path("paths").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> path = it.next();
            for (Iterator<Map.Entry<String, JsonNode>> ops = path.getValue().fields(); ops.hasNext(); ) {
                Map.Entry<String, JsonNode> op = ops.next();
                if (!METHODS.contains(op.getKey())) continue;
                assertEquals("public", op.getValue().path("x-runeclan-audience").asText(), "only public operations belong in this public repo: " + op.getKey() + " " + path.getKey());
                inSpec.add(op.getKey().toUpperCase() + " " + path.getKey());
            }
        }
        Set<String> inRoutes = new HashSet<>();
        for (Route r : Routes.ALL) assertTrue(inRoutes.add(r.toString()), "listed twice: " + r);
        assertEquals(inSpec, inRoutes);
        assertTrue(inRoutes.size() > 100, "the whole public API, not a slice");
    }

    @Test
    void theSpecKeepsNoPrivateSecuritySchemes() throws Exception {
        Set<String> schemes = new HashSet<>();
        spec().path("components").path("securitySchemes").fieldNames().forEachRemaining(schemes::add);
        assertEquals(Set.of("apiKey"), schemes);
    }

    @Test
    void theVersionMatchesThePom() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("<artifactId>runeclan-sdk</artifactId>\\s*<version>([^<]+)</version>").matcher(pom);
        assertTrue(m.find(), "the pom's own version");
        assertEquals(m.group(1), RuneClanClient.VERSION);
        assertTrue(RuneClanClient.VERSION.matches("\\d+\\.\\d+\\.\\d+(-[0-9A-Za-z.-]+)?"));
    }

    @Test
    void theSpecCarriesNoInternalNotes() throws Exception {
        String text = Files.readString(Path.of("spec", "openapi.json"));
        assertTrue(!java.util.regex.Pattern.compile("\\b[TBPL]-\\d{2,4}\\b").matcher(text).find(), "ticket numbers");
        assertTrue(!text.contains("HUB_"), "hub switch names");
    }

    @Test
    void scopesFromTheSpecReachTheRoutes() {
        assertEquals(java.util.List.of("players:read"), Routes.GET_PLAYERS.scopes());
        assertEquals(java.util.List.of("polls:read", "polls:write"), Routes.GET_CLANS_CLAN_ID_POLLS.scopes());
        assertTrue(Routes.GET_CLANS_CLAN_ID.scopes().isEmpty());
        assertEquals(Me.class, Routes.GET_ME.responseType());
        assertTrue(!Routes.POST_APPS_REQUESTS.keyed(), "asking for a key takes no key");
    }

    @Test
    void generatedTypesIgnoreFieldsTheyDontKnow() throws Exception {
        Me me = Core.JSON.readValue("{\"appId\":\"a\",\"rateLimit\":{\"perMinute\":60,\"burst\":10,\"windowSeconds\":60,\"new\":true},\"brandNew\":[1]}", Me.class);
        assertEquals(60L, me.rateLimit().perMinute());
    }

    @Test
    void versionsCompareLikeTheHub() {
        assertTrue(Versions.compare("0.2.0-rc1", "0.2.0") < 0);
        assertTrue(Versions.compare("0.10.0", "0.9.9") > 0);
        assertEquals(0, Versions.compare("1.0.0", "1.0.0"));
    }
}

package com.runeclans.sdk;

import com.runeclans.sdk.model.Clan;
import com.runeclans.sdk.model.Community;
import com.runeclans.sdk.model.CommunityListing;
import com.runeclans.sdk.model.Me;
import com.runeclans.sdk.model.Player;
import com.runeclans.sdk.model.PlayerPage;
import com.runeclans.sdk.model.RankingPage;
import com.runeclans.sdk.testing.FakeTransport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The client against a fake hub and a fake clock: handshake, headers, scopes, pacing, 429s, retries, async. */
class ClientTest {
    static final String KEY = "rsh_" + "k".repeat(43); // made at runtime, not a real key
    private final FakeTransport hub = new FakeTransport();
    private final FakeTicker clock = new FakeTicker();

    static String me(String scopes, int perMinute, int burst) {
        return "{\"appId\":\"app1\",\"appName\":\"Test bot\",\"keyId\":\"k1\",\"scopes\":[" + scopes + "],\"tier\":\"standard\",\"firstParty\":false,"
                + "\"rateLimit\":{\"perMinute\":" + perMinute + ",\"burst\":" + burst + ",\"windowSeconds\":60},"
                + "\"sdk\":{\"client\":\"runeclan-sdk-java\",\"version\":\"0.2.0\",\"minVersion\":null,\"latest\":\"0.2.0\"},\"notices\":[],\"futureField\":1}";
    }

    private RuneClanConfig.Builder config() {
        return RuneClanConfig.builder().apiKey(KEY).baseUrl("https://hub.example.org/").transport(hub).ticker(clock)
                .executor(Runnable::run).env(n -> null);
    }

    private RuneClanClient connect() {
        return RuneClan.connect(config().build());
    }

    @Test
    void connectRunsTheHandshakeAndSendsTheClientTag() {
        hub.answer(200, me("\"players:read\",\"links:read\"", 60, 10));
        RuneClanClient c = connect();
        Me me = c.handshake().orElseThrow();
        assertEquals("app1", me.appId());
        assertEquals(10L, me.rateLimit().burst());
        assertEquals("0.2.0", me.sdk().latest());
        assertTrue(c.hasScope("players:read"));
        assertFalse(c.hasScope("events:read"));
        Transport.Request r = hub.last();
        assertEquals("GET https://hub.example.org/v1/me", r.toString());
        assertEquals("Bearer " + KEY, r.header("authorization"));
        assertEquals("runeclan-sdk-java/" + RuneClanClient.VERSION, r.header("X-RuneClan-Client"));
        assertTrue(r.header("User-Agent").startsWith("runeclan-sdk-java/0.2.0 (Java "), r.header("User-Agent"));
        assertFalse(r.toString().contains(KEY));
        assertFalse(c.toString().contains(KEY));
    }

    @Test
    void aWrongKeyFailsAtConnect() {
        hub.answer(401, "{\"error\":\"unauthorized\",\"message\":\"Send a valid API key\"}");
        RuneClanException e = assertThrows(RuneClanException.class, this::connect);
        assertEquals(401, e.status());
        assertEquals("unauthorized", e.code());
        assertFalse(e.getMessage().contains(KEY));
    }

    @Test
    void anUnreachableHubStillConnectsAndTheHandshakeRunsAgainLater() {
        IOException down = new ConnectException("refused");
        hub.fail(down).fail(down).fail(down);
        RuneClanClient c = connect();
        assertTrue(c.handshake().isEmpty());
        assertEquals(3, hub.requests().size(), "GET /v1/me is safe to repeat: 3 attempts");

        // within a minute the handshake isn't tried again: the call goes out unpaced
        hub.answer(200, "{\"data\":[],\"nextCursor\":null}");
        c.players().list(null, 5).complete();
        assertEquals("/v1/players", hub.last().uri().getPath());

        clock.advance(Duration.ofSeconds(61));
        hub.answer(200, me("\"players:read\"", 60, 10)).answer(200, "{\"data\":[],\"nextCursor\":null}");
        c.players().list(null, 5).complete();
        List<Transport.Request> sent = hub.requests();
        assertEquals("/v1/me", sent.get(sent.size() - 2).uri().getPath());
        assertTrue(c.handshake().isPresent());
    }

    @Test
    void aMissingScopeFailsWithoutSendingAnything() {
        hub.answer(200, me("\"players:read\"", 60, 10));
        RuneClanClient c = connect();
        int before = hub.requests().size();
        RuneClanException e = assertThrows(RuneClanException.class, () -> c.players().events("a1_x", null, null, null, null).complete());
        assertEquals("missing_scope", e.code());
        assertTrue(e.local());
        assertTrue(e.getMessage().contains("events:read"), e.getMessage());
        assertEquals(before, hub.requests().size());
    }

    @Test
    void eitherScopeOfAnOrPairIsEnough() {
        hub.answer(200, me("\"polls:write\"", 60, 10)).answer(200, "{\"polls\":[]}");
        RuneClanClient c = connect();
        c.clans().polls("clan-1", null).complete();
        assertEquals("/v1/clans/clan-1/polls", hub.last().uri().getPath());
    }

    @Test
    void routesWithoutAScopeNeedOnlyAKey() {
        hub.answer(200, me("", 60, 10)).answer(200, "{\"clanId\":\"c1\",\"name\":\"Younglings\"}");
        Optional<Clan> clan = connect().clans().byName("Young lings").complete();
        assertEquals("Younglings", clan.orElseThrow().name());
        assertEquals("/v1/clans/by-name/Young%20lings", hub.last().uri().getRawPath());
    }

    @Test
    void communitiesAreReadLikeClans() {
        hub.answer(200, me("", 60, 10))
                .answer(200, "{\"data\":[{\"communityId\":\"k1\",\"name\":\"Boss Hunters\",\"slug\":\"boss-hunters\",\"members\":3,\"joinMode\":\"request\",\"requireLink\":true}]}")
                .answer(200, "{\"communityId\":\"k1\",\"name\":\"Boss Hunters\",\"slug\":\"boss-hunters\"}")
                .answer(200, "{\"communityId\":\"k1\",\"kind\":\"community\",\"name\":\"Boss Hunters\",\"joinMode\":\"request\",\"members\":3,\"live\":true}")
                .answer(404, "{\"error\":\"not_found\",\"message\":\"Not found\"}");
        RuneClanClient c = connect();
        List<CommunityListing> list = c.communities().list().complete();
        assertEquals("/v1/communities", hub.last().uri().getPath());
        assertEquals("k1", list.get(0).communityId());
        assertEquals(3L, list.get(0).members());
        assertTrue(list.get(0).requireLink());
        assertEquals("Boss Hunters", c.communities().bySlug("boss-hunters").complete().orElseThrow().name());
        assertEquals("/v1/communities/by-slug/boss-hunters", hub.last().uri().getPath());
        Community one = c.communities().get("k1").complete().orElseThrow();
        assertEquals("/v1/communities/k1", hub.last().uri().getPath());
        assertEquals("community", one.kind());
        assertTrue(one.live());
        assertTrue(c.communities().get("missing").complete().isEmpty(), "404 is empty, as for clans");
    }

    private static Map<String, String> query(Transport.Request r) {
        Map<String, String> out = new java.util.TreeMap<>();
        String raw = r.uri().getRawQuery();
        if (raw == null) return out;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            out.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return out;
    }

    @Test
    void statsRankingsSendTheirQuery() {
        hub.answer(200, me("", 60, 10))
                .answer(200, "{\"entity\":\"clan\",\"metric\":\"gained\",\"total\":1,\"period\":{\"key\":\"custom\",\"from\":\"2026-11-01\",\"to\":\"2026-11-07\"},"
                        + "\"entries\":[{\"rank\":1,\"slug\":\"younglings\",\"name\":\"Younglings\",\"members\":40,\"gained\":123456},{\"rank\":2,\"slug\":\"other\"}],\"found\":null}")
                .answer(200, "{\"entries\":[]}");
        RuneClanClient c = connect();
        RankingPage page = c.stats().rankings(new Stats.Query().entity("clan").skill("attack").metric("gained").custom("2026-11-01", "2026-11-07")
                .accountType("ironman").limit(10).offset(20).find("A b&c")).complete();
        assertEquals("/v1/stats/rankings", hub.last().uri().getPath());
        assertEquals(Map.of("entity", "clan", "skill", "attack", "metric", "gained", "period", "custom", "from", "2026-11-01", "to", "2026-11-07",
                "accountType", "ironman", "limit", "10", "offset", "20", "find", "A%20b%26c"), query(hub.last()));
        assertEquals(123456L, page.entries().get(0).gained());
        assertNull(page.entries().get(1).gained(), "a missing figure decodes as null");
        assertEquals("2026-11-07", page.period().to());

        c.stats().rankings(new Stats.Query().custom("2026-11-01", "2026-11-07").period("week")).complete();
        assertEquals(Map.of("period", "week"), query(hub.last()), "a named period drops the custom days");
    }

    @Test
    void statsGainersSendTheirQuery() {
        hub.answer(200, me("", 60, 10))
                .answer(200, "{\"entries\":[{\"rank\":1,\"rsn\":\"Ann\",\"gained\":400}]}")
                .answer(200, "{\"entries\":[]}");
        RuneClanClient c = connect();
        assertEquals(400L, c.stats().gainers("hour", new Stats.Query().entity("community").skill("slayer").metric("xp").accountType("main").limit(5)
                .offset(5).find("A b&c")).complete().entries().get(0).gained());
        assertEquals("/v1/stats/gainers", hub.last().uri().getPath());
        assertEquals(Map.of("period", "hour", "entity", "community", "skill", "slayer", "accountType", "main", "limit", "5", "offset", "5",
                "find", "A%20b%26c"), query(hub.last()), "metric is not sent");
        c.stats().gainers(null, new Stats.Query().period("week")).complete();
        assertEquals(Map.of("period", "week"), query(hub.last()), "a null period takes the query's");
    }

    @Test
    void statsRecordsSendTheirQuery() {
        hub.answer(200, me("", 60, 10))
                .answer(200, "{\"kind\":\"week\",\"entries\":[{\"rank\":1,\"rsn\":\"Ann\",\"gained\":1500,\"start\":\"2026-11-11\",\"end\":\"2026-11-17\"}]}");
        assertEquals("2026-11-17", connect().stats().records("week", new Stats.Query().skill("overall").accountType("ironman").limit(3).offset(6)
                .find("A b&c")).complete().entries().get(0).end());
        assertEquals("/v1/stats/records", hub.last().uri().getPath());
        assertEquals(Map.of("kind", "week", "skill", "overall", "accountType", "ironman", "limit", "3", "offset", "6", "find", "A%20b%26c"),
                query(hub.last()));
    }

    @Test
    void statsRankSummarySendsItsQuery() {
        hub.answer(200, me("", 60, 10))
                .answer(200, "{\"skills\":29,\"entries\":[{\"rank\":1,\"rsn\":\"Ann\",\"rankSum\":30}]}")
                .answer(200, "{\"skills\":29,\"entries\":[]}");
        RuneClanClient c = connect();
        assertEquals(30L, c.stats().rankSummary(new Stats.Query().accountType("ironman").limit(2).offset(4).find("A b&c")).complete().entries().get(0).rankSum());
        assertEquals("/v1/stats/rank-summary", hub.last().uri().getPath());
        assertEquals(Map.of("accountType", "ironman", "limit", "2", "offset", "4", "find", "A%20b%26c"), query(hub.last()));
        c.stats().rankSummary(null).complete();
        assertEquals(Map.of(), query(hub.last()));
    }

    @Test
    void a429IsWaitedOutAndSentAgain() {
        hub.answer(200, me("\"players:read\"", 60, 10))
                .answer(429, "{\"error\":\"rate_limited\",\"message\":\"Too many\",\"retryAfter\":2}", Map.of("Retry-After", "2", "RateLimit-Remaining", "0"))
                .answer(200, "{\"data\":[{\"alias\":\"a1_one\"}],\"nextCursor\":null}");
        PlayerPage page = connect().players().list(null, null).complete();
        assertEquals("a1_one", page.data().get(0).alias());
        assertTrue(clock.slept().compareTo(Duration.ofSeconds(2)) >= 0, "waited at least Retry-After: " + clock.slept());
    }

    @Test
    void aPostIsSentAgainAfterA429TooBecauseTheHubDidNothing() {
        hub.answer(200, me("\"webhooks:manage\"", 60, 10))
                .answer(429, "{\"error\":\"rate_limited\",\"message\":\"Too many\"}", Map.of("Retry-After", "1"))
                .answer(201, "{\"webhookId\":\"w1\",\"secret\":\"s\"}");
        assertEquals("w1", connect().webhooks().create("https://bot.example.org/h", null).complete().webhookId());
    }

    @Test
    void aLongRetryAfterIsThrownNotWaited() {
        hub.answer(200, me("\"players:read\"", 60, 10))
                .answer(429, "{\"error\":\"rate_limited\",\"message\":\"Too many\"}", Map.of("Retry-After", "600"));
        RuneClanException e = assertThrows(RuneClanException.class, () -> connect().players().list(null, null).complete());
        assertTrue(e.isRateLimited());
        assertEquals(600, e.retryAfter());
        assertTrue(clock.slept().compareTo(Duration.ofSeconds(60)) < 0);
    }

    @Test
    void aRefusedLongWaitDoesntHoldBackTheNextCall() {
        hub.answer(200, me("\"players:read\"", 600, 100))
                .answer(429, "{\"error\":\"rate_limited\",\"message\":\"Too many\"}", Map.of("Retry-After", "600"))
                .answer(200, "{\"data\":[],\"nextCursor\":null}");
        RuneClanClient c = connect();
        assertThrows(RuneClanException.class, () -> c.players().list(null, null).complete());
        c.players().list(null, null).complete();
        assertEquals(Duration.ZERO, clock.slept(), "the caller got the 429; nothing else waits 10 minutes");
    }

    @Test
    void aProxys429RepeatsOnlyRepeatableCalls() {
        hub.answer(200, me("\"webhooks:manage\",\"players:read\"", 600, 100))
                .answer(429, "<html>slow down</html>", Map.of("Retry-After", "1"));
        RuneClanClient c = connect();
        RuneClanException e = assertThrows(RuneClanException.class, () -> c.webhooks().create("https://bot.example.org/h", null).complete());
        assertEquals(429, e.status());
        assertEquals(2, hub.requests().size(), "not the hub's own 429: the POST may have gone through");

        hub.answer(429, "<html>slow down</html>", Map.of("Retry-After", "1")).answer(200, "{\"data\":[],\"nextCursor\":null}");
        c.players().list(null, null).complete();
        assertEquals(4, hub.requests().size(), "a GET goes again either way");
    }

    @Test
    void aMinVersionTheSdkCantReadIsIgnored() {
        for (String bad : new String[]{"0.3", "v0.3.0", "soon"}) {
            FakeTransport h = new FakeTransport().answer(200, me("\"players:read\"", 60, 10).replace("\"minVersion\":null", "\"minVersion\":\"" + bad + "\""));
            RuneClanClient c = RuneClan.connect(config().transport(h).build());
            assertTrue(c.handshake().isPresent(), bad);
            assertTrue(c.hasScope("players:read"), bad);
        }
    }

    @Test
    void a429OnTheHandshakeStillConnects() {
        hub.answer(429, "{\"error\":\"rate_limited\",\"message\":\"Too many\"}", Map.of("Retry-After", "600"));
        RuneClanClient c = connect();
        assertTrue(c.handshake().isEmpty());
        assertEquals(1, hub.requests().size());
        clock.advance(Duration.ofSeconds(601));
        hub.answer(200, me("\"players:read\"", 60, 10)).answer(200, "{\"data\":[],\"nextCursor\":null}");
        c.players().list(null, null).complete();
        assertTrue(c.handshake().isPresent(), "tried again on a later call");
    }

    @Test
    void anEmptyHandshakeIsABadAnswer() {
        for (String body : new String[]{"", "null"}) {
            FakeTransport h = new FakeTransport().answer(200, body);
            RuneClanException e = assertThrows(RuneClanException.class, () -> RuneClan.connect(config().transport(h).build()));
            assertEquals("bad_answer", e.code());
        }
    }

    @Test
    void theHandshakesOwnRemainingHeaderCounts() {
        hub.answer(200, me("\"players:read\"", 60, 10), Map.of("RateLimit-Limit", "60", "RateLimit-Remaining", "1"))
                .handler(r -> new Transport.Response(200, Map.of(), "{\"data\":[],\"nextCursor\":null}".getBytes()));
        RuneClanClient c = connect();
        c.players().list(null, 1).complete();
        assertEquals(Duration.ZERO, clock.slept());
        c.players().list(null, 1).complete();
        assertEquals(1000, clock.slept().toMillis(), "only one token was left after the handshake");
    }

    @Test
    void aWriteThatTimedOutSaysItMayHaveHappened() {
        hub.answer(200, me("\"webhooks:manage\"", 600, 100)).fail(new java.net.http.HttpTimeoutException("request timed out"));
        RuneClanException e = assertThrows(RuneClanException.class, () -> connect().webhooks().create("https://bot.example.org/h", null).complete());
        assertEquals("timeout", e.code());
        assertTrue(e.getMessage().contains("may still have done it"));
    }

    @Test
    void readsAreRetriedAfterALostConnectionOrA503() {
        hub.answer(200, me("\"players:read\"", 600, 100))
                .fail(new IOException("reset"))
                .answer(503, "{\"error\":\"unavailable\",\"message\":\"Busy\"}")
                .answer(200, "{\"alias\":\"a1_one\"}");
        assertEquals("a1_one", connect().players().get("a1_one").complete().orElseThrow().alias());
        assertEquals(4, hub.requests().size());
    }

    @Test
    void writesWithoutAnIdempotencyKeyAreNeverRepeatedBlind() {
        hub.answer(200, me("\"webhooks:manage\",\"guilds:write\"", 600, 100)).fail(new IOException("reset"));
        RuneClanClient c = connect();
        RuneClanException e = assertThrows(RuneClanException.class, () -> c.webhooks().create("https://bot.example.org/h", null).complete());
        assertEquals("unreachable", e.code());
        assertEquals(0, e.status());
        assertEquals(2, hub.requests().size(), "the POST went once");

        hub.answer(503, "{\"error\":\"unavailable\",\"message\":\"Busy\"}");
        assertEquals(503, assertThrows(RuneClanException.class, () -> c.guilds().remove("1".repeat(18)).complete()).status());
        assertEquals(3, hub.requests().size(), "a DELETE isn't repeated on a 503 either");
    }

    @Test
    void anIdempotentWriteIsRepeatedWithTheSameKey() {
        hub.answer(200, me("\"webhooks:manage\"", 600, 100))
                .fail(new IOException("reset"))
                .answer(409, "{\"error\":\"idempotency_in_progress\",\"message\":\"Still running\"}", Map.of("Retry-After", "1"))
                .answer(201, "{\"webhookId\":\"w1\"}");
        RuneClanClient c = connect();
        String id = c.route(Routes.POST_WEBHOOKS).body(Map.of("url", "https://bot.example.org/h")).idempotent().asJson().complete().path("webhookId").asText();
        assertEquals("w1", id);
        List<Transport.Request> sent = hub.requests().subList(1, 4);
        String key = sent.get(0).header("Idempotency-Key");
        assertTrue(key != null && key.length() >= 8);
        for (Transport.Request r : sent) assertEquals(key, r.header("Idempotency-Key"));
        assertThrows(IllegalArgumentException.class, () -> c.route(Routes.POST_WEBHOOKS).idempotencyKey("has space"));
    }

    @Test
    void anOtherConflictIsNotRetried() {
        hub.answer(200, me("\"webhooks:manage\"", 600, 100))
                .answer(409, "{\"error\":\"too_many_webhooks\",\"message\":\"At most 5\"}");
        RuneClanException e = assertThrows(RuneClanException.class,
                () -> connect().route(Routes.POST_WEBHOOKS).body(Map.of("url", "https://x.example.org")).idempotent().send().complete());
        assertEquals("too_many_webhooks", e.code());
    }

    @Test
    void callsArePacedToTheHandshakesLimit() {
        hub.answer(200, me("\"players:read\"", 60, 2)).handler(r -> new Transport.Response(200, Map.of(), "{\"data\":[],\"nextCursor\":null}".getBytes()));
        RuneClanClient c = connect();
        // the handshake spent nothing locally (it set the bucket), so two calls go at once, the third waits a second
        c.players().list(null, 1).complete();
        c.players().list(null, 1).complete();
        assertEquals(Duration.ZERO, clock.slept());
        c.players().list(null, 1).complete();
        assertEquals(1000, clock.slept().toMillis());
    }

    @Test
    void theRemainingHeaderSlowsTheNextCall() {
        hub.answer(200, me("\"players:read\"", 60, 10))
                .answer(200, "{\"data\":[],\"nextCursor\":null}", Map.of("RateLimit-Limit", "60", "RateLimit-Remaining", "0", "RateLimit-Reset", "10"))
                .answer(200, "{\"data\":[],\"nextCursor\":null}");
        RuneClanClient c = connect();
        c.players().list(null, 1).complete();
        assertEquals(Duration.ZERO, clock.slept());
        c.players().list(null, 1).complete();
        assertEquals(1000, clock.slept().toMillis(), "another copy of the bot spent the bucket: wait for a refill");
    }

    @Test
    void notFoundIsEmptyAndOtherRefusalsCarryTheHubsCode() {
        hub.answer(200, me("\"players:read\",\"links:read\"", 600, 100))
                .answer(404, "{\"error\":\"not_found\",\"message\":\"No such player\"}")
                .answer(403, "{\"error\":\"missing_scope\",\"message\":\"This key doesn't have the scope links:read.\"}")
                .answer(500, "<html>proxy</html>");
        RuneClanClient c = connect();
        assertTrue(c.players().byDiscordId("1".repeat(18)).complete().isEmpty());
        RuneClanException e = assertThrows(RuneClanException.class, () -> c.players().byDiscordId("2".repeat(18)).complete());
        assertEquals(403, e.status());
        assertEquals("missing_scope", e.code());
        assertFalse(e.local(), "the hub said so this time");
        RuneClanException html = assertThrows(RuneClanException.class, () -> c.players().byDiscordId("3".repeat(18)).complete());
        assertEquals("http_500", html.code());
    }

    @Test
    void sdkTooOldIsReported() {
        hub.answer(426, "{\"error\":\"sdk_too_old\",\"message\":\"Please update to 0.4.0\",\"minVersion\":\"0.3.0\",\"latest\":\"0.4.0\"}");
        RuneClanException e = assertThrows(RuneClanException.class, this::connect);
        assertEquals(426, e.status());
        assertEquals("0.4.0", e.body().path("latest").asText());
    }

    @Test
    void forGuildSendsTheServerOnEveryCall() {
        hub.answer(200, me("\"players:read\"", 600, 100)).answer(200, "{\"data\":[],\"nextCursor\":null}");
        RuneClanClient c = connect();
        String guild = "4".repeat(18);
        c.forGuild(guild).players().list(null, null).complete();
        assertEquals(guild, hub.last().header("X-Guild-Id"));
        assertNull(hub.requests().get(0).header("X-Guild-Id"));
    }

    @Test
    void pagesAreFollowedUntilTheCursorRunsOut() {
        hub.answer(200, me("\"players:read\"", 600, 100))
                .answer(200, "{\"data\":[{\"alias\":\"a1_one\"}],\"nextCursor\":\"c2\"}")
                .answer(200, "{\"data\":[{\"alias\":\"a1_two\"}],\"nextCursor\":null}");
        List<String> aliases = new ArrayList<>();
        for (Player p : connect().players().all()) aliases.add(p.alias());
        assertEquals(List.of("a1_one", "a1_two"), aliases);
        assertEquals("cursor=c2&limit=200", hub.last().uri().getRawQuery());
    }

    @Test
    void asyncCallsCompleteAndFailOnTheExecutor() throws Exception {
        hub.answer(200, me("\"players:read\"", 600, 100))
                .answer(200, "{\"alias\":\"a1_one\"}")
                .answer(400, "{\"error\":\"bad_limit\",\"message\":\"1 to 200\"}");
        RuneClanClient c = RuneClan.connect(config().executor(null).build());
        try {
            CompletableFuture<Optional<Player>> f = c.players().get("a1_one").submit();
            assertEquals("a1_one", f.get(5, TimeUnit.SECONDS).orElseThrow().alias());
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            c.players().list(null, 999).queue(p -> done.countDown(), t -> {
                failure.set(t);
                done.countDown();
            });
            assertTrue(done.await(5, TimeUnit.SECONDS));
            RuneClanException e = assertInstanceOf(RuneClanException.class, failure.get());
            assertEquals("bad_limit", e.code());
            CompletionException ce = assertThrows(CompletionException.class, () -> c.players().get("a1_x").submit().join());
            assertInstanceOf(IllegalStateException.class, ce.getCause(), "nothing queued in the fake");
        } finally {
            c.close();
        }
    }

    @Test
    void anActionIsSentAgainEachTimeAndBuilderChangesDontLeakIn() {
        hub.answer(200, me("", 600, 100)).answer(200, "{\"a\":1}").answer(200, "{\"a\":2}");
        RuneClanClient c = connect();
        RouteCall call = c.route(Routes.GET_CLANS).query("q", "yo");
        ApiAction<com.fasterxml.jackson.databind.JsonNode> action = call.asJson();
        call.query("q", "changed");
        assertEquals(1, action.complete().path("a").asInt());
        assertEquals(2, action.complete().path("a").asInt());
        assertEquals("q=yo", hub.last().uri().getRawQuery());
    }

    @Test
    void theSdksOwnHeadersCantBeReplaced() {
        hub.answer(200, me("", 600, 100));
        RuneClanClient c = connect();
        assertThrows(IllegalArgumentException.class, () -> c.route(Routes.GET_CLANS).header("Authorization", "Bearer other"));
        assertThrows(IllegalArgumentException.class, () -> c.route(Routes.GET_CLANS).header("x-runeclan-client", "x/1.0.0"));
        assertThrows(IllegalArgumentException.class, () -> c.route(Routes.GET_CLANS_CLAN_ID).asJson(), "a missing path part");
    }

    @Test
    void withoutTheHandshakeNothingIsCheckedLocally() {
        hub.answer(200, "{\"data\":[],\"nextCursor\":null}");
        RuneClanClient c = RuneClan.connect(config().handshake(false).build());
        c.players().events("a1_x", "XP", null, null, 5).map(p -> p).complete();
        assertEquals(1, hub.requests().size());
        assertTrue(c.handshake().isEmpty());
    }

    @Test
    void noticesAreKept() {
        String withNotice = me("", 60, 10).replace("\"notices\":[]",
                "\"notices\":[{\"code\":\"sdk_update_required\",\"level\":\"warning\",\"message\":\"Update before 2027-01-01\",\"until\":\"2027-01-01\"}]");
        hub.answer(200, withNotice);
        Me me = connect().handshake().orElseThrow();
        assertEquals("sdk_update_required", me.notices().get(0).code());
        assertEquals("2027-01-01", me.notices().get(0).until());
    }

    @Test
    void backoffGrowsAndStaysCapped() {
        assertTrue(Core.backoff(1).toMillis() >= 500 && Core.backoff(1).toMillis() <= 625);
        assertTrue(Core.backoff(3).toMillis() >= 2000);
        assertTrue(Core.backoff(30).toMillis() <= 10_000);
        assertNotEquals(0, Core.backoff(2).toMillis());
    }
}

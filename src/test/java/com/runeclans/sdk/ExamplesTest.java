package com.runeclans.sdk;

import com.runeclans.sdk.testing.FakeTransport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The files in examples/ compile against this SDK and do what the docs say, against a fake hub. A change that breaks an example breaks
 * the build, so the copy-paste code in the docs never goes stale.
 */
class ExamplesTest {
    static final Path EXAMPLES = Path.of("examples");
    static final String GUILD = "1".repeat(18);           // made at runtime, not a real id
    static final String ALIAS = "a1_" + "b".repeat(26);
    static URLClassLoader loader;

    @BeforeAll
    static void compileExamples() throws Exception {
        List<String> files;
        try (Stream<Path> s = Files.list(EXAMPLES)) {
            files = s.filter(p -> p.toString().endsWith(".java")).map(Path::toString).sorted().toList();
        }
        assertFalse(files.isEmpty(), "no examples found");
        Path out = Files.createTempDirectory("runeclan-examples");
        String cp = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        List<String> args = new ArrayList<>(List.of("-d", out.toString(), "-cp", cp, "-Xlint:all", "-Werror", "-proc:none"));
        args.addAll(files);
        int rc = javac.run(null, errors, errors, args.toArray(String[]::new));
        assertEquals(0, rc, "examples don't compile:\n" + errors.toString(StandardCharsets.UTF_8));
        loader = new URLClassLoader(new URL[] {out.toUri().toURL()}, ExamplesTest.class.getClassLoader());
    }

    /** A fake hub that answers by path; the handshake grants every scope the examples use. */
    static RuneClanClient fakeHub(FakeTransport fake) {
        fake.handler(req -> {
            String path = req.uri().getPath().replace(GUILD, "{guild}").replace(ALIAS, "{alias}");
            String body = switch (path) {
                case "/v1/me" -> "{\"appId\":\"app1\",\"appName\":\"Example bot\",\"scopes\":[\"players:read\",\"events:read\",\"links:read\","
                        + "\"guilds:write\",\"feed:read\"],\"tier\":\"standard\",\"rateLimit\":{\"perMinute\":60,\"burst\":120,\"windowSeconds\":60}}";
                case "/v1/clans/by-name/Younglings" -> "{\"clanId\":\"c-1\",\"name\":\"Younglings\"}";
                case "/v1/clans/c-1/events" -> "{\"events\":[{\"id\":\"e-1\",\"name\":\"Skill of the week\",\"status\":\"RUNNING\",\"startsAt\":\"2026-11-01T00:00:00Z\"}]}";
                case "/v1/events" -> "{\"events\":[{\"id\":\"e-1\",\"name\":\"Skill of the week\",\"status\":\"RUNNING\"},{\"id\":\"e-2\",\"name\":\"Bingo\",\"status\":\"DONE\"}]}";
                case "/v1/events/e-1/standings" -> "{\"eventId\":\"e-1\",\"standings\":[{\"place\":1,\"name\":\"Zezima\",\"points\":40}]}";
                case "/v1/links/discord/{guild}", "/v1/players/{alias}" ->
                        "{\"alias\":\"" + ALIAS + "\",\"categories\":[\"XP\"],\"characters\":[{\"rsn\":\"Zezima\",\"verification\":\"jagex_signed\"}]}";
                case "/v1/guilds/{guild}" -> "{\"guildId\":\"" + GUILD + "\",\"name\":\"My clan server\"}";
                case "/v1/guilds/{guild}/players" -> "{\"data\":[{\"alias\":\"" + ALIAS + "\",\"characters\":[{\"rsn\":\"Zezima\"}]}],\"nextCursor\":null}";
                case "/v1/feed" -> req.uri().getQuery() != null && req.uri().getQuery().contains("cursor=c1")
                        ? "{\"data\":[{\"kind\":\"link.created\",\"alias\":\"" + ALIAS + "\"}],\"nextCursor\":\"c2\",\"more\":false}"
                        : "{\"data\":[{\"kind\":\"plugin.event\",\"alias\":\"" + ALIAS + "\"}],\"nextCursor\":\"c1\",\"more\":true}";
                case "/v1/players/{alias}/events" -> req.uri().getQuery().contains("category=XP")
                        ? null : "{\"data\":[{\"id\":\"x\"}],\"nextCursor\":null}";
                case "/v1/guilds/{guild}/xp/leaderboard" -> "{\"period\":\"week\",\"players\":[{\"xp\":1}]}";
                default -> req.method().equals("PUT") ? "" : null;
            };
            if (body == null) return new Transport.Response(404, Map.of("Content-Type", List.of("application/json")),
                    "{\"error\":\"not_found\",\"message\":\"No such player\"}".getBytes(StandardCharsets.UTF_8));
            return new Transport.Response(body.isEmpty() ? 204 : 200, Map.of("Content-Type", List.of("application/json")),
                    body.getBytes(StandardCharsets.UTF_8));
        });
        return RuneClan.connect(RuneClanConfig.builder().apiKey("rsh_" + "e".repeat(43)).baseUrl("https://hub.example.org")
                .transport(fake).executor(Runnable::run).env(n -> null).build());
    }

    static String run(String className, Object... args) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buf, true, StandardCharsets.UTF_8);
        Object[] all = Arrays.copyOf(args, args.length + 1);
        all[args.length] = out;
        Class<?> c = loader.loadClass(className);
        Method m = Arrays.stream(c.getDeclaredMethods()).filter(x -> x.getName().equals("run") && x.getParameterCount() == all.length)
                .findFirst().orElseThrow();
        m.setAccessible(true);
        try {
            Object answer = m.invoke(null, all);
            return buf.toString(StandardCharsets.UTF_8) + (answer == null ? "" : "=> " + answer);
        } catch (InvocationTargetException e) {
            throw e.getCause() instanceof Exception ex ? ex : e;
        }
    }

    @Test
    void quickStart() throws Exception {
        String out = run("QuickStart", fakeHub(new FakeTransport()), "Younglings");
        assertTrue(out.contains("App Example bot (standard)"), out);
        assertTrue(out.contains("Younglings has 1 open event(s)"), out);
        assertTrue(run("QuickStart", fakeHub(new FakeTransport()), "Nobody").contains("No registered clan"));
    }

    @Test
    void linkedPlayer() throws Exception {
        assertTrue(run("LinkedPlayer", fakeHub(new FakeTransport()), GUILD).contains("RSN Zezima (link jagex_signed"));
        assertTrue(run("LinkedPlayer", fakeHub(new FakeTransport()), "2".repeat(18)).contains("Not linked"));
    }

    @Test
    void guildSync() throws Exception {
        FakeTransport fake = new FakeTransport();
        String out = run("GuildSync", fakeHub(fake), GUILD, "My clan server", List.of(ALIAS));
        assertTrue(out.contains("1 player(s) in My clan server"), out);
        assertTrue(fake.requests().stream().anyMatch(r -> r.method().equals("PUT") && r.uri().getPath().endsWith("/members/" + ALIAS)));
    }

    @Test
    void feedPollerFollowsMoreAndAnswersTheLastCursor() throws Exception {
        String out = run("FeedPoller", fakeHub(new FakeTransport()), null);
        assertTrue(out.contains("plugin.event") && out.contains("link.created"), out);
        assertTrue(out.endsWith("=> c2"), out);
    }

    @Test
    void webhookReceiverRefusesABadSignature() throws Exception {
        Class<?> c = loader.loadClass("WebhookReceiver");
        Method accept = c.getDeclaredMethod("accept", String.class, String.class, String.class);
        accept.setAccessible(true);
        assertEquals(false, accept.invoke(null, "secret", "t=1,v1=00", "{}"));
    }

    @Test
    void eventStandings() throws Exception {
        String out = run("EventStandings", fakeHub(new FakeTransport()));
        assertTrue(out.contains("1. Zezima  40 pts"), out);
        assertTrue(out.contains("Bingo [DONE]"), out);
    }

    @Test
    void anyRoute() throws Exception {
        FakeTransport fake = new FakeTransport();
        String out = run("AnyRoute", fakeHub(fake), GUILD, ALIAS);
        assertTrue(out.contains("1 recent drop(s)") && out.contains("1 player(s)"), out);
        assertTrue(fake.requests().stream().anyMatch(r -> GUILD.equals(r.header("X-Guild-Id"))));
    }

    @Test
    void handlingErrors() throws Exception {
        assertTrue(run("HandlingErrors", fakeHub(new FakeTransport()), ALIAS).contains("Unknown player"));
    }

    @Test
    void testingYourBot() throws Exception {
        String out = run("TestingYourBot");
        assertTrue(out.contains("Fake player plays Zezima"), out);
    }

    @Test
    void everyExampleIsTested() throws Exception {
        String me = Files.readString(Path.of("src/test/java/com/runeclans/sdk/ExamplesTest.java"));
        try (Stream<Path> s = Files.list(EXAMPLES)) {
            for (Path p : s.filter(x -> x.toString().endsWith(".java")).toList()) {
                String name = p.getFileName().toString().replace(".java", "");
                assertTrue(me.contains("\"" + name + "\""), name + " has no test in ExamplesTest");
            }
        }
    }

}

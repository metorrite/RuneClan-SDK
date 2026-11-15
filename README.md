# RuneClan SDK for Java

Talk to the [RuneClans](https://runeclans.com) hub API from your Discord bot (or anything on Java 17+) in about five lines. The SDK
handles the API key, the connection, the hub's rate limits and retries, so your bot doesn't have to.

```java
RuneClanClient hub = RuneClan.connect();   // reads your key, says hello to the hub

hub.players().byDiscordId(userId).queue(player -> {
    if (player.isEmpty()) event.reply("Link your RuneScape account first.").queue();
    else event.reply("You are " + player.get().characters().get(0).rsn()).queue();
});
```

It is a library for the RuneScape 3 clan platform RuneClans. Its only dependency is Jackson (JDA bots already have it).

## The full reference: docs and examples

- **[docs/index.html](docs/index.html)**: every SDK method and every public hub route on one page, with what it does, its scope,
  parameters, answer fields, errors and a Java and curl example each, plus a guide (keys, scopes, rate limits, retries, errors, testing).
  It is a single file that works offline: download it and open it in any browser.
- **The jar carries the same page.** With only the SDK jar: `java -cp runeclan-sdk-0.2.0.jar com.runeclans.sdk.Docs` writes
  `runeclan-sdk-docs.html` in the current folder and opens it.
- **[examples/](examples/)**: whole programs to copy (quick start, Discord id lookup, server sync, feed polling, webhook receiver, event
  standings, any route, error handling, testing with fakes). Each runs on its own with Java 17+:
  `java -cp runeclan-sdk.jar:jackson-databind.jar:jackson-core.jar:jackson-annotations.jar examples/QuickStart.java`. The SDK's tests
  compile and run every one against a fake hub, so they keep working.

## 1. Add it

Releases are built by [JitPack](https://jitpack.io) from this repository's tags (Maven Central comes with 1.0).

```xml
<repositories>
    <repository><id>jitpack.io</id><url>https://jitpack.io</url></repository>
</repositories>
<dependency>
    <groupId>com.github.metorrite</groupId>
    <artifactId>runeclan-sdk</artifactId>
    <version>v0.2.0</version>
</dependency>
```

Gradle: `maven { url 'https://jitpack.io' }` and `implementation 'com.github.metorrite:runeclan-sdk:v0.2.0'`.

## 2. Give it your key

Get an API key for your app from RuneClans. Then **either** put it in a file called `runeclan.properties` next to your bot:

```properties
apiKey=rsh_...
# baseUrl=https://api.runeclans.com   (optional)
```

**or** set the environment variable `RUNECLAN_API_KEY` (handy on hosts like Railway). `RUNECLAN_API_URL` sets the address, and
`RUNECLAN_CONFIG` points at a key file somewhere else. Order: what you pass in code, then the environment, then the file.

Keep the key out of git: add `runeclan.properties` to your `.gitignore`. The SDK warns at start when the file sits in a git folder and
no `.gitignore` names it. It never logs the key.

## 3. Connect and call

```java
RuneClanClient hub = RuneClan.connect();
```

`connect()` runs the **handshake**: it asks the hub who the key is, which scopes it has, its tier and how fast it may call, and which
SDK versions the hub still serves. A wrong or revoked key fails right here with a plain message. If the hub can't be reached at start,
`connect()` still works and the handshake runs again before a later call.

Keep one client for your whole bot. It is safe to share between threads.

Every method gives back an `ApiAction`, like JDA's `RestAction`:

| You write | What happens |
| --- | --- |
| `.complete()` | Sends the call and waits for the answer. Use it on your own worker or virtual thread. |
| `.submit()` | Gives a `CompletableFuture`. |
| `.queue(ok, failed)` | Runs your callbacks when the answer comes. Never blocks a JDA listener. Without `failed`, a failure is logged. |

```java
Optional<Clan> clan = hub.clans().byName("Younglings").complete();
EventList events = hub.clans().events(clan.orElseThrow().clanId(), "open").complete();
for (Player p : hub.players().all()) System.out.println(p.alias());     // follows the pages for you
FeedPage feed = hub.feed().after(savedCursor, 100).complete();
```

What's there: `players()` (list, get, by Discord id, by RSN, events, XP, logs, stats), `guilds()` (register, members, players, events),
`feed()`, `webhooks()` (create, list, delete, ping, resume, and `Webhooks.verify` for deliveries), `clans()` (search, by name, events,
site, leagues, polls, signups, features), `communities()` (the community finder's list, by address, by id with join rules),
`sites()` (clan and community sites by address), `events()` (shared events, one event,
participants, standings, board) and `directory()` (the public clan list).

**Every other public operation** is in `Routes`, with typed answers in `com.runeclans.sdk.model`:

```java
EventStandingsAnswer standings = hub.route(Routes.GET_EVENTS_EVENT_ID_STANDINGS)
        .path("eventId", eventId).as(EventStandingsAnswer.class).complete();
```

**Reading for one Discord server**: a player can limit their grant to some servers. `hub.forGuild(guildId)` gives the same client with
`X-Guild-Id` on every call.

## What the SDK does for you

- **Scopes.** A call needs a scope the key lacks? It fails straight away (`RuneClanException`, code `missing_scope`, `local()` true)
  instead of a 403 later. Got a new scope? Call `hub.refreshHandshake()`.
- **Rate limits.** The SDK keeps a local copy of your key's limit from the handshake and waits for a free slot instead of sending a call
  it knows will be refused. Every answer's `RateLimit-Remaining` keeps it honest when several copies of your bot share one key.
- **429.** It waits the hub's `Retry-After` (up to 60 s; set `maxRetryWait`) and sends the call again. The hub's own 429 comes before
  it does anything, so that is safe for every call; a 429 from something in between (a proxy) only repeats reads and `PUT`s. A longer
  wait is handed to you as the 429 and holds back no other call.
- **Outages.** After a lost connection or a 502/503/504, reads and `PUT`s are sent again (3 tries in all, with backoff). `POST` and
  `DELETE` are **never** sent again blind, because the first one may have worked. Mark a write with `.idempotent()` on a `route(...)`
  call to make it safe to repeat (the hub then answers a repeat with the first answer). Only use it once the hub supports
  `Idempotency-Key`: a hub without it ignores the key, and a resend could then do the write twice.
- **Versions.** Every call says `X-RuneClan-Client: runeclan-sdk-java/<version>`. The handshake's notices (for example "update before
  2027-01-01") are logged once, and kept in `hub.handshake()`. A version the hub no longer serves gets a clear `sdk_too_old` error.

The hub checks every key, scope and limit on its own: the SDK's checks only save you wasted calls.

## Errors

Everything that fails throws (or completes the future with) `RuneClanException`:

- `status()`: the HTTP status, or 0 when no answer came back
- `code()`: the hub's error code (`not_found`, `missing_scope`, `rate_limited`, ... listed under `x-runeclan-error-codes` in
  `spec/openapi.json`), or the SDK's own `unreachable`, `timeout` (sent, but no answer in time: it may have happened), `interrupted`,
  `bad_answer`
- `retryAfter()`, `body()` (the hub's whole error answer), `local()` (true when nothing was sent)

Lookups (`get`, `byName`, `byDiscordId`, ...) give an empty `Optional` for a 404.

## Settings

```java
RuneClanClient hub = RuneClan.connect(RuneClanConfig.builder()
        .apiKey(System.getenv("MY_KEY_NAME"))      // or leave out: env, then runeclan.properties
        .baseUrl("http://localhost:8090")           // a local hub
        .requestTimeout(Duration.ofSeconds(10))
        .maxAttempts(3)
        .maxRetryWait(Duration.ofSeconds(60))
        .executor(myExecutor)                       // runs submit()/queue(); default virtual threads on Java 21+
        .userAgent("my-bot/1.4")
        .build());
```

## Testing your bot

Pass a `FakeTransport` and nothing touches the network:

```java
FakeTransport fake = new FakeTransport()
        .answer(200, "{\"appId\":\"a\",\"scopes\":[\"players:read\"]}")          // the handshake
        .answer(200, "{\"alias\":\"a1_x\",\"characters\":[]}");
RuneClanClient hub = RuneClan.connect(RuneClanConfig.builder().apiKey(testKey).transport(fake).build());
// ... then check fake.requests()
```

`ApiAction.of(value)` makes a ready answer for your own fakes.

## Working on the SDK

- `mvn verify` runs the tests; `python3 tools/gen_model.py --check` checks the generated code matches the spec and
  `python3 tools/gen_docs.py --check` that `docs/index.html` is current.
- After changing a friendly method, the spec or an example, run `python3 tools/gen_docs.py`. New methods need a short example in
  `tools/docs_content.py`; new files in `examples/` need a test in `ExamplesTest`.
- `spec/openapi.json` is the hub's spec with only its public operations. Refresh it with `tools/sync_spec.py`, then run
  `tools/gen_model.py`. Never edit `spec/`, `src/main/java/com/runeclans/sdk/model/`, `Routes.java` or `docs/index.html` by hand.
- Branch from `dev`, PR into `dev`. Versions are tagged from `master`.

## Licence

MIT: see [LICENSE](LICENSE). Use it in any bot, open or closed source.

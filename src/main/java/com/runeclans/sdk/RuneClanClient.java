package com.runeclans.sdk;

import com.runeclans.sdk.model.Me;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A connection to the RuneClans hub API. Make one with {@link RuneClan#connect()} and keep it for the life of your bot: it holds one
 * connection pool, the handshake and the rate-limit pacer, and is safe to share between threads.
 *
 * <pre>{@code
 * RuneClanClient hub = RuneClan.connect();                 // key from runeclan.properties or RUNECLAN_API_KEY
 * hub.players().byDiscordId(userId).queue(p -> ...);      // never blocks a JDA listener
 * Clan clan = hub.clans().byName("Younglings").complete().orElseThrow();
 * }</pre>
 *
 * Every friendly method answers an {@link ApiAction}; {@link #route(Route)} reaches any public operation in {@link Routes}.
 */
public final class RuneClanClient implements AutoCloseable {
    /** This SDK's version; also sent as {@code X-RuneClan-Client: runeclan-sdk-java/<version>}. */
    public static final String VERSION = "0.2.0";
    public static final String CLIENT_NAME = "runeclan-sdk-java";

    private final Core core;
    private final String guildId;

    RuneClanClient(Core core, String guildId) {
        this.core = core;
        this.guildId = guildId;
    }

    /** A client for this config, without the start-up handshake yet (it then runs before the first call). */
    public static RuneClanClient create(RuneClanConfig config) {
        return new RuneClanClient(new Core(config), null);
    }

    Core core() {
        return core;
    }

    /** The Discord server this view reads for ({@code X-Guild-Id}), or null. */
    public String guildId() {
        return guildId;
    }

    /**
     * The same client, reading for one Discord server: every call carries {@code X-Guild-Id}. Needed when a player limited their grant to
     * some servers. Shares the connection, handshake and pacing with this client.
     */
    public RuneClanClient forGuild(String guildId) {
        return new RuneClanClient(core, Objects.requireNonNull(guildId, "guildId"));
    }

    /** The last handshake: who the key is, its scopes and tier, its rate limit, the SDK versions the hub serves and its notices. */
    public Optional<Me> handshake() {
        return Optional.ofNullable(core.handshake());
    }

    /** Runs the handshake again now (e.g. after your app was given a new scope) and answers it. */
    public Me refreshHandshake() {
        return core.refreshHandshake();
    }

    /** True when the handshake says the key has this scope (false before any handshake). */
    public boolean hasScope(String scope) {
        Me me = core.handshake();
        return me != null && me.scopes() != null && me.scopes().contains(scope);
    }

    /** {@code GET /v1/me}: the handshake as a call (it also refreshes the handshake this client keeps). */
    public ApiAction<Me> me() {
        return action(core::refreshHandshake);
    }

    /** A call to any public route, e.g. {@code hub.route(Routes.GET_EVENTS_EVENT_ID_BOARD).path("eventId", id).as(EventBoard.class)}. */
    public RouteCall route(Route route) {
        return new RouteCall(this, route);
    }

    public Players players() {
        return new Players(this);
    }

    public Guilds guilds() {
        return new Guilds(this);
    }

    public Feed feed() {
        return new Feed(this);
    }

    public Webhooks webhooks() {
        return new Webhooks(this);
    }

    public Clans clans() {
        return new Clans(this);
    }

    /** Published sites, clan and community alike (communities are reached through their site). */
    public Sites sites() {
        return new Sites(this);
    }

    public Events events() {
        return new Events(this);
    }

    public Directory directory() {
        return new Directory(this);
    }

    /** Where to send a player so they can let your app see their data. */
    public static String grantUrl(String signInBaseUrl, String appId) {
        return signInBaseUrl.replaceAll("/+$", "") + "/grant?app=" + Paths.enc(appId);
    }

    <T> ApiAction<T> action(Supplier<T> call) {
        return new ApiAction<>(call, core.executor);
    }

    /** Stops the SDK's own threads and closes the transport. Views from {@link #forGuild} share them, so close the main client only. */
    @Override
    public void close() {
        core.close();
    }

    @Override
    public String toString() {
        return "RuneClanClient[" + core.config + (guildId == null ? "" : ", guild " + guildId) + "]";
    }
}

package com.runeclans.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.runeclans.sdk.model.EventPage;
import com.runeclans.sdk.model.Player;
import com.runeclans.sdk.model.PlayerLogs;
import com.runeclans.sdk.model.PlayerPage;
import com.runeclans.sdk.model.PlayerStatsBatch;
import com.runeclans.sdk.model.XpAnswer;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Players who granted your app access ({@code players:read}), their RuneSync events and XP ({@code events:read}) and the Discord link
 * lookup ({@code links:read}). A player is known to your app by an alias ({@code a1_...}); a Discord id only with their identity grant.
 */
public final class Players {
    private final RuneClanClient c;

    Players(RuneClanClient c) {
        this.c = c;
    }

    /** One page of players (limit 1 to 200; null cursor for the first page). */
    public ApiAction<PlayerPage> list(String cursor, Integer limit) {
        return c.route(Routes.GET_PLAYERS).query("cursor", cursor).query("limit", limit).as(PlayerPage.class);
    }

    /** Every player, page by page (blocking: run it off the JDA gateway thread). */
    public Iterable<Player> all() {
        return Pages.iterate(cursor -> list(cursor, 200), PlayerPage::data, PlayerPage::nextCursor);
    }

    /** One player by alias; empty when unknown or not granted. */
    public ApiAction<Optional<Player>> get(String alias) {
        return c.route(Routes.GET_PLAYERS_ALIAS).path("alias", alias).asOptional(Player.class);
    }

    /** The player linked to this Discord account; empty unless they granted your app identity ({@code links:read}). */
    public ApiAction<Optional<Player>> byDiscordId(String discordId) {
        return c.route(Routes.GET_LINKS_DISCORD_DISCORD_ID).path("discordId", discordId).asOptional(Player.class);
    }

    /** Players (who granted your app) linked to an RSN now. */
    public ApiAction<List<Player>> byRsn(String rsn) {
        return c.route(Routes.GET_PLAYERS_BY_RSN_RSN).path("rsn", rsn).asOptional(Data.class)
                .map(d -> d.map(x -> Core.JSON.convertValue(x.data(), new TypeReference<List<Player>>() {})).orElse(List.of()));
    }

    /**
     * One page of a player's RuneSync events, newest first. {@code category} (XP, DROPS, KILLS, ACHIEVEMENTS, PRESENCE), {@code since}
     * (ISO-8601) and {@code cursor} may be null.
     */
    public ApiAction<EventPage> events(String alias, String category, String since, String cursor, Integer limit) {
        return c.route(Routes.GET_PLAYERS_ALIAS_EVENTS).path("alias", alias)
                .query("category", category).query("since", since).query("cursor", cursor).query("limit", limit).as(EventPage.class);
    }

    /**
     * A player's XP gains. {@code filters} are the route's query settings ({@code from}, {@code to}, {@code tz}, {@code skills}, {@code by},
     * ...; see {@code GET /v1/players/{alias}/xp} in the spec); null for the defaults.
     */
    public ApiAction<XpAnswer> xp(String alias, Map<String, String> filters) {
        RouteCall call = c.route(Routes.GET_PLAYERS_ALIAS_XP).path("alias", alias);
        if (filters != null) filters.forEach(call::query);
        return call.as(XpAnswer.class);
    }

    /** A player's logs (drop log, clue log, pets, kill counts) for their default character. */
    public ApiAction<PlayerLogs> logs(String alias) {
        return c.route(Routes.GET_PLAYERS_ALIAS_LOGS).path("alias", alias).as(PlayerLogs.class);
    }

    /** Latest stats of up to 100 RSNs. */
    public ApiAction<PlayerStatsBatch> stats(List<String> rsns) {
        return c.route(Routes.POST_PLAYERS_STATS).body(Map.of("rsns", rsns)).as(PlayerStatsBatch.class);
    }
}

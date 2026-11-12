package com.runeclans.sdk;

import com.runeclans.sdk.model.EventPage;
import com.runeclans.sdk.model.Guild;
import com.runeclans.sdk.model.Player;
import com.runeclans.sdk.model.PlayerPage;

import java.util.Map;

/** Your app's Discord servers and their member lists ({@code guilds:write}), and reads per server. */
public final class Guilds {
    private final RuneClanClient c;

    Guilds(RuneClanClient c) {
        this.c = c;
    }

    /** Registers (or renames) one of your app's servers. Safe to repeat. */
    public ApiAction<Guild> register(String guildId, String name) {
        return c.route(Routes.PUT_GUILDS_GUILD_ID).path("guildId", guildId).body(Map.of("name", name == null ? "" : name)).as(Guild.class);
    }

    /** Forgets one of your app's servers and its member list. */
    public ApiAction<Void> remove(String guildId) {
        return c.route(Routes.DELETE_GUILDS_GUILD_ID).path("guildId", guildId).send();
    }

    /** Adds a player (by alias) to the server's member list. Safe to repeat. */
    public ApiAction<Void> addMember(String guildId, String alias) {
        return c.route(Routes.PUT_GUILDS_GUILD_ID_MEMBERS_ALIAS).path("guildId", guildId).path("alias", alias).body(Map.of()).send();
    }

    public ApiAction<Void> removeMember(String guildId, String alias) {
        return c.route(Routes.DELETE_GUILDS_GUILD_ID_MEMBERS_ALIAS).path("guildId", guildId).path("alias", alias).send();
    }

    /** One page of the server's players ({@code players:read}). */
    public ApiAction<PlayerPage> players(String guildId, String cursor, Integer limit) {
        return c.route(Routes.GET_GUILDS_GUILD_ID_PLAYERS).path("guildId", guildId).query("cursor", cursor).query("limit", limit).as(PlayerPage.class);
    }

    public Iterable<Player> allPlayers(String guildId) {
        return Pages.iterate(cursor -> players(guildId, cursor, 200), PlayerPage::data, PlayerPage::nextCursor);
    }

    /** One page of the server's players' events, newest first ({@code events:read}). */
    public ApiAction<EventPage> events(String guildId, String since, String cursor, Integer limit) {
        return c.route(Routes.GET_GUILDS_GUILD_ID_EVENTS).path("guildId", guildId)
                .query("since", since).query("cursor", cursor).query("limit", limit).as(EventPage.class);
    }
}

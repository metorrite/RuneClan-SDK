package com.runeclans.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.runeclans.sdk.model.Clan;
import com.runeclans.sdk.model.EventList;
import com.runeclans.sdk.model.LeagueList;
import com.runeclans.sdk.model.PollList;
import com.runeclans.sdk.model.SignupList;
import com.runeclans.sdk.model.SiteFeatures;
import com.runeclans.sdk.model.SiteSummary;

import java.util.List;
import java.util.Optional;

/**
 * Registered clans. A clan has the hub's own id (a uuid; Jagex has none). Polls and signups also accept the clan site's Discord server
 * id. Communities have no clan: see {@link RuneClanClient#communities()}.
 */
public final class Clans {
    private final RuneClanClient c;

    Clans(RuneClanClient c) {
        this.c = c;
    }

    /** Registered clans whose name starts with {@code q} (at least 2 letters). */
    public ApiAction<List<Clan>> search(String q) {
        return c.route(Routes.GET_CLANS).query("q", q).as(new TypeReference<Data<Clan>>() {}).map(Data::data);
    }

    public ApiAction<Optional<Clan>> get(String clanId) {
        return c.route(Routes.GET_CLANS_CLAN_ID).path("clanId", clanId).asOptional(Clan.class);
    }

    /** By its RuneScape name (case and spacing don't matter). */
    public ApiAction<Optional<Clan>> byName(String name) {
        return c.route(Routes.GET_CLANS_BY_NAME_NAME).path("name", name).asOptional(Clan.class);
    }

    /** The clan's events you may see. {@code status}: DRAFT, SIGNUPS, RUNNING, SCORING, DONE, CANCELLED, open, or null. */
    public ApiAction<EventList> events(String clanId, String status) {
        return c.route(Routes.GET_CLANS_CLAN_ID_EVENTS).path("clanId", clanId).query("status", status).as(EventList.class);
    }

    public ApiAction<Optional<SiteSummary>> site(String clanId) {
        return c.route(Routes.GET_CLANS_CLAN_ID_SITE).path("clanId", clanId).asOptional(SiteSummary.class);
    }

    public ApiAction<LeagueList> leagues(String clanId) {
        return c.route(Routes.GET_CLANS_CLAN_ID_LEAGUES).path("clanId", clanId).as(LeagueList.class);
    }

    /** {@code polls:read}; {@code status} open, closed or null. */
    public ApiAction<PollList> polls(String clanId, String status) {
        return c.route(Routes.GET_CLANS_CLAN_ID_POLLS).path("clanId", clanId).query("status", status).as(PollList.class);
    }

    /** {@code signups:read}; {@code status} active, closed or null. */
    public ApiAction<SignupList> signups(String clanId, String status) {
        return c.route(Routes.GET_CLANS_CLAN_ID_SIGNUPS).path("clanId", clanId).query("status", status).as(SignupList.class);
    }

    public ApiAction<SiteFeatures> features(String clanId) {
        return c.route(Routes.GET_CLANS_CLAN_ID_FEATURES).path("clanId", clanId).as(SiteFeatures.class);
    }
}

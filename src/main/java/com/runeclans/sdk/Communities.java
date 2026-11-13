package com.runeclans.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.runeclans.sdk.model.Community;
import com.runeclans.sdk.model.CommunityListing;
import com.runeclans.sdk.model.GroupStatsActivity;
import com.runeclans.sdk.model.GroupStatsDaily;
import com.runeclans.sdk.model.GroupStatsPage;
import com.runeclans.sdk.model.GroupStatsRecap;

import java.util.List;
import java.util.Optional;

/**
 * Communities: groups with a RuneClans website that aren't a clan (and the member lists of clan websites). Read with any key, as
 * {@link Clans} are; you see only what the public sees. Joining or leaving for a Discord user is for RuneClans' own apps and isn't here.
 */
public final class Communities {
    private final RuneClanClient c;

    Communities(RuneClanClient c) {
        this.c = c;
    }

    /** Live communities that take members (open or request), biggest first, at most 500: the website's community finder. */
    public ApiAction<List<CommunityListing>> list() {
        return c.route(Routes.GET_COMMUNITIES).as(new TypeReference<Data<CommunityListing>>() {}).map(Data::data);
    }

    /** By its website address ({@code <slug>.runeclans.com}); closed communities too. Empty when no live community has that address. */
    public ApiAction<Optional<CommunityListing>> bySlug(String slug) {
        return c.route(Routes.GET_COMMUNITIES_BY_SLUG_SLUG).path("slug", slug).asOptional(CommunityListing.class);
    }

    /** One community with its join rules and join form. Empty when it doesn't exist or its website isn't public. */
    public ApiAction<Optional<Community>> get(String communityId) {
        return c.route(Routes.GET_COMMUNITIES_COMMUNITY_ID).path("communityId", communityId).asOptional(Community.class);
    }

    /**
     * A community's stats page: the overview (characters counted, levels, XP, gains today, this week and this month) and one page of its
     * members' characters with their level, XP and gain over the period. {@code community} is its id or its website address. Players hidden
     * from leaderboards are never counted. Empty when no public community has that id or address.
     */
    public ApiAction<Optional<GroupStatsPage>> stats(String community, GroupStatsQuery query) {
        GroupStatsQuery q = query == null ? GroupStatsQuery.defaults() : query;
        return c.route(Routes.GET_COMMUNITIES_COMMUNITY_STATS).path("community", community).query("skill", q.skill()).query("period", q.period())
                .query("from", q.from()).query("to", q.to()).query("sort", q.sort()).query("order", q.order()).query("limit", q.limit())
                .query("offset", q.offset()).asOptional(GroupStatsPage.class);
    }

    /** The same with the defaults: overall, this week, most XP first, the first 100. */
    public ApiAction<Optional<GroupStatsPage>> stats(String community) {
        return stats(community, null);
    }

    /** A community's XP gained per UTC day for the last {@code days} days (1 to 366; null: 30), overall or in {@code skill}. */
    public ApiAction<Optional<GroupStatsDaily>> dailyGains(String community, String skill, Integer days) {
        return c.route(Routes.GET_COMMUNITIES_COMMUNITY_STATS_DAILY).path("community", community).query("skill", skill).query("days", days).asOptional(GroupStatsDaily.class);
    }

    /** A community's recap over {@code period} (null: this week): XP gained, active members, boss kills, level-ups, joins and leaves, top gainers. */
    public ApiAction<Optional<GroupStatsRecap>> recap(String community, String period) {
        return c.route(Routes.GET_COMMUNITIES_COMMUNITY_STATS_RECAP).path("community", community).query("period", period).asOptional(GroupStatsRecap.class);
    }

    /** A community's recent Adventurer's Log lines, newest first (at most {@code limit}, 1 to 200; null: 50). */
    public ApiAction<Optional<GroupStatsActivity>> activity(String community, Integer limit) {
        return c.route(Routes.GET_COMMUNITIES_COMMUNITY_STATS_ACTIVITY).path("community", community).query("limit", limit).asOptional(GroupStatsActivity.class);
    }
}

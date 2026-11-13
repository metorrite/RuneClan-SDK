package com.runeclans.sdk;

import com.runeclans.sdk.model.RankSummaryPage;
import com.runeclans.sdk.model.RankingPage;
import com.runeclans.sdk.model.XpRecordPage;

/**
 * The Stats rankings, read with any key: hiscores of players, clans and communities by level, XP or XP gained, top gainers, XP records and
 * the rank summary. Players hidden from leaderboards never appear. Days are UTC; weeks start on Wednesday (the Citadel week).
 */
public final class Stats {
    private final RuneClanClient c;

    Stats(RuneClanClient c) {
        this.c = c;
    }

    /**
     * What to rank. Every field is optional: {@code new Stats.Query()} is players by overall level, first page. {@code skill}: overall, a skill
     * name or id 0-28; {@code accountType}: main, ironman (with hardcore), hardcore_ironman, group_ironman, competitive_group_ironman;
     * {@code find}: a player name or clan/community slug whose rank and page the answer reports.
     */
    public static final class Query {
        String entity, skill, metric, period, from, to, accountType, find;
        Integer limit, offset;

        /** player (default), clan or community. */
        public Query entity(String entity) { this.entity = entity; return this; }
        public Query skill(String skill) { this.skill = skill; return this; }
        /** level (default), xp or gained. */
        public Query metric(String metric) { this.metric = metric; return this; }
        /** For gained: hour, today, yesterday, week, month, year, or a recap period such as last-week or 2026-09. */
        public Query period(String period) { this.period = period; return this; }
        /** For gained over chosen days: first and last day (YYYY-MM-DD, at most 366 days). */
        public Query custom(String from, String to) { this.period = "custom"; this.from = from; this.to = to; return this; }
        public Query accountType(String accountType) { this.accountType = accountType; return this; }
        /** 1 to 100 (default 25). */
        public Query limit(Integer limit) { this.limit = limit; return this; }
        public Query offset(Integer offset) { this.offset = offset; return this; }
        public Query find(String find) { this.find = find; return this; }
    }

    /** {@code GET /v1/stats/rankings}. */
    public ApiAction<RankingPage> rankings(Query q) {
        Query x = q == null ? new Query() : q;
        return c.route(Routes.GET_STATS_RANKINGS).query("entity", x.entity).query("skill", x.skill).query("metric", x.metric).query("period", x.period)
                .query("from", x.from).query("to", x.to).query("accountType", x.accountType).query("limit", x.limit).query("offset", x.offset)
                .query("find", x.find).as(RankingPage.class);
    }

    /** {@code GET /v1/stats/gainers}: {@code period} hour, day (default), week or month; the query's metric and custom days are ignored. */
    public ApiAction<RankingPage> gainers(String period, Query q) {
        Query x = q == null ? new Query() : q;
        return c.route(Routes.GET_STATS_GAINERS).query("period", period).query("entity", x.entity).query("skill", x.skill).query("accountType", x.accountType)
                .query("limit", x.limit).query("offset", x.offset).query("find", x.find).as(RankingPage.class);
    }

    /** {@code GET /v1/stats/records}: each player's best day, week or month ({@code kind}) in a skill. Null arguments take the defaults. */
    public ApiAction<XpRecordPage> records(String skill, String kind, String accountType, Integer limit, Integer offset) {
        return c.route(Routes.GET_STATS_RECORDS).query("skill", skill).query("kind", kind).query("accountType", accountType).query("limit", limit)
                .query("offset", offset).as(XpRecordPage.class);
    }

    /** {@code GET /v1/stats/rank-summary}: each player's ranks in the 29 skills summed, lowest first. */
    public ApiAction<RankSummaryPage> rankSummary(String accountType, Integer limit, Integer offset) {
        return c.route(Routes.GET_STATS_RANK_SUMMARY).query("accountType", accountType).query("limit", limit).query("offset", offset).as(RankSummaryPage.class);
    }
}

package com.runeclans.sdk;

/**
 * What a community stats page shows ({@link Communities#stats(String, GroupStatsQuery)}). Every field is optional; null leaves the hub's default.
 *
 * @param skill  {@code overall} (default), a skill name ({@code attack}, {@code necromancy}...) or id 0-28
 * @param period {@code hour}, {@code today}, {@code yesterday}, {@code week} (default: the Citadel week so far), {@code month}, {@code year},
 *               {@code custom} (with {@code from} and {@code to}), or a recap period ({@code last-week}, {@code last-30d}, {@code 2026-09}, {@code all})
 * @param from   custom only: the first day, {@code YYYY-MM-DD}
 * @param to     custom only: the last day, {@code YYYY-MM-DD} (inclusive, at most 366 days)
 * @param sort   {@code xp} (default), {@code level}, {@code gained} or {@code rsn}
 * @param order  {@code desc} (default; {@code asc} for {@code rsn}) or {@code asc}
 * @param limit  1 to 500 (default 100)
 * @param offset 0 to 100,000 (default 0)
 */
public record GroupStatsQuery(String skill, String period, String from, String to, String sort, String order, Integer limit, Integer offset) {
    /** The hub's defaults. */
    public static GroupStatsQuery defaults() {
        return new GroupStatsQuery(null, null, null, null, null, null, null, null);
    }

    public GroupStatsQuery withSkill(String skill) {
        return new GroupStatsQuery(skill, period, from, to, sort, order, limit, offset);
    }

    public GroupStatsQuery withPeriod(String period) {
        return new GroupStatsQuery(skill, period, null, null, sort, order, limit, offset);
    }

    /** A custom period of whole UTC days, {@code YYYY-MM-DD}, inclusive. */
    public GroupStatsQuery withDays(String from, String to) {
        return new GroupStatsQuery(skill, "custom", from, to, sort, order, limit, offset);
    }

    public GroupStatsQuery withSort(String sort, String order) {
        return new GroupStatsQuery(skill, period, from, to, sort, order, limit, offset);
    }

    public GroupStatsQuery withPage(Integer limit, Integer offset) {
        return new GroupStatsQuery(skill, period, from, to, sort, order, limit, offset);
    }
}

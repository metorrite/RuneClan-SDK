package com.runeclans.sdk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.runeclans.sdk.model.DirectoryClan;

import java.util.List;
import java.util.Optional;

/** The public clan directory: listed clans (public, verified, not suspended). */
public final class Directory {
    private final RuneClanClient c;

    Directory(RuneClanClient c) {
        this.c = c;
    }

    /** One directory page. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClanPage(List<DirectoryClan> clans, String nextCursor, String generatedAt) {}

    /** {@code q} part of the name, {@code recruiting} OPEN, INVITE_ONLY, CLOSED or true; any may be null. */
    public ApiAction<ClanPage> clans(String q, String recruiting, String cursor, Integer limit) {
        return c.route(Routes.GET_DIRECTORY_CLANS).query("q", q).query("recruiting", recruiting).query("cursor", cursor).query("limit", limit).as(ClanPage.class);
    }

    public Iterable<DirectoryClan> allClans(String q, String recruiting) {
        return Pages.iterate(cursor -> clans(q, recruiting, cursor, 200), ClanPage::clans, ClanPage::nextCursor);
    }

    public ApiAction<Optional<DirectoryClan>> clan(String slug) {
        return c.route(Routes.GET_DIRECTORY_CLANS_SLUG).path("slug", slug).asOptional(DirectoryClan.class);
    }
}

package com.runeclans.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.runeclans.sdk.model.Community;
import com.runeclans.sdk.model.CommunityListing;

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
}

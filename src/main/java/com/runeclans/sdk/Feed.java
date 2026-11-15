package com.runeclans.sdk;

import com.runeclans.sdk.model.FeedPage;

/** Your app's change feed, oldest first ({@code feed:read}). Store {@code nextCursor}; {@code more} means call again now. */
public final class Feed {
    private final RuneClanClient c;

    Feed(RuneClanClient c) {
        this.c = c;
    }

    /** The feed after {@code cursor} (null for the start). */
    public ApiAction<FeedPage> after(String cursor, Integer limit) {
        return c.route(Routes.GET_FEED).query("cursor", cursor).query("limit", limit).as(FeedPage.class);
    }
}

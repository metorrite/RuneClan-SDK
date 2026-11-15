package com.runeclans.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.runeclans.sdk.model.SiteFeatures;

import java.util.Optional;

/** Published sites, clan and community alike, by their address ({@code <slug>.runeclans.com}). */
public final class Sites {
    private final RuneClanClient c;

    Sites(RuneClanClient c) {
        this.c = c;
    }

    /**
     * A published site, or where its address moved to: a {@code Site} or a {@code SiteMoved} (see the spec), so this answers JSON: a
     * {@code movedTo} field means it moved; otherwise read it with Jackson's {@code treeToValue(node, Site.class)}.
     */
    public ApiAction<Optional<JsonNode>> get(String slug) {
        return c.route(Routes.GET_SITES_SLUG).path("slug", slug).asOptional(JsonNode.class);
    }

    /** Every feature of a site with its state, by site id (community sites have no clan). */
    public ApiAction<SiteFeatures> features(String siteId) {
        return c.route(Routes.GET_SITES_SITE_ID_FEATURES).path("siteId", siteId).as(SiteFeatures.class);
    }

    /** The site's open polls (counts only). */
    public ApiAction<JsonNode> polls(String slug) {
        return c.route(Routes.GET_SITES_SLUG_POLLS).path("slug", slug).asJson();
    }

    /** The site's active signups (seats only). */
    public ApiAction<JsonNode> signups(String slug) {
        return c.route(Routes.GET_SITES_SLUG_SIGNUPS).path("slug", slug).asJson();
    }

    /** The site's open group-finder posts. */
    public ApiAction<JsonNode> groups(String slug) {
        return c.route(Routes.GET_SITES_SLUG_GROUPS).path("slug", slug).asJson();
    }

    /** The site's open forms with their questions. */
    public ApiAction<JsonNode> forms(String slug) {
        return c.route(Routes.GET_SITES_SLUG_FORMS).path("slug", slug).asJson();
    }
}

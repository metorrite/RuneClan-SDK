package com.runeclans.sdk;

import com.runeclans.sdk.model.GimGroupDetail;
import com.runeclans.sdk.model.GimGroupList;

import java.util.Optional;

/**
 * RS3 Group Ironman groups, from Jagex's group leaderboard as the hub reads it once a day. Read with any key. Jagex's feed is undocumented and
 * lists no members, so a group here has its name, size, mode (regular or competitive) and the whole group's total XP and level, not who is in it.
 */
public final class GimGroups {
    private final RuneClanClient c;

    GimGroups(RuneClanClient c) {
        this.c = c;
    }

    /** The groups on the board, best first by total XP. */
    public ApiAction<GimGroupList> list() {
        return list(null, null, null, null, null, null);
    }

    /**
     * Groups on the board. Every argument may be null: {@code name} a part of the group name (an exact name first), {@code size} 1 to 5,
     * {@code mode} "regular" or "competitive", {@code sort} "xp" (default) or "level", {@code limit} 1 to 500 (default 50), {@code offset} from 0.
     */
    public ApiAction<GimGroupList> list(String name, Integer size, String mode, String sort, Integer limit, Integer offset) {
        return c.route(Routes.GET_GIM_GROUPS).query("q", name).query("size", size).query("mode", mode).query("sort", sort).query("limit", limit).query("offset", offset)
                .as(GimGroupList.class);
    }

    /** One group by Jagex's group id with its totals history over {@code days} (1 to 400; null for 30). Empty when the hub never saw it. */
    public ApiAction<Optional<GimGroupDetail>> get(long groupId, Integer days) {
        return c.route(Routes.GET_GIM_GROUPS_ID).path("id", String.valueOf(groupId)).query("days", days).asOptional(GimGroupDetail.class);
    }
}

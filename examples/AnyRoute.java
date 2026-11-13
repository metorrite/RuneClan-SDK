import com.fasterxml.jackson.databind.JsonNode;
import com.runeclans.sdk.RuneClan;
import com.runeclans.sdk.RuneClanClient;
import com.runeclans.sdk.Routes;
import com.runeclans.sdk.model.EventPage;

import java.io.PrintStream;

/**
 * Every public hub operation is in Routes, even the ones with no friendly method yet. route(...) still gets the SDK's scope check,
 * pacing and retries. forGuild(...) reads for one Discord server (players may limit their grant to some servers).
 */
public class AnyRoute {
    public static void main(String[] args) {
        try (RuneClanClient hub = RuneClan.connect()) {
            run(hub, args[0], args[1], System.out);
        }
    }

    static void run(RuneClanClient hub, String guildId, String alias, PrintStream out) {
        // A typed answer: the generated record for the route's JSON.
        EventPage drops = hub.forGuild(guildId)
                .route(Routes.GET_PLAYERS_ALIAS_EVENTS)
                .path("alias", alias)
                .query("category", "DROPS")
                .query("limit", 10)
                .as(EventPage.class)
                .complete();
        out.println(drops.data().size() + " recent drop(s)");

        // Or plain JSON when you only need a field or two.
        JsonNode top = hub.route(Routes.GET_GUILDS_GUILD_ID_XP_LEADERBOARD).path("guildId", guildId).query("period", "week").asJson().complete();
        out.println("This week's XP leaderboard: " + top.path("players").size() + " player(s)");
    }
}

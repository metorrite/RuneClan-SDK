import com.runeclans.sdk.RuneClan;
import com.runeclans.sdk.RuneClanClient;
import com.runeclans.sdk.model.Player;

import java.io.PrintStream;
import java.util.List;

/**
 * Tell the hub which Discord server your bot is in and which granted players are its members, then read the server's players.
 * Needs guilds:write (writes) and players:read (reads). Register and addMember are PUTs: safe to repeat, so run them on every start.
 */
public class GuildSync {
    public static void main(String[] args) {
        try (RuneClanClient hub = RuneClan.connect()) {
            run(hub, args[0], "My clan server", List.of(args).subList(1, args.length), System.out);
        }
    }

    static void run(RuneClanClient hub, String guildId, String guildName, List<String> memberAliases, PrintStream out) {
        hub.guilds().register(guildId, guildName).complete();
        for (String alias : memberAliases) hub.guilds().addMember(guildId, alias).complete();

        int count = 0;
        for (Player p : hub.guilds().allPlayers(guildId)) {   // follows nextCursor page by page for you
            out.println(p.alias() + " -> " + (p.characters().isEmpty() ? "no RSN" : p.characters().get(0).rsn()));
            count++;
        }
        out.println(count + " player(s) in " + guildName);
    }
}

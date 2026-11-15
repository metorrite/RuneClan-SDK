import com.runeclans.sdk.RuneClan;
import com.runeclans.sdk.RuneClanClient;
import com.runeclans.sdk.model.Character;
import com.runeclans.sdk.model.Player;

import java.io.PrintStream;

/**
 * "Who is this Discord user in game?" Needs the scope links:read, and the player must have granted your app their identity.
 * In a JDA listener use queue(...) instead of complete() so the gateway thread never waits.
 *
 * <pre>
 * java -cp ... LinkedPlayer.java &lt;discord user id&gt;
 * </pre>
 */
public class LinkedPlayer {
    public static void main(String[] args) {
        try (RuneClanClient hub = RuneClan.connect()) {
            run(hub, args[0], System.out);
        }
    }

    static void run(RuneClanClient hub, String discordUserId, PrintStream out) {
        if (!hub.hasScope("links:read")) {
            out.println("This key can't look players up by Discord id (it needs links:read).");
            return;
        }
        hub.players().byDiscordId(discordUserId).complete().ifPresentOrElse(
                player -> describe(player, out),
                () -> out.println("Not linked, or they haven't let your app see their identity yet."));
    }

    static void describe(Player player, PrintStream out) {
        out.println("Player " + player.alias() + " shares " + player.categories());
        for (Character c : player.characters()) {
            out.println("  RSN " + c.rsn() + " (link " + c.verification() + ", since " + c.linkedAt() + ")");
        }
    }
}

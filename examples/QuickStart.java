import com.runeclans.sdk.RuneClan;
import com.runeclans.sdk.RuneClanClient;
import com.runeclans.sdk.model.Clan;
import com.runeclans.sdk.model.Event;
import com.runeclans.sdk.model.EventList;
import com.runeclans.sdk.model.Me;

import java.io.PrintStream;
import java.util.Optional;

/**
 * Quick start: connect, see what your key may do, then look a clan up by name and list its open events.
 *
 * <pre>
 * RUNECLAN_API_KEY=rsh_... java -cp runeclan-sdk.jar:jackson.jar QuickStart.java "Clan name"
 * </pre>
 */
public class QuickStart {
    public static void main(String[] args) {
        try (RuneClanClient hub = RuneClan.connect()) {          // key from RUNECLAN_API_KEY or runeclan.properties
            run(hub, args.length > 0 ? args[0] : "Younglings", System.out);
        }
    }

    static void run(RuneClanClient hub, String clanName, PrintStream out) {
        // 1. The handshake already ran inside connect(): who am I and what may I do?
        Me me = hub.handshake().orElseThrow();
        out.println("App " + me.appName() + " (" + me.tier() + "), scopes " + me.scopes());
        out.println("Rate limit: " + me.rateLimit().perMinute() + " a minute, bursts of " + me.rateLimit().burst());

        // 2. A lookup gives an Optional: empty means "no such clan" (a 404), never an exception.
        Optional<Clan> clan = hub.clans().byName(clanName).complete();
        if (clan.isEmpty()) {
            out.println("No registered clan is called " + clanName);
            return;
        }

        // 3. Its open events (any key may read them).
        EventList events = hub.clans().events(clan.get().clanId(), "open").complete();
        out.println(clan.get().name() + " has " + events.events().size() + " open event(s)");
        for (Event e : events.events()) out.println("  - " + e.name() + " [" + e.status() + "] starts " + e.startsAt());
    }
}

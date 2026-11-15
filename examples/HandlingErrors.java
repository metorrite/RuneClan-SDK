import com.runeclans.sdk.RuneClan;
import com.runeclans.sdk.RuneClanClient;
import com.runeclans.sdk.RuneClanException;

import java.io.PrintStream;

/**
 * Everything that fails is a RuneClanException with the HTTP status and the hub's error code. Lookups answer an empty Optional for a
 * 404 instead. The SDK already waits out 429s and retries safe calls after an outage, so you only see what is left.
 */
public class HandlingErrors {
    public static void main(String[] args) {
        try (RuneClanClient hub = RuneClan.connect()) {
            run(hub, args[0], System.out);
        } catch (RuneClanException e) {
            // A wrong or revoked key fails right at connect().
            System.out.println("Couldn't connect: " + e.code() + " " + e.getMessage());
        }
    }

    static void run(RuneClanClient hub, String alias, PrintStream out) {
        try {
            var page = hub.players().events(alias, "XP", null, null, 20).complete();
            out.println(page.data().size() + " XP event(s)");
        } catch (RuneClanException e) {
            switch (e.code()) {
                case "missing_scope" -> out.println("Ask for events:read on your key (local: " + e.local() + ")");
                case "not_found" -> out.println("Unknown player, or they didn't grant your app");
                case "rate_limited" -> out.println("Still limited; try again in " + e.retryAfter() + " s");
                case "unreachable", "timeout" -> out.println("Hub unreachable right now: " + e.getMessage());
                default -> out.println("Refused: " + e.status() + " " + e.code() + ": " + e.getMessage());
            }
        }
    }
}

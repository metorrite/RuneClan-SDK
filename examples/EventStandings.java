import com.runeclans.sdk.RuneClan;
import com.runeclans.sdk.RuneClanClient;
import com.runeclans.sdk.model.Event;
import com.runeclans.sdk.model.EventList;
import com.runeclans.sdk.model.EventStandingsAnswer;

import java.io.PrintStream;

/**
 * Events clans share publicly (competitions, bingos), with the standings of each running one. Any key may read them.
 */
public class EventStandings {
    public static void main(String[] args) {
        try (RuneClanClient hub = RuneClan.connect()) {
            run(hub, System.out);
        }
    }

    static void run(RuneClanClient hub, PrintStream out) {
        EventList shared = hub.events().shared(null, null, null).complete();   // null = defaults (public events)
        for (Event e : shared.events()) {
            out.println(e.name() + " [" + e.status() + "]");
            if (!"RUNNING".equals(e.status())) continue;
            EventStandingsAnswer table = hub.events().standings(e.id()).complete();
            for (EventStandingsAnswer.StandingsItem row : table.standings()) {
                out.println("  " + (row.place() == null ? "-" : row.place()) + ". " + row.name() + "  " + row.points() + " pts");
            }
        }
    }
}

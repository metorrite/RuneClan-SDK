import com.runeclans.sdk.RuneClan;
import com.runeclans.sdk.RuneClanClient;
import com.runeclans.sdk.model.FeedEntry;
import com.runeclans.sdk.model.FeedPage;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Catch up on everything that changed for your app (new plugin events, grants changed, links made or ended), oldest first.
 * Needs feed:read. Save nextCursor somewhere that survives a restart; "more" means there is another page right now.
 * Run it every minute or so (or use webhooks, see WebhookReceiver).
 */
public class FeedPoller {
    static final Path CURSOR_FILE = Path.of("feed-cursor.txt");

    public static void main(String[] args) throws IOException {
        try (RuneClanClient hub = RuneClan.connect()) {
            String cursor = Files.exists(CURSOR_FILE) ? Files.readString(CURSOR_FILE).strip() : null;
            String next = run(hub, cursor, System.out);
            if (next != null) Files.writeString(CURSOR_FILE, next);
        }
    }

    /** Reads every page after {@code cursor} and answers the cursor to store. */
    static String run(RuneClanClient hub, String cursor, PrintStream out) {
        FeedPage page;
        do {
            page = hub.feed().after(cursor, 100).complete();
            for (FeedEntry e : page.data()) out.println(e.createdAt() + " " + e.kind() + " " + e.alias());
            if (page.nextCursor() != null) cursor = page.nextCursor();
        } while (Boolean.TRUE.equals(page.more()));
        return cursor;
    }
}

import com.runeclans.sdk.RuneClan;
import com.runeclans.sdk.RuneClanClient;
import com.runeclans.sdk.RuneClanConfig;
import com.runeclans.sdk.testing.FakeTransport;

import java.io.PrintStream;

/**
 * Test your bot without the network: FakeTransport answers in order (the first answer is the handshake) and records every request.
 * This one runs offline as it is: java -cp ... TestingYourBot.java
 */
public class TestingYourBot {
    public static void main(String[] args) {
        run(System.out);
    }

    static void run(PrintStream out) {
        FakeTransport fake = new FakeTransport()
                .answer(200, "{\"appId\":\"app1\",\"appName\":\"Test bot\",\"scopes\":[\"players:read\"],\"tier\":\"standard\","
                        + "\"rateLimit\":{\"perMinute\":60,\"burst\":120,\"windowSeconds\":60}}")
                .answer(200, "{\"alias\":\"a1_" + "a".repeat(26) + "\",\"categories\":[\"XP\"],\"characters\":[{\"rsn\":\"Zezima\"}]}");

        RuneClanConfig config = RuneClanConfig.builder()
                .apiKey("rsh_" + "x".repeat(43))   // a made-up key: FakeTransport never sends it anywhere
                .transport(fake)
                .build();
        try (RuneClanClient hub = RuneClan.connect(config)) {
            var player = hub.players().get("a1_" + "a".repeat(26)).complete().orElseThrow();
            out.println("Fake player plays " + player.characters().get(0).rsn());
            out.println("The SDK sent: " + fake.requests().size() + " requests, last " + fake.last());
        }
    }
}

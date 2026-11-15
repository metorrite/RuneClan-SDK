package com.runeclans.sdk;

/**
 * The SDK's front door.
 *
 * <pre>{@code
 * RuneClanClient hub = RuneClan.connect();
 * }</pre>
 *
 * {@link #connect()} reads the key from {@code RUNECLAN_API_KEY} or {@code runeclan.properties} (see {@link RuneClanConfig}) and runs
 * the start-up handshake: a wrong or revoked key fails here, at start, with a plain message. When the hub can't be reached, connecting
 * still works and the handshake runs again before a later call.
 */
public final class RuneClan {
    private RuneClan() {}

    public static RuneClanClient connect() {
        return connect(RuneClanConfig.builder().build());
    }

    public static RuneClanClient connect(String apiKey) {
        return connect(RuneClanConfig.builder().apiKey(apiKey).build());
    }

    public static RuneClanClient connect(RuneClanConfig config) {
        RuneClanClient client = RuneClanClient.create(config);
        if (config.handshake()) {
            try {
                client.refreshHandshake();
            } catch (RuneClanException e) {
                if (!Core.transientFailure(e)) {
                    client.close();
                    throw e;
                }
                Core.LOG.log(System.Logger.Level.WARNING, "RuneClan SDK: the start-up handshake didn''t work ({0}); it will be tried again.", e.code());
            } catch (RuntimeException | Error e) {
                client.close();
                throw e;
            }
        }
        return client;
    }
}

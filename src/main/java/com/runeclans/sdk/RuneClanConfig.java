package com.runeclans.sdk;

import java.io.IOException;
import java.io.Reader;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * How a {@link RuneClanClient} connects. Build one with {@link #builder()}; anything left out comes from, in order: the builder, the
 * environment ({@code RUNECLAN_API_KEY}, {@code RUNECLAN_API_URL}), then the key file {@code runeclan.properties} in the working folder
 * (or the file {@code RUNECLAN_CONFIG} names):
 *
 * <pre>
 * apiKey=rsh_...
 * # baseUrl=https://api.runeclans.com   (optional)
 * </pre>
 *
 * The key is never logged or shown by {@link #toString()}. When the key file sits inside a git folder and no {@code .gitignore} on the
 * way up names it, the SDK warns once at start, so the key isn't committed by accident.
 */
public final class RuneClanConfig {
    /** The hub address the spec names as production; override with {@code baseUrl} / {@code RUNECLAN_API_URL}. */
    public static final String DEFAULT_BASE_URL = "https://api.runeclans.com";
    public static final String KEY_FILE = "runeclan.properties";
    public static final String ENV_KEY = "RUNECLAN_API_KEY";
    public static final String ENV_URL = "RUNECLAN_API_URL";
    public static final String ENV_CONFIG = "RUNECLAN_CONFIG";
    static final Pattern KEY_SHAPE = Pattern.compile("rsh_[A-Za-z0-9_-]{43}");
    private static final System.Logger LOG = System.getLogger("com.runeclans.sdk");

    private final String apiKey;
    private final String baseUrl;
    private final String keySource;
    private final Transport transport;
    private final Ticker ticker;
    private final Executor executor;
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final int maxAttempts;
    private final Duration maxRetryWait;
    private final boolean handshake;
    private final String userAgentSuffix;

    private RuneClanConfig(Builder b, String apiKey, String baseUrl, String keySource) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.keySource = keySource;
        this.connectTimeout = b.connectTimeout;
        this.requestTimeout = b.requestTimeout;
        this.transport = b.transport != null ? b.transport : new JdkTransport(b.connectTimeout);
        this.ticker = b.ticker;
        this.executor = b.executor;
        this.maxAttempts = b.maxAttempts;
        this.maxRetryWait = b.maxRetryWait;
        this.handshake = b.handshake;
        this.userAgentSuffix = b.userAgentSuffix;
    }

    public static Builder builder() {
        return new Builder();
    }

    String apiKey() {
        return apiKey;
    }

    public String baseUrl() {
        return baseUrl;
    }

    /** Where the key came from: {@code builder}, {@code env RUNECLAN_API_KEY} or the key file's path. */
    public String keySource() {
        return keySource;
    }

    public Transport transport() {
        return transport;
    }

    public Ticker ticker() {
        return ticker;
    }

    /** Runs {@code submit()} and {@code queue()} calls; null for the SDK's own (virtual threads on Java 21+). */
    public Executor executor() {
        return executor;
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public Duration maxRetryWait() {
        return maxRetryWait;
    }

    public boolean handshake() {
        return handshake;
    }

    public String userAgentSuffix() {
        return userAgentSuffix;
    }

    @Override
    public String toString() {
        return "RuneClanConfig[baseUrl=" + baseUrl + ", key=" + KeyMask.mask(apiKey) + " from " + keySource + "]";
    }

    /** Builds a {@link RuneClanConfig}; every setting is optional. */
    public static final class Builder {
        private String apiKey;
        private String baseUrl;
        private Path keyFile;
        private Function<String, String> env = System::getenv;
        private Path workingDir = Path.of("").toAbsolutePath();
        private Transport transport;
        private Ticker ticker = Ticker.SYSTEM;
        private Executor executor;
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration requestTimeout = Duration.ofSeconds(10);
        private int maxAttempts = 3;
        private Duration maxRetryWait = Duration.ofSeconds(60);
        private boolean handshake = true;
        private String userAgentSuffix;

        private Builder() {}

        /** The API key ({@code rsh_...}). Without it: {@code RUNECLAN_API_KEY}, then the key file. */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /** The hub address. Without it: {@code RUNECLAN_API_URL}, the key file's {@code baseUrl}, then {@link #DEFAULT_BASE_URL}. */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /** Reads this key file instead of {@code ./runeclan.properties} (a missing file is an error). */
        public Builder keyFile(Path keyFile) {
            this.keyFile = keyFile;
            return this;
        }

        /** Where the environment comes from (tests); default {@link System#getenv(String)}. */
        public Builder env(Function<String, String> env) {
            this.env = Objects.requireNonNull(env);
            return this;
        }

        /** Where {@code runeclan.properties} is looked for; default the working folder. */
        public Builder workingDir(Path dir) {
            this.workingDir = dir.toAbsolutePath();
            return this;
        }

        public Builder transport(Transport transport) {
            this.transport = transport;
            return this;
        }

        public Builder ticker(Ticker ticker) {
            this.ticker = Objects.requireNonNull(ticker);
            return this;
        }

        public Builder executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        public Builder connectTimeout(Duration d) {
            this.connectTimeout = Objects.requireNonNull(d);
            return this;
        }

        public Builder requestTimeout(Duration d) {
            this.requestTimeout = Objects.requireNonNull(d);
            return this;
        }

        /** How many times a call may be sent in all (1 = never again); default 3. */
        public Builder maxAttempts(int n) {
            if (n < 1) throw new IllegalArgumentException("maxAttempts must be at least 1");
            this.maxAttempts = n;
            return this;
        }

        /** The longest {@code Retry-After} the SDK waits out on its own; a longer one is thrown as the 429. Default 60 s. */
        public Builder maxRetryWait(Duration d) {
            this.maxRetryWait = Objects.requireNonNull(d);
            return this;
        }

        /** False skips the start-up handshake (no scope checks or pacing until {@link RuneClanClient#refreshHandshake()}). */
        public Builder handshake(boolean on) {
            this.handshake = on;
            return this;
        }

        /** Added to the User-Agent, e.g. {@code my-bot/1.4}. */
        public Builder userAgent(String suffix) {
            this.userAgentSuffix = suffix;
            return this;
        }

        public RuneClanConfig build() {
            String key = blankToNull(apiKey);
            String url = blankToNull(baseUrl);
            String source = key != null ? "builder" : null;
            if (key == null && blankToNull(env.apply(ENV_KEY)) != null) {
                key = env.apply(ENV_KEY).strip();
                source = "env " + ENV_KEY;
            }
            if (url == null) url = blankToNull(env.apply(ENV_URL));
            Path file = keyFile;
            if (file == null && blankToNull(env.apply(ENV_CONFIG)) != null) file = Path.of(env.apply(ENV_CONFIG).strip());
            boolean fileNamed = file != null;
            if (file == null) file = workingDir.resolve(KEY_FILE);
            if (file != null && !file.isAbsolute()) file = workingDir.resolve(file);
            if ((key == null || url == null) && (fileNamed || Files.isRegularFile(file))) {
                Map<String, String> props = readKeyFile(file);
                if (key == null && blankToNull(props.get("apiKey")) != null) {
                    key = props.get("apiKey").strip();
                    source = file.toString();
                    GitCheck.warnIfTracked(file, LOG);
                }
                if (url == null) url = blankToNull(props.get("baseUrl"));
            }
            if (key == null) {
                throw new IllegalStateException("No RuneClan API key: put apiKey=rsh_... in " + KEY_FILE + ", set " + ENV_KEY + ", or call apiKey(...).");
            }
            if (!KEY_SHAPE.matcher(key).matches()) {
                throw new IllegalStateException("The RuneClan API key from " + source + " isn't a key (they look like rsh_ and 43 more letters).");
            }
            if (url == null) url = DEFAULT_BASE_URL;
            url = url.strip().replaceAll("/+$", "");
            if (!url.startsWith("https://") && !url.startsWith("http://")) throw new IllegalStateException("baseUrl must start with https:// (got " + url + ")");
            if (url.startsWith("http://") && !isLocal(url)) {
                LOG.log(Level.WARNING, "RuneClan SDK: {0} is plain http, so the API key travels unencrypted. Use https outside your own computer.", url);
            }
            return new RuneClanConfig(this, key, url, source);
        }

        private static Map<String, String> readKeyFile(Path file) {
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException e) {
                throw new IllegalStateException("Couldn't read the RuneClan key file " + file + ": " + e.getClass().getSimpleName());
            }
            return Map.of("apiKey", p.getProperty("apiKey", ""), "baseUrl", p.getProperty("baseUrl", ""));
        }

        private static boolean isLocal(String url) {
            String rest = url.substring("http://".length());
            return List.of("localhost", "127.0.0.1", "[::1]").stream().anyMatch(h -> rest.equals(h) || rest.startsWith(h + ":") || rest.startsWith(h + "/"));
        }

        private static String blankToNull(String s) {
            return s == null || s.isBlank() ? null : s;
        }
    }
}

package com.runeclans.sdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where the key and address come from, and that the key never shows. */
class ConfigTest {
    private static final String FILE_KEY = "rsh_" + "f".repeat(43);
    private static final String ENV_KEY = "rsh_" + "e".repeat(43);
    @TempDir
    Path dir;

    private RuneClanConfig.Builder builder(Map<String, String> env) {
        return RuneClanConfig.builder().workingDir(dir).env(env::get).transport(new com.runeclans.sdk.testing.FakeTransport());
    }

    @Test
    void theKeyFileInTheWorkingFolder() throws Exception {
        Files.writeString(dir.resolve("runeclan.properties"), "# my bot\napiKey=" + FILE_KEY + "\nbaseUrl=http://localhost:8090/\n");
        RuneClanConfig c = builder(Map.of()).build();
        assertEquals(FILE_KEY, c.apiKey());
        assertEquals("http://localhost:8090", c.baseUrl());
        assertEquals(dir.resolve("runeclan.properties").toString(), c.keySource());
        assertFalse(c.toString().contains(FILE_KEY));
        assertTrue(c.toString().contains("rsh_…ffff"), c.toString());
    }

    @Test
    void theEnvironmentWinsOverTheFileAndTheBuilderOverBoth() throws Exception {
        Files.writeString(dir.resolve("runeclan.properties"), "apiKey=" + FILE_KEY + "\n");
        assertEquals(ENV_KEY, builder(Map.of("RUNECLAN_API_KEY", ENV_KEY)).build().apiKey());
        assertEquals("env RUNECLAN_API_KEY", builder(Map.of("RUNECLAN_API_KEY", ENV_KEY)).build().keySource());
        String own = "rsh_" + "b".repeat(43);
        assertEquals(own, builder(Map.of("RUNECLAN_API_KEY", ENV_KEY)).apiKey(own).build().apiKey());
    }

    @Test
    void runeclanConfigNamesAnotherFile() throws Exception {
        Path other = dir.resolve("secrets").resolve("hub.properties");
        Files.createDirectories(other.getParent());
        Files.writeString(other, "apiKey=" + FILE_KEY + "\n");
        assertEquals(FILE_KEY, builder(Map.of("RUNECLAN_CONFIG", "secrets/hub.properties")).build().apiKey());
        assertThrows(IllegalStateException.class, () -> builder(Map.of("RUNECLAN_CONFIG", "missing.properties")).build());
    }

    @Test
    void theAddressDefaultsToTheSpecsServer() {
        RuneClanConfig c = builder(Map.of("RUNECLAN_API_KEY", ENV_KEY)).build();
        assertEquals(RuneClanConfig.DEFAULT_BASE_URL, c.baseUrl());
        assertEquals("https://hub.example.org", builder(Map.of("RUNECLAN_API_KEY", ENV_KEY, "RUNECLAN_API_URL", "https://hub.example.org//")).build().baseUrl());
        assertThrows(IllegalStateException.class, () -> builder(Map.of("RUNECLAN_API_KEY", ENV_KEY)).baseUrl("hub.example.org").build());
    }

    @Test
    void noKeyOrABadKeyIsAPlainErrorThatNeverEchoesIt() {
        IllegalStateException none = assertThrows(IllegalStateException.class, () -> builder(Map.of()).build());
        assertTrue(none.getMessage().contains("runeclan.properties"));
        String bad = "sk_live_" + "z".repeat(30);
        IllegalStateException wrong = assertThrows(IllegalStateException.class, () -> builder(Map.of("RUNECLAN_API_KEY", bad)).build());
        assertFalse(wrong.getMessage().contains(bad));
    }

    @Test
    void aKeyFileInsideGitIsCheckedAgainstGitignore() throws Exception {
        Files.createDirectories(dir.resolve(".git"));
        Path bot = Files.createDirectories(dir.resolve("bot"));
        Path file = bot.resolve("runeclan.properties");
        Files.writeString(file, "apiKey=" + FILE_KEY + "\n");
        assertEquals(dir, GitCheck.gitRoot(bot));
        assertFalse(GitCheck.ignored(file, dir));
        Files.writeString(dir.resolve(".gitignore"), "target/\n# keys\n/bot/runeclan.properties\n");
        assertTrue(GitCheck.ignored(file, dir));
        Files.writeString(dir.resolve(".gitignore"), "**/runeclan.properties\n");
        assertTrue(GitCheck.ignored(file, dir));
        Files.writeString(dir.resolve(".gitignore"), "");
        Files.writeString(bot.resolve(".gitignore"), "*.properties\n");
        assertTrue(GitCheck.ignored(file, dir));
        Files.writeString(bot.resolve(".gitignore"), "!runeclan.properties\n");
        assertFalse(GitCheck.ignored(file, dir));
        // loading still works either way: the check only warns
        assertEquals(FILE_KEY, builder(Map.of()).workingDir(bot).build().apiKey());
    }

    @Test
    void outsideGitThereIsNothingToCheck() {
        assertEquals(null, GitCheck.gitRoot(dir));
    }
}

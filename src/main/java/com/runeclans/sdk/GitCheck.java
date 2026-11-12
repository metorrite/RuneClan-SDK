package com.runeclans.sdk;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Warns when the key file sits inside a git working tree and no {@code .gitignore} between the file and the tree's top names it, so a
 * {@code git add .} would commit the key. A simple check (the file's name, {@code *.properties}, or a pattern ending in its name), not a
 * full gitignore engine: it may warn about a file git does ignore through a rule it doesn't read, never the other way round in practice.
 */
final class GitCheck {
    private GitCheck() {}

    static void warnIfTracked(Path file, System.Logger log) {
        Path abs = file.toAbsolutePath().normalize();
        Path root = gitRoot(abs.getParent());
        if (root == null) return;
        if (!ignored(abs, root)) {
            log.log(Level.WARNING, "RuneClan SDK: the key file {0} is inside a git folder and no .gitignore names it. Add \"{1}\" to {2} so the key is never committed.",
                    abs, abs.getFileName(), root.resolve(".gitignore"));
        }
    }

    static Path gitRoot(Path dir) {
        for (Path d = dir; d != null; d = d.getParent()) if (Files.exists(d.resolve(".git"))) return d;
        return null;
    }

    static boolean ignored(Path file, Path root) {
        String name = file.getFileName().toString();
        for (Path d = file.getParent(); d != null && d.startsWith(root); d = d.getParent()) {
            Path gi = d.resolve(".gitignore");
            if (!Files.isRegularFile(gi)) continue;
            String rel = d.relativize(file).toString().replace('\\', '/');
            try {
                List<String> lines = Files.readAllLines(gi, StandardCharsets.UTF_8);
                for (String raw : lines) {
                    String line = raw.strip();
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue;
                    String p = line.replaceFirst("^/", "").replaceFirst("^\\*\\*/", "");
                    if (p.equals(name) || p.equals(rel) || p.equals("*.properties")) return true;
                }
            } catch (IOException e) {
                return false;
            }
            if (d.equals(root)) break;
        }
        return false;
    }
}

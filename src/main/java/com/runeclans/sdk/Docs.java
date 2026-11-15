package com.runeclans.sdk;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Opens the SDK's API reference, which the jar carries (the same page as {@code docs/index.html} in the repository). Run it with only the
 * SDK jar, no other library needed:
 *
 * <pre>{@code
 * java -cp runeclan-sdk-0.2.0.jar com.runeclans.sdk.Docs            # writes runeclan-sdk-docs.html here and opens it
 * java -cp runeclan-sdk-0.2.0.jar com.runeclans.sdk.Docs some/folder
 * }</pre>
 */
public final class Docs {
    /** Where the page sits inside the jar. */
    public static final String RESOURCE = "/com/runeclans/sdk/docs/index.html";
    static final String FILE_NAME = "runeclan-sdk-docs.html";

    private Docs() {}

    public static void main(String[] args) throws IOException {
        Path file = write(Path.of(args.length > 0 ? args[0] : "."));
        System.out.println("RuneClan SDK docs: " + file.toUri());
        if (!open(file)) System.out.println("Open that file in your browser.");
    }

    /** Copies the page into {@code folder} and answers the file. */
    public static Path write(Path folder) throws IOException {
        try (InputStream in = Docs.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IOException("The docs page isn't in this jar (" + RESOURCE + ")");
            Files.createDirectories(folder);
            Path file = folder.resolve(FILE_NAME).toAbsolutePath().normalize();
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
            return file;
        }
    }

    private static boolean open(Path file) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(file.toUri());
                return true;
            }
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            // no desktop here (a server): the path is printed instead
        }
        return false;
    }
}

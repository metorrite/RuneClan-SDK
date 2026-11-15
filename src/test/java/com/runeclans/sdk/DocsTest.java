package com.runeclans.sdk;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** docs/index.html covers every public SDK method and route, and the jar carries the same page. Rebuild it with tools/gen_docs.py. */
class DocsTest {
    static final Path PAGE = Path.of("docs/index.html");

    static String page() throws Exception {
        return Files.readString(PAGE, StandardCharsets.UTF_8);
    }

    @Test
    void everyPublicMethodHasACard() throws Exception {
        String html = page();
        List<String> missing = new ArrayList<>();
        List<Class<?>> groups = new ArrayList<>(List.of(RuneClanClient.class));
        for (Method m : RuneClanClient.class.getMethods()) {
            if (m.getParameterCount() == 0 && m.getReturnType().getPackage() == RuneClanClient.class.getPackage()
                    && m.getReturnType() != RuneClanClient.class && m.getReturnType() != RouteCall.class && m.getReturnType() != ApiAction.class) {
                groups.add(m.getReturnType());
            }
        }
        for (Class<?> c : groups) {
            for (Method m : c.getDeclaredMethods()) {
                if (!Modifier.isPublic(m.getModifiers()) || m.isSynthetic()) continue;
                if (c == RuneClanClient.class && (groups.contains(m.getReturnType()) || List.of("create", "close", "toString").contains(m.getName()))) continue;
                String id = "id=\"m-" + c.getSimpleName().toLowerCase(Locale.ROOT) + "-" + m.getName().toLowerCase(Locale.ROOT) + "\"";
                if (!html.contains(id)) missing.add(c.getSimpleName() + "." + m.getName());
            }
        }
        assertTrue(groups.size() >= 10, "found the groups: " + groups);
        assertEquals(List.of(), missing, "not in docs/index.html: run python3 tools/gen_docs.py");
    }

    @Test
    void everyRouteHasACard() throws Exception {
        String html = page();
        List<String> missing = new ArrayList<>();
        int count = 0;
        for (Field f : Routes.class.getFields()) {
            if (f.getType() != Route.class) continue;
            count++;
            if (!html.contains("id=\"r-" + f.getName() + "\"")) missing.add(f.getName());
        }
        assertTrue(count > 0);
        assertEquals(List.of(), missing, "not in docs/index.html: run python3 tools/gen_docs.py");
    }

    @Test
    void pageNamesThisVersionAndIsSelfContained() throws Exception {
        String html = page();
        assertTrue(html.contains("v" + RuneClanClient.VERSION + " · Java"), "docs/index.html was made for another version");
        // nothing loaded from the internet: it must open offline, from the jar or a file
        assertTrue(!html.contains("<script src") && !html.contains("<link rel=\"stylesheet\""), "the page must stay self-contained");
    }

    @Test
    void theJarCarriesThePageAndDocsWritesIt() throws Exception {
        byte[] inJar;
        try (InputStream in = Docs.class.getResourceAsStream(Docs.RESOURCE)) {
            assertNotNull(in, "docs/index.html isn't packed into the jar (pom resources)");
            inJar = in.readAllBytes();
        }
        assertArrayEquals(Files.readAllBytes(PAGE), inJar);
        Path dir = Files.createTempDirectory("rc-docs");
        Path file = Docs.write(dir);
        assertEquals(dir.resolve(Docs.FILE_NAME).toAbsolutePath().normalize(), file);
        assertArrayEquals(inJar, Files.readAllBytes(file));
    }
}

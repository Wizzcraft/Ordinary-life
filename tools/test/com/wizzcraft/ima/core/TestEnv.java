package com.wizzcraft.ima.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Where things are, for tests that read the source tree or scan compiled classes. Not a test class itself. */
final class TestEnv {
    private TestEnv() {
    }

    /** The repository root: -Dima.root, else the working directory (tools/check.sh runs from the root). */
    static Path root() {
        String p = System.getProperty("ima.root");
        return Path.of(p != null ? p : ".").toAbsolutePath().normalize();
    }

    /** The game's own sources (the app, not the desktop tools). */
    static Path coreSources() {
        return root().resolve("app/src/main/java/com/wizzcraft/ima/core");
    }

    static List<Path> coreJavaFiles() throws IOException {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(coreSources())) {
            s.filter(p -> p.getFileName().toString().endsWith(".java")).sorted().forEach(out::add);
        }
        return out;
    }

    /** Names of all compiled top-level classes of the core package that exist in the app sources. */
    static List<String> appClassNames() throws IOException {
        List<String> out = new ArrayList<>();
        for (Path p : coreJavaFiles()) {
            String n = p.getFileName().toString();
            out.add("com.wizzcraft.ima.core." + n.substring(0, n.length() - 5));
        }
        return out;
    }
}

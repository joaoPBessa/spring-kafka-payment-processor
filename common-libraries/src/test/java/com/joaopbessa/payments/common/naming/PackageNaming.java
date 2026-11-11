package com.joaopbessa.payments.common.naming;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public final class PackageNaming {

    private static final String CLASS_FILE_SUFFIX = ".class";

    private PackageNaming() {
    }

    public static List<String> violationsIn(Class<?> anchor) {
        Path classesDirectory = classesDirectoryOf(anchor);
        try (Stream<Path> files = Files.walk(classesDirectory)) {
            return files.filter(file -> file.toString().endsWith(CLASS_FILE_SUFFIX))
                    .map(file -> classesDirectory.relativize(file.getParent()).toString())
                    .distinct()
                    .map(directory -> directory.replace(File.separatorChar, '.'))
                    .filter(PackageNaming::hasUpperCase)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path classesDirectoryOf(Class<?> anchor) {
        try {
            return Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Cannot locate the classes of " + anchor.getName(), e);
        }
    }

    private static boolean hasUpperCase(String packageName) {
        return !packageName.equals(packageName.toLowerCase());
    }

}

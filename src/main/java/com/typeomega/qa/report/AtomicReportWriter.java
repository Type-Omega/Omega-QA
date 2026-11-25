package com.typeomega.qa.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writes report artifacts via {@code .tmp} + atomic rename so partial files are never observed by CI. */
public final class AtomicReportWriter {

    private AtomicReportWriter() {}

    public static void writeAtomic(Path target, String content) throws IOException {
        Path abs = target.toAbsolutePath().normalize();
        if (abs.getParent() != null) Files.createDirectories(abs.getParent());
        Path tmp = abs.resolveSibling(abs.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, abs, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException fallback) {
            Files.move(tmp, abs, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

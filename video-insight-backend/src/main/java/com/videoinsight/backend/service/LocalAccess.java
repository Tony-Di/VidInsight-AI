package com.videoinsight.backend.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handle to a local-filesystem copy of a stored file. For local storage this wraps the
 * file in place (close is a noop). For remote storage (S3/MinIO) the object is downloaded
 * to a temp file and close() deletes it.
 *
 * Always use with try-with-resources:
 * <pre>
 *     try (LocalAccess access = fileStorageService.accessLocal(url)) {
 *         Path p = access.path();
 *         // run ffmpeg / read bytes / etc.
 *     }
 * </pre>
 */
public final class LocalAccess implements AutoCloseable {

    private final Path path;
    private final boolean cleanupOnClose;

    private LocalAccess(Path path, boolean cleanupOnClose) {
        this.path = path;
        this.cleanupOnClose = cleanupOnClose;
    }

    public static LocalAccess wrap(Path path) {
        return new LocalAccess(path, false);
    }

    public static LocalAccess temp(Path path) {
        return new LocalAccess(path, true);
    }

    public Path path() {
        return path;
    }

    @Override
    public void close() {
        if (!cleanupOnClose || path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Temp file cleanup is best-effort; orphans get reaped by OS temp policy.
        }
    }
}

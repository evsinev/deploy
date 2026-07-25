package io.pne.deploy.server.vertx.dashboard;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Tails a plain-text log file for the dashboard "Log" screen: an initial "last N lines" read plus cheap
 * incremental reads of the bytes appended since a byte offset. Handles rotation/truncation (svlogd renames
 * {@code current}): if the file shrank below the last offset, reading restarts from the beginning. Not
 * thread-safe — each SSE connection uses its own instance (it carries an incomplete trailing line between reads).
 */
public class ServerLogTailer {

    private static final int MAX_READ = 1_000_000;

    private final File          file;
    private final StringBuilder partial = new StringBuilder(); // incomplete trailing line carried between reads

    public ServerLogTailer(File aFile) {
        this.file = aFile;
    }

    /** Last {@code aCount} lines of the file (whole-file read; the log is small, a few MB at most). */
    public List<String> lastLines(int aCount) {
        if (file == null || !file.isFile()) {
            return List.of();
        }
        try {
            List<String> all = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            int from = Math.max(0, all.size() - aCount);
            return new ArrayList<>(all.subList(from, all.size()));
        } catch (IOException e) {
            return List.of("(cannot read " + file + ": " + e.getMessage() + ")");
        }
    }

    public long size() {
        return file == null ? 0 : file.length();
    }

    /** Complete lines appended since {@code aOffset}; returns the offset to continue from. Resets to 0 on truncation. */
    public Chunk readFrom(long aOffset) {
        if (file == null || !file.isFile()) {
            return new Chunk(List.of(), aOffset);
        }
        long length = file.length();
        long offset = aOffset;
        if (length < offset) { // rotated / truncated -> start over
            offset = 0;
            partial.setLength(0);
        }
        if (length <= offset) {
            return new Chunk(List.of(), offset);
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(offset);
            byte[] buf = new byte[(int) Math.min(length - offset, MAX_READ)];
            int read = raf.read(buf);
            if (read <= 0) {
                return new Chunk(List.of(), offset);
            }
            partial.append(new String(buf, 0, read, StandardCharsets.UTF_8));
            List<String> lines = new ArrayList<>();
            int nl;
            while ((nl = partial.indexOf("\n")) >= 0) {
                String line = partial.substring(0, nl);
                if (line.endsWith("\r")) {
                    line = line.substring(0, line.length() - 1);
                }
                lines.add(line);
                partial.delete(0, nl + 1);
            }
            return new Chunk(lines, offset + read);
        } catch (IOException e) {
            return new Chunk(List.of(), offset);
        }
    }

    /** Appended complete lines plus the offset to continue reading from. */
    public record Chunk(List<String> lines, long offset) {
    }
}

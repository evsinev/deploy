package io.pne.deploy.client.redmine.remote.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Небольшой durable-спул: одна операция = один {@code *.json} файл. Пишем файл ДО отправки, удаляем ПОСЛЕ успеха,
 * {@link #loadAll()} при старте поднимает незакрытые. {@link #append} — FIFO по имени (счётчик), {@link #put} —
 * overwrite по ключу (коалесинг). Запись атомарна (temp-файл + move).
 */
public class PersistentSpool {

    private static final Logger LOG         = LoggerFactory.getLogger(PersistentSpool.class);
    private static final String SUFFIX      = ".json";
    private static final String DEAD_SUBDIR = "dead";

    private final File       dir;
    private final AtomicLong sequence;
    private final AtomicLong sent         = new AtomicLong();
    private final AtomicLong deadLettered = new AtomicLong();

    public PersistentSpool(File aDir) {
        this.dir = aDir;
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Cannot create spool dir " + dir);
        }
        this.sequence = new AtomicLong(maxSequence());
    }

    /** Последовательная запись (FIFO). @return имя файла для последующего {@link #remove}. */
    public synchronized String append(String aJson) {
        String name = String.format("%020d%s", sequence.incrementAndGet(), SUFFIX);
        writeAtomic(name, aJson);
        return name;
    }

    /** Запись по ключу (overwrite = коалесинг). @return имя файла. */
    public synchronized String put(String aKey, String aJson) {
        String name = sanitize(aKey) + SUFFIX;
        writeAtomic(name, aJson);
        return name;
    }

    public synchronized void remove(String aFileName) {
        try {
            Files.deleteIfExists(new File(dir, aFileName).toPath());
        } catch (IOException e) {
            LOG.warn("Cannot remove spool file {}", aFileName, e);
        }
        sent.incrementAndGet();
    }

    /** Move a give-up operation into the {@code dead/} sub-directory so it stops being retried but is kept. */
    public synchronized void deadLetter(String aFileName) {
        File deadDir = new File(dir, DEAD_SUBDIR);
        if (!deadDir.exists() && !deadDir.mkdirs()) {
            LOG.warn("Cannot create dead-letter dir {}", deadDir);
            return;
        }
        try {
            Files.move(new File(dir, aFileName).toPath(), new File(deadDir, aFileName).toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            LOG.warn("Moved {} to dead-letter dir {}", aFileName, deadDir);
        } catch (IOException e) {
            LOG.warn("Cannot dead-letter spool file {}", aFileName, e);
        }
        deadLettered.incrementAndGet();
    }

    /** Number of active (pending) operations currently spooled. */
    public synchronized int size() {
        File[] files = dir.listFiles((d, name) -> name.endsWith(SUFFIX));
        return files == null ? 0 : files.length;
    }

    /** Number of dead-lettered operations kept in {@code dead/}. */
    public int deadSize() {
        File[] files = new File(dir, DEAD_SUBDIR).listFiles((d, name) -> name.endsWith(SUFFIX));
        return files == null ? 0 : files.length;
    }

    /** Cumulative count of successfully sent (removed) operations since start. */
    public long sentCount() {
        return sent.get();
    }

    /** Cumulative count of operations moved to dead-letter since start. */
    public long deadLetterCount() {
        return deadLettered.get();
    }

    public synchronized List<Stored> loadAll() {
        File[] files = dir.listFiles((d, name) -> name.endsWith(SUFFIX));
        List<Stored> result = new ArrayList<>();
        if (files == null) {
            return result;
        }
        Arrays.sort(files);
        for (File file : files) {
            try {
                String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                result.add(new Stored(file.getName(), json));
            } catch (IOException e) {
                LOG.warn("Cannot read spool file {}", file, e);
            }
        }
        return result;
    }

    private void writeAtomic(String aName, String aJson) {
        Path target = new File(dir, aName).toPath();
        Path tmp    = new File(dir, aName + ".tmp").toPath();
        try {
            Files.write(tmp, aJson.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write spool file " + aName, e);
        }
    }

    private long maxSequence() {
        File[] files = dir.listFiles((d, name) -> name.endsWith(SUFFIX));
        long max = 0;
        if (files != null) {
            for (File file : files) {
                String base = file.getName().substring(0, file.getName().length() - SUFFIX.length());
                try {
                    max = Math.max(max, Long.parseLong(base));
                } catch (NumberFormatException ignore) {
                    // keyed entries (edit-...) are not sequential — skip
                }
            }
        }
        return max;
    }

    private static String sanitize(String aKey) {
        return aKey.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public static final class Stored {
        private final String fileName;
        private final String json;

        public Stored(String aFileName, String aJson) {
            this.fileName = aFileName;
            this.json     = aJson;
        }

        public String getFileName() {
            return fileName;
        }

        public String getJson() {
            return json;
        }
    }
}

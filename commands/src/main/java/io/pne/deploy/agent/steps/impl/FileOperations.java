package io.pne.deploy.agent.steps.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.UUID;

/**
 * File primitives shared by the steps.
 *
 * <p>Anything written is first written next to its destination and then moved into place, so a reader never sees a
 * half-written file and a failed step leaves the previous content untouched. The temporary file is a sibling on
 * purpose: a move within one filesystem is atomic, a move across filesystems is not.
 */
final class FileOperations {

    private FileOperations() {
    }

    static void createDirectories(Path aDirectory) throws IOException {
        if (aDirectory != null) {
            Files.createDirectories(aDirectory);
        }
    }

    static void writeString(Path aTarget, String aContent, boolean aAtomic) throws IOException {
        byte[] bytes = aContent.getBytes(StandardCharsets.UTF_8);
        if (!aAtomic) {
            Files.write(aTarget, bytes);
            return;
        }
        Path temp = temporarySibling(aTarget);
        try {
            Files.write(temp, bytes);
            moveInto(temp, aTarget);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Copies the contents of one file over another. An atomic copy replaces the destination file, so the
     * permissions the destination already had are carried over to the replacement rather than inherited from
     * the source - a copy must not widen who can read a file.
     */
    static void copy(Path aSource, Path aTarget, boolean aAtomic) throws IOException {
        if (!aAtomic) {
            Files.copy(aSource, aTarget, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        Path temp = temporarySibling(aTarget);
        try {
            Files.copy(aSource, temp, StandardCopyOption.REPLACE_EXISTING);
            copyPermissions(aTarget, temp);
            moveInto(temp, aTarget);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void copyPermissions(Path aFrom, Path aTo) throws IOException {
        if (!Files.exists(aFrom)) {
            return;
        }
        PosixFileAttributeView view = Files.getFileAttributeView(aFrom, PosixFileAttributeView.class);
        if (view == null) {
            return;
        }
        Files.setPosixFilePermissions(aTo, view.readAttributes().permissions());
    }

    static Path temporarySibling(Path aTarget) {
        Path parent = aTarget.getParent();
        String name = "." + aTarget.getFileName() + ".part-" + UUID.randomUUID();
        return parent == null ? Path.of(name) : parent.resolve(name);
    }

    static void moveInto(Path aTemp, Path aTarget) throws IOException {
        try {
            Files.move(aTemp, aTarget, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(aTemp, aTarget, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Deletes a directory tree. Symbolic links are removed as links and never followed. */
    static void deleteRecursively(Path aPath) throws IOException {
        if (!Files.exists(aPath, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(aPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path aFile, BasicFileAttributes aAttributes) throws IOException {
                Files.delete(aFile);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path aDirectory, IOException aFailure) throws IOException {
                if (aFailure != null) {
                    throw aFailure;
                }
                Files.delete(aDirectory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Copies at most {@code aMaxBytes}; more than that is an error rather than a truncated file. */
    static long copyLimited(InputStream aInput, OutputStream aOutput, long aMaxBytes) throws IOException {
        return copyLimited(aInput, aOutput, aMaxBytes, Long.MAX_VALUE);
    }

    /**
     * Copies with both a size limit and a deadline. The deadline matters for a source that answers and then sends
     * slowly or not at all: without it such a source holds the transfer open for as long as it likes.
     */
    static long copyLimited(InputStream aInput, OutputStream aOutput, long aMaxBytes, long aDeadlineMillis) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long   total  = 0;
        int    read;
        while ((read = aInput.read(buffer)) > 0) {
            total += read;
            if (total > aMaxBytes) {
                throw new IOException("Refusing to read more than " + aMaxBytes + " bytes");
            }
            if (System.currentTimeMillis() > aDeadlineMillis) {
                throw new IOException("The transfer did not finish in time, gave up after " + total + " bytes");
            }
            aOutput.write(buffer, 0, read);
        }
        return total;
    }
}

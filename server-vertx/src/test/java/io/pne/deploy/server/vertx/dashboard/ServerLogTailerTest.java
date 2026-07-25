package io.pne.deploy.server.vertx.dashboard;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ServerLogTailerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void lastLinesReturnsTail() throws Exception {
        File f = write("l1\nl2\nl3\nl4\nl5\n");
        assertEquals(List.of("l3", "l4", "l5"), new ServerLogTailer(f).lastLines(3));
    }

    @Test
    public void readFromReturnsOnlyNewCompleteLinesAndAdvancesOffset() throws Exception {
        File f = write("a\nb\n");
        ServerLogTailer tailer = new ServerLogTailer(f);
        long offset = f.length();

        append(f, "c\nd\ne-part");                 // two full lines + a partial (no trailing newline yet)
        ServerLogTailer.Chunk chunk = tailer.readFrom(offset);
        assertEquals(List.of("c", "d"), chunk.lines()); // "e-part" is withheld until its line completes

        append(f, "-done\n");
        ServerLogTailer.Chunk chunk2 = tailer.readFrom(chunk.offset());
        assertEquals(List.of("e-part-done"), chunk2.lines());
    }

    @Test
    public void shrinkingFileResetsToStart() throws Exception {
        File f = write("one\ntwo\nthree\n");
        ServerLogTailer tailer = new ServerLogTailer(f);
        long offset = f.length();

        write(f, "fresh\n"); // rotation: file replaced with a shorter one
        ServerLogTailer.Chunk chunk = tailer.readFrom(offset);
        assertTrue(chunk.lines().toString(), chunk.lines().contains("fresh"));
    }

    private File write(String content) throws IOException {
        File f = tmp.newFile();
        Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
        return f;
    }

    private void write(File f, String content) throws IOException {
        Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
    }

    private void append(File f, String content) throws IOException {
        Files.writeString(f.toPath(), content, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }
}

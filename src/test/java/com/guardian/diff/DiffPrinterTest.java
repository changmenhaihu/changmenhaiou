package com.guardian.diff;

import com.guardian.rewrite.SourceFileResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DiffPrinterTest {

    private final PrintStream originalOut = System.out;
    private ByteArrayOutputStream buffer;

    @BeforeEach
    void setUp() {
        buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    void printsEmptyMessageWhenNoResults() {
        DiffPrinter.print(List.of(), false);
        assertTrue(buffer.toString().contains("未发现需要修改的文件"));
    }

    @Test
    void printsDiffWithDryRunHint() {
        var result = new SourceFileResult(
                Path.of("src/main/java/com/example/MyController.java"),
                "@@ -1,2 +1,2 @@\n"
                        + "-import javax.persistence.Entity;\n"
                        + "+import jakarta.persistence.Entity;\n",
                true
        );
        DiffPrinter.print(List.of(result), false);
        String out = buffer.toString();
        assertTrue(out.contains("受影响的文件: 1 个"));
        assertTrue(out.contains("jakarta.persistence.Entity"));
        assertTrue(out.contains("dry-run"));
    }

    @Test
    void printsAppliedHintWhenApplyIsTrue() {
        var result = new SourceFileResult(
                Path.of("src/main/java/com/example/App.java"),
                "@@ -1 +1 @@\n-import javax.servlet.http.*;\n+import jakarta.servlet.http.*;\n",
                true
        );
        DiffPrinter.print(List.of(result), true);
        assertTrue(buffer.toString().contains("修改已写入磁盘"));
    }
}

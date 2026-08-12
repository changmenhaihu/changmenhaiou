package com.guardian.diff;

import com.guardian.rewrite.recipes.CodeHealthCompositeRecipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.RecipeRun;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthReportPrinterTest {

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
    void groupsFindingsByCategoryAndPrintsSummary() throws Exception {
        Path tempDir = Files.createTempDirectory("guardian-report");
        Path javaFile = tempDir.resolve("App.java");
        Files.writeString(javaFile, """
                public class App {
                    void go() {
                        System.out.println("x");
                    }
                }
                """);

        ExecutionContext ctx = new InMemoryExecutionContext();
        List<SourceFile> sources = new ArrayList<>();
        JavaParser.fromJavaVersion().build().parse(List.of(javaFile), tempDir, ctx).forEach(sources::add);
        RecipeRun run = new CodeHealthCompositeRecipe().run(new InMemoryLargeSourceSet(sources), ctx);

        HealthReportPrinter.print(run.getChangeset().getAllResults());

        String out = buffer.toString();
        assertTrue(out.contains("App.java"), "应打印文件路径");
        assertTrue(out.contains("[规范]"), "应打印规范类问题");
        assertTrue(out.contains("规范类问题:"), "应统计规范类问题");
        assertTrue(out.contains("架构类问题:"), "应统计架构类问题");
        assertTrue(out.contains("合计:"), "应统计合计");
    }

    @Test
    void printsNoIssueMessageWhenEmpty() {
        HealthReportPrinter.print(List.of());
        assertTrue(buffer.toString().contains("未发现任何代码健康问题"));
    }
}

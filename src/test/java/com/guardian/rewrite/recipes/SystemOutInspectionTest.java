package com.guardian.rewrite.recipes;

import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.RecipeRun;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemOutInspectionTest {

    @Test
    void detectsSystemOutAndErrPrintCalls() throws Exception {
        Path tempDir = Files.createTempDirectory("guardian-systemout");
        Path javaFile = tempDir.resolve("Foo.java");
        Files.writeString(javaFile, """
                public class Foo {
                    void bar() {
                        System.out.println("a");
                        System.err.print("b");
                        System.out.printf("c");
                        logger.info("ok");
                    }
                }
                """);

        ExecutionContext ctx = new InMemoryExecutionContext();
        List<SourceFile> sources = new ArrayList<>();
        JavaParser.fromJavaVersion().build().parse(List.of(javaFile), tempDir, ctx).forEach(sources::add);

        RecipeRun run = new SystemOutInspection().run(new InMemoryLargeSourceSet(sources), ctx);

        int findings = 0;
        for (Result result : run.getChangeset().getAllResults()) {
            if (result.getAfter() != null) {
                findings += collectFindings(result.getAfter());
            }
        }
        assertEquals(3, findings, "应检测到 3 处 System.out/err 输出，log 调用不应误报");
    }

    @Test
    void noFindingsWhenNoSystemOut() throws Exception {
        Path tempDir = Files.createTempDirectory("guardian-systemout");
        Path javaFile = tempDir.resolve("Bar.java");
        Files.writeString(javaFile, """
                public class Bar {
                    private final Logger logger = LoggerFactory.getLogger(Bar.class);
                    void go() {
                        logger.info("no system out here");
                    }
                }
                """);

        ExecutionContext ctx = new InMemoryExecutionContext();
        List<SourceFile> sources = new ArrayList<>();
        JavaParser.fromJavaVersion().build().parse(List.of(javaFile), tempDir, ctx).forEach(sources::add);

        RecipeRun run = new SystemOutInspection().run(new InMemoryLargeSourceSet(sources), ctx);

        int findings = 0;
        for (Result result : run.getChangeset().getAllResults()) {
            if (result.getAfter() != null) {
                findings += collectFindings(result.getAfter());
            }
        }
        assertTrue(findings == 0, "无 System.out/err 时不应产生标记");
    }

    private static int collectFindings(SourceFile sourceFile) {
        int[] count = {0};
        new org.openrewrite.java.JavaVisitor<ExecutionContext>() {
            @Override
            public org.openrewrite.java.tree.J preVisit(org.openrewrite.java.tree.J tree, ExecutionContext p) {
                if (tree != null) {
                    count[0] += tree.getMarkers().findAll(HealthFinding.class).size();
                }
                return tree;
            }
        }.visit(sourceFile, new InMemoryExecutionContext());
        return count[0];
    }
}

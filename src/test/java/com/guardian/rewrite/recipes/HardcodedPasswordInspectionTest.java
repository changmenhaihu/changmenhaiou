package com.guardian.rewrite.recipes;

import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.RecipeRun;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.properties.PropertiesParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HardcodedPasswordInspectionTest {

    @Test
    void detectsHardcodedPasswordInProperties() throws Exception {
        Path tempDir = Files.createTempDirectory("guardian-config");
        Path config = tempDir.resolve("application.properties");
        Files.writeString(config, "spring.datasource.password=123456\nserver.port=8080\n");

        ExecutionContext ctx = new InMemoryExecutionContext();
        List<SourceFile> sources = new java.util.ArrayList<>();
        PropertiesParser.builder().build()
                .parse(List.of(config), tempDir, ctx).forEach(sources::add);

        RecipeRun run = new HardcodedPasswordInspection()
                .run(new InMemoryLargeSourceSet(sources), ctx);
        List<Result> results = run.getChangeset().getAllResults();

        assertFalse(results.isEmpty(), "应产生检测结果");
        Result result = results.get(0);
        assertNotNull(result.getAfter(), "properties 文件应保留在结果中");
        assertFalse(result.getAfter().getMarkers().findAll(HealthFinding.class).isEmpty(),
                "properties 文件应带 HealthFinding 标记");
    }
}

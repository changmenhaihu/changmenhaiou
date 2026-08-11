package com.guardian.rewrite.recipes;

import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.RecipeRun;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringFactoriesMigrationTest {

    @Test
    void migratesAutoConfigsToImportsFile() throws Exception {
        // 准备一个 spring.factories（含多行续行），内容仅自动配置
        Path tempDir = Files.createTempDirectory("guardian-factories");
        Path factories = tempDir.resolve("META-INF/spring.factories");
        Files.createDirectories(factories.getParent());
        Files.writeString(factories,
                "org.springframework.boot.autoconfigure.EnableAutoConfiguration=\\\n"
                        + "com.example.MyAutoConfig,\\\n"
                        + "com.example.OtherAutoConfig\n");

        // 作为纯文本解析（.factories 非 .properties 扩展名）
        List<SourceFile> sources = new PlainTextParser().parse(List.of(factories), tempDir,
                new InMemoryExecutionContext())
                .map(s -> (SourceFile) s)
                .toList();

        ExecutionContext ctx = new InMemoryExecutionContext();
        RecipeRun run = new SpringFactoriesMigration().run(new InMemoryLargeSourceSet(sources), ctx);
        List<Result> results = run.getChangeset().getAllResults();

        // 1. 生成新的 AutoConfiguration.imports 文件
        Result importsResult = results.stream()
                .filter(r -> r.getAfter() != null
                        && r.getAfter().getSourcePath().toString().endsWith(
                        SpringFactoriesMigration.AUTO_CONFIG_IMPORTS_FILE))
                .findFirst().orElse(null);
        assertNotNull(importsResult, "应生成 AutoConfiguration.imports 文件");
        String importsContent = importsResult.getAfter().printAll();
        assertTrue(importsContent.contains("com.example.MyAutoConfig"), "应包含第一个自动配置类");
        assertTrue(importsContent.contains("com.example.OtherAutoConfig"), "应包含第二个自动配置类");

        // 2. 旧 spring.factories 因仅含自动配置键被删除（getAfter() == null）
        Result deleted = results.stream()
                .filter(r -> r.getBefore() != null
                        && r.getBefore().getSourcePath().toString().endsWith("spring.factories")
                        && r.getAfter() == null)
                .findFirst().orElse(null);
        assertNotNull(deleted, "仅含自动配置的旧 spring.factories 应被删除");
    }

    @Test
    void keepsOtherKeysWhenRemovingAutoConfig() throws Exception {
        Path tempDir = Files.createTempDirectory("guardian-factories");
        Path factories = tempDir.resolve("META-INF/spring.factories");
        Files.createDirectories(factories.getParent());
        Files.writeString(factories,
                "# license header\n"
                        + "org.springframework.boot.autoconfigure.EnableAutoConfiguration=com.example.Foo\n"
                        + "org.springframework.boot.autoconfigure.ExcludeAutoConfiguration=com.example.Bar\n");

        List<SourceFile> sources = new PlainTextParser().parse(List.of(factories), tempDir,
                new InMemoryExecutionContext())
                .map(s -> (SourceFile) s)
                .toList();

        ExecutionContext ctx = new InMemoryExecutionContext();
        RecipeRun run = new SpringFactoriesMigration().run(new InMemoryLargeSourceSet(sources), ctx);
        List<Result> results = run.getChangeset().getAllResults();

        // 生成 imports 文件
        boolean hasImports = results.stream()
                .anyMatch(r -> r.getAfter() != null
                        && r.getAfter().getSourcePath().toString().endsWith(
                        SpringFactoriesMigration.AUTO_CONFIG_IMPORTS_FILE));
        assertTrue(hasImports, "应生成 AutoConfiguration.imports 文件");

        // 旧文件保留其它键，只移除 EnableAutoConfiguration
        Result kept = results.stream()
                .filter(r -> r.getBefore() != null
                        && r.getBefore().getSourcePath().toString().endsWith("spring.factories"))
                .findFirst().orElse(null);
        assertNotNull(kept, "含其它键的 spring.factories 应保留");
        assertNotNull(kept.getAfter(), "含其它键的 spring.factories 不应被删除");
        String afterText = kept.getAfter().printAll();
        assertTrue(afterText.contains("ExcludeAutoConfiguration"), "应保留其它键");
        assertTrue(!afterText.contains("EnableAutoConfiguration"), "应移除 EnableAutoConfiguration 键");
    }

    @Test
    void noOpWhenNoFactoriesFile() {
        // 没有 spring.factories 时不生成任何文件
        PlainText unrelated = PlainText.builder()
                .id(UUID.randomUUID())
                .sourcePath(Path.of("application.properties"))
                .text("server.port=8080\n")
                .build();

        ExecutionContext ctx = new InMemoryExecutionContext();
        RecipeRun run = new SpringFactoriesMigration()
                .run(new InMemoryLargeSourceSet(List.of(unrelated)), ctx);
        List<Result> results = run.getChangeset().getAllResults();

        boolean anyChange = results.stream()
                .anyMatch(r -> r.getAfter() == null
                        || (r.getBefore() != null && r.getAfter() != null
                        && !r.getBefore().equals(r.getAfter())));
        assertEquals(false, anyChange, "无 spring.factories 时不应产生任何变更");
    }
}

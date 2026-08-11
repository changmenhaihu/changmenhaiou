package com.guardian.rewrite;

import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.RecipeRun;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.config.Environment;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * OpenRewrite 封装：扫描目标项目的 Java 源文件，应用官方 Jakarta 迁移配方。
 * 基于 AST Visitor 模式安全重构，只修改匹配节点，保留注释与格式。
 * 默认 dry-run（仅收集 Diff），只有 apply=true 才将变更写回磁盘。
 */
public class JakartaMigrationService {

    private static final Logger log = LoggerFactory.getLogger(JakartaMigrationService.class);

    /**
     * OpenRewrite 官方 Jakarta 迁移配方完整名称。
     * 该配方以声明式 YAML 定义（rewrite-migrate-java 的 META-INF/rewrite/jakarta-ee-9.yml），
     * 需通过 Environment 从运行时 classpath 扫描后按名称激活。
     */
    public static final String JAVAX_TO_JAKARTA_RECIPE =
            "org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta";

    /**
     * 对目标项目执行 javax → jakarta 迁移。
     *
     * @param projectDir 目标 Spring Boot 项目根目录
     * @param apply      是否真正写入磁盘（false 仅 dry-run 输出 diff 预览）
     * @return 变更文件的结果列表（未变更文件不包含在内）
     */
    public List<SourceFileResult> execute(Path projectDir, boolean apply) throws IOException {
        Path projectRoot = projectDir.toAbsolutePath().normalize();
        List<Path> javaFiles = findJavaFiles(projectRoot);
        if (javaFiles.isEmpty()) {
            log.warn("在 {} 下未找到任何 Java 源文件", projectDir.toAbsolutePath());
            return List.of();
        }
        log.info("扫描到 {} 个 Java 文件", javaFiles.size());

        // 内存执行上下文：运行期异常直接抛出，便于定位
        ExecutionContext ctx = new InMemoryExecutionContext(t -> {
            if (t instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(t);
        });

        // 1. 从运行时 classpath 扫描配方描述符，激活官方 Jakarta 迁移配方
        Recipe recipe = Environment.builder()
                .scanRuntimeClasspath()
                .build()
                .activateRecipes(JAVAX_TO_JAKARTA_RECIPE);
        log.info("应用配方: {}", recipe.getDisplayName());

        // 2. 解析目标项目源文件为 AST（CompilationUnit）
        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        List<SourceFile> sourceFiles = javaParser.parse(javaFiles, projectRoot, ctx)
                .collect(Collectors.toList());
        log.info("解析完成，共 {} 个编译单元", sourceFiles.size());

        // 3. 执行配方
        RecipeRun run = recipe.run(new InMemoryLargeSourceSet(sourceFiles), ctx);
        List<Result> results = run.getChangeset().getAllResults();

        // 4. 收集结果：仅保留实际变更的文件；apply=true 时写回磁盘
        //    getSourcePath() 返回相对项目根目录的路径，写盘前需解析为绝对路径
        List<SourceFileResult> outputs = new ArrayList<>();
        for (Result result : results) {
            if (result.getAfter() == null) {
                continue; // 该文件无实际变更
            }
            Path sourcePath = result.getBefore().getSourcePath();
            String diff = result.diff();
            if (apply) {
                Path writePath = sourcePath.isAbsolute() ? sourcePath : projectRoot.resolve(sourcePath);
                Files.writeString(writePath, result.getAfter().printAll(), StandardCharsets.UTF_8);
                log.info("已写入磁盘: {}", writePath);
            }
            outputs.add(new SourceFileResult(sourcePath, diff, true));
        }
        log.info("迁移完成，受影响文件 {} 个（dry-run={}）", outputs.size(), !apply);
        return outputs;
    }

    /** 递归收集 Java 源文件，跳过 target / .git 等目录 */
    private List<Path> findJavaFiles(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !isExcluded(p))
                    .collect(Collectors.toList());
        }
    }

    /** 判断路径是否包含应跳过的目录（target 构建输出、.git 等） */
    private boolean isExcluded(Path path) {
        for (Path part : path) {
            String name = part.toString();
            if (name.equals("target") || name.equals(".git")) {
                return true;
            }
        }
        return false;
    }
}

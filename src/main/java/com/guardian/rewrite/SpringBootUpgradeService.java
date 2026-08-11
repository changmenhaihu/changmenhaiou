package com.guardian.rewrite;

import com.guardian.rewrite.recipes.SpringFactoriesMigration;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.RecipeRun;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.config.CompositeRecipe;
import org.openrewrite.config.Environment;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;
import org.openrewrite.properties.PropertiesParser;
import org.openrewrite.text.PlainTextParser;
import org.openrewrite.yaml.YamlParser;
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
 * Spring Boot 完整升级服务：把 Spring Boot 2.7 → 3.x 需要的多个 OpenRewrite 官方配方
 * 与自定义配方（{@link SpringFactoriesMigration}）打包成一个组合 Recipe 一键执行。
 * <p>
 * 组合顺序（与官方推荐升级路径一致）：
 * <ol>
 *   <li>{@code JavaxMigrationToJakarta}（javax → jakarta）</li>
 *   <li>{@code UpgradeSpringSecurity_6_0}（Spring Security 5 → 6）</li>
 *   <li>{@code JUnit4to5Migration}（JUnit 4 → 5）</li>
 *   <li>{@code SpringBootProperties_3_0}（Boot 2.7 → 3.0 配置项迁移）</li>
 *   <li>{@link SpringFactoriesMigration}（自定义：spring.factories → AutoConfiguration.imports）</li>
 * </ol>
 * 与 {@link JakartaMigrationService} 相同，默认 dry-run，仅 {@code apply=true} 时写盘。
 */
public class SpringBootUpgradeService {

    private static final Logger log = LoggerFactory.getLogger(SpringBootUpgradeService.class);

    /** 官方 Jakarta 迁移配方完整名称 */
    public static final String JAVAX_TO_JAKARTA_RECIPE =
            "org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta";
    /** Spring Security 5 → 6 迁移配方完整名称 */
    public static final String UPGRADE_SPRING_SECURITY_6_0_RECIPE =
            "org.openrewrite.java.spring.security6.UpgradeSpringSecurity_6_0";
    /** JUnit 4 → 5 迁移配方完整名称 */
    public static final String JUNIT4TO5_MIGRATION_RECIPE =
            "org.openrewrite.java.testing.junit5.JUnit4to5Migration";
    /** Spring Boot 2.7 → 3.0 配置项迁移配方完整名称 */
    public static final String SPRING_BOOT_PROPERTIES_3_0_RECIPE =
            "org.openrewrite.java.spring.boot3.SpringBootProperties_3_0";

    /**
     * 对目标项目执行 Spring Boot 2.7 → 3.x 完整升级。
     *
     * @param projectDir 目标 Spring Boot 项目根目录
     * @param apply      是否真正写入磁盘（false 仅 dry-run 输出 diff 预览）
     * @return 变更文件的结果列表（未变更文件不包含在内）
     */
    public List<SourceFileResult> execute(Path projectDir, boolean apply) throws IOException {
        Path projectRoot = projectDir.toAbsolutePath().normalize();

        // 内存执行上下文：运行期异常直接抛出，便于定位
        ExecutionContext ctx = new InMemoryExecutionContext(t -> {
            if (t instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(t);
        });

        // 1. 组装多类型源文件集合：Java / properties / yaml / spring.factories
        List<SourceFile> sourceFiles = parseProjectFiles(projectRoot, ctx);
        if (sourceFiles.isEmpty()) {
            log.warn("在 {} 下未找到任何可解析的源文件", projectDir.toAbsolutePath());
            return List.of();
        }
        log.info("共解析 {} 个源文件（Java/properties/yaml/spring.factories）", sourceFiles.size());

        // 2. 从运行时 classpath 扫描配方描述符，激活官方配方
        Recipe officialRecipes = Environment.builder()
                .scanRuntimeClasspath()
                .build()
                .activateRecipes(
                        JAVAX_TO_JAKARTA_RECIPE,
                        UPGRADE_SPRING_SECURITY_6_0_RECIPE,
                        JUNIT4TO5_MIGRATION_RECIPE,
                        SPRING_BOOT_PROPERTIES_3_0_RECIPE
                );

        // 3. 组合配方：官方配方 + 自定义 spring.factories 迁移
        Recipe composite = new CompositeRecipe(List.of(officialRecipes, new SpringFactoriesMigration()));
        log.info("执行 Spring Boot 完整升级，组合配方: {} 个步骤", composite.getRecipeList().size());

        // 4. 执行组合配方
        RecipeRun run = composite.run(new InMemoryLargeSourceSet(sourceFiles), ctx);
        List<Result> results = run.getChangeset().getAllResults();

        // 5. 收集结果并（按需）写盘
        List<SourceFileResult> outputs = new ArrayList<>();
        for (Result result : results) {
            SourceFile before = result.getBefore();
            SourceFile after = result.getAfter();

            if (before == null && after == null) {
                continue; // 无实际变更
            }

            String diff = result.diff();
            if (apply) {
                applyResult(projectRoot, before, after);
            }
            // 新生成文件取 after 路径；其余取 before 路径
            Path displayPath = (before == null) ? after.getSourcePath() : before.getSourcePath();
            outputs.add(new SourceFileResult(displayPath, diff, true));
        }
        log.info("升级完成，受影响文件 {} 个（dry-run={}）", outputs.size(), !apply);
        return outputs;
    }

    /** 将单个 RecipeRun 结果写入/删除磁盘 */
    private void applyResult(Path projectRoot, SourceFile before, SourceFile after) throws IOException {
        if (after == null) {
            // 文件被删除
            Path deletePath = resolve(projectRoot, before.getSourcePath());
            Files.deleteIfExists(deletePath);
            log.info("已删除文件: {}", deletePath);
            return;
        }
        Path writePath = resolve(projectRoot, after.getSourcePath());
        Files.createDirectories(writePath.getParent());
        Files.writeString(writePath, after.printAll(), StandardCharsets.UTF_8);
        log.info("已写入磁盘: {}", writePath);
    }

    /** 将相对路径解析为项目根下的绝对路径 */
    private Path resolve(Path projectRoot, Path sourcePath) {
        return sourcePath.isAbsolute() ? sourcePath : projectRoot.resolve(sourcePath);
    }

    /** 扫描并解析目标项目的多类型源文件 */
    private List<SourceFile> parseProjectFiles(Path projectRoot, ExecutionContext ctx) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        List<Path> propertiesFiles = new ArrayList<>();
        List<Path> yamlFiles = new ArrayList<>();
        List<Path> springFactoriesFiles = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(projectRoot)) {
            stream.filter(Files::isRegularFile)
                    .filter(this::isNotExcluded)
                    .forEach(path -> {
                        String name = path.getFileName().toString().toLowerCase();
                        if (name.endsWith(".java")) {
                            javaFiles.add(path);
                        } else if (name.endsWith(".properties")) {
                            propertiesFiles.add(path);
                        } else if (name.endsWith(".yml") || name.endsWith(".yaml")) {
                            yamlFiles.add(path);
                        } else if (SpringFactoriesMigration.SPRING_FACTORIES_FILE.equalsIgnoreCase(name)) {
                            springFactoriesFiles.add(path);
                        }
                    });
        }

        List<SourceFile> sources = new ArrayList<>();
        if (!javaFiles.isEmpty()) {
            JavaParser.fromJavaVersion().build().parse(javaFiles, projectRoot, ctx).forEach(sources::add);
        }
        if (!propertiesFiles.isEmpty()) {
            PropertiesParser.builder().build().parse(propertiesFiles, projectRoot, ctx).forEach(sources::add);
        }
        if (!yamlFiles.isEmpty()) {
            YamlParser.builder().build().parse(yamlFiles, projectRoot, ctx).forEach(sources::add);
        }
        // spring.factories 是 Properties 格式但扩展名非 .properties，需按纯文本解析
        if (!springFactoriesFiles.isEmpty()) {
            PlainTextParser.builder().build().parse(springFactoriesFiles, projectRoot, ctx).forEach(sources::add);
        }
        return sources.stream().collect(Collectors.toList());
    }

    /** 是否应跳过（target 构建输出、.git 等） */
    private boolean isNotExcluded(Path path) {
        for (Path part : path) {
            String name = part.toString();
            if (name.equals("target") || name.equals(".git") || name.equals("node_modules")) {
                return false;
            }
        }
        return true;
    }
}

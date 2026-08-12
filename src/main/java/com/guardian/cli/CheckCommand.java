package com.guardian.cli;

import ch.qos.logback.classic.Level;
import com.guardian.ai.Intent;
import com.guardian.ai.IntentParser;
import com.guardian.config.GuardianConfig;
import com.guardian.diff.DiffPrinter;
import com.guardian.diff.HealthReportPrinter;
import com.guardian.diff.InspectionPrinter;
import com.guardian.rewrite.JakartaMigrationService;
import com.guardian.rewrite.SpringBootUpgradeService;
import com.guardian.rewrite.recipes.CodeHealthCompositeRecipe;
import com.guardian.rewrite.recipes.SystemOutInspection;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;
import org.openrewrite.properties.PropertiesParser;
import org.openrewrite.yaml.YamlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/**
 * guardian check 子命令：扫描目标 Spring Boot 项目并输出修改建议。
 * 默认 dry-run（不修改任何文件），必须加 --apply 才真正写入磁盘。
 */
@Command(
        name = "check",
        description = "扫描目标 Spring Boot 项目并输出修改建议（默认 dry-run）",
        mixinStandardHelpOptions = true
)
public class CheckCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(CheckCommand.class);

    @Option(names = "--project", required = true, description = "目标 Spring Boot 项目路径")
    private Path project;

    @Option(names = "--action", description = "显式动作：javax-to-jakarta / spring-boot-upgrade / code-style / code-health")
    private String action;

    @Option(names = "--apply", defaultValue = "false", description = "真正写入磁盘（默认仅 dry-run 预览）")
    private boolean apply;

    @Option(names = "--instruction", description = "自然语言指令（触发 AI 意图解析，可选）")
    private String instruction;

    @Option(names = "--verbose", defaultValue = "false", description = "输出 DEBUG 级别日志")
    private boolean verbose;

    @Override
    public Integer call() {
        if (verbose) {
            setRootLogLevel(Level.DEBUG);
        }
        log.debug("project={}, action={}, apply={}, instruction={}, verbose={}",
                project, action, apply, instruction, verbose);

        // 校验目标项目路径
        if (project == null || !project.toFile().isDirectory()) {
            System.err.println("✖ 项目路径不存在或不是目录: " + project);
            return 1;
        }

        try {
            // 1. 解析意图：--instruction 触发 AI，否则走无 AI 模式
            List<Intent> intents = resolveIntents();

            // 2. 根据意图调度 OpenRewrite 配方
            boolean springBootUpgrade = intents.stream()
                    .anyMatch(i -> Intent.SPRING_BOOT_UPGRADE.equals(i.name()));
            boolean jakartaMigration = intents.stream()
                    .anyMatch(i -> Intent.JAKARTA_MIGRATION.equals(i.name()));
            boolean codeStyle = intents.stream()
                    .anyMatch(i -> Intent.CODE_STYLE.equals(i.name()));
            boolean codeHealth = intents.stream()
                    .anyMatch(i -> Intent.CODE_HEALTH.equals(i.name()));

            if (springBootUpgrade) {
                log.info("执行 Spring Boot 2.7 → 3.x 完整升级（组合配方）...");
                SpringBootUpgradeService service = new SpringBootUpgradeService();
                var results = service.execute(project, apply);
                DiffPrinter.print(results, apply);
            } else if (jakartaMigration) {
                log.info("执行 javax → jakarta 迁移...");
                JakartaMigrationService service = new JakartaMigrationService();
                var results = service.execute(project, apply);
                DiffPrinter.print(results, apply);
            } else if (codeHealth) {
                log.info("执行代码健康检查（代码规范 + 架构腐化检测）...");
                var recipe = new CodeHealthCompositeRecipe();
                List<Result> results = executeRecipe(project, recipe, false);
                HealthReportPrinter.print(results);
            } else if (codeStyle) {
                log.info("执行代码规范检测...");
                var recipe = new SystemOutInspection();
                List<Result> results = executeRecipe(project, recipe, false);
                InspectionPrinter.print(results);
            } else {
                // 兼容旧行为：无法识别的意图回退到默认 Jakarta 迁移
                log.warn("未能识别可执行意图（{}），回退到默认 Jakarta 迁移", intents);
                JakartaMigrationService service = new JakartaMigrationService();
                var results = service.execute(project, apply);
                DiffPrinter.print(results, apply);
            }
            return 0;
        } catch (Exception e) {
            if (verbose) {
                log.error("执行失败", e);
            } else {
                System.err.println("✖ 执行失败: " + e.getMessage());
            }
            return 1;
        }
    }

    /**
     * 通用 Recipe 执行方法：扫描目标项目的 Java / properties / yaml 源文件，
     * 应用 Recipe（含检测类配方），返回全部结果。检测类 Recipe 默认不写盘。
     */
    private List<Result> executeRecipe(Path projectDir, Recipe recipe, boolean apply) throws IOException {
        List<SourceFile> sourceFiles = parseProjectSources(projectDir);
        if (sourceFiles.isEmpty()) {
            log.warn("目标项目中没有找到可解析的源文件");
            return List.of();
        }
        ExecutionContext ctx = new InMemoryExecutionContext();
        var run = recipe.run(new InMemoryLargeSourceSet(sourceFiles), ctx);
        return run.getChangeset().getAllResults();
    }

    /** 扫描并解析目标项目的 Java / properties / yaml 源文件（跳过 target / .git 等目录） */
    private List<SourceFile> parseProjectSources(Path projectDir) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        List<Path> propertiesFiles = new ArrayList<>();
        List<Path> yamlFiles = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(projectDir)) {
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
                        }
                    });
        }

        ExecutionContext ctx = new InMemoryExecutionContext();
        List<SourceFile> sources = new ArrayList<>();
        if (!javaFiles.isEmpty()) {
            JavaParser.fromJavaVersion().build()
                    .parse(javaFiles, projectDir, ctx).forEach(sources::add);
        }
        if (!propertiesFiles.isEmpty()) {
            PropertiesParser.builder().build()
                    .parse(propertiesFiles, projectDir, ctx).forEach(sources::add);
        }
        if (!yamlFiles.isEmpty()) {
            YamlParser.builder().build()
                    .parse(yamlFiles, projectDir, ctx).forEach(sources::add);
        }
        return sources;
    }

    /** 是否应跳过（target 构建输出、.git、node_modules 等） */
    private boolean isNotExcluded(Path path) {
        for (Path part : path) {
            String name = part.toString();
            if (name.equals("target") || name.equals(".git") || name.equals("node_modules")
                    || name.equals("build") || name.equals(".idea") || name.equals(".mvn")) {
                return false;
            }
        }
        return true;
    }

    /** 解析要执行的意图列表：AI 模式失败时优雅降级到无 AI 默认迁移 */
    private List<Intent> resolveIntents() {
        if (instruction != null && !instruction.isBlank()) {
            return resolveFromAI();
        }
        if ("code-style".equalsIgnoreCase(action)) {
            return List.of(Intent.codeStyle());
        }
        if ("code-health".equalsIgnoreCase(action)) {
            return List.of(Intent.codeHealth());
        }
        // 无 AI 模式：--action 显式指定意图，缺省默认 Jakarta 迁移
        if (action != null && !action.isBlank()) {
            if ("spring-boot-upgrade".equalsIgnoreCase(action)) {
                return List.of(Intent.springBootUpgrade());
            }
            if ("javax-to-jakarta".equalsIgnoreCase(action)) {
                return List.of(Intent.jakartaMigration());
            }
            log.warn("未知动作 '{}'，当前支持 javax-to-jakarta / spring-boot-upgrade / code-style / code-health，将使用默认 Jakarta 迁移", action);
            return List.of(Intent.jakartaMigration());
        }
        return List.of(Intent.jakartaMigration());
    }

    /** 调用 DeepSeek 做意图解析；任何失败都降级为 JAKARTA_MIGRATION */
    private List<Intent> resolveFromAI() {
        GuardianConfig config = new GuardianConfig();
        String apiKey = config.getDeepSeekApiKey();
        if (apiKey == null) {
            log.warn("未配置环境变量 DEEPSEEK_API_KEY，AI 模式不可用，降级为无 AI 模式（Jakarta 迁移）");
            return List.of(Intent.jakartaMigration());
        }
        try {
            IntentParser parser = new IntentParser(apiKey, config.getDeepSeekModel());
            List<Intent> intents = parser.parse(instruction);
            if (intents.isEmpty()) {
                log.warn("AI 未返回任何意图，降级为 Jakarta 迁移");
                return List.of(Intent.jakartaMigration());
            }
            return intents;
        } catch (Exception e) {
            log.warn("AI 意图解析失败（{}），降级为无 AI 模式（Jakarta 迁移）", e.getMessage());
            return List.of(Intent.jakartaMigration());
        }
    }

    /** 设置 root logger 日志级别 */
    private static void setRootLogLevel(Level level) {
        ch.qos.logback.classic.Logger root =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(level);
    }
}

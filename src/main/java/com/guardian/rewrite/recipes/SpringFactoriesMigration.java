package com.guardian.rewrite.recipes;

import org.openrewrite.ExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextParser;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 自定义 Recipe：Spring Boot 2.7 的 {@code META-INF/spring.factories} → 3.x 的
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports} 迁移。
 * <p>
 * Spring Boot 2.7 用 {@code spring.factories} 的
 * {@code org.springframework.boot.autoconfigure.EnableAutoConfiguration} 键注册自动配置；
 * 3.x 改为纯类名列表文件 {@code AutoConfiguration.imports}（每行一个类名）。
 * <p>
 * 实现说明：需要"扫描旧文件 → 生成新文件 + 修改/删除旧文件"三阶段处理，
 * 因此继承 {@link ScanningRecipe}（官方同功能配方 MoveAutoConfigurationToImportsFile 亦如此）：
 * <ul>
 *   <li>{@link #getScanner}：扫描所有源文件，提取每个 spring.factories 的自动配置类名；</li>
 *   <li>{@link #generate}：为每个 spring.factories 生成对应的 AutoConfiguration.imports；</li>
 *   <li>{@link #getVisitor}：删除旧 spring.factories 中的自动配置条目（若仅剩该键则整文件删除）。</li>
 * </ul>
 */
public class SpringFactoriesMigration extends ScanningRecipe<SpringFactoriesMigration.Accumulator> {

    public static final String SPRING_FACTORIES_FILE = "spring.factories";
    public static final String ENABLE_AUTO_CONFIG_KEY =
            "org.springframework.boot.autoconfigure.EnableAutoConfiguration";
    public static final String AUTO_CONFIG_IMPORTS_FILE =
            "org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    /** 扫描阶段累积的结果：spring.factories 源路径 → 提取出的自动配置类名 */
    public static class Accumulator {
        final Map<Path, List<String>> autoConfigs = new LinkedHashMap<>();
    }

    @Override
    public String getDisplayName() {
        return "Spring Boot spring.factories → AutoConfiguration.imports";
    }

    @Override
    public String getDescription() {
        return "将 META-INF/spring.factories 中的 EnableAutoConfiguration 自动配置条目迁移到 "
                + "META-INF/spring/" + AUTO_CONFIG_IMPORTS_FILE + "，并移除/删除旧的 spring.factories 条目，"
                + "适配 Spring Boot 3.x 的自动配置注册机制。";
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    /** 第一阶段：扫描所有源文件，把每个 spring.factories 的自动配置类名记入 accumulator */
    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile sf && isSpringFactories(sf.getSourcePath())) {
                    List<String> classes = extractAutoConfigs(sf.printAll());
                    if (!classes.isEmpty()) {
                        acc.autoConfigs.put(sf.getSourcePath(), classes);
                    }
                }
                return tree;
            }
        };
    }

    /** 第二阶段：为每个 spring.factories 生成新的 AutoConfiguration.imports 文件 */
    @Override
    public Collection<? extends SourceFile> generate(Accumulator acc, Collection<SourceFile> sources,
                                                     ExecutionContext ctx) {
        List<SourceFile> generated = new ArrayList<>();
        for (Map.Entry<Path, List<String>> entry : acc.autoConfigs.entrySet()) {
            Path importsPath = importsPathFor(entry.getKey());
            String content = entry.getValue().stream()
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining("\n")) + "\n";
            PlainText importsFile = PlainText.builder()
                    .id(UUID.randomUUID())
                    .sourcePath(importsPath)
                    .text(content)
                    .build();
            generated.add(importsFile);
        }
        return generated;
    }

    /** 第三阶段：从旧 spring.factories 中移除自动配置条目；若仅剩该键则返回 null 表示整文件删除 */
    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile sf && isSpringFactories(sf.getSourcePath())) {
                    return removeAutoConfigEntry(sf);
                }
                return tree;
            }
        };
    }

    /** 是否为 spring.factories 文件（按文件名匹配，不限定具体目录层级） */
    private static boolean isSpringFactories(Path path) {
        return path != null && path.getFileName() != null
                && SPRING_FACTORIES_FILE.equalsIgnoreCase(path.getFileName().toString());
    }

    /** 从 spring.factories 文本中提取 EnableAutoConfiguration 键对应的类名列表 */
    private static List<String> extractAutoConfigs(String text) {
        Properties props = new Properties();
        try {
            props.load(new StringReader(text));
        } catch (IOException e) {
            return List.of();
        }
        String value = props.getProperty(ENABLE_AUTO_CONFIG_KEY);
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 生成 imports 文件路径：与 spring.factories 同级目录下的 spring/ 子目录。
     * 例：{@code src/main/resources/META-INF/spring.factories}
     *  → {@code src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
     */
    private static Path importsPathFor(Path springFactoriesPath) {
        Path parent = springFactoriesPath.getParent();
        return parent == null
                ? Path.of("spring", AUTO_CONFIG_IMPORTS_FILE)
                : parent.resolve("spring").resolve(AUTO_CONFIG_IMPORTS_FILE);
    }

    /**
     * 移除 spring.factories 中的 EnableAutoConfiguration 条目（含多行续行）。
     * 若移除后仅剩空白/注释，则返回 {@code null} 表示整文件删除；否则返回保留其余键的文件。
     */
    private static Tree removeAutoConfigEntry(SourceFile sf) {
        PlainText pt = PlainTextParser.convert(sf);
        String text = pt.getText();
        String[] lines = text.split("\n", -1);
        List<String> remaining = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().startsWith(ENABLE_AUTO_CONFIG_KEY)) {
                // 消费该键及其续行（以 \ 结尾的行属于同一个值）
                boolean continuation = line.endsWith("\\");
                while (continuation && i + 1 < lines.length) {
                    i++;
                    line = lines[i];
                    continuation = line.endsWith("\\");
                }
                continue;
            }
            remaining.add(line);
        }
        String result = String.join("\n", remaining).stripTrailing();
        if (result.isBlank() || isOnlyComments(result)) {
            return null;
        }
        return pt.withText(result + "\n");
    }

    /** 判断移除自动配置键后，剩余内容是否只剩注释（说明该文件原先只用于注册自动配置） */
    private static boolean isOnlyComments(String text) {
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("!")) {
                return false;
            }
        }
        return true;
    }
}

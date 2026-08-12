package com.guardian.rewrite.recipes;

import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.RecipeRun;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.properties.PropertiesParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 代码健康组合配方集成测试：构造一个包含多种规范/架构问题的示例工程，
 * 运行 CodeHealthCompositeRecipe 后断言各类问题均被标记出来。
 */
class CodeHealthCompositeRecipeTest {

    private static final String SAMPLE_JAVA = """
            package com.example;

            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RestController;

            import java.util.HashMap;
            import java.util.Map;

            @RestController
            @Slf4j
            public class UserControllerImpl {
                private static final Logger log = LoggerFactory.getLogger(UserControllerImpl.class);
                private static final String maxName = "x";
                private final UserRepository userRepository;

                public UserControllerImpl(UserRepository userRepository) {
                    this.userRepository = userRepository;
                }

                @GetMapping("/users")
                public Map<String, Object> getUsers() {
                    System.out.println("fetching users");
                    userRepository.save(1);
                    try {
                        return new HashMap<>();
                    } catch (Exception e) {
                        // 静默吞掉
                    }
                    return new HashMap<>();
                }

                @Transactional(readOnly = false)
                public String getUserById(String id) {
                    String sql = "SELECT * FROM users WHERE id=" + id;
                    try {
                        return sql;
                    } catch (Exception e) {
                        log.error(e.getMessage());
                        return null;
                    }
                }

                @Transactional
                private void do_thing() {
                    try {
                        userRepository.save(1);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            """;

    @Test
    void compositeRecipeDetectsStyleAndArchitectureIssues() throws Exception {
        Path tempDir = Files.createTempDirectory("guardian-health");
        Path javaFile = tempDir.resolve("src/main/java/com/example/UserControllerImpl.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, SAMPLE_JAVA);

        Path configFile = tempDir.resolve("src/main/resources/application.properties");
        Files.createDirectories(configFile.getParent());
        Files.writeString(configFile, "spring.datasource.password=123456\n");

        ExecutionContext ctx = new InMemoryExecutionContext();
        List<SourceFile> sources = new ArrayList<>();
        JavaParser.fromJavaVersion().build()
                .parse(List.of(javaFile), tempDir, ctx).forEach(sources::add);
        PropertiesParser.builder().build()
                .parse(List.of(configFile), tempDir, ctx).forEach(sources::add);

        RecipeRun run = new CodeHealthCompositeRecipe()
                .run(new InMemoryLargeSourceSet(sources), ctx);
        List<String> messages = new ArrayList<>();
        for (Result result : run.getChangeset().getAllResults()) {
            if (result.getAfter() != null) {
                collectMarkers(result.getAfter(), messages);
            }
        }

        // 代码规范检测
        assertContains(messages, "System.out/err");
        assertContains(messages, "空 catch 块");
        assertContains(messages, "printStackTrace");
        assertContains(messages, "未打印堆栈");
        assertContains(messages, "@Slf4j 与显式 Logger");
        assertContains(messages, "static final 常量 'maxName'");
        assertContains(messages, "以 Impl 结尾");
        assertContains(messages, "do_thing", "不符合小驼峰");
        assertContains(messages, "缺少 Javadoc");
        assertContains(messages, "硬编码敏感值");

        // 架构腐化检测
        assertContains(messages, "返回 Map<String, Object>");
        assertContains(messages, "GET 处理中调用了写操作方法 'save()'");
        assertContains(messages, "字符串拼接构造 SQL");
        assertContains(messages, "readOnly = true");
        assertContains(messages, "非 public 方法");
    }

    private static void collectMarkers(SourceFile sourceFile, List<String> messages) {
        messages.addAll(sourceFile.getMarkers().findAll(HealthFinding.class).stream()
                .map(HealthFinding::getDescription).toList());
        new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J preVisit(J tree, ExecutionContext p) {
                if (tree != null) {
                    for (HealthFinding marker : tree.getMarkers().findAll(HealthFinding.class)) {
                        if (marker.getDescription() != null) {
                            messages.add(marker.getDescription());
                        }
                    }
                }
                return tree;
            }
        }.visit(sourceFile, new InMemoryExecutionContext());
    }

    private static void assertContains(List<String> messages, String... fragments) {
        String joined = String.join("\n", messages);
        for (String fragment : fragments) {
            assertTrue(joined.contains(fragment),
                    "标记中应包含: " + fragment + "\n实际标记:\n" + joined);
        }
    }
}

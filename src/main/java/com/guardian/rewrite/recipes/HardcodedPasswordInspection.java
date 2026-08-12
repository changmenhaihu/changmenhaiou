package com.guardian.rewrite.recipes;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 配置文件硬编码密码检测：
 * 扫描 {@code .properties / .yml / .yaml} 配置文件中形如
 * {@code password=明文} 或 {@code secret: 明文} 的硬编码敏感值，建议改为环境变量或加密存储。
 */
public class HardcodedPasswordInspection extends Recipe {

    // 键名允许带点号前缀，如 spring.datasource.password / JWT.secret
    private static final Pattern SENSITIVE_KEY =
            Pattern.compile("(?i)^\\s*(?:[\\w.-]*\\.)?(password|passwd|pwd|secret|secretkey|token)\\s*[=:]\\s*(.*)$");

    @Override
    public String getDisplayName() {
        return "配置文件硬编码密码检测";
    }

    @Override
    public String getDescription() {
        return "检测 application.properties/yml 中硬编码的密码等敏感配置，建议使用环境变量或加密。";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile sf && isConfigFile(sf.getSourcePath())) {
                    List<String> leaked = findHardcodedSecrets(sf.printAll());
                    if (!leaked.isEmpty()) {
                        return HealthFinding.mark(sf, HealthFinding.STYLE,
                                "配置文件硬编码敏感值：" + String.join(", ", leaked));
                    }
                }
                return tree;
            }
        };
    }

    private static boolean isConfigFile(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".properties") || name.endsWith(".yml") || name.endsWith(".yaml");
    }

    /** 逐行扫描，收集被赋了明文敏感值的位置（跳过占位符 / 空值 / 注释） */
    private static List<String> findHardcodedSecrets(String text) {
        List<String> hits = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher m = SENSITIVE_KEY.matcher(line);
            if (m.matches()) {
                String value = m.group(2).trim();
                if (isRealSecret(value)) {
                    hits.add("第 " + (i + 1) + " 行");
                }
            }
        }
        return hits;
    }

    private static boolean isRealSecret(String value) {
        if (value.isEmpty() || value.startsWith("#")) {
            return false;
        }
        // 占位符 / 环境变量引用 / 加密注解 / 布尔与空值均不算硬编码
        if (value.startsWith("${") || value.startsWith("$")
                || value.startsWith("@") || value.startsWith("ENC(")
                || value.startsWith("'") || value.startsWith("\"")) {
            return false;
        }
        if (value.equalsIgnoreCase("null") || value.equalsIgnoreCase("true")
                || value.equalsIgnoreCase("false") || value.equalsIgnoreCase("none")) {
            return false;
        }
        return true;
    }
}

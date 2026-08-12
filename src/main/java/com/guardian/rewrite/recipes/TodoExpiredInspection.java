package com.guardian.rewrite.recipes;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TODO/FIXME 标记检测：
 * <ul>
 *   <li>存在带过期日期（格式 {@code TODO(2024-01-01)}）且日期已过的标记；</li>
 *   <li>单个文件中 TODO/FIXME 总数超过 5 个。</li>
 * </ul>
 */
public class TodoExpiredInspection extends Recipe {

    private static final int TODO_LIMIT = 5;

    private static final Pattern TODO_PATTERN = Pattern.compile("(?i)\\b(todo|fixme)\\b");
    private static final Pattern DATE_PATTERN =
            Pattern.compile("(?i)\\btodo\\s*\\(?\\s*(\\d{4}[-/. ]\\d{1,2}[-/. ]\\d{1,2})");

    @Override
    public String getDisplayName() {
        return "TODO/FIXME 标记检测";
    }

    @Override
    public String getDescription() {
        return "检测已过期的 TODO 日期标记，以及 TODO/FIXME 数量超过阈值（5 个）的代码。";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                J.CompilationUnit c = super.visitCompilationUnit(cu, ctx);
                String source = cu.printAll();
                int total = countMatches(TODO_PATTERN, source);
                String expired = findExpiredDate(source);
                if (expired != null) {
                    c = HealthFinding.mark(c, HealthFinding.STYLE, "存在已过期的 TODO(" + expired + ") 标记，应处理或更新日期");
                } else if (total > TODO_LIMIT) {
                    c = HealthFinding.mark(c, HealthFinding.STYLE, "TODO/FIXME 标记共 " + total + " 个，超过 " + TODO_LIMIT + " 个，建议尽快处理");
                }
                return c;
            }
        };
    }

    private static int countMatches(Pattern pattern, String text) {
        int count = 0;
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /** 返回第一个已过期 TODO 的日期字符串，无则返回 null */
    private static String findExpiredDate(String text) {
        Matcher matcher = DATE_PATTERN.matcher(text);
        while (matcher.find()) {
            String dateStr = matcher.group(1).trim();
            LocalDate date = parseDate(dateStr);
            if (date != null && date.isBefore(LocalDate.now())) {
                return dateStr;
            }
        }
        return null;
    }

    private static LocalDate parseDate(String dateStr) {
        // 统一分隔符为 - 后解析；支持 yyyy-MM-dd 与 yyyy-M-d
        String normalized = dateStr.replace('.', '-').replace('/', '-').replace(' ', '-');
        for (String pattern : new String[]{"yyyy-MM-dd", "yyyy-M-d"}) {
            try {
                return LocalDate.parse(normalized, DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException ignored) {
                // 尝试下一种格式
            }
        }
        return null;
    }
}

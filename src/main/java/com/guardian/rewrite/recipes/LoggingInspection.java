package com.guardian.rewrite.recipes;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;

import java.util.List;
import java.util.Set;

/**
 * 日志规范检测：
 * <ol>
 *   <li>{@code log.error(e.getMessage())} 只打印异常消息未打印堆栈，应改为 {@code log.error("msg", e)}；</li>
 *   <li>类同时使用了 Lombok 的 {@code @Slf4j} 与显式 {@code LoggerFactory.getLogger} 字段，应统一为一种方式。</li>
 * </ol>
 */
public class LoggingInspection extends Recipe {

    private static final Set<String> LOG_METHODS = Set.of("error", "warn", "info", "debug", "trace");
    private static final Set<String> LOGGER_NAMES = Set.of("log", "logger", "LOGGER");

    @Override
    public String getDisplayName() {
        return "日志规范检测";
    }

    @Override
    public String getDescription() {
        return "检测日志使用不规范：仅打印异常消息未带堆栈、@Slf4j 与显式 Logger 混用。";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (isLogWithoutStackTrace(m)) {
                    m = HealthFinding.mark(m, HealthFinding.STYLE, "log.error(e.getMessage()) 未打印堆栈，建议改为 log.error(\"msg\", e)");
                }
                return m;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                if (hasAnnotation(cd.getAllAnnotations(), "Slf4j") && hasExplicitLoggerField(cd)) {
                    cd = HealthFinding.mark(cd, HealthFinding.STYLE, "类同时使用 @Slf4j 与显式 LoggerFactory.getLogger，建议统一为一种日志方式");
                }
                return cd;
            }
        };
    }

    /** log.error(e.getMessage())：日志方法单参数且参数是 e.getMessage() */
    private static boolean isLogWithoutStackTrace(J.MethodInvocation m) {
        if (!LOG_METHODS.contains(m.getSimpleName())) {
            return false;
        }
        Expression select = m.getSelect();
        if (!(select instanceof J.Identifier id) || !LOGGER_NAMES.contains(id.getSimpleName())) {
            return false;
        }
        List<Expression> args = m.getArguments();
        if (args.size() != 1 || !(args.get(0) instanceof J.MethodInvocation arg)) {
            return false;
        }
        return "getMessage".equals(arg.getSimpleName());
    }

    /** 类声明中是否存在显式 Logger 字段（类型名为 Logger 或 org.slf4j.Logger） */
    private static boolean hasExplicitLoggerField(J.ClassDeclaration cd) {
        J.Block body = cd.getBody();
        if (body == null) {
            return false;
        }
        for (Statement statement : body.getStatements()) {
            if (statement instanceof J.VariableDeclarations vd && isLoggerType(vd.getTypeExpression())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLoggerType(TypeTree typeExpression) {
        if (typeExpression == null) {
            return false;
        }
        String name = typeExpression.toString();
        if ("Logger".equals(name) || name.endsWith(".Logger")) {
            return true;
        }
        if (typeExpression.getType() instanceof JavaType.FullyQualified fq) {
            return "org.slf4j.Logger".equals(fq.getFullyQualifiedName());
        }
        return false;
    }

    private static boolean hasAnnotation(List<J.Annotation> annotations, String simpleName) {
        return annotations.stream().anyMatch(a -> simpleName.equals(a.getSimpleName()));
    }
}

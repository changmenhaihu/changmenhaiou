package com.guardian.rewrite.recipes;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.Set;

/**
 * 检测代码中直接使用 System.out.println / System.err.println 等标准输出，
 * 应替换为 SLF4J 等日志框架。默认仅标记问题（HealthFinding），不做修改。
 */
public class SystemOutInspection extends Recipe {

    private static final Set<String> PRINT_METHODS = Set.of("print", "println", "printf");

    @Override
    public String getDisplayName() {
        return "检测 System.out/err 残留";
    }

    @Override
    public String getDescription() {
        return "生产代码中不应直接使用 System.out / System.err 输出，建议改用 SLF4J 等日志框架。";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (isSystemOutOrErr(m)) {
                    m = HealthFinding.mark(m, HealthFinding.STYLE, "发现 System.out/err 直接输出，建议改用 SLF4J 日志框架");
                }
                return m;
            }
        };
    }

    /**
     * 判断方法调用是否为 System.out 或 System.err 的 print/println/printf。
     * 形如 {@code System.out.println(...)} 会解析为 MethodInvocation，
     * 其 select 是 FieldAccess（target=System，name=out/err）。
     */
    private static boolean isSystemOutOrErr(J.MethodInvocation m) {
        if (!PRINT_METHODS.contains(m.getSimpleName())) {
            return false;
        }
        Expression select = m.getSelect();
        if (!(select instanceof J.FieldAccess fieldAccess)) {
            return false;
        }
        if (!(fieldAccess.getTarget() instanceof J.Identifier target)
                || !"System".equals(target.getSimpleName())) {
            return false;
        }
        String field = fieldAccess.getSimpleName();
        return "out".equals(field) || "err".equals(field);
    }
}

package com.guardian.rewrite.recipes;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.ArrayList;
import java.util.List;

/**
 * 仅调用 {@code e.printStackTrace()} 的 catch 块检测。
 * 应改用 SLF4J 等日志框架（{@code log.error("msg", e)}），
 * 避免堆栈打印到标准错误流且无法统一管理。
 */
public class PrintStackTraceInspection extends Recipe {

    @Override
    public String getDisplayName() {
        return "catch 块中 printStackTrace 检测";
    }

    @Override
    public String getDescription() {
        return "catch 块中仅调用 printStackTrace() 应改用日志框架记录，便于统一收集与告警。";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Try visitTry(J.Try tryCatch, ExecutionContext ctx) {
                J.Try t = super.visitTry(tryCatch, ctx);
                List<J.Try.Catch> catches = t.getCatches();
                List<J.Try.Catch> newCatches = null;
                for (int i = 0; i < catches.size(); i++) {
                    J.Try.Catch c = catches.get(i);
                    if (onlyPrintStackTrace(c)) {
                        if (newCatches == null) {
                            newCatches = new ArrayList<>(catches);
                        }
                        newCatches.set(i, HealthFinding.mark(c, HealthFinding.STYLE,
                                "catch 块中仅调用 e.printStackTrace()，建议改用 log.error(\"msg\", e)"));
                    }
                }
                return newCatches == null ? t : t.withCatches(newCatches);
            }
        };
    }

    private static boolean onlyPrintStackTrace(J.Try.Catch c) {
        J.Block body = c.getBody();
        if (body == null) {
            return false;
        }
        List<Statement> statements = body.getStatements();
        if (statements.size() != 1 || !(statements.get(0) instanceof J.MethodInvocation invocation)) {
            return false;
        }
        return "printStackTrace".equals(invocation.getSimpleName())
                && (invocation.getArguments().isEmpty() || invocation.getArguments().size() == 1
                && invocation.getArguments().get(0) instanceof J.Empty);
    }
}

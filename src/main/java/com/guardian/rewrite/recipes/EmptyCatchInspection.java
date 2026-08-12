package com.guardian.rewrite.recipes;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;

/**
 * 空 catch 块检测：{@code catch (Exception e) { }} 未记录任何日志，
 * 异常被静默吞掉，建议至少记录一条日志或向上抛出。
 */
public class EmptyCatchInspection extends Recipe {

    @Override
    public String getDisplayName() {
        return "空 catch 块检测";
    }

    @Override
    public String getDescription() {
        return "catch 块为空（无任何语句）时异常被静默吞掉，建议至少记录一条日志。";
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
                    if (isEmptyCatch(c)) {
                        if (newCatches == null) {
                            newCatches = new ArrayList<>(catches);
                        }
                        newCatches.set(i, HealthFinding.mark(c, HealthFinding.STYLE, "空 catch 块未记录任何日志，异常被静默吞掉"));
                    }
                }
                return newCatches == null ? t : t.withCatches(newCatches);
            }
        };
    }

    private static boolean isEmptyCatch(J.Try.Catch c) {
        J.Block body = c.getBody();
        return body == null || body.getStatements().isEmpty();
    }
}

package com.guardian.rewrite.recipes;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

/**
 * SQL 拼接检测：检测 {@code "SELECT ... " + 变量} 之类的 SQL 字符串拼接，
 * 存在 SQL 注入与可读性隐患，建议改用参数化查询 / MyBatis / JPA Criteria 等。
 */
public class SqlConcatenationInspection extends Recipe {

    @Override
    public String getDisplayName() {
        return "SQL 拼接检测";
    }

    @Override
    public String getDescription() {
        return "检测字符串拼接构造 SQL 的代码，建议改用参数化查询，避免 SQL 注入风险。";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Binary visitBinary(J.Binary binary, ExecutionContext ctx) {
                J.Binary b = super.visitBinary(binary, ctx);
                if (b.getOperator() == J.Binary.Type.Addition && isSqlConcat(b)) {
                    b = HealthFinding.mark(b, HealthFinding.ARCHITECTURE, "检测到字符串拼接构造 SQL，存在注入风险，建议使用参数化查询");
                }
                return b;
            }
        };
    }

    /** 一侧为包含 SQL 关键字的字符串字面量，另一侧为非常量表达式 */
    private static boolean isSqlConcat(J.Binary b) {
        return (isSqlLiteral(b.getLeft()) && !isLiteral(b.getRight()))
                || (isSqlLiteral(b.getRight()) && !isLiteral(b.getLeft()));
    }

    private static boolean isSqlLiteral(Expression expression) {
        if (!(expression instanceof J.Literal literal) || !(literal.getValue() instanceof String s)) {
            return false;
        }
        String upper = s.trim().toUpperCase();
        return upper.startsWith("SELECT") || upper.startsWith("INSERT") || upper.startsWith("UPDATE")
                || upper.startsWith("DELETE") || upper.startsWith("FROM") || upper.startsWith("WHERE")
                || upper.startsWith("JOIN");
    }

    private static boolean isLiteral(Expression expression) {
        return expression instanceof J.Literal;
    }
}

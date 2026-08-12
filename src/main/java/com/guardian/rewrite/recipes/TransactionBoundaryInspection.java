package com.guardian.rewrite.recipes;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.List;

/**
 * 事务边界检测：
 * <ul>
 *   <li>读操作（方法名以 get/find/list/query/count/exists/select 等开头）标记了
 *       {@code @Transactional} 却未声明 {@code readOnly = true}（浪费资源）；</li>
 *   <li>{@code @Transactional} 用在非 public 方法上（Spring AOP 默认不生效）。</li>
 * </ul>
 */
public class TransactionBoundaryInspection extends Recipe {

    @Override
    public String getDisplayName() {
        return "事务边界检测";
    }

    @Override
    public String getDescription() {
        return "检测 @Transactional 使用不当：读操作未标记 readOnly、注解用在非 public 方法。";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                J.Annotation tx = findAnnotation(md.getAllAnnotations(), "Transactional");
                if (tx == null) {
                    return md;
                }
                String name = md.getSimpleName();
                boolean isRead = isReadOperation(name);
                if (isRead && !isReadOnlyTrue(tx)) {
                    md = HealthFinding.mark(md, HealthFinding.ARCHITECTURE,
                            "读操作 '" + name + "' 使用 @Transactional 应声明 readOnly = true");
                }
                if (!isPublic(md)) {
                    md = HealthFinding.mark(md, HealthFinding.ARCHITECTURE,
                            "@Transactional 用于非 public 方法 '" + name + "'，Spring AOP 默认不会拦截");
                }
                return md;
            }
        };
    }

    private static boolean isReadOperation(String methodName) {
        String lower = methodName.toLowerCase();
        return lower.startsWith("get") || lower.startsWith("find") || lower.startsWith("list")
                || lower.startsWith("query") || lower.startsWith("count") || lower.startsWith("exists")
                || lower.startsWith("select") || lower.startsWith("has");
    }

    /** 判断 @Transactional 注解是否显式指定 readOnly = true */
    private static boolean isReadOnlyTrue(J.Annotation annotation) {
        for (Expression argument : annotation.getArguments()) {
            if (argument instanceof J.Assignment assignment
                    && assignment.getVariable() instanceof J.Identifier id
                    && "readOnly".equals(id.getSimpleName())) {
                String value = assignment.getAssignment().toString().trim();
                return "true".equalsIgnoreCase(value);
            }
        }
        return false;
    }

    private static boolean isPublic(J.MethodDeclaration md) {
        return md.getModifiers().stream()
                .anyMatch(m -> m.getType() == J.Modifier.Type.Public);
    }

    private static J.Annotation findAnnotation(List<J.Annotation> annotations, String simpleName) {
        for (J.Annotation annotation : annotations) {
            if (simpleName.equals(annotation.getSimpleName())) {
                return annotation;
            }
        }
        return null;
    }
}

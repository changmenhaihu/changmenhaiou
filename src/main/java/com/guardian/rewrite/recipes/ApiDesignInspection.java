package com.guardian.rewrite.recipes;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeTree;

import java.util.List;
import java.util.Set;

/**
 * REST API 设计缺陷检测：
 * <ul>
 *   <li>GET 处理逻辑中调用写操作（save/update/delete 等）方法；</li>
 *   <li>接口返回类型使用 {@code Map<String, Object>} 而非 DTO；</li>
 *   <li>方法参数过多（超过 5 个）。</li>
 * </ul>
 */
public class ApiDesignInspection extends Recipe {

    private static final int MAX_PARAMS = 5;

    private static final Set<String> WRITE_VERBS = Set.of(
            "save", "update", "delete", "insert", "remove", "create", "add", "put", "persist", "merge");

    private static final Set<String> MAPPING_ANNOTATIONS = Set.of(
            "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping", "RequestMapping");

    private static final Set<String> GET_ANNOTATIONS = Set.of("GetMapping", "RequestMapping");

    @Override
    public String getDisplayName() {
        return "REST API 设计缺陷检测";
    }

    @Override
    public String getDescription() {
        return "检测 GET 中调用写方法、接口返回 Map 而非 DTO、参数过多等 API 设计问题。";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                J.MethodDeclaration enclosing = getCursor().firstEnclosing(J.MethodDeclaration.class);
                if (enclosing != null && isGetHandler(enclosing) && WRITE_VERBS.contains(m.getSimpleName())) {
                    m = HealthFinding.mark(m, HealthFinding.ARCHITECTURE,
                            "GET 处理中调用了写操作方法 '" + m.getSimpleName() + "()'，应改用 @PostMapping/@PutMapping 等");
                }
                return m;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                if (isMappingHandler(md) && returnsMap(md)) {
                    md = HealthFinding.mark(md, HealthFinding.ARCHITECTURE,
                            "接口 '" + md.getSimpleName() + "' 返回 Map<String, Object>，建议定义 DTO");
                }
                int paramCount = (int) md.getParameters().stream()
                        .filter(p -> p instanceof J.VariableDeclarations).count();
                if (paramCount > MAX_PARAMS) {
                    md = HealthFinding.mark(md, HealthFinding.ARCHITECTURE,
                            "方法 '" + md.getSimpleName() + "' 参数过多（" + paramCount + " 个），建议抽取为请求对象");
                }
                return md;
            }
        };
    }

    private static boolean isGetHandler(J.MethodDeclaration md) {
        return md.getAllAnnotations().stream()
                .anyMatch(a -> GET_ANNOTATIONS.contains(a.getSimpleName()));
    }

    private static boolean isMappingHandler(J.MethodDeclaration md) {
        return md.getAllAnnotations().stream()
                .anyMatch(a -> MAPPING_ANNOTATIONS.contains(a.getSimpleName()));
    }

    private static boolean returnsMap(J.MethodDeclaration md) {
        TypeTree returnType = md.getReturnTypeExpression();
        if (returnType == null) {
            return false;
        }
        return returnType.toString().startsWith("Map");
    }
}

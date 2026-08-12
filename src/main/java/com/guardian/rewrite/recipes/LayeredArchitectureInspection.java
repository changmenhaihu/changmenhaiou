package com.guardian.rewrite.recipes;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;

import java.util.List;

/**
 * 分层架构违反检测：
 * <ul>
 *   <li>Controller 直接注入 DAO / Repository / Mapper（应经由 Service 层）；</li>
 *   <li>Service 方法返回 HttpServletRequest / HttpServletResponse 等 Web 对象。</li>
 * </ul>
 */
public class LayeredArchitectureInspection extends Recipe {

    private static final String[] DAO_SUFFIXES = {"Repository", "Dao", "DAO", "Mapper", "DaoImpl"};

    @Override
    public String getDisplayName() {
        return "分层架构违反检测";
    }

    @Override
    public String getDescription() {
        return "检测 Controller 直接注入数据访问对象、Service 返回 Web 对象等分层违反。";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                if (isController(cd)) {
                    String daoField = findDaoField(cd);
                    if (daoField != null) {
                        cd = HealthFinding.mark(cd, HealthFinding.ARCHITECTURE,
                                "Controller 直接注入数据访问对象 '" + daoField + "'，应经由 Service 层访问");
                    }
                }
                return cd;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                J.ClassDeclaration enclosing = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (enclosing != null && isService(enclosing) && returnsWebObject(md)) {
                    md = HealthFinding.mark(md, HealthFinding.ARCHITECTURE,
                            "Service 方法 '" + md.getSimpleName() + "' 返回 Web 对象，应保持分层边界");
                }
                return md;
            }
        };
    }

    private static boolean isController(J.ClassDeclaration cd) {
        return cd.getAllAnnotations().stream()
                .anyMatch(a -> "RestController".equals(a.getSimpleName()) || "Controller".equals(a.getSimpleName()));
    }

    private static boolean isService(J.ClassDeclaration cd) {
        return cd.getAllAnnotations().stream()
                .anyMatch(a -> "Service".equals(a.getSimpleName()));
    }

    /** 返回 Controller 中直接注入的数据访问对象字段名，无则返回 null */
    private static String findDaoField(J.ClassDeclaration cd) {
        J.Block body = cd.getBody();
        if (body == null) {
            return null;
        }
        for (Statement statement : body.getStatements()) {
            if (statement instanceof J.VariableDeclarations vd
                    && !vd.getVariables().isEmpty()
                    && isDaoType(vd.getTypeExpression())) {
                return vd.getVariables().get(0).getSimpleName();
            }
        }
        return null;
    }

    private static boolean isDaoType(TypeTree typeExpression) {
        if (typeExpression == null) {
            return false;
        }
        String name = typeExpression.toString();
        for (String suffix : DAO_SUFFIXES) {
            if (name.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean returnsWebObject(J.MethodDeclaration md) {
        TypeTree returnType = md.getReturnTypeExpression();
        if (returnType == null) {
            return false;
        }
        String name = returnType.toString();
        return name.contains("HttpServletRequest")
                || name.contains("HttpServletResponse")
                || name.contains("HttpSession");
    }
}

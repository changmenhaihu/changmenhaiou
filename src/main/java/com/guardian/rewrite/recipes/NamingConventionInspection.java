package com.guardian.rewrite.recipes;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

/**
 * 命名与格式规范检测：
 * <ul>
 *   <li>常量（static final）未使用全大写命名，或全大写命名的字段未声明为 static final；</li>
 *   <li>方法名不符合驼峰命名；</li>
 *   <li>包名包含下划线；</li>
 *   <li>类名以 Impl 结尾（坏味道，建议使用具象命名）。</li>
 * </ul>
 */
public class NamingConventionInspection extends Recipe {

    @Override
    public String getDisplayName() {
        return "命名规范检测";
    }

    @Override
    public String getDescription() {
        return "检测常量、方法名、包名、类名中的命名不规范问题。";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable,
                                                                    ExecutionContext ctx) {
                J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);
                // 仅检查字段（不在任何方法体内），排除局部变量
                if (getCursor().firstEnclosing(J.MethodDeclaration.class) == null
                        && !vd.getVariables().isEmpty()) {
                    String name = vd.getVariables().get(0).getSimpleName();
                    boolean isStaticFinal = hasModifier(vd, J.Modifier.Type.Static)
                            && hasModifier(vd, J.Modifier.Type.Final);
                    boolean allCaps = name.matches("[A-Z][A-Z0-9_]*");
                    if (allCaps && !isStaticFinal) {
                        vd = HealthFinding.mark(vd, HealthFinding.STYLE, "常量 '" + name + "' 应声明为 static final");
                    } else if (isStaticFinal && !allCaps) {
                        vd = HealthFinding.mark(vd, HealthFinding.STYLE, "static final 常量 '" + name + "' 应使用全大写命名");
                    }
                }
                return vd;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                String name = md.getSimpleName();
                J.ClassDeclaration enclosing = getCursor().firstEnclosing(J.ClassDeclaration.class);
                boolean isConstructor = enclosing != null && name.equals(enclosing.getSimpleName());
                if (!isConstructor && !name.matches("[a-z][a-zA-Z0-9]*")) {
                    md = HealthFinding.mark(md, HealthFinding.STYLE, "方法名 '" + name + "' 不符合小驼峰命名规范");
                }
                return md;
            }

            @Override
            public J.Package visitPackage(J.Package pkg, ExecutionContext ctx) {
                J.Package p = super.visitPackage(pkg, ctx);
                if (p.getExpression() != null && p.getExpression().toString().contains("_")) {
                    p = HealthFinding.mark(p, HealthFinding.STYLE, "包名 '" + p.getExpression() + "' 包含下划线，应避免");
                }
                return p;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                if (cd.getSimpleName().endsWith("Impl")) {
                    cd = HealthFinding.mark(cd, HealthFinding.STYLE, "类名 '" + cd.getSimpleName() + "' 以 Impl 结尾，建议使用更具体的命名");
                }
                return cd;
            }

            private boolean hasModifier(J.VariableDeclarations vd, J.Modifier.Type type) {
                for (J.Modifier m : vd.getModifiers()) {
                    if (m.getType() == type) {
                        return true;
                    }
                }
                return false;
            }
        };
    }
}

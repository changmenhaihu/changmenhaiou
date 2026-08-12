package com.guardian.rewrite.recipes;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Javadoc;

import java.util.List;
import java.util.Set;

/**
 * 公开 API 缺少 Javadoc 检测：
 * {@code @RestController / @Service / @Controller / @Repository} 标注的类或其 public 方法
 * 缺少 Javadoc 时给出提示。构造器与 {@code @Override} 方法不参与检测，避免噪音。
 */
public class MissingJavadocInspection extends Recipe {

    private static final Set<String> API_ANNOTATIONS = Set.of(
            "RestController", "Service", "Controller", "Repository", "Component");

    @Override
    public String getDisplayName() {
        return "公开 API 缺少 Javadoc 检测";
    }

    @Override
    public String getDescription() {
        return "标注 @RestController/@Service 等注解的类或其 public 方法缺少 Javadoc 时给出提示。";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                if (isApiClass(cd) && !hasJavadoc(cd)) {
                    cd = HealthFinding.mark(cd, HealthFinding.STYLE,
                            "公开 API 类 '" + cd.getSimpleName() + "' 缺少 Javadoc");
                }
                return cd;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                J.ClassDeclaration enclosing = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (enclosing == null || !isApiClass(enclosing)) {
                    return md;
                }
                String name = md.getSimpleName();
                boolean constructor = name.equals(enclosing.getSimpleName());
                boolean overridden = hasAnnotation(md.getAllAnnotations(), "Override");
                if (!constructor && !overridden && isPublic(md) && !hasJavadoc(md)) {
                    md = HealthFinding.mark(md, HealthFinding.STYLE,
                            "公开 API 方法 '" + name + "' 缺少 Javadoc");
                }
                return md;
            }
        };
    }

    private static boolean isApiClass(J.ClassDeclaration cd) {
        return cd.getAllAnnotations().stream()
                .anyMatch(a -> API_ANNOTATIONS.contains(a.getSimpleName()));
    }

    private static boolean hasJavadoc(J tree) {
        return tree.getComments().stream().anyMatch(c -> c instanceof Javadoc.DocComment);
    }

    private static boolean isPublic(J.MethodDeclaration md) {
        return md.getModifiers().stream()
                .anyMatch(m -> m.getType() == J.Modifier.Type.Public);
    }

    private static boolean hasAnnotation(List<J.Annotation> annotations, String simpleName) {
        return annotations.stream().anyMatch(a -> simpleName.equals(a.getSimpleName()));
    }
}

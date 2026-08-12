package com.guardian.diff;

import com.guardian.rewrite.recipes.HealthFinding;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;

/**
 * 代码健康报告打印器：遍历结果树收集全部 {@link HealthFinding} 标记，
 * 按类别（规范 / 架构）分组输出，并给出统计摘要。
 */
public final class HealthReportPrinter {

    private HealthReportPrinter() {}

    public static void print(List<Result> results) {
        List<Finding> findings = collectFindings(results);
        if (findings.isEmpty()) {
            System.out.println("✅ 未发现任何代码健康问题。");
            return;
        }

        String currentFile = null;
        int styleCount = 0;
        int archCount = 0;

        for (Finding finding : findings) {
            if (!finding.path.equals(currentFile)) {
                currentFile = finding.path;
                System.out.println("\n📄 " + currentFile);
            }
            System.out.println("   ⚠️  [" + finding.category + "] " + finding.description);
            if (HealthFinding.ARCHITECTURE.equals(finding.category)) {
                archCount++;
            } else {
                styleCount++;
            }
        }

        System.out.println();
        System.out.println("=================== 代码健康报告 ===================");
        System.out.println("  规范类问题: " + styleCount + " 处");
        System.out.println("  架构类问题: " + archCount + " 处");
        System.out.println("  合计: " + findings.size() + " 处");
        System.out.println("====================================================");
    }

    private static List<Finding> collectFindings(List<Result> results) {
        List<Finding> findings = new ArrayList<>();
        if (results == null) {
            return findings;
        }
        for (Result result : results) {
            SourceFile after = result.getAfter();
            if (after == null) {
                continue;
            }
            List<HealthFinding> markers = collectMarkers(after);
            for (HealthFinding marker : markers) {
                if (marker.getDescription() != null) {
                    findings.add(new Finding(after.getSourcePath().toString(),
                            marker.getCategory(), marker.getDescription()));
                }
            }
        }
        return findings;
    }

    /**
     * 遍历整棵树收集全部 HealthFinding 标记。
     * 根节点（如配置文件）直接读取其 markers，其余通过 Java 访问器遍历 J 子节点。
     */
    private static List<HealthFinding> collectMarkers(SourceFile sourceFile) {
        List<HealthFinding> markers = new ArrayList<>();
        markers.addAll(sourceFile.getMarkers().findAll(HealthFinding.class));
        new JavaVisitor<ExecutionContext>() {
            @Override
            public J preVisit(J tree, ExecutionContext p) {
                if (tree != null) {
                    markers.addAll(tree.getMarkers().findAll(HealthFinding.class));
                }
                return tree;
            }
        }.visit(sourceFile, new InMemoryExecutionContext());
        return markers;
    }

    private record Finding(String path, String category, String description) {}
}

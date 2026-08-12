package com.guardian.diff;

import com.guardian.rewrite.recipes.HealthFinding;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 打印检测类 Recipe（如代码规范检查）的结果。
 * 与 DiffPrinter 不同，这里只输出发现了哪些问题，不显示 Diff。
 * 标记可能附着在任意深层节点上，因此遍历整棵树收集 SearchResult / HealthFinding。
 */
public class InspectionPrinter {

    public static void print(List<Result> results) {
        int totalIssues = 0;
        if (results != null) {
            String currentFile = null;
            for (Result result : results) {
                SourceFile after = result.getAfter();
                if (after == null) {
                    continue;
                }
                List<String> lines = new ArrayList<>();
                for (SearchResult marker : collectMarkers(after, SearchResult.class)) {
                    lines.add(marker.getDescription());
                }
                for (HealthFinding finding : collectMarkers(after, HealthFinding.class)) {
                    lines.add("[" + finding.getCategory() + "] " + finding.getDescription());
                }
                if (lines.isEmpty()) {
                    continue;
                }
                if (!after.getSourcePath().toString().equals(currentFile)) {
                    currentFile = after.getSourcePath().toString();
                    System.out.println("\n📄 " + currentFile);
                }
                for (String line : lines) {
                    System.out.println("   ⚠️  " + line);
                    totalIssues++;
                }
            }
        }

        if (totalIssues > 0) {
            System.out.println("\n共发现 " + totalIssues + " 处问题。");
        } else {
            System.out.println("✅ 未发现任何问题。");
        }
    }

    /** 遍历整棵树收集指定类型的标记（根节点 markers + 所有 J 子节点） */
    private static <M extends org.openrewrite.marker.Marker> List<M> collectMarkers(SourceFile sourceFile,
                                                                                   Class<M> type) {
        List<M> markers = new ArrayList<>();
        markers.addAll(sourceFile.getMarkers().findAll(type));
        new JavaVisitor<ExecutionContext>() {
            @Override
            public J preVisit(J tree, ExecutionContext p) {
                if (tree != null) {
                    markers.addAll(tree.getMarkers().findAll(type));
                }
                return tree;
            }
        }.visit(sourceFile, new InMemoryExecutionContext());
        return markers;
    }
}

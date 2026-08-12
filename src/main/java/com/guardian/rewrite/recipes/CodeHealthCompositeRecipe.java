package com.guardian.rewrite.recipes;

import org.openrewrite.Recipe;
import org.openrewrite.config.CompositeRecipe;

import java.util.List;

/**
 * 代码健康检测组合配方：按顺序聚合全部代码规范检测与架构腐化检测 Recipe，
 * 通过一次 {@code guardian check --action code-health} 运行全部规则。
 * 所有子 Recipe 均为检测型（仅添加 SearchResult 标记，不修改代码）。
 */
public class CodeHealthCompositeRecipe extends CompositeRecipe {

    public CodeHealthCompositeRecipe() {
        super(List.of(
                // 代码规范检测
                new SystemOutInspection(),
                new EmptyCatchInspection(),
                new PrintStackTraceInspection(),
                new LoggingInspection(),
                new NamingConventionInspection(),
                new MissingJavadocInspection(),
                new TodoExpiredInspection(),
                new HardcodedPasswordInspection(),
                // 架构腐化检测
                new LayeredArchitectureInspection(),
                new TransactionBoundaryInspection(),
                new ApiDesignInspection(),
                new SqlConcatenationInspection()
        ));
    }
}

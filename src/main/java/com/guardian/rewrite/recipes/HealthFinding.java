package com.guardian.rewrite.recipes;

import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.marker.Marker;

import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * 代码健康检查标记：附着在 AST 节点上，报告一条代码规范或架构腐化问题。
 * <p>
 * 与 {@link org.openrewrite.marker.SearchResult} 不同，采用独立 Marker 类型，
 * 使同一个节点上的多条问题标记可以共存（SearchResult 在同一节点上仅保留首个标记），
 * 从而保证组合配方能完整报告同一位置的全部问题。
 */
public class HealthFinding implements Marker {

    /** 代码规范类问题 */
    public static final String STYLE = "规范";
    /** 架构腐化类问题 */
    public static final String ARCHITECTURE = "架构";

    private final UUID id;
    private final String category;
    private final String description;

    public HealthFinding(UUID id, String category, String description) {
        this.id = id;
        this.category = category;
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public HealthFinding withId(UUID id) {
        return new HealthFinding(id, category, description);
    }

    @Override
    public String print(Cursor cursor, UnaryOperator<String> commentWrapper, boolean verbose) {
        return "";
    }

    /** 在节点上附加一条健康问题标记 */
    public static <T extends Tree> T mark(T tree, String category, String description) {
        return tree.withMarkers(tree.getMarkers().add(new HealthFinding(UUID.randomUUID(), category, description)));
    }
}

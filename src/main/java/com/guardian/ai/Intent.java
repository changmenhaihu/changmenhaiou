package com.guardian.ai;

/**
 * 结构化意图：一个可执行的维护动作，由意图调度层据此分派到对应的 OpenRewrite 配方。
 *
 * @param name        意图名称，如 JAKARTA_MIGRATION / SPRING_BOOT_UPGRADE / CODE_STYLE
 * @param confidence  置信度 0.0 ~ 1.0
 * @param explanation 简短说明（便于调试与展示）
 */
public record Intent(String name, double confidence, String explanation) {

    /** 意图：javax → jakarta 迁移 */
    public static final String JAKARTA_MIGRATION = "JAKARTA_MIGRATION";

    /** 意图：Spring Boot 2.7 → 3.x 完整升级（组合配方） */
    public static final String SPRING_BOOT_UPGRADE = "SPRING_BOOT_UPGRADE";

    /** 意图：代码规范检测（如 System.out 残留等） */
    public static final String CODE_STYLE = "CODE_STYLE";

    /** 意图：代码健康检查（代码规范 + 架构腐化，聚合多个检测配方） */
    public static final String CODE_HEALTH = "CODE_HEALTH";

    /** 无 AI 模式下的默认意图（置信度 1.0） */
    public static Intent jakartaMigration() {
        return new Intent(JAKARTA_MIGRATION, 1.0, "默认 Jakarta 迁移（无 AI 模式）");
    }

    /** Spring Boot 完整升级意图（无 AI 模式下由 --action spring-boot-upgrade 触发） */
    public static Intent springBootUpgrade() {
        return new Intent(SPRING_BOOT_UPGRADE, 1.0, "Spring Boot 2.7 → 3.x 完整升级（组合配方）");
    }

    /** 代码规范检测意图（无 AI 模式下由 --action code-style 触发） */
    public static Intent codeStyle() {
        return new Intent(CODE_STYLE, 1.0, "代码规范检测（如 System.out 残留）");
    }

    /** 代码健康检查意图（无 AI 模式下由 --action code-health 触发） */
    public static Intent codeHealth() {
        return new Intent(CODE_HEALTH, 1.0, "代码健康检查（代码规范 + 架构腐化检测）");
    }
}
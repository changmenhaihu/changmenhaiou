package com.guardian.rewrite;

import java.nio.file.Path;

/**
 * 单个源文件的迁移结果：路径 + unified diff + 是否有实际变更。
 * 由各迁移服务（JakartaMigrationService / SpringBootUpgradeService）复用，
 * 供 CLI 与 DiffPrinter 统一展示。
 *
 * @param path    源文件相对项目根目录的路径
 * @param diff    该文件的 unified diff 文本
 * @param changed 是否发生了实际变更
 */
public record SourceFileResult(Path path, String diff, boolean changed) {
}

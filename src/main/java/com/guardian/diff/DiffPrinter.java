package com.guardian.diff;

import com.guardian.rewrite.SourceFileResult;

import java.util.List;

/**
 * 控制台 Diff 打印器：ANSI 颜色高亮（绿色=新增、红色=删除、青色=@@ 行、蓝色=文件头）。
 * 当 ANSI 不可用时（例如管道/重定向/旧 Windows 控制台），自动降级为纯文本。
 * <p>
 * 可通过系统属性 <b>-Dguardian.color=true</b> 强制启用，
 * 或 <b>-Dguardian.color=false</b> 强制禁用 ANSI 颜色。
 * 同时尊重 <b>NO_COLOR</b> 环境变量标准。
 */
public final class DiffPrinter {

    private static final boolean ANSI_ENABLED = ansiSupported();

    /**
     * 判断是否应启用 ANSI 转义码。
     * 优先级：
     * 1. JVM 属性 guardian.color（true/false）
     * 2. 环境变量 NO_COLOR（若存在则禁用）
     * 3. 检测已知支持 ANSI 的 IDE / 终端（TERM_PROGRAM / TERM / WT_SESSION 等）
     * 4. 回退到 System.console() 是否为交互式终端
     */
    private static boolean ansiSupported() {
        // 1. 用户显式控制
        String forceColor = System.getProperty("guardian.color");
        if (forceColor != null) {
            return Boolean.parseBoolean(forceColor);
        }

        // 2. 遵守 NO_COLOR 规范
        if (System.getenv("NO_COLOR") != null) {
            return false;
        }

        // 3. 检测已知支持 ANSI 的环境（即使在 IDE 中 System.console() 可能为 null）
        if (isAnsiTerminal()) {
            return true;
        }

        // 4. 否则，仅当标准输入是交互式终端时才启用（避免管道/重定向中出现乱码）
        return System.console() != null;
    }

    private static boolean isAnsiTerminal() {
        // Windows Terminal
        if (System.getenv("WT_SESSION") != null) {
            return true;
        }
        // VS Code / IntelliJ / Git Bash 等常会设置 TERM_PROGRAM
        String termProgram = System.getenv("TERM_PROGRAM");
        if (termProgram != null) {
            return true;
        }
        // JetBrains IDE 终端
        if ("JetBrains-JediTerm".equals(System.getenv("TERMINAL_EMULATOR"))) {
            return true;
        }
        // TERM 环境变量：xterm / screen / tmux / 包含 color 的终端
        String term = System.getenv("TERM");
        if (term != null && (term.contains("xterm") || term.contains("screen") ||
                             term.contains("tmux") || term.contains("color"))) {
            return true;
        }
        return false;
    }

    // ANSI 转义序列常量
    private static final String RESET  = "\u001b[0m";
    private static final String RED    = "\u001b[31m";
    private static final String GREEN  = "\u001b[32m";
    private static final String YELLOW = "\u001b[33m";
    private static final String BLUE   = "\u001b[34m";
    private static final String CYAN   = "\u001b[36m";
    private static final String BOLD   = "\u001b[1m";

    private DiffPrinter() {}

    /** 按 ANSI 是否启用，返回着色或纯文本 */
    private static String s(String ansiCode, String text) {
        return ANSI_ENABLED ? ansiCode + text + RESET : text;
    }

    /**
     * 打印迁移结果汇总与各文件 unified diff 预览。
     *
     * @param results 迁移结果列表
     * @param apply   是否已真正写入磁盘（影响末尾提示文案）
     */
    public static void print(List<SourceFileResult> results, boolean apply) {
        if (results == null || results.isEmpty()) {
            System.out.println();
            System.out.println("[OK] 未发现需要修改的文件");
            return;
        }

        System.out.println();
        System.out.println(s(BOLD, "[OK] 受影响的文件: " + results.size() + " 个"));
        System.out.println("==================================================");
        System.out.println();

        for (SourceFileResult result : results) {
            if (!result.changed()) {
                continue;
            }
            printFile(result);
            System.out.println();
        }

        System.out.println("==================================================");
        if (apply) {
            System.out.println(s(GREEN, "[OK] 修改已写入磁盘"));
        } else {
            System.out.println(s(YELLOW, "[!] 当前为 dry-run 模式，未修改任何文件。确认无误后请加 --apply 应用修改。"));
        }
        System.out.println();
    }

    private static void printFile(SourceFileResult result) {
        System.out.println(s(BLUE, "--- " + result.path()));
        System.out.println(s(CYAN, "+++ " + result.path() + " (修改后)"));
        System.out.println();
        printDiff(result.diff());
    }

    /**
     * 按行对 unified diff 染色：
     *   - 删除行 → 红色
     *   - 新增行 → 绿色
     *   - 位置信息行（@@） → 青色
     *   - 其余行 → 默认颜色（白色/无色）
     * 跳过 OpenRewrite 自带的 --- / +++ 文件头（我们已用横幅替代）。
     */
    private static void printDiff(String diff) {
        if (diff == null || diff.isEmpty()) {
            System.out.println("  (无差异)");
            return;
        }
        for (String line : diff.split("\n", -1)) {
            if (line.startsWith("---") || line.startsWith("+++")) {
                continue;
            } else if (line.startsWith("@@")) {
                System.out.println(s(CYAN, line));
            } else if (line.startsWith("+")) {
                System.out.println(s(GREEN, line));
            } else if (line.startsWith("-")) {
                System.out.println(s(RED, line));
            } else {
                // 上下文行：不添加颜色，即为白色（或终端默认前景色）
                System.out.println(line);
            }
        }
    }
}
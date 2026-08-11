package com.guardian;

import com.guardian.cli.CheckCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Spring Boot Guardian 入口：picocli CLI 应用。
 * AI 只做意图解析，所有代码修改由 OpenRewrite 确定性配方完成，默认 dry-run。
 */
@Command(
        name = "guardian",
        description = "Spring Boot 项目专属医生：AI 意图解析 + OpenRewrite 确定性代码迁移",
        mixinStandardHelpOptions = true,
        version = "1.0",
        subcommands = CheckCommand.class
)
public class GuardianApplication implements Runnable {

    @Override
    public void run() {
        // 未指定子命令时打印帮助信息
        new CommandLine(this).usage(System.out);
    }

    public static void main(String[] args) {
        configureConsole();

        int exitCode = new CommandLine(new GuardianApplication()).execute(args);
        System.exit(exitCode);
    }

    /**
     * 配置控制台输出编码，避免 Windows 控制台（默认代码页 936/GBK）出现中文乱码：
     * <ul>
     *   <li>附着于真实控制台（System.console() != null）：使用系统原生编码（中文系统为 GBK），
     *       与控制台解码代码页一致；</li>
     *   <li>输出被管道 / 重定向（System.console() == null）：强制 UTF-8，保证脚本解析与日志捕获正确。</li>
     * </ul>
     * 同时把编码名写入系统属性 {@code guardian.console.charset}，供 logback.xml 中的
     * {@code <charset>} 引用——logback 默认用 UTF-8 编码，会覆盖 System.out 的编码导致日志行乱码。
     */
    private static void configureConsole() {
        boolean consoleAttached = System.console() != null;
        Charset charset = consoleAttached ? nativeCharset() : StandardCharsets.UTF_8;

        System.setProperty("guardian.console.charset", charset.name());
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, charset));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, charset));
    }

    /** 系统原生编码：优先 native.encoding（JDK19+），回退 sun.jnu.encoding（所有版本） */
    private static Charset nativeCharset() {
        String name = System.getProperty("native.encoding");
        if (name == null || name.isBlank()) {
            name = System.getProperty("sun.jnu.encoding");
        }
        if (name != null && !name.isBlank()) {
            try {
                return Charset.forName(name);
            } catch (Exception ignored) {
                // 编码名无法识别时回退 UTF-8
            }
        }
        return StandardCharsets.UTF_8;
    }
}

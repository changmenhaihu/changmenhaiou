package com.guardian.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * 配置读取：环境变量优先级最高，其次可选 .guardian/config.yml 简单 key: value 文件。
 * 为便于单元测试，环境变量以 Map 形式注入（默认为 System.getenv()）。
 */
public class GuardianConfig {

    private static final Logger log = LoggerFactory.getLogger(GuardianConfig.class);

    private static final String DEFAULT_MODEL = "deepseek-chat";

    private final Map<String, String> env;
    private final Properties fileProperties = new Properties();

    public GuardianConfig() {
        this(System.getenv());
    }

    /** 测试用：注入自定义环境变量 Map */
    GuardianConfig(Map<String, String> env) {
        this.env = env;
        loadConfigFile();
    }

    /** DeepSeek API Key，来自环境变量 DEEPSEEK_API_KEY（必填项） */
    public String getDeepSeekApiKey() {
        String key = env.get("DEEPSEEK_API_KEY");
        return key == null || key.isBlank() ? null : key.trim();
    }

    /** DeepSeek 模型名：环境变量 DEEPSEEK_MODEL > 配置文件 deepseek.model > 默认 deepseek-*/
    public String getDeepSeekModel() {
        String envModel = env.get("DEEPSEEK_MODEL");
        if (envModel != null && !envModel.isBlank()) {
            return envModel.trim();
        }
        String fileModel = fileProperties.getProperty("deepseek.model");
        if (fileModel != null && !fileModel.isBlank()) {
            return fileModel.trim();
        }
        return DEFAULT_MODEL;
    }

    /** 读取当前工作目录下 .guardian/config.yml 的简单 key: value 配置（忽略注释与空行） */
    private void loadConfigFile() {
        Path configPath = Path.of(".guardian", "config.yml");
        if (!Files.exists(configPath)) {
            return;
        }
        try (var reader = Files.newBufferedReader(configPath)) {
            reader.lines()
                    .filter(line -> line != null && !line.isBlank())
                    .map(String::trim)
                    .filter(line -> !line.startsWith("#"))
                    .forEach(line -> {
                        int idx = line.indexOf(':');
                        if (idx > 0) {
                            String key = line.substring(0, idx).trim();
                            String value = line.substring(idx + 1).trim().replaceAll("[\"']", "");
                            fileProperties.setProperty(key, value);
                        }
                    });
            log.info("已读取配置文件: {}", configPath.toAbsolutePath());
        } catch (IOException e) {
            log.warn("读取 {} 失败: {}", configPath, e.getMessage());
        }
    }
}

# Spring Boot Guardian

Spring Boot 项目专属医生：一个垂直领域的代码维护 CLI 工具。它把「自然语言意图」解析与「确定性代码迁移」分离——

- **AI 只做意图解析**（自然语言 → 结构化 JSON），绝不直接生成或修改源码；
- **所有代码修改由确定性工具 OpenRewrite 完成**，基于 AST Visitor 模式安全重构，只修改匹配节点，保留注释与格式，保证**可审计、可回滚、可测试**；
- **默认 dry-run**：只输出 Diff 预览，必须加 `--apply` 才会真正写入磁盘。

> 本项目本身是普通 Maven 项目，**不是** Web 应用，不引入 Spring Boot 依赖。

## 前置要求

- JDK 17+（构建机已验证 JDK 21）
- Maven 3.8+

## 构建

```bash
mvn clean package -DskipTests
```

构建产物：

- `target/spring-boot-guardian-1.0.0-jar-with-dependencies.jar`（可执行 fat JAR，包含全部依赖）
- `target/spring-boot-guardian-1.0.0.jar`（精简 JAR）

## 运行帮助

```bash
java -jar target/spring-boot-guardian-1.0.0-jar-with-dependencies.jar --help
java -jar target/spring-boot-guardian-1.0.0-jar-with-dependencies.jar check --help
```

## 配置 DeepSeek API Key

AI 模式（`--instruction`）需要配置 API Key；无 AI 模式不需要任何配置。

- Linux / macOS / Git Bash：

  ```bash
  export DEEPSEEK_API_KEY=sk-xxxx
  export DEEPSEEK_MODEL=deepseek-chat   # 可选，默认 deepseek-chat
  ```

- Windows CMD：

  ```bat
  set DEEPSEEK_API_KEY=sk-xxxx
  set DEEPSEEK_MODEL=deepseek-chat
  ```

也可在项目目录下创建 `.guardian/config.yml`：

```yaml
deepseek.model: deepseek-chat
```

环境变量优先级高于配置文件。

## 使用示例

### 无 AI 模式（直接执行 Jakarta 迁移）

```bash
java -jar target/spring-boot-guardian-1.0.0-jar-with-dependencies.jar check \
  --project /path/to/spring-boot-app --action javax-to-jakarta
```

### 无 AI 模式（Spring Boot 2.7 → 3.x 完整升级）

组合执行多个官方配方 + 自定义配方：javax→jakarta、Spring Security 5→6、
JUnit 4→5、Boot 2.7→3.0 配置项迁移，以及 `spring.factories` → `AutoConfiguration.imports`。

```bash
java -jar target/spring-boot-guardian-1.0.0-jar-with-dependencies.jar check \
  --project /path/to/spring-boot-app --action spring-boot-upgrade
```

### AI 模式（自然语言意图解析）

```bash
java -jar target/spring-boot-guardian-1.0.0-jar-with-dependencies.jar check \
  --project /path/to/spring-boot-app --instruction "把项目升级到 Spring Boot 3，所有 javax 改成 jakarta"
```

> 若未配置 API Key 或 AI 调用失败，会自动**降级为无 AI 模式**（直接执行 Jakarta 迁移），不影响使用。
> AI 识别到“完整升级”类指令（如“把项目升级到 Spring Boot 3”）时会返回 `SPRING_BOOT_UPGRADE` 意图，
> 从而走完整升级流程。

### 应用修改（重要！）

```bash
java -jar target/spring-boot-guardian-1.0.0-jar-with-dependencies.jar check \
  --project /path/to/spring-boot-app --action spring-boot-upgrade --apply
```

> 默认是 **dry-run**，只预览 Diff、不修改任何文件。确认无误后再加 `--apply`。

## 命令行包装脚本

构建完成后，可直接使用项目根目录下的 `guardian`（Linux/macOS/Git Bash）或 `guardian.bat`（Windows），无需记忆长命令：

```bash
./guardian check --project /path/to/app --action javax-to-jakarta
./guardian check --project /path/to/app --action spring-boot-upgrade
./guardian check --project /path/to/app --instruction "升级到 Spring Boot 3" --apply
```

```bat
guardian check --project C:\dev\my-app --action javax-to-jakarta
guardian check --project C:\dev\my-app --action spring-boot-upgrade
guardian check --project C:\dev\my-app --instruction "把所有 javax 改成 jakarta" --apply
```

若希望全局使用，可将脚本所在目录加入系统 `PATH`，或在 `/usr/local/bin` 下创建符号链接。

## 命令选项

```
guardian check --project <路径> [--action javax-to-jakarta|spring-boot-upgrade] [--apply] [--instruction "指令"] [--verbose]
```

| 选项 | 说明 |
| --- | --- |
| `--project` | （必填）目标 Spring Boot 项目路径 |
| `--action` | 显式动作，支持 `javax-to-jakarta` 与 `spring-boot-upgrade`；缺省时默认执行 Jakarta 迁移 |
| `--apply` | 真正写入磁盘；缺省为 dry-run |
| `--instruction` | 自然语言指令，触发 AI 意图解析（可选） |
| `--verbose` | 输出 DEBUG 级别日志 |

## 如何准备测试项目

创建一个简单的 Spring Boot 2.x 项目（如包含 `spring-boot-starter-data-jpa` 依赖），在其中写一个含 `import javax.persistence.Entity;` 等的实体类，例如：

```java
package com.example;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class User {
    @Id
    private Long id;
    private String name;
}
```

然后运行：

```bash
./guardian check --project /path/to/spring-boot-app
```

即可预览 `javax.persistence` → `jakarta.persistence` 等迁移 diff。

## 项目结构

```
spring-boot-guardian/
├── pom.xml
├── .gitignore
├── guardian                      # Linux / macOS / Git Bash 包装脚本
├── guardian.bat                  # Windows 包装脚本
├── src/main/java/com/guardian/
│   ├── GuardianApplication.java  # picocli 入口，定义顶级命令
│   ├── cli/CheckCommand.java     # check 子命令（意图解析 → 配方调度 → Diff 输出）
│   ├── rewrite/JakartaMigrationService.java  # OpenRewrite 封装：AST 解析、配方执行、写盘
│   ├── rewrite/SpringBootUpgradeService.java # 完整升级服务：组合官方+自定义配方
│   ├── rewrite/SourceFileResult.java         # 迁移结果 record（两个服务共用）
│   ├── rewrite/recipes/SpringFactoriesMigration.java # 自定义配方：spring.factories → AutoConfiguration.imports
│   ├── ai/IntentParser.java      # DeepSeek 调用 + 严格 JSON 解析
│   ├── ai/Intent.java            # 结构化意图 record
│   ├── diff/DiffPrinter.java     # ANSI 彩色 Diff 打印
│   └── config/GuardianConfig.java# 环境变量 + .guardian/config.yml 配置
├── src/main/resources/
│   ├── logback.xml
│   └── prompts/intent-system-prompt.txt  # 意图解析 System Prompt（强制严格 JSON）
├── src/test/java/com/guardian/   # 单元测试
└── README.md
```

## 设计要点

- **双输入路径**：显式动作（`--action`）直接调度配方（无 AI）；自然语言（`--instruction`）经 DeepSeek 意图分类返回 JSON 后再调度配方。
- **意图调度层**：根据结构化意图分发到对应 OpenRewrite Recipe。支持 `JAKARTA_MIGRATION`（`org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta`）与 `SPRING_BOOT_UPGRADE`（组合配方）。
- **完整升级（SPRING_BOOT_UPGRADE）**：`SpringBootUpgradeService` 将多个官方配方（Jakarta 迁移、Spring Security 6、JUnit 5、Boot 3 配置项）与自定义 `SpringFactoriesMigration` 打包为组合 Recipe 一键执行。
- **自定义配方**：`SpringFactoriesMigration` 基于 `ScanningRecipe` 实现 —— 扫描 `META-INF/spring.factories`、生成新的 `AutoConfiguration.imports` 文件、并移除/删除旧条目，官方无现成可直接激活的同类配方，故自定义实现。
- **安全机制**：默认 dry-run；所有修改前必须在控制台展示 Diff；只有显式 `--apply` 才写盘。
- **技术栈**：Java 17+ / Maven / picocli / OpenRewrite / Jackson / SLF4J + Logback / 原生 Java HttpClient（未引入 Spring AI / LangChain4j）。

## 常见问题

**Q: `--instruction` 未配置 API Key 会怎样？**
A: 打印警告并降级为无 AI 模式（直接执行 Jakarta 迁移）。

**Q: AI 调用失败（网络、非 200、JSON 解析失败）？**
A: 同样优雅降级，不影响 dry-run 与 apply 流程。

**Q: 如何查看更详细的日志？**
A: 加 `--verbose` 开启 DEBUG 级别日志。

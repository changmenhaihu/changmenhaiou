package com.guardian.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 调用 DeepSeek Chat Completions API 完成意图解析（自然语言 → 结构化 JSON）。
 * AI 只做意图解析，绝不直接修改源码；源码修改一律交给 OpenRewrite 确定性配方。
 */
public class IntentParser {

    private static final Logger log = LoggerFactory.getLogger(IntentParser.class);

    /** DeepSeek 兼容 OpenAI 接口，模型默认为 deepseek-（可在配置中覆盖） */
    private static final String CHAT_ENDPOINT = "https://api.deepseek.com/v1/chat/completions";

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IntentParser(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * 将自然语言指令解析为意图列表。
     *
     * @param userInstruction 用户自然语言指令
     * @return 解析出的意图列表
     * @throws AIException 网络异常、非 200 响应或 JSON 解析失败时抛出
     */
    public List<Intent> parse(String userInstruction) throws AIException {
        String systemPrompt = loadSystemPrompt();
        String requestBody = buildRequestBody(systemPrompt, userInstruction);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CHAT_ENDPOINT))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new AIException("DeepSeek API 返回状态码 " + response.statusCode()
                        + "，响应: " + truncate(response.body()));
            }
            return parseResponse(response.body());
        } catch (IOException e) {
            throw new AIException("调用 DeepSeek API 网络异常: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AIException("调用 DeepSeek API 被中断", e);
        }
    }

    /** 从 classpath 资源加载意图解析 System Prompt */
    private String loadSystemPrompt() {
        try (var in = getClass().getClassLoader().getResourceAsStream("prompts/intent-system-prompt.txt")) {
            if (in == null) {
                throw new AIException("缺少资源文件 prompts/intent-system-prompt.txt");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AIException("读取 System Prompt 资源失败: " + e.getMessage(), e);
        }
    }

    /** 构建 OpenAI 兼容的 chat/completions 请求体 */
    private String buildRequestBody(String systemPrompt, String userInstruction) throws AIException {
        try {
            var root = objectMapper.createObjectNode();
            root.put("model", model);
            var messages = root.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userInstruction);
            root.put("temperature", 0.0);
            root.set("response_format", objectMapper.createObjectNode().put("type", "json_object"));
            return objectMapper.writeValueAsString(root);
        } catch (IOException e) {
            throw new AIException("构建请求体失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析 API 响应：取 choices[0].message.content（严格 JSON），
     * 形如 {"intents":["JAKARTA_MIGRATION"],"target_version":"3.x","confidence":0.95,"explanation":"..."}。
     */
    private List<Intent> parseResponse(String responseBody) throws AIException {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode contentNode = root.at("/choices/0/message/content");
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                throw new AIException("响应缺少 choices[0].message.content: " + truncate(responseBody));
            }
            JsonNode intentJson = objectMapper.readTree(contentNode.asText());
            double confidence = intentJson.path("confidence").asDouble(0.0);
            String explanation = intentJson.path("explanation").asText("");

            JsonNode intentsNode = intentJson.path("intents");
            List<Intent> intents = new ArrayList<>();
            if (intentsNode.isArray()) {
                for (JsonNode node : intentsNode) {
                    String name = node.isTextual() ? node.asText() : node.path("name").asText("");
                    if (!name.isBlank()) {
                        intents.add(new Intent(name.trim(), confidence, explanation));
                    }
                }
            }
            log.info("AI 意图解析结果: {}", intents);
            return intents;
        } catch (IOException e) {
            throw new AIException("解析 AI 响应 JSON 失败: " + e.getMessage(), e);
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }
}

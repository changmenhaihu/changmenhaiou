package com.guardian.ai;

/** AI 意图解析异常：网络异常、非 200 响应或 JSON 解析失败时抛出 */
public class AIException extends RuntimeException {

    public AIException(String message) {
        super(message);
    }

    public AIException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.ai.reviewer.backend.api.dto;

import java.time.Instant;
import java.util.Map;

/**
 * 健康检查响应。
 */
public record HealthResponse(
    String status,
    Instant timestamp,
    Map<String, ComponentHealth> components
) {
    
    /**
     * 组件健康状态。
     */
    public record ComponentHealth(
        String status,
        String message,
        Map<String, Object> details
    ) {}
    
    /**
     * 整体状态枚举。
     */
    public enum Status {
        UP, DOWN, DEGRADED
    }
}

package com.ai.reviewer.backend.api.controller;

import com.ai.reviewer.backend.api.dto.HealthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器。
 */
@RestController
@RequestMapping("/api")
public class HealthController {
    
    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);
    
    private final DataSource dataSource;
    
    @Autowired
    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    /**
     * 健康检查端点。
     * 
     * @return 系统健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        logger.debug("Performing health check");
        
        Map<String, HealthResponse.ComponentHealth> components = new HashMap<>();
        boolean allHealthy = true;
        
        // 检查数据库连接
        HealthResponse.ComponentHealth dbHealth = checkDatabase();
        components.put("database", dbHealth);
        if (!"UP".equals(dbHealth.status())) {
            allHealthy = false;
        }
        
        // 检查适配器状态
        HealthResponse.ComponentHealth adaptersHealth = checkAdapters();
        components.put("adapters", adaptersHealth);
        if (!"UP".equals(adaptersHealth.status())) {
            allHealthy = false;
        }
        
        // 检查内存使用
        HealthResponse.ComponentHealth memoryHealth = checkMemory();
        components.put("memory", memoryHealth);
        if (!"UP".equals(memoryHealth.status())) {
            allHealthy = false;
        }
        
        // 确定整体状态
        String overallStatus = allHealthy ? "UP" : "DOWN";
        
        HealthResponse response = new HealthResponse(
            overallStatus,
            Instant.now(),
            components
        );
        
        logger.info("Health check completed: status={}, components={}", 
            overallStatus, components.size());
        
        if (allHealthy) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(503).body(response); // Service Unavailable
        }
    }
    
    /**
     * 检查数据库连接。
     */
    private HealthResponse.ComponentHealth checkDatabase() {
        try {
            try (Connection connection = dataSource.getConnection()) {
                boolean isValid = connection.isValid(5); // 5秒超时
                if (isValid) {
                    Map<String, Object> details = Map.of(
                        "driver", connection.getMetaData().getDriverName(),
                        "url", maskSensitiveUrl(connection.getMetaData().getURL())
                    );
                    return new HealthResponse.ComponentHealth("UP", "Database connection is healthy", details);
                } else {
                    return new HealthResponse.ComponentHealth("DOWN", "Database connection is not valid", Map.of());
                }
            }
        } catch (Exception e) {
            logger.warn("Database health check failed", e);
            return new HealthResponse.ComponentHealth("DOWN", 
                "Database connection failed: " + e.getMessage(), Map.of());
        }
    }
    
    /**
     * 检查适配器状态。
     */
    private HealthResponse.ComponentHealth checkAdapters() {
        try {
            // 这里可以检查GitHub、GitLab等适配器的可用性
            // 目前返回基本状态
            Map<String, Object> details = Map.of(
                "github", "Available",
                "gitlab", "Available"
            );
            return new HealthResponse.ComponentHealth("UP", "All adapters are available", details);
        } catch (Exception e) {
            logger.warn("Adapters health check failed", e);
            return new HealthResponse.ComponentHealth("DEGRADED", 
                "Some adapters may not be available: " + e.getMessage(), Map.of());
        }
    }
    
    /**
     * 检查内存使用情况。
     */
    private HealthResponse.ComponentHealth checkMemory() {
        try {
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            
            double usageRatio = (double) usedMemory / maxMemory;
            
            Map<String, Object> details = Map.of(
                "used", formatBytes(usedMemory),
                "total", formatBytes(totalMemory),
                "max", formatBytes(maxMemory),
                "usage", String.format("%.1f%%", usageRatio * 100)
            );
            
            if (usageRatio > 0.9) {
                return new HealthResponse.ComponentHealth("DOWN", 
                    "Memory usage is critically high", details);
            } else if (usageRatio > 0.75) {
                return new HealthResponse.ComponentHealth("DEGRADED", 
                    "Memory usage is high", details);
            } else {
                return new HealthResponse.ComponentHealth("UP", 
                    "Memory usage is normal", details);
            }
        } catch (Exception e) {
            logger.warn("Memory health check failed", e);
            return new HealthResponse.ComponentHealth("DOWN", 
                "Failed to check memory status: " + e.getMessage(), Map.of());
        }
    }
    
    /**
     * 格式化字节数。
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    /**
     * 屏蔽数据库URL中的敏感信息。
     */
    private String maskSensitiveUrl(String url) {
        if (url == null) return null;
        // 屏蔽密码等敏感信息
        return url.replaceAll("password=[^&;]*", "password=***")
                  .replaceAll("://[^:]*:[^@]*@", "://***:***@");
    }
}

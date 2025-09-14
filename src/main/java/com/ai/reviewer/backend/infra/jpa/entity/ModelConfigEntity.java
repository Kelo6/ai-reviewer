package com.ai.reviewer.backend.infra.jpa.entity;

import com.ai.reviewer.shared.enums.Dimension;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 模型配置数据库实体
 */
@Entity
@Table(name = "model_config")
public class ModelConfigEntity {
    
    @Id
    @Column(name = "id", length = 100)
    private String id;
    
    @Column(name = "name", nullable = false, length = 200)
    private String name;
    
    @Column(name = "display_name", length = 200)
    private String displayName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private ModelType type;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = false;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    private ModelStatus status = ModelStatus.CONFIGURED;
    
    // AI模型配置
    @Column(name = "api_url", length = 500)
    private String apiUrl;
    
    @Column(name = "api_key", length = 500)
    private String apiKey;
    
    @Column(name = "model_name", length = 200)
    private String modelName;
    
    @Column(name = "model_version", length = 100)
    private String modelVersion;
    
    @Column(name = "max_tokens")
    private Integer maxTokens;
    
    @Column(name = "temperature")
    private Double temperature;
    
    @Column(name = "top_p")
    private Double topP;
    
    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds;
    
    @Column(name = "max_concurrent_requests")
    private Integer maxConcurrentRequests;
    
    // 静态分析工具配置
    @Column(name = "tool_path", length = 500)
    private String toolPath;
    
    @Column(name = "config_file", length = 500)
    private String configFile;
    
    @Column(name = "supported_languages", columnDefinition = "TEXT")
    private String supportedLanguages; // JSON字符串
    
    @Column(name = "supported_file_types", columnDefinition = "TEXT")
    private String supportedFileTypes; // JSON字符串
    
    @Column(name = "supported_dimensions", columnDefinition = "TEXT")
    private String supportedDimensions; // JSON字符串
    
    @Column(name = "exclude_patterns", columnDefinition = "TEXT")
    private String excludePatterns; // JSON字符串
    
    // 高级配置
    @Column(name = "advanced_settings", columnDefinition = "TEXT")
    private String advancedSettings; // JSON字符串
    
    // 统计信息
    @Column(name = "total_requests")
    private Long totalRequests = 0L;
    
    @Column(name = "success_rate")
    private Double successRate = 0.0;
    
    @Column(name = "average_response_time")
    private Double averageResponseTime = 0.0;
    
    @Column(name = "estimated_cost")
    private Double estimatedCost = 0.0;
    
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
    
    // 时间戳
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    // 枚举定义
    public enum ModelType {
        COMMERCIAL_AI("commercial-ai", "商业AI模型"),
        LOCAL_AI("local-ai", "本地AI模型"),
        STATIC_ANALYZER("static-analyzer", "静态分析工具");
        
        private final String code;
        private final String displayName;
        
        ModelType(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }
        
        public String getCode() { return code; }
        public String getDisplayName() { return displayName; }
    }
    
    public enum ModelStatus {
        CONFIGURED("configured", "已配置"),
        CONNECTED("connected", "已连接"),
        ERROR("error", "错误"),
        DISABLED("disabled", "已禁用");
        
        private final String code;
        private final String displayName;
        
        ModelStatus(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }
        
        public String getCode() { return code; }
        public String getDisplayName() { return displayName; }
    }
    
    // 构造函数
    public ModelConfigEntity() {}
    
    public ModelConfigEntity(String id, String name, ModelType type) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    
    public ModelType getType() { return type; }
    public void setType(ModelType type) { this.type = type; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    
    public ModelStatus getStatus() { return status; }
    public void setStatus(ModelStatus status) { this.status = status; }
    
    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
    
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    
    public Double getTopP() { return topP; }
    public void setTopP(Double topP) { this.topP = topP; }
    
    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    
    public Integer getMaxConcurrentRequests() { return maxConcurrentRequests; }
    public void setMaxConcurrentRequests(Integer maxConcurrentRequests) { this.maxConcurrentRequests = maxConcurrentRequests; }
    
    public String getToolPath() { return toolPath; }
    public void setToolPath(String toolPath) { this.toolPath = toolPath; }
    
    public String getConfigFile() { return configFile; }
    public void setConfigFile(String configFile) { this.configFile = configFile; }
    
    public String getSupportedLanguages() { return supportedLanguages; }
    public void setSupportedLanguages(String supportedLanguages) { this.supportedLanguages = supportedLanguages; }
    
    public String getSupportedFileTypes() { return supportedFileTypes; }
    public void setSupportedFileTypes(String supportedFileTypes) { this.supportedFileTypes = supportedFileTypes; }
    
    public String getSupportedDimensions() { return supportedDimensions; }
    public void setSupportedDimensions(String supportedDimensions) { this.supportedDimensions = supportedDimensions; }
    
    public String getExcludePatterns() { return excludePatterns; }
    public void setExcludePatterns(String excludePatterns) { this.excludePatterns = excludePatterns; }
    
    public String getAdvancedSettings() { return advancedSettings; }
    public void setAdvancedSettings(String advancedSettings) { this.advancedSettings = advancedSettings; }
    
    public Long getTotalRequests() { return totalRequests; }
    public void setTotalRequests(Long totalRequests) { this.totalRequests = totalRequests; }
    
    public Double getSuccessRate() { return successRate; }
    public void setSuccessRate(Double successRate) { this.successRate = successRate; }
    
    public Double getAverageResponseTime() { return averageResponseTime; }
    public void setAverageResponseTime(Double averageResponseTime) { this.averageResponseTime = averageResponseTime; }
    
    public Double getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(Double estimatedCost) { this.estimatedCost = estimatedCost; }
    
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

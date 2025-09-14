package com.ai.reviewer.backend.web.dto;

import com.ai.reviewer.shared.enums.Dimension;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 模型配置数据传输对象
 * 
 * 支持AI模型和静态分析工具的统一配置管理
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelConfigDto {
    
    /**
     * 模型类型枚举
     */
    public enum ModelType {
        /** OpenAI等闭源大模型 */
        COMMERCIAL_AI("commercial-ai", "商业AI模型"),
        /** 本地部署的开源模型 */
        LOCAL_AI("local-ai", "本地AI模型"),
        /** 静态分析工具 */
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
    
    /**
     * 模型状态枚举
     */
    public enum ModelStatus {
        /** 已配置但未测试 */
        CONFIGURED("configured", "已配置"),
        /** 连接正常 */
        CONNECTED("connected", "已连接"),
        /** 连接失败 */
        ERROR("error", "连接失败"),
        /** 已禁用 */
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
    
    // 基础信息
    private String id;
    private String name;
    private String displayName;
    private ModelType type;
    private String description;
    private boolean enabled;
    private ModelStatus status;
    
    // 连接配置
    private String apiUrl;
    private String apiKey;
    private String modelName;
    private String modelVersion;
    
    // AI模型特定配置
    private Integer maxTokens;
    private Double temperature;
    private Double topP;
    private Integer timeoutSeconds;
    private Integer maxConcurrentRequests;
    
    // 静态分析工具特定配置
    private String toolPath;
    private String configFile;
    private List<String> supportedLanguages;
    private List<String> supportedFileTypes;
    
    // 高级配置
    private Map<String, Object> advancedSettings;
    private List<Dimension> supportedDimensions;
    private List<String> excludePatterns;
    
    // 统计信息
    private Long totalRequests;
    private Long successfulRequests;
    private Long failedRequests;
    private Double successRate; // 成功率 (0.0 - 100.0)
    private Double averageResponseTime;
    private Double estimatedCost;
    private Instant lastUsed;
    private String lastError;
    
    // 创建和修改时间
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
    
    // 构造函数
    public ModelConfigDto() {
        this.enabled = true;
        this.status = ModelStatus.CONFIGURED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    public ModelConfigDto(String id, String name, ModelType type) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.enabled = true;
        this.status = ModelStatus.CONFIGURED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    // 静态工厂方法
    public static ModelConfigDto createOpenAiModel(String name) {
        ModelConfigDto config = new ModelConfigDto("openai-" + name.toLowerCase().replaceAll("[^a-z0-9]", "-"), 
                                                  name, ModelType.COMMERCIAL_AI);
        config.setDisplayName("OpenAI " + name);
        config.setApiUrl("https://api.openai.com/v1/chat/completions");
        config.setModelName("gpt-4o");
        config.setMaxTokens(2000);
        config.setTemperature(0.3);
        config.setTimeoutSeconds(60);
        config.setMaxConcurrentRequests(5);
        config.setSupportedDimensions(List.of(Dimension.QUALITY, Dimension.SECURITY, 
                                            Dimension.MAINTAINABILITY, Dimension.PERFORMANCE));
        return config;
    }
    
    public static ModelConfigDto createLocalModel(String name, String apiUrl) {
        ModelConfigDto config = new ModelConfigDto("local-" + name.toLowerCase().replaceAll("[^a-z0-9]", "-"), 
                                                  name, ModelType.LOCAL_AI);
        config.setDisplayName("本地模型 " + name);
        config.setApiUrl(apiUrl);
        config.setMaxTokens(2048);
        config.setTemperature(0.3);
        config.setTimeoutSeconds(120);
        config.setMaxConcurrentRequests(3);
        config.setSupportedDimensions(List.of(Dimension.QUALITY, Dimension.SECURITY, Dimension.MAINTAINABILITY));
        return config;
    }
    
    public static ModelConfigDto createStaticAnalyzer(String name, String toolPath) {
        ModelConfigDto config = new ModelConfigDto("static-" + name.toLowerCase().replaceAll("[^a-z0-9]", "-"), 
                                                  name, ModelType.STATIC_ANALYZER);
        config.setDisplayName(name + " 静态分析");
        config.setToolPath(toolPath);
        config.setTimeoutSeconds(300);
        config.setMaxConcurrentRequests(2);
        return config;
    }
    
    // 业务方法
    
    /**
     * 检查配置是否完整
     */
    public boolean isConfigurationComplete() {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        
        switch (type) {
            case COMMERCIAL_AI:
                return apiUrl != null && !apiUrl.trim().isEmpty() &&
                       apiKey != null && !apiKey.trim().isEmpty() &&
                       modelName != null && !modelName.trim().isEmpty();
                       
            case LOCAL_AI:
                return apiUrl != null && !apiUrl.trim().isEmpty() &&
                       modelName != null && !modelName.trim().isEmpty();
                       
            case STATIC_ANALYZER:
                return toolPath != null && !toolPath.trim().isEmpty();
                
            default:
                return false;
        }
    }
    
    /**
     * 获取成功率
     */
    public Double getSuccessRate() {
        if (successRate != null) {
            return successRate;
        }
        if (totalRequests == null || totalRequests == 0) {
            return 0.0;
        }
        return successfulRequests != null ? 
               (double) successfulRequests / totalRequests * 100 : 0.0;
    }
    
    /**
     * 是否为AI模型
     */
    public boolean isAiModel() {
        return type == ModelType.COMMERCIAL_AI || type == ModelType.LOCAL_AI;
    }
    
    /**
     * 是否为静态分析工具
     */
    public boolean isStaticAnalyzer() {
        return type == ModelType.STATIC_ANALYZER;
    }
    
    /**
     * 获取状态图标
     */
    public String getStatusIcon() {
        if (!enabled) {
            return "⏸️";
        }
        
        return switch (status) {
            case CONNECTED -> "✅";
            case ERROR -> "❌";
            case CONFIGURED -> "⚙️";
            case DISABLED -> "⏸️";
        };
    }
    
    /**
     * 获取类型图标
     */
    public String getTypeIcon() {
        return switch (type) {
            case COMMERCIAL_AI -> "🤖";
            case LOCAL_AI -> "🏠";
            case STATIC_ANALYZER -> "🔍";
        };
    }
    
    // Getter 和 Setter 方法
    
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
    
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
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
    
    public List<String> getSupportedLanguages() { return supportedLanguages; }
    public void setSupportedLanguages(List<String> supportedLanguages) { this.supportedLanguages = supportedLanguages; }
    
    public List<String> getSupportedFileTypes() { return supportedFileTypes; }
    public void setSupportedFileTypes(List<String> supportedFileTypes) { this.supportedFileTypes = supportedFileTypes; }
    
    public Map<String, Object> getAdvancedSettings() { return advancedSettings; }
    public void setAdvancedSettings(Map<String, Object> advancedSettings) { this.advancedSettings = advancedSettings; }
    
    public List<Dimension> getSupportedDimensions() { return supportedDimensions; }
    public void setSupportedDimensions(List<Dimension> supportedDimensions) { this.supportedDimensions = supportedDimensions; }
    
    public List<String> getExcludePatterns() { return excludePatterns; }
    public void setExcludePatterns(List<String> excludePatterns) { this.excludePatterns = excludePatterns; }
    
    public Long getTotalRequests() { return totalRequests; }
    public void setTotalRequests(Long totalRequests) { this.totalRequests = totalRequests; }
    
    public Long getSuccessfulRequests() { return successfulRequests; }
    public void setSuccessfulRequests(Long successfulRequests) { this.successfulRequests = successfulRequests; }
    
    public Long getFailedRequests() { return failedRequests; }
    public void setFailedRequests(Long failedRequests) { this.failedRequests = failedRequests; }
    
    public void setSuccessRate(Double successRate) { this.successRate = successRate; }
    
    public Double getAverageResponseTime() { return averageResponseTime; }
    public void setAverageResponseTime(Double averageResponseTime) { this.averageResponseTime = averageResponseTime; }
    
    public Double getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(Double estimatedCost) { this.estimatedCost = estimatedCost; }
    
    public Instant getLastUsed() { return lastUsed; }
    public void setLastUsed(Instant lastUsed) { this.lastUsed = lastUsed; }
    
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    
    @Override
    public String toString() {
        return "ModelConfigDto{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               ", type=" + type +
               ", enabled=" + enabled +
               ", status=" + status +
               '}';
    }
}

package com.ai.reviewer.backend.domain.orchestrator.report;

import com.ai.reviewer.shared.enums.Dimension;
import com.ai.reviewer.shared.enums.Severity;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 报告配置管理器，负责管理各种报告生成的配置参数。
 * 
 * <p>支持的配置功能：
 * - 报告内容定制（包含/排除特定部分）
 * - 格式输出控制（JSON、Markdown、HTML、PDF）
 * - 样式和主题配置
 * - 过滤器和排序规则
 * - 模板和布局选择
 * 
 * <p>配置可以按项目、用户或全局级别进行管理。
 */
@Component
public class ReportConfigManager {
    
    private final ObjectMapper objectMapper;
    private final Map<String, ReportConfiguration> configCache;
    
    @Value("${ai-reviewer.reports.config-dir:config/reports}")
    private String configDirectory;
    
    public ReportConfigManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.configCache = new HashMap<>();
        initializeDefaultConfigurations();
    }
    
    /**
     * 获取报告配置。
     * 
     * @param configId 配置ID（如"default", "detailed", "summary"等）
     * @return 报告配置
     */
    public ReportConfiguration getConfiguration(String configId) {
        ReportConfiguration config = configCache.get(configId);
        if (config == null) {
            config = loadConfigurationFromFile(configId);
            if (config == null) {
                config = createDefaultConfiguration(configId);
            }
            configCache.put(configId, config);
        }
        return config;
    }
    
    /**
     * 保存报告配置。
     * 
     * @param configId 配置ID
     * @param configuration 配置对象
     */
    public void saveConfiguration(String configId, ReportConfiguration configuration) {
        try {
            // 更新缓存
            configCache.put(configId, configuration);
            
            // 保存到文件
            Path configPath = getConfigFilePath(configId);
            Files.createDirectories(configPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(configPath.toFile(), configuration);
                
        } catch (IOException e) {
            throw new ReportConfigurationException("Failed to save configuration: " + configId, e);
        }
    }
    
    /**
     * 删除报告配置。
     * 
     * @param configId 配置ID
     */
    public void deleteConfiguration(String configId) {
        configCache.remove(configId);
        
        try {
            Path configPath = getConfigFilePath(configId);
            Files.deleteIfExists(configPath);
        } catch (IOException e) {
            // 记录错误但不抛出异常
        }
    }
    
    /**
     * 获取所有可用的配置列表。
     * 
     * @return 配置ID列表
     */
    public List<String> getAvailableConfigurations() {
        Set<String> configIds = new HashSet<>(configCache.keySet());
        
        try {
            Path configDir = Paths.get(configDirectory);
            if (Files.exists(configDir)) {
                Files.list(configDir)
                    .filter(path -> path.toString().endsWith(".json"))
                    .map(path -> path.getFileName().toString().replace(".json", ""))
                    .forEach(configIds::add);
            }
        } catch (IOException e) {
            // 忽略文件系统错误，只返回缓存中的配置
        }
        
        return new ArrayList<>(configIds);
    }
    
    /**
     * 克隆现有配置。
     * 
     * @param sourceConfigId 源配置ID
     * @param newConfigId 新配置ID
     * @param modifications 修改内容
     * @return 新的配置对象
     */
    public ReportConfiguration cloneConfiguration(String sourceConfigId, String newConfigId, 
                                                ConfigModifications modifications) {
        ReportConfiguration sourceConfig = getConfiguration(sourceConfigId);
        ReportConfiguration newConfig = applyModifications(sourceConfig, modifications);
        
        // 更新基本信息
        newConfig = new ReportConfiguration(
            newConfigId,
            newConfig.displayName(),
            newConfig.description(),
            newConfig.content(),
            newConfig.formatting(),
            newConfig.styling(),
            newConfig.filtering(),
            newConfig.templates(),
            newConfig.metadata(),
            Instant.now(),
            newConfig.lastModified()
        );
        
        saveConfiguration(newConfigId, newConfig);
        return newConfig;
    }
    
    /**
     * 验证配置有效性。
     * 
     * @param configuration 配置对象
     * @return 验证结果
     */
    public ConfigValidationResult validateConfiguration(ReportConfiguration configuration) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // 检查必需字段
        if (configuration.configId() == null || configuration.configId().isEmpty()) {
            errors.add("配置ID不能为空");
        }
        
        if (configuration.displayName() == null || configuration.displayName().isEmpty()) {
            errors.add("显示名称不能为空");
        }
        
        // 检查内容配置
        ContentConfiguration content = configuration.content();
        if (content != null) {
            if (content.includeSections().isEmpty()) {
                warnings.add("没有选择任何报告部分，报告可能为空");
            }
        }
        
        // 检查格式配置
        FormattingConfiguration formatting = configuration.formatting();
        if (formatting != null) {
            if (formatting.outputFormats().isEmpty()) {
                errors.add("至少需要选择一种输出格式");
            }
        }
        
        // 检查过滤器配置
        FilteringConfiguration filtering = configuration.filtering();
        if (filtering != null) {
            if (filtering.severityFilter().isEmpty() && filtering.dimensionFilter().isEmpty()) {
                warnings.add("没有设置任何过滤器，将显示所有问题");
            }
        }
        
        return new ConfigValidationResult(
            errors.isEmpty(),
            errors,
            warnings
        );
    }
    
    /**
     * 从文件加载配置。
     */
    private ReportConfiguration loadConfigurationFromFile(String configId) {
        try {
            Path configPath = getConfigFilePath(configId);
            if (Files.exists(configPath)) {
                return objectMapper.readValue(configPath.toFile(), ReportConfiguration.class);
            }
        } catch (IOException e) {
            // 记录错误但不抛出异常
        }
        return null;
    }
    
    /**
     * 获取配置文件路径。
     */
    private Path getConfigFilePath(String configId) {
        return Paths.get(configDirectory, configId + ".json");
    }
    
    /**
     * 初始化默认配置。
     */
    private void initializeDefaultConfigurations() {
        // 默认配置
        configCache.put("default", createDefaultConfiguration("default"));
        
        // 详细配置
        configCache.put("detailed", createDetailedConfiguration());
        
        // 简要配置
        configCache.put("summary", createSummaryConfiguration());
        
        // 安全专用配置
        configCache.put("security", createSecurityConfiguration());
        
        // 性能专用配置
        configCache.put("performance", createPerformanceConfiguration());
    }
    
    /**
     * 创建默认配置。
     */
    private ReportConfiguration createDefaultConfiguration(String configId) {
        return new ReportConfiguration(
            configId,
            "默认报告配置",
            "标准的代码审查报告配置，包含所有基本信息",
            new ContentConfiguration(
                Set.of(ReportSection.OVERVIEW, ReportSection.STATISTICS, 
                      ReportSection.FINDINGS, ReportSection.SCORES, ReportSection.RECOMMENDATIONS),
                true,  // includeScores
                true,  // includeStatistics
                true,  // includeFindings
                false, // includePatches
                3,     // maxFindingsPerSeverity
                true   // groupByDimension
            ),
            new FormattingConfiguration(
                Set.of(OutputFormat.MARKDOWN, OutputFormat.HTML),
                "zh-CN",
                "yyyy-MM-dd HH:mm:ss",
                true,  // enableSyntaxHighlighting
                true   // enableTableOfContents
            ),
            new StylingConfiguration(
                "default",
                null,  // customCss
                Map.of("primaryColor", "#2563eb", "secondaryColor", "#64748b"),
                "Inter, -apple-system, BlinkMacSystemFont, sans-serif",
                14     // fontSize
            ),
            new FilteringConfiguration(
                Set.of(Severity.CRITICAL, Severity.MAJOR, Severity.MINOR, Severity.INFO),
                Set.of(Dimension.values()),
                0.5,   // minConfidence
                SortOrder.SEVERITY_DESC,
                100    // maxResults
            ),
            new TemplateConfiguration(
                "default",
                null,  // customTemplate
                Map.of("showTimestamp", "true", "showRunId", "true")
            ),
            Map.of("version", "1.0", "author", "AI-Reviewer"),
            Instant.now(),
            Instant.now()
        );
    }
    
    /**
     * 创建详细配置。
     */
    private ReportConfiguration createDetailedConfiguration() {
        ReportConfiguration defaultConfig = createDefaultConfiguration("detailed");
        
        return new ReportConfiguration(
            "detailed",
            "详细报告配置",
            "包含所有详细信息的完整报告配置",
            new ContentConfiguration(
                Set.of(ReportSection.values()), // 包含所有部分
                true, true, true, true,  // 包含所有内容
                -1,   // 无限制发现数量
                true
            ),
            new FormattingConfiguration(
                Set.of(OutputFormat.values()), // 所有格式
                defaultConfig.formatting().locale(),
                defaultConfig.formatting().dateFormat(),
                true, true
            ),
            defaultConfig.styling(),
            defaultConfig.filtering(),
            defaultConfig.templates(),
            defaultConfig.metadata(),
            defaultConfig.createdAt(),
            defaultConfig.lastModified()
        );
    }
    
    /**
     * 创建简要配置。
     */
    private ReportConfiguration createSummaryConfiguration() {
        return new ReportConfiguration(
            "summary",
            "简要报告配置",
            "只包含关键信息的简化报告配置",
            new ContentConfiguration(
                Set.of(ReportSection.OVERVIEW, ReportSection.SCORES),
                true, false, true, false,
                5,   // 最多5个发现
                false
            ),
            new FormattingConfiguration(
                Set.of(OutputFormat.MARKDOWN),
                "zh-CN",
                "yyyy-MM-dd",
                false, false
            ),
            new StylingConfiguration(
                "minimal",
                null,
                Map.of("primaryColor", "#059669"),
                "system-ui, sans-serif",
                12
            ),
            new FilteringConfiguration(
                Set.of(Severity.CRITICAL, Severity.MAJOR),
                Set.of(Dimension.values()),
                0.7,
                SortOrder.SEVERITY_DESC,
                20
            ),
            new TemplateConfiguration(
                "summary",
                null,
                Map.of("compact", "true")
            ),
            Map.of("version", "1.0", "type", "summary"),
            Instant.now(),
            Instant.now()
        );
    }
    
    /**
     * 创建安全专用配置。
     */
    private ReportConfiguration createSecurityConfiguration() {
        return new ReportConfiguration(
            "security",
            "安全报告配置",
            "专注于安全问题的报告配置",
            new ContentConfiguration(
                Set.of(ReportSection.OVERVIEW, ReportSection.FINDINGS, ReportSection.SCORES, ReportSection.PATCHES),
                true, false, true, true,
                -1, true
            ),
            new FormattingConfiguration(
                Set.of(OutputFormat.MARKDOWN, OutputFormat.PDF),
                "zh-CN", "yyyy-MM-dd HH:mm:ss", true, true
            ),
            new StylingConfiguration(
                "security",
                null,
                Map.of("primaryColor", "#dc2626", "warningColor", "#f59e0b"),
                "system-ui, sans-serif", 14
            ),
            new FilteringConfiguration(
                Set.of(Severity.CRITICAL, Severity.MAJOR),
                Set.of(Dimension.SECURITY),
                0.6,
                SortOrder.SEVERITY_DESC,
                50
            ),
            new TemplateConfiguration(
                "security",
                null,
                Map.of("highlightSecurity", "true")
            ),
            Map.of("version", "1.0", "focus", "security"),
            Instant.now(),
            Instant.now()
        );
    }
    
    /**
     * 创建性能专用配置。
     */
    private ReportConfiguration createPerformanceConfiguration() {
        return new ReportConfiguration(
            "performance",
            "性能报告配置",
            "专注于性能问题的报告配置",
            new ContentConfiguration(
                Set.of(ReportSection.OVERVIEW, ReportSection.FINDINGS, ReportSection.STATISTICS, ReportSection.PATCHES),
                true, true, true, true,
                -1, true
            ),
            new FormattingConfiguration(
                Set.of(OutputFormat.HTML, OutputFormat.JSON),
                "zh-CN", "yyyy-MM-dd HH:mm:ss", true, true
            ),
            new StylingConfiguration(
                "performance",
                null,
                Map.of("primaryColor", "#059669", "accentColor", "#0891b2"),
                "system-ui, sans-serif", 14
            ),
            new FilteringConfiguration(
                Set.of(Severity.values()),
                Set.of(Dimension.PERFORMANCE),
                0.5,
                SortOrder.CONFIDENCE_DESC,
                100
            ),
            new TemplateConfiguration(
                "performance",
                null,
                Map.of("showMetrics", "true", "includeCharts", "true")
            ),
            Map.of("version", "1.0", "focus", "performance"),
            Instant.now(),
            Instant.now()
        );
    }
    
    /**
     * 应用配置修改。
     */
    private ReportConfiguration applyModifications(ReportConfiguration baseConfig, ConfigModifications modifications) {
        // 这里可以实现复杂的配置修改逻辑
        // 暂时返回基础配置
        return baseConfig;
    }
    
    /**
     * 报告配置。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReportConfiguration(
        String configId,
        String displayName,
        String description,
        ContentConfiguration content,
        FormattingConfiguration formatting,
        StylingConfiguration styling,
        FilteringConfiguration filtering,
        TemplateConfiguration templates,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant lastModified
    ) {}
    
    /**
     * 内容配置。
     */
    public record ContentConfiguration(
        Set<ReportSection> includeSections,
        boolean includeScores,
        boolean includeStatistics,
        boolean includeFindings,
        boolean includePatches,
        int maxFindingsPerSeverity,
        boolean groupByDimension
    ) {}
    
    /**
     * 格式配置。
     */
    public record FormattingConfiguration(
        Set<OutputFormat> outputFormats,
        String locale,
        String dateFormat,
        boolean enableSyntaxHighlighting,
        boolean enableTableOfContents
    ) {}
    
    /**
     * 样式配置。
     */
    public record StylingConfiguration(
        String theme,
        String customCss,
        Map<String, String> colorScheme,
        String fontFamily,
        int fontSize
    ) {}
    
    /**
     * 过滤配置。
     */
    public record FilteringConfiguration(
        Set<Severity> severityFilter,
        Set<Dimension> dimensionFilter,
        double minConfidence,
        SortOrder sortOrder,
        int maxResults
    ) {}
    
    /**
     * 模板配置。
     */
    public record TemplateConfiguration(
        String templateName,
        String customTemplate,
        Map<String, String> templateVariables
    ) {}
    
    /**
     * 配置修改。
     */
    public record ConfigModifications(
        Map<String, Object> changes
    ) {}
    
    /**
     * 配置验证结果。
     */
    public record ConfigValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings
    ) {}
    
    /**
     * 报告部分枚举。
     */
    public enum ReportSection {
        OVERVIEW("概览"),
        STATISTICS("统计信息"),
        SCORES("评分详情"),
        FINDINGS("发现问题"),
        PATCHES("修复补丁"),
        RECOMMENDATIONS("改进建议"),
        COST_ANALYSIS("成本分析"),
        METADATA("元数据");
        
        private final String displayName;
        
        ReportSection(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() { return displayName; }
    }
    
    /**
     * 输出格式枚举。
     */
    public enum OutputFormat {
        JSON("JSON"),
        MARKDOWN("Markdown"),
        HTML("HTML"),
        PDF("PDF"),
        SARIF("SARIF");
        
        private final String displayName;
        
        OutputFormat(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() { return displayName; }
    }
    
    /**
     * 排序顺序枚举。
     */
    public enum SortOrder {
        SEVERITY_DESC("按严重性降序"),
        SEVERITY_ASC("按严重性升序"),
        CONFIDENCE_DESC("按置信度降序"),
        CONFIDENCE_ASC("按置信度升序"),
        FILE_NAME("按文件名"),
        LINE_NUMBER("按行号");
        
        private final String displayName;
        
        SortOrder(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() { return displayName; }
    }
    
    /**
     * 报告配置异常。
     */
    public static class ReportConfigurationException extends RuntimeException {
        public ReportConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

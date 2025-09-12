package com.ai.reviewer.backend.domain.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * AI 评审配置服务。
 * 
 * <p>负责从仓库根目录读取和解析 .ai-review.yml 配置文件。
 */
@Service
public class ConfigService {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigService.class);
    private static final String CONFIG_FILE_NAME = ".ai-review.yml";
    
    private final ObjectMapper yamlMapper;
    private final Validator validator;
    
    @Autowired
    public ConfigService(Validator validator) {
        this.validator = validator;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }
    
    /**
     * 从仓库路径加载配置。
     * 
     * @param repoPath 仓库根目录路径
     * @return AI评审配置
     */
    public AiReviewConfig loadConfig(String repoPath) {
        if (repoPath == null || repoPath.trim().isEmpty()) {
            logger.debug("Repository path is null or empty, using default configuration");
            return AiReviewConfig.getDefault();
        }
        
        Path configPath = Paths.get(repoPath, CONFIG_FILE_NAME);
        
        if (!Files.exists(configPath)) {
            logger.info("Configuration file not found at {}, using default configuration", configPath);
            return AiReviewConfig.getDefault();
        }
        
        try {
            logger.debug("Loading configuration from: {}", configPath);
            return loadConfigFromFile(configPath);
        } catch (Exception e) {
            logger.error("Failed to load configuration from {}, falling back to default", configPath, e);
            return AiReviewConfig.getDefault();
        }
    }
    
    /**
     * 从输入流加载配置（用于测试）。
     */
    public AiReviewConfig loadConfig(InputStream inputStream) throws IOException, ConfigValidationException {
        AiReviewConfig config = yamlMapper.readValue(inputStream, AiReviewConfig.class);
        validateConfig(config);
        return config;
    }
    
    /**
     * 从文件路径加载配置。
     */
    private AiReviewConfig loadConfigFromFile(Path configPath) throws IOException, ConfigValidationException {
        try (InputStream inputStream = new FileInputStream(configPath.toFile())) {
            AiReviewConfig config = yamlMapper.readValue(inputStream, AiReviewConfig.class);
            logger.debug("Successfully parsed configuration file");
            
            validateConfig(config);
            logger.info("Configuration loaded and validated successfully from {}", configPath);
            
            return config;
        }
    }
    
    /**
     * 验证配置有效性。
     */
    private void validateConfig(AiReviewConfig config) throws ConfigValidationException {
        if (config == null) {
            throw new ConfigValidationException("Configuration is null");
        }
        
        // Bean Validation 验证
        Set<ConstraintViolation<AiReviewConfig>> violations = validator.validate(config);
        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder("Configuration validation failed:");
            for (ConstraintViolation<AiReviewConfig> violation : violations) {
                sb.append("\n- ").append(violation.getPropertyPath()).append(": ").append(violation.getMessage());
            }
            throw new ConfigValidationException(sb.toString());
        }
        
        // 自定义业务逻辑验证
        try {
            config.validate();
        } catch (IllegalArgumentException e) {
            throw new ConfigValidationException("Configuration validation failed: " + e.getMessage(), e);
        }
        
        logger.debug("Configuration validation passed");
    }
    
    /**
     * 合并配置（用于覆盖默认值）。
     * 
     * @param defaultConfig 默认配置
     * @param overrideConfig 覆盖配置（可以是部分配置）
     * @return 合并后的配置
     */
    public AiReviewConfig mergeConfig(AiReviewConfig defaultConfig, AiReviewConfig overrideConfig) {
        if (overrideConfig == null) {
            return defaultConfig;
        }
        
        // 简单合并逻辑，实际项目中可能需要更复杂的合并策略
        return new AiReviewConfig(
            overrideConfig.provider() != null ? overrideConfig.provider() : defaultConfig.provider(),
            overrideConfig.llm() != null ? overrideConfig.llm() : defaultConfig.llm(),
            overrideConfig.scoring() != null ? overrideConfig.scoring() : defaultConfig.scoring(),
            overrideConfig.report() != null ? overrideConfig.report() : defaultConfig.report()
        );
    }
    
    /**
     * 保存配置到文件（用于测试或配置生成）。
     * 
     * @param config 配置对象
     * @param outputPath 输出路径
     */
    public void saveConfig(AiReviewConfig config, Path outputPath) throws IOException, ConfigValidationException {
        validateConfig(config);
        
        Files.createDirectories(outputPath.getParent());
        yamlMapper.writeValue(outputPath.toFile(), config);
        
        logger.info("Configuration saved to: {}", outputPath);
    }
    
    /**
     * 检查配置文件是否存在。
     */
    public boolean hasConfigFile(String repoPath) {
        if (repoPath == null || repoPath.trim().isEmpty()) {
            return false;
        }
        
        Path configPath = Paths.get(repoPath, CONFIG_FILE_NAME);
        return Files.exists(configPath) && Files.isRegularFile(configPath);
    }
    
    /**
     * 获取配置文件路径。
     */
    public Path getConfigPath(String repoPath) {
        return Paths.get(repoPath, CONFIG_FILE_NAME);
    }
    
    /**
     * 创建示例配置文件。
     */
    public void createExampleConfig(Path outputPath) throws IOException {
        AiReviewConfig exampleConfig = AiReviewConfig.getDefault();
        yamlMapper.writeValue(outputPath.toFile(), exampleConfig);
        logger.info("Example configuration created at: {}", outputPath);
    }
}

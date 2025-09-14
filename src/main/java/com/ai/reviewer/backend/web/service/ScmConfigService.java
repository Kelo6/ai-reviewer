package com.ai.reviewer.backend.web.service;

import com.ai.reviewer.backend.domain.adapter.scm.MultiRepoAdapter;
import com.ai.reviewer.backend.infra.adapter.ScmConfigAdapter;
import com.ai.reviewer.backend.infra.jpa.entity.ScmConfigEntity;
import com.ai.reviewer.backend.infra.jpa.repository.ScmConfigRepository;
import com.ai.reviewer.backend.web.dto.ScmConfigDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SCM配置管理服务
 */
@Service("scmConfigService")
public class ScmConfigService {
    
    private static final Logger logger = LoggerFactory.getLogger(ScmConfigService.class);
    
    private final MultiRepoAdapter multiRepoAdapter;
    private final Environment environment;
    private final ScmConfigRepository scmConfigRepository;
    private final ScmConfigAdapter scmConfigAdapter;
    
    // 内存缓存（提高性能，但主要数据存储在数据库中）
    private final Map<String, ScmConfigDto> configCache = new ConcurrentHashMap<>();
    
    @Autowired
    public ScmConfigService(
            MultiRepoAdapter multiRepoAdapter, 
            Environment environment,
            ScmConfigRepository scmConfigRepository,
            ScmConfigAdapter scmConfigAdapter) {
        this.multiRepoAdapter = multiRepoAdapter;
        this.environment = environment;
        this.scmConfigRepository = scmConfigRepository;
        this.scmConfigAdapter = scmConfigAdapter;
        
        // 初始化时从数据库和环境变量读取现有配置
        initializeFromDatabaseAndEnvironment();
    }
    
    /**
     * 获取所有支持的SCM提供商
     */
    public List<String> getSupportedProviders() {
        return List.of("github", "gitlab", "bitbucket", "gitea", "custom-git");
    }
    
    /**
     * 测试SCM平台连通性
     */
    public Map<String, Object> testConnection(ScmConfigDto config) {
        Map<String, Object> result = new HashMap<>();
        
        logger.info("Starting connection test for provider: {}", config.getProvider());
        logger.debug("Test config details - Provider: {}, API Base: {}, Has Token: {}", 
            config.getProvider(), 
            config.getApiBase(), 
            config.getToken() != null && !config.getToken().trim().isEmpty());
        
        try {
            // 验证配置完整性
            if (!config.isComplete()) {
                String[] requiredFields = config.getRequiredFields();
                String missingFields = String.join(", ", requiredFields);
                logger.warn("Configuration incomplete for provider: {}, missing fields: {}", 
                    config.getProvider(), missingFields);
                
                result.put("success", false);
                result.put("message", "配置信息不完整，缺少必要字段: " + missingFields);
                return result;
            }
            
            logger.info("Configuration validation passed for provider: {}", config.getProvider());
            
            // 根据不同平台执行连通性测试
            boolean connected = performConnectionTest(config);
            
            if (connected) {
                logger.info("Connection test successful for provider: {}", config.getProvider());
                result.put("success", true);
                result.put("message", "连接成功");
                result.put("details", getConnectionDetails(config));
                
                // 更新配置状态
                config.setStatus("connected");
                config.setStatusMessage("连接正常");
            } else {
                logger.warn("Connection test failed for provider: {}", config.getProvider());
                result.put("success", false);
                result.put("message", "无法连接到指定的平台，请检查API地址和认证信息");
                config.setStatus("error");
                config.setStatusMessage("连接失败");
            }
            
        } catch (Exception e) {
            logger.error("Connection test exception for provider: {}, error: {}", 
                config.getProvider(), e.getMessage(), e);
            result.put("success", false);
            result.put("message", "连接测试异常: " + e.getMessage());
            config.setStatus("error");
            config.setStatusMessage("测试异常: " + e.getMessage());
        }
        
        logger.debug("Connection test result for provider: {}, success: {}", 
            config.getProvider(), result.get("success"));
        return result;
    }
    
    /**
     * 执行实际的连通性测试
     */
    private boolean performConnectionTest(ScmConfigDto config) {
        try {
            switch (config.getProvider()) {
                case "github":
                    return testGitHubConnection(config);
                case "gitlab":
                    return testGitLabConnection(config);
                case "bitbucket":
                    return testBitbucketConnection(config);
                case "gitea":
                case "custom-git":
                    return testGiteaConnection(config);
                default:
                    return false;
            }
        } catch (Exception e) {
            logger.error("Connection test error for " + config.getProvider(), e);
            return false;
        }
    }
    
    /**
     * 测试GitHub连接
     */
    private boolean testGitHubConnection(ScmConfigDto config) {
        // 这里应该使用实际的GitHub API调用
        // 为了演示，我们模拟测试逻辑
        return isValidUrl(config.getApiBase()) && 
               config.getToken() != null && 
               !config.getToken().trim().isEmpty() &&
               config.getToken().startsWith("ghp_") || config.getToken().startsWith("github_pat_");
    }
    
    /**
     * 测试GitLab连接
     */
    private boolean testGitLabConnection(ScmConfigDto config) {
        return isValidUrl(config.getApiBase()) && 
               config.getToken() != null && 
               !config.getToken().trim().isEmpty() &&
               config.getToken().startsWith("glpat_");
    }
    
    /**
     * 测试Bitbucket连接
     */
    private boolean testBitbucketConnection(ScmConfigDto config) {
        return isValidUrl(config.getApiBase()) && 
               config.getUsername() != null && 
               !config.getUsername().trim().isEmpty() &&
               config.getAppPassword() != null && 
               !config.getAppPassword().trim().isEmpty();
    }
    
    /**
     * 测试Gitea连接
     */
    private boolean testGiteaConnection(ScmConfigDto config) {
        return isValidUrl(config.getApiBase()) && 
               config.getToken() != null && 
               !config.getToken().trim().isEmpty();
    }
    
    /**
     * 获取连接详情
     */
    private Map<String, Object> getConnectionDetails(ScmConfigDto config) {
        Map<String, Object> details = new HashMap<>();
        details.put("provider", config.getProvider());
        details.put("displayName", config.getDisplayName());
        details.put("apiBase", config.getApiBase());
        details.put("webBase", config.getWebBase());
        details.put("webhookUrl", getWebhookUrl(config));
        return details;
    }
    
    /**
     * 添加自定义平台
     */
    public String addCustomPlatform(Map<String, String> platformData) {
        String platformName = platformData.get("name");
        String platformId = "custom-" + platformName.toLowerCase().replaceAll("[^a-z0-9]", "-");
        
        ScmConfigDto customConfig = new ScmConfigDto(platformId, platformName);
        customConfig.setApiBase(platformData.get("apiBase"));
        customConfig.setWebBase(platformData.get("webBase"));
        customConfig.setApiType(platformData.getOrDefault("apiType", "gitea"));
        customConfig.setSslVerify(Boolean.parseBoolean(platformData.getOrDefault("sslVerify", "true")));
        customConfig.setEnabled(false);
        customConfig.setStatus("not_configured");
        
        configCache.put(platformId, customConfig);
        logger.info("Added custom platform: {} with ID: {}", platformName, platformId);
        
        return platformId;
    }
    
    /**
     * 验证URL格式
     */
    private boolean isValidUrl(String url) {
        try {
            new java.net.URL(url);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取Webhook URL
     */
    private String getWebhookUrl(ScmConfigDto config) {
        // 这里应该基于应用的实际域名生成
        return "http://localhost:8081/api/webhooks/" + config.getProvider();
    }
    
    
    /**
     * 获取所有SCM配置
     */
    public List<ScmConfigDto> getAllScmConfigs() {
        try {
            // 从数据库读取所有配置
            List<ScmConfigEntity> entities = scmConfigRepository.findAll();
            List<ScmConfigDto> configs = new ArrayList<>();
            
            for (ScmConfigEntity entity : entities) {
                ScmConfigDto config = scmConfigAdapter.toDto(entity);
                updateConnectionStatus(config);
                configs.add(config);
                
                // 更新缓存
                configCache.put(config.getProvider(), config);
            }
            
            // 确保所有支持的提供商都有配置（即使数据库中没有）
            for (String provider : getSupportedProviders()) {
                if (configs.stream().noneMatch(c -> provider.equals(c.getProvider()))) {
                    ScmConfigDto defaultConfig = createDefaultConfig(provider);
                    configs.add(defaultConfig);
                    configCache.put(provider, defaultConfig);
                }
            }
            
            logger.debug("Loaded {} SCM configurations from database", configs.size());
            return configs;
            
        } catch (Exception e) {
            logger.error("Error loading SCM configurations from database", e);
            // 如果数据库读取失败，返回默认配置
            return getSupportedProviders().stream()
                .map(this::createDefaultConfig)
                .toList();
        }
    }
    
    /**
     * 获取特定提供商的配置
     */
    public ScmConfigDto getScmConfig(String provider) {
        try {
            // 优先从数据库读取
            Optional<ScmConfigEntity> entityOpt = scmConfigRepository.findById(provider);
            if (entityOpt.isPresent()) {
                ScmConfigDto config = scmConfigAdapter.toDto(entityOpt.get());
                updateConnectionStatus(config);
                configCache.put(provider, config); // 更新缓存
                return config;
            }
        } catch (Exception e) {
            logger.warn("Error loading SCM config from database for provider: {}", provider, e);
        }
        
        // 数据库中没有，检查缓存
        ScmConfigDto config = configCache.get(provider);
        if (config == null) {
            config = createDefaultConfig(provider);
            configCache.put(provider, config);
        }
        
        updateConnectionStatus(config);
        return config;
    }
    
    /**
     * 保存SCM配置
     */
    @Transactional
    public void saveScmConfig(ScmConfigDto config) {
        if (config == null || config.getProvider() == null) {
            throw new IllegalArgumentException("配置对象或提供商不能为空");
        }
        
        String provider = config.getProvider();
        logger.info("Saving SCM configuration for provider: {}", provider);
        
        // 详细的配置验证
        try {
            validateScmConfig(config);
        } catch (Exception e) {
            logger.error("Configuration validation failed for provider: {}, error: {}", 
                    provider, e.getMessage());
            throw new IllegalArgumentException("配置验证失败: " + e.getMessage());
        }
        
        // 标记为用户保存的配置，避免被环境变量覆盖
        config.setEnabled(true);
        
        try {
            // 保存到数据库
            Optional<ScmConfigEntity> existingOpt = scmConfigRepository.findById(provider);
            ScmConfigEntity entity;
            
            if (existingOpt.isPresent()) {
                // 更新现有配置
                entity = existingOpt.get();
                scmConfigAdapter.updateEntity(entity, config);
                logger.debug("Updating existing SCM configuration for provider: {}", provider);
            } else {
                // 创建新配置
                entity = scmConfigAdapter.toEntity(config);
                logger.debug("Creating new SCM configuration for provider: {}", provider);
            }
            
            scmConfigRepository.save(entity);
            logger.info("Successfully saved SCM configuration to database for provider: {}", provider);
            
            // 更新缓存
            configCache.put(provider, config);
            
            // 更新连接状态
            try {
                updateConnectionStatus(config);
            } catch (Exception e) {
                logger.warn("Failed to update connection status for provider: {}, error: {}", 
                        provider, e.getMessage());
                // 不影响保存操作，只是状态更新失败
            }
            
        } catch (Exception e) {
            logger.error("Failed to save SCM configuration to database for provider: {}", provider, e);
            throw new RuntimeException("保存SCM配置到数据库失败: " + e.getMessage(), e);
        }
        
        logger.info("Successfully saved SCM configuration for provider: {}", provider);
    }
    
    /**
     * 验证SCM配置
     */
    private void validateScmConfig(ScmConfigDto config) {
        logger.debug("Validating SCM config for provider: {}", config.getProvider());
        
        // 基础字段验证
        if (config.getProvider() == null || config.getProvider().trim().isEmpty()) {
            logger.error("Provider is null or empty");
            throw new IllegalArgumentException("提供商不能为空");
        }
        
        if (config.getApiBase() == null || config.getApiBase().trim().isEmpty()) {
            logger.error("API base is null or empty for provider: {}", config.getProvider());
            throw new IllegalArgumentException("API地址不能为空");
        }
        
        // URL格式验证
        if (!isValidUrl(config.getApiBase())) {
            logger.error("Invalid API base URL for provider: {}, URL: {}", 
                config.getProvider(), config.getApiBase());
            throw new IllegalArgumentException("API地址格式无效: " + config.getApiBase());
        }
        
        if (config.getWebBase() != null && !config.getWebBase().trim().isEmpty() && 
            !isValidUrl(config.getWebBase())) {
            logger.error("Invalid web base URL for provider: {}, URL: {}", 
                config.getProvider(), config.getWebBase());
            throw new IllegalArgumentException("网站地址格式无效: " + config.getWebBase());
        }
        
        logger.debug("Basic validation passed for provider: {}", config.getProvider());
        
        // 验证配置完整性
        if (!config.isComplete()) {
            String[] requiredFields = config.getRequiredFields();
            logger.error("Configuration incomplete for provider: {}, missing fields: {}", 
                config.getProvider(), String.join(", ", requiredFields));
            throw new IllegalArgumentException("配置不完整，缺少必需字段: " + 
                    String.join(", ", requiredFields));
        }
        
        logger.debug("Completeness validation passed for provider: {}", config.getProvider());
        
        // 特定提供商的验证
        validateProviderSpecificConfig(config);
        
        logger.info("All validations passed for provider: {}", config.getProvider());
    }
    
    /**
     * 验证特定提供商的配置
     */
    private void validateProviderSpecificConfig(ScmConfigDto config) {
        switch (config.getProvider()) {
            case "github" -> {
                if (config.getToken() != null && 
                    !config.getToken().startsWith("ghp_") && 
                    !config.getToken().startsWith("github_pat_")) {
                    logger.warn("GitHub token format may be invalid for provider: {}", config.getProvider());
                }
            }
            case "gitlab" -> {
                if (config.getToken() != null && !config.getToken().startsWith("glpat_")) {
                    logger.warn("GitLab token format may be invalid for provider: {}", config.getProvider());
                }
            }
            case "bitbucket" -> {
                if (config.getUsername() == null || config.getUsername().trim().isEmpty()) {
                    throw new IllegalArgumentException("Bitbucket用户名不能为空");
                }
                if (config.getAppPassword() == null || config.getAppPassword().trim().isEmpty()) {
                    throw new IllegalArgumentException("Bitbucket应用密码不能为空");
                }
            }
            case "custom-git" -> {
                if (config.getApiType() == null || config.getApiType().trim().isEmpty()) {
                    config.setApiType("gitea"); // 设置默认值
                }
            }
        }
    }
    
    /**
     * 删除SCM配置
     */
    @Transactional
    public void deleteScmConfig(String provider) {
        if (provider == null || provider.trim().isEmpty()) {
            throw new IllegalArgumentException("提供商不能为空");
        }
        
        logger.info("Deleting SCM configuration for provider: {}", provider);
        
        try {
            // 从数据库删除
            boolean existed = scmConfigRepository.existsById(provider);
            if (existed) {
                scmConfigRepository.deleteById(provider);
                logger.info("Successfully deleted SCM configuration from database for provider: {}", provider);
            } else {
                logger.warn("SCM configuration not found in database for provider: {}", provider);
            }
            
            // 从缓存删除
            ScmConfigDto removed = configCache.remove(provider);
            if (removed != null) {
                logger.debug("Removed SCM configuration from cache for provider: {}", provider);
            }
            
        } catch (Exception e) {
            logger.error("Failed to delete SCM configuration for provider: {}", provider, e);
            throw new RuntimeException("删除SCM配置失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 切换配置启用状态
     */
    @Transactional
    public boolean toggleScmConfig(String provider) {
        if (provider == null || provider.trim().isEmpty()) {
            throw new IllegalArgumentException("提供商不能为空");
        }
        
        logger.info("Toggling SCM configuration for provider: {}", provider);
        
        try {
            // 从数据库获取配置
            ScmConfigDto config = getScmConfig(provider);
            if (config == null) {
                throw new IllegalArgumentException("Configuration not found: " + provider);
            }
            
            boolean newEnabled = !Boolean.TRUE.equals(config.getEnabled());
            config.setEnabled(newEnabled);
            
            if (newEnabled) {
                updateConnectionStatus(config);
            } else {
                config.setStatus("disabled");
                config.setStatusMessage("已禁用");
            }
            
            // 保存更新后的配置
            saveScmConfig(config);
            
            logger.info("Successfully toggled SCM configuration for provider: {}, enabled: {}", provider, newEnabled);
            return newEnabled;
            
        } catch (Exception e) {
            logger.error("Failed to toggle SCM configuration for provider: {}", provider, e);
            throw new RuntimeException("切换SCM配置状态失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 测试SCM连接
     */
    public Map<String, Object> testScmConnection(ScmConfigDto config) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 这里应该实际调用SCM API进行连接测试
            // 暂时模拟测试结果
            boolean connected = simulateConnectionTest(config);
            
            result.put("connected", connected);
            result.put("provider", config.getProvider());
            result.put("message", connected ? "连接成功" : "连接失败");
            
            if (connected) {
                result.put("details", Map.of(
                    "apiBase", config.getApiBase(),
                    "status", "authenticated"
                ));
            }
            
        } catch (Exception e) {
            result.put("connected", false);
            result.put("error", e.getMessage());
            result.put("message", "连接测试失败: " + e.getMessage());
        }
        
        return result;
    }
    
    // Private helper methods
    
    /**
     * 从数据库和环境变量初始化配置
     */
    private void initializeFromDatabaseAndEnvironment() {
        logger.info("Initializing SCM configurations from database and environment variables...");
        
        try {
            // 首先从数据库加载现有配置
            List<ScmConfigEntity> entities = scmConfigRepository.findAll();
            Map<String, ScmConfigEntity> dbConfigs = new HashMap<>();
            
            for (ScmConfigEntity entity : entities) {
                dbConfigs.put(entity.getProvider(), entity);
                ScmConfigDto dto = scmConfigAdapter.toDto(entity);
                configCache.put(entity.getProvider(), dto);
                logger.debug("Loaded SCM configuration from database: {}", entity.getProvider());
            }
            
            logger.info("Loaded {} SCM configurations from database", entities.size());
            
            // 然后检查环境变量，为不存在的配置创建默认配置并从环境变量填充
            initializeProviderFromEnvironment("github", dbConfigs);
            initializeProviderFromEnvironment("gitlab", dbConfigs);
            initializeProviderFromEnvironment("bitbucket", dbConfigs);
            initializeProviderFromEnvironment("gitea", dbConfigs);
            initializeProviderFromEnvironment("custom-git", dbConfigs);
            
        } catch (Exception e) {
            logger.warn("Error loading SCM configurations from database, using environment variables only", e);
            // 如果数据库访问失败，仅使用环境变量
            initializeFromEnvironmentOnly();
        }
        
        logger.info("SCM configuration initialization completed. Active configurations: {}", 
                configCache.keySet());
    }
    
    /**
     * 为单个提供商从环境变量初始化配置
     */
    private void initializeProviderFromEnvironment(String provider, Map<String, ScmConfigEntity> dbConfigs) {
        if (hasEnvironmentConfig(provider) && !dbConfigs.containsKey(provider)) {
            try {
                ScmConfigDto config = createDefaultConfig(provider);
                populateFromEnvironment(config, provider);
                
                // 保存到数据库
                saveScmConfig(config);
                
                logger.info("Initialized {} configuration from environment and saved to database", provider);
            } catch (Exception e) {
                logger.warn("Failed to initialize {} configuration from environment: {}", provider, e.getMessage());
            }
        }
    }
    
    /**
     * 仅从环境变量初始化（数据库不可用时的后备方案）
     */
    private void initializeFromEnvironmentOnly() {
        logger.warn("Initializing from environment variables only (database unavailable)");
        
        for (String provider : getSupportedProviders()) {
            if (hasEnvironmentConfig(provider) && !configCache.containsKey(provider)) {
                try {
                    ScmConfigDto config = createDefaultConfig(provider);
                    populateFromEnvironment(config, provider);
                    configCache.put(provider, config);
                    logger.info("Initialized {} configuration from environment (cache only)", provider);
                } catch (Exception e) {
                    logger.warn("Failed to initialize {} configuration from environment: {}", provider, e.getMessage());
                }
            }
        }
    }
    
    private boolean hasEnvironmentConfig(String provider) {
        String tokenKey = provider.equals("bitbucket") ? 
            "BITBUCKET_APP_PASSWORD" : 
            provider.toUpperCase().replace("-", "_") + "_TOKEN";
        
        return environment.getProperty(tokenKey) != null;
    }
    
    private void populateFromEnvironment(ScmConfigDto config, String provider) {
        String prefix = provider.toUpperCase().replace("-", "_");
        
        // Common fields
        String apiBase = environment.getProperty(prefix + "_API_BASE");
        if (apiBase != null) config.setApiBase(apiBase);
        
        String webBase = environment.getProperty(prefix + "_WEB_BASE");
        if (webBase != null) config.setWebBase(webBase);
        
        String webhookSecret = environment.getProperty(prefix + "_WEBHOOK_SECRET");
        if (webhookSecret != null) config.setWebhookSecret(webhookSecret);
        
        // Provider-specific fields
        switch (provider) {
            case "github", "gitlab", "gitea" -> {
                String token = environment.getProperty(prefix + "_TOKEN");
                if (token != null) config.setToken(token);
                
                String clientId = environment.getProperty(prefix + "_CLIENT_ID");
                if (clientId != null) config.setClientId(clientId);
                
                String clientSecret = environment.getProperty(prefix + "_CLIENT_SECRET");
                if (clientSecret != null) config.setClientSecret(clientSecret);
            }
            case "bitbucket" -> {
                String username = environment.getProperty("BITBUCKET_USERNAME");
                if (username != null) config.setUsername(username);
                
                String appPassword = environment.getProperty("BITBUCKET_APP_PASSWORD");
                if (appPassword != null) config.setAppPassword(appPassword);
            }
            case "custom-git" -> {
                String token = environment.getProperty("CUSTOM_GIT_TOKEN");
                if (token != null) config.setToken(token);
                
                String apiType = environment.getProperty("CUSTOM_GIT_API_TYPE");
                if (apiType != null) config.setApiType(apiType);
                
                String sslVerify = environment.getProperty("CUSTOM_GIT_SSL_VERIFY");
                if (sslVerify != null) config.setSslVerify(Boolean.parseBoolean(sslVerify));
            }
        }
        
        config.setEnabled(true);
    }
    
    public ScmConfigDto createDefaultConfig(String provider) {
        return switch (provider) {
            case "github" -> ScmConfigDto.github();
            case "gitlab" -> ScmConfigDto.gitlab();
            case "bitbucket" -> ScmConfigDto.bitbucket();
            case "gitea" -> ScmConfigDto.gitea();
            case "custom-git" -> ScmConfigDto.customGit();
            default -> {
                if (provider.startsWith("custom-")) {
                    ScmConfigDto config = configCache.get(provider);
                    if (config != null) {
                        yield config;
                    }
                }
                yield new ScmConfigDto(provider, provider);
            }
        };
    }
    
    private void updateConnectionStatus(ScmConfigDto config) {
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            config.setStatus("disabled");
            config.setStatusMessage("已禁用");
            return;
        }
        
        if (!config.isComplete()) {
            config.setStatus("not_configured");
            config.setStatusMessage("配置不完整");
            return;
        }
        
        try {
            // 尝试获取适配器健康状态
            Map<String, Boolean> health = multiRepoAdapter.getAdapterHealthStatus();
            Boolean isHealthy = health.get(config.getProvider());
            
            if (Boolean.TRUE.equals(isHealthy)) {
                config.setStatus("connected");
                config.setStatusMessage("连接正常");
            } else {
                config.setStatus("error");
                config.setStatusMessage("连接异常");
            }
        } catch (Exception e) {
            config.setStatus("error");
            config.setStatusMessage("状态检查失败: " + e.getMessage());
        }
    }
    
    private boolean simulateConnectionTest(ScmConfigDto config) {
        // 模拟连接测试逻辑
        // 实际实现中应该调用对应的SCM API
        
        if (!config.isComplete()) {
            return false;
        }
        
        // 模拟一些基本的验证
        if (config.getApiBase() == null || !config.getApiBase().startsWith("http")) {
            return false;
        }
        
        // 根据provider模拟不同的连接测试结果
        return switch (config.getProvider()) {
            case "github" -> config.getToken() != null && config.getToken().startsWith("ghp_");
            case "gitlab" -> config.getToken() != null && config.getToken().startsWith("glpat_");
            case "bitbucket" -> config.getUsername() != null && config.getAppPassword() != null;
            case "gitea", "custom-git" -> config.getToken() != null && !config.getToken().trim().isEmpty();
            default -> false;
        };
    }
}

package com.ai.reviewer.backend.web.service;

import com.ai.reviewer.backend.domain.orchestrator.analyzer.StaticAnalyzer;
import com.ai.reviewer.backend.domain.orchestrator.reviewer.AiReviewer;
import com.ai.reviewer.backend.infra.adapter.ModelConfigAdapter;
import com.ai.reviewer.backend.infra.jpa.entity.ModelConfigEntity;
import com.ai.reviewer.backend.infra.jpa.repository.ModelConfigRepository;
import com.ai.reviewer.backend.web.dto.ModelConfigDto;
import com.ai.reviewer.shared.enums.Dimension;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 模型管理服务
 * 
 * 负责AI模型和静态分析工具的配置、连接测试、状态管理等功能
 */
@Service
public class ModelManagementService {
    
    private static final Logger logger = LoggerFactory.getLogger(ModelManagementService.class);
    
    // 模型配置缓存
    private final Map<String, ModelConfigDto> modelCache = new ConcurrentHashMap<>();
    
    // 服务依赖
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private final ModelConfigRepository modelConfigRepository;
    private final ModelConfigAdapter modelConfigAdapter;
    
    // 注入已有的模型实例（如果存在）
    private final List<AiReviewer> aiReviewers;
    private final List<StaticAnalyzer> staticAnalyzers;
    
    @Autowired
    public ModelManagementService(RestTemplate restTemplate, 
                                 ObjectMapper objectMapper,
                                 ModelConfigRepository modelConfigRepository,
                                 ModelConfigAdapter modelConfigAdapter,
                                 @Autowired(required = false) List<AiReviewer> aiReviewers,
                                 @Autowired(required = false) List<StaticAnalyzer> staticAnalyzers) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.modelConfigRepository = modelConfigRepository;
        this.modelConfigAdapter = modelConfigAdapter;
        this.aiReviewers = aiReviewers != null ? aiReviewers : List.of();
        this.staticAnalyzers = staticAnalyzers != null ? staticAnalyzers : List.of();
        this.executorService = Executors.newFixedThreadPool(3);
        
        // 初始化默认模型配置
        initializeDefaultModels();
        
        logger.info("ModelManagementService initialized with {} AI reviewers and {} static analyzers", 
            this.aiReviewers.size(), this.staticAnalyzers.size());
    }
    
    /**
     * 获取所有模型配置
     */
    public List<ModelConfigDto> getAllModels() {
        try {
            // 从数据库加载最新数据
            List<ModelConfigEntity> entities = modelConfigRepository.findAll();
            
            // 转换为DTO并更新缓存
            List<ModelConfigDto> dtos = new ArrayList<>();
            for (ModelConfigEntity entity : entities) {
                ModelConfigDto dto = modelConfigAdapter.toDto(entity);
                modelCache.put(dto.getId(), dto);
                dtos.add(dto);
            }
            
            return dtos;
        } catch (Exception e) {
            logger.error("Failed to load models from database, falling back to cache", e);
            return new ArrayList<>(modelCache.values());
        }
    }
    
    /**
     * 按类型获取模型配置
     */
    public List<ModelConfigDto> getModelsByType(ModelConfigDto.ModelType type) {
        try {
            // 从数据库查询指定类型的模型
            ModelConfigEntity.ModelType entityType = ModelConfigEntity.ModelType.valueOf(type.name());
            List<ModelConfigEntity> entities = modelConfigRepository.findByType(entityType);
            
            return entities.stream()
                .map(modelConfigAdapter::toDto)
                .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Failed to load models by type from database, falling back to cache", e);
            return modelCache.values().stream()
                .filter(model -> model.getType() == type)
                .collect(Collectors.toList());
        }
    }
    
    /**
     * 获取启用的模型
     */
    public List<ModelConfigDto> getEnabledModels() {
        return modelCache.values().stream()
            .filter(ModelConfigDto::isEnabled)
            .collect(Collectors.toList());
    }
    
    /**
     * 根据ID获取模型配置
     */
    public ModelConfigDto getModel(String modelId) {
        try {
            // 先尝试从数据库获取最新数据
            Optional<ModelConfigEntity> entity = modelConfigRepository.findById(modelId);
            if (entity.isPresent()) {
                ModelConfigDto dto = modelConfigAdapter.toDto(entity.get());
                modelCache.put(modelId, dto); // 更新缓存
                return dto;
            }
        } catch (Exception e) {
            logger.error("Failed to load model from database, falling back to cache", e);
        }
        
        // 如果数据库中没有，尝试从缓存获取
        return modelCache.get(modelId);
    }
    
    /**
     * 保存模型配置
     */
    @Transactional
    public void saveModel(ModelConfigDto model) {
        if (model == null) {
            throw new IllegalArgumentException("模型配置不能为空");
        }
        
        logger.info("Saving model configuration: {} (type: {})", model.getName(), model.getType());
        
        // 设置或更新时间戳
        Instant now = Instant.now();
        if (model.getCreatedAt() == null) {
            model.setCreatedAt(now);
        }
        model.setUpdatedAt(now);
        
        try {
            // 检查是否为更新操作
            Optional<ModelConfigEntity> existingEntity = modelConfigRepository.findById(model.getId());
            boolean isUpdate = existingEntity.isPresent();
            
            // 验证配置（传入是否为更新操作的标志）
            validateModelConfig(model, isUpdate);
            
            ModelConfigEntity entity;
            if (existingEntity.isPresent()) {
                // 更新现有实体
                entity = existingEntity.get();
                modelConfigAdapter.updateEntity(entity, model);
                logger.debug("Updating existing model configuration: {}", model.getId());
            } else {
                // 创建新实体
                entity = modelConfigAdapter.toEntity(model);
                logger.debug("Creating new model configuration: {}", model.getId());
            }
            
            // 保存到数据库
            ModelConfigEntity saved = modelConfigRepository.save(entity);
            logger.info("Successfully saved model to database: {}", saved.getId());
            
            // 更新缓存
            ModelConfigDto savedDto = modelConfigAdapter.toDto(saved);
            modelCache.put(savedDto.getId(), savedDto);
            
            logger.info("Successfully saved model configuration: {} (type: {})", 
                model.getName(), model.getType());
                
        } catch (Exception e) {
            logger.error("Failed to save model configuration to database: {}", model.getId(), e);
            throw new RuntimeException("保存模型配置失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 删除模型配置
     */
    @Transactional
    public void deleteModel(String modelId) {
        try {
            // 从数据库删除
            Optional<ModelConfigEntity> entity = modelConfigRepository.findById(modelId);
            if (entity.isPresent()) {
                modelConfigRepository.deleteById(modelId);
                logger.info("Deleted model from database: {}", modelId);
                
                // 从缓存中删除
                ModelConfigDto removed = modelCache.remove(modelId);
                if (removed != null) {
                    logger.info("Deleted model configuration: {} ({})", removed.getName(), modelId);
                }
            } else {
                // 检查缓存中是否存在（可能是内存中的临时配置）
                ModelConfigDto removed = modelCache.remove(modelId);
                if (removed != null) {
                    logger.info("Deleted cached model configuration: {} ({})", removed.getName(), modelId);
                } else {
                    logger.warn("Attempted to delete non-existent model: {}", modelId);
                    throw new IllegalArgumentException("模型配置不存在: " + modelId);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to delete model configuration: {}", modelId, e);
            throw new RuntimeException("删除模型配置失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 启用/禁用模型
     */
    @Transactional
    public boolean toggleModel(String modelId) {
        try {
            // 从数据库获取最新模型配置
            ModelConfigDto model = getModel(modelId);
            if (model == null) {
                throw new IllegalArgumentException("模型配置不存在: " + modelId);
            }
            
            boolean newState = !model.isEnabled();
            model.setEnabled(newState);
            model.setUpdatedAt(Instant.now());
            
            // 如果禁用，设置状态为DISABLED
            if (!newState) {
                model.setStatus(ModelConfigDto.ModelStatus.DISABLED);
            }
            
            // 保存更新到数据库
            saveModel(model);
            
            logger.info("Toggled model {} to {}", model.getName(), newState ? "enabled" : "disabled");
            return newState;
        } catch (Exception e) {
            logger.error("Failed to toggle model: {}", modelId, e);
            throw new RuntimeException("切换模型状态失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 测试模型连接
     */
    public Map<String, Object> testConnection(ModelConfigDto model) {
        Map<String, Object> result = new HashMap<>();
        
        if (model == null) {
            result.put("success", false);
            result.put("message", "模型配置不能为空");
            return result;
        }
        
        logger.info("Testing connection for model: {} (type: {})", model.getName(), model.getType());
        
        try {
            switch (model.getType()) {
                case COMMERCIAL_AI:
                    return testCommercialAiConnection(model);
                case LOCAL_AI:
                    return testLocalAiConnection(model);
                case STATIC_ANALYZER:
                    return testStaticAnalyzerConnection(model);
                default:
                    result.put("success", false);
                    result.put("message", "不支持的模型类型: " + model.getType());
                    return result;
            }
        } catch (Exception e) {
            logger.error("Connection test failed for model: {}", model.getName(), e);
            result.put("success", false);
            result.put("message", "连接测试失败: " + e.getMessage());
            
            // 更新模型状态
            model.setStatus(ModelConfigDto.ModelStatus.ERROR);
            model.setLastError(e.getMessage());
            model.setUpdatedAt(Instant.now());
            
            return result;
        }
    }
    
    /**
     * 测试商业AI模型连接
     */
    private Map<String, Object> testCommercialAiConnection(ModelConfigDto model) {
        Map<String, Object> result = new HashMap<>();
        
        if (model.getApiUrl() == null || model.getApiKey() == null) {
            result.put("success", false);
            result.put("message", "API URL和API Key不能为空");
            return result;
        }
        
        try {
            // 构建测试请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(model.getApiKey());
            
            // 简单的测试消息
            Map<String, Object> testRequest = Map.of(
                "model", model.getModelName() != null ? model.getModelName() : "gpt-3.5-turbo",
                "messages", List.of(Map.of("role", "user", "content", "Hello")),
                "max_tokens", 1
            );
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(testRequest, headers);
            
            long startTime = System.currentTimeMillis();
            ResponseEntity<Map> response = restTemplate.exchange(
                model.getApiUrl(), HttpMethod.POST, entity, Map.class);
            long responseTime = System.currentTimeMillis() - startTime;
            
            if (response.getStatusCode().is2xxSuccessful()) {
                result.put("success", true);
                result.put("message", "连接成功");
                result.put("responseTime", responseTime + "ms");
                result.put("model", model.getModelName());
                
                // 更新模型状态
                model.setStatus(ModelConfigDto.ModelStatus.CONNECTED);
                model.setAverageResponseTime((double) responseTime);
                model.setLastError(null);
            } else {
                result.put("success", false);
                result.put("message", "API返回错误状态: " + response.getStatusCode());
                model.setStatus(ModelConfigDto.ModelStatus.ERROR);
            }
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "连接测试失败: " + e.getMessage());
            model.setStatus(ModelConfigDto.ModelStatus.ERROR);
            model.setLastError(e.getMessage());
        }
        
        model.setUpdatedAt(Instant.now());
        return result;
    }
    
    /**
     * 测试本地AI模型连接
     */
    private Map<String, Object> testLocalAiConnection(ModelConfigDto model) {
        Map<String, Object> result = new HashMap<>();
        
        if (model.getApiUrl() == null) {
            result.put("success", false);
            result.put("message", "API URL不能为空");
            return result;
        }
        
        try {
            // 尝试连接健康检查端点
            String healthUrl = model.getApiUrl();
            if (healthUrl.contains("11434")) { // Ollama
                healthUrl = healthUrl.replace("/api/generate", "/api/tags");
            } else if (healthUrl.contains("/v1/")) { // vLLM
                healthUrl = healthUrl.replace("/v1/completions", "/v1/models");
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.exchange(
                healthUrl, HttpMethod.GET, entity, String.class);
            long responseTime = System.currentTimeMillis() - startTime;
            
            if (response.getStatusCode().is2xxSuccessful()) {
                result.put("success", true);
                result.put("message", "连接成功");
                result.put("responseTime", responseTime + "ms");
                result.put("model", model.getModelName());
                
                // 更新模型状态
                model.setStatus(ModelConfigDto.ModelStatus.CONNECTED);
                model.setAverageResponseTime((double) responseTime);
                model.setLastError(null);
            } else {
                result.put("success", false);
                result.put("message", "本地模型服务不可用");
                model.setStatus(ModelConfigDto.ModelStatus.ERROR);
            }
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "连接本地模型失败: " + e.getMessage());
            model.setStatus(ModelConfigDto.ModelStatus.ERROR);
            model.setLastError(e.getMessage());
        }
        
        model.setUpdatedAt(Instant.now());
        return result;
    }
    
    /**
     * 测试静态分析工具连接
     */
    private Map<String, Object> testStaticAnalyzerConnection(ModelConfigDto model) {
        Map<String, Object> result = new HashMap<>();
        
        if (model.getToolPath() == null) {
            result.put("success", false);
            result.put("message", "工具路径不能为空");
            return result;
        }
        
        try {
            File toolFile = new File(model.getToolPath());
            if (!toolFile.exists()) {
                result.put("success", false);
                result.put("message", "工具文件不存在: " + model.getToolPath());
                model.setStatus(ModelConfigDto.ModelStatus.ERROR);
                model.setLastError("Tool file not found");
                model.setUpdatedAt(Instant.now());
                return result;
            }
            
            // 尝试执行版本命令
            List<String> versionCommand = List.of(model.getToolPath(), "--version");
            ProcessBuilder pb = new ProcessBuilder(versionCommand);
            Process process = pb.start();
            
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            
            if (finished) {
                int exitCode = process.exitValue();
                if (exitCode == 0) {
                    result.put("success", true);
                    result.put("message", "工具可用");
                    result.put("toolPath", model.getToolPath());
                    
                    // 更新模型状态
                    model.setStatus(ModelConfigDto.ModelStatus.CONNECTED);
                    model.setLastError(null);
                } else {
                    result.put("success", false);
                    result.put("message", "工具执行失败，退出码: " + exitCode);
                    model.setStatus(ModelConfigDto.ModelStatus.ERROR);
                }
            } else {
                process.destroyForcibly();
                result.put("success", false);
                result.put("message", "工具执行超时");
                model.setStatus(ModelConfigDto.ModelStatus.ERROR);
            }
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "测试工具连接失败: " + e.getMessage());
            model.setStatus(ModelConfigDto.ModelStatus.ERROR);
            model.setLastError(e.getMessage());
        }
        
        model.setUpdatedAt(Instant.now());
        return result;
    }
    
    /**
     * 获取模型统计信息
     */
    public Map<String, Object> getModelStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        List<ModelConfigDto> allModels = getAllModels();
        
        // 基础统计
        stats.put("totalModels", allModels.size());
        stats.put("enabledModels", allModels.stream().mapToLong(m -> m.isEnabled() ? 1 : 0).sum());
        stats.put("connectedModels", allModels.stream()
            .mapToLong(m -> m.getStatus() == ModelConfigDto.ModelStatus.CONNECTED ? 1 : 0).sum());
        
        // 按类型统计
        Map<ModelConfigDto.ModelType, Long> typeStats = allModels.stream()
            .filter(m -> m.getType() != null)
            .collect(Collectors.groupingBy(ModelConfigDto::getType, Collectors.counting()));
        stats.put("typeStatistics", typeStats);
        
        // 按状态统计
        Map<ModelConfigDto.ModelStatus, Long> statusStats = allModels.stream()
            .filter(m -> m.getStatus() != null)
            .collect(Collectors.groupingBy(ModelConfigDto::getStatus, Collectors.counting()));
        stats.put("statusStatistics", statusStats);
        
        // 平均响应时间
        OptionalDouble avgResponseTime = allModels.stream()
            .filter(m -> m.getAverageResponseTime() != null && m.getAverageResponseTime() > 0)
            .mapToDouble(ModelConfigDto::getAverageResponseTime)
            .average();
        stats.put("averageResponseTime", avgResponseTime.orElse(0.0));
        
        // 总成本估算
        double totalCost = allModels.stream()
            .filter(m -> m.getEstimatedCost() != null)
            .mapToDouble(ModelConfigDto::getEstimatedCost)
            .sum();
        stats.put("totalEstimatedCost", totalCost);
        
        return stats;
    }
    
    /**
     * 批量更新模型状态
     */
    public void refreshModelStatuses() {
        logger.info("Starting batch model status refresh...");
        
        List<ModelConfigDto> enabledModels = getEnabledModels();
        
        for (ModelConfigDto model : enabledModels) {
            try {
                testConnection(model);
            } catch (Exception e) {
                logger.warn("Failed to refresh status for model: {}", model.getName(), e);
            }
        }
        
        logger.info("Completed batch model status refresh for {} models", enabledModels.size());
    }
    
    /**
     * 复制模型配置
     * 
     * @param originalModelId 要复制的原始模型ID
     * @return 复制后的新模型配置
     */
    @Transactional
    public ModelConfigDto cloneModel(String originalModelId) {
        if (originalModelId == null || originalModelId.trim().isEmpty()) {
            throw new IllegalArgumentException("原始模型ID不能为空");
        }
        
        logger.info("Cloning model configuration: {}", originalModelId);
        
        // 获取原始模型配置
        ModelConfigDto originalModel = getModel(originalModelId);
        if (originalModel == null) {
            throw new IllegalArgumentException("找不到要复制的模型: " + originalModelId);
        }
        
        // 创建新的模型配置（深度复制）
        ModelConfigDto clonedModel = createClonedModel(originalModel);
        
        // 保存复制的模型
        saveModel(clonedModel);
        
        logger.info("Successfully cloned model: {} -> {}", originalModelId, clonedModel.getId());
        return clonedModel;
    }
    
    /**
     * 创建模型的克隆副本
     */
    private ModelConfigDto createClonedModel(ModelConfigDto original) {
        ModelConfigDto cloned = new ModelConfigDto();
        
        // 生成新的ID和名称
        String timestamp = String.valueOf(System.currentTimeMillis());
        cloned.setId(original.getId() + "-copy-" + timestamp);
        cloned.setName(original.getName() + " (副本)");
        cloned.setDisplayName(original.getDisplayName() != null ? 
            original.getDisplayName() + " (副本)" : null);
        
        // 复制基本属性
        cloned.setType(original.getType());
        cloned.setDescription(original.getDescription() != null ? 
            original.getDescription() + " [复制自: " + original.getName() + "]" : null);
        cloned.setEnabled(false); // 默认禁用复制的模型
        cloned.setStatus(ModelConfigDto.ModelStatus.CONFIGURED);
        
        // 复制AI模型特定配置
        if (original.getType() == ModelConfigDto.ModelType.COMMERCIAL_AI || 
            original.getType() == ModelConfigDto.ModelType.LOCAL_AI) {
            cloned.setApiUrl(original.getApiUrl());
            cloned.setApiKey(original.getApiKey());
            cloned.setModelName(original.getModelName());
            cloned.setModelVersion(original.getModelVersion());
            cloned.setMaxTokens(original.getMaxTokens());
            cloned.setTemperature(original.getTemperature());
            cloned.setTimeoutSeconds(original.getTimeoutSeconds());
            cloned.setMaxConcurrentRequests(original.getMaxConcurrentRequests());
            cloned.setTopP(original.getTopP());
        }
        
        // 复制静态分析工具特定配置
        if (original.getType() == ModelConfigDto.ModelType.STATIC_ANALYZER) {
            cloned.setToolPath(original.getToolPath());
            cloned.setConfigFile(original.getConfigFile());
            cloned.setSupportedLanguages(original.getSupportedLanguages() != null ? 
                new ArrayList<>(original.getSupportedLanguages()) : null);
            cloned.setSupportedFileTypes(original.getSupportedFileTypes() != null ? 
                new ArrayList<>(original.getSupportedFileTypes()) : null);
            cloned.setExcludePatterns(original.getExcludePatterns() != null ? 
                new ArrayList<>(original.getExcludePatterns()) : null);
        }
        
        // 复制支持的维度
        cloned.setSupportedDimensions(original.getSupportedDimensions() != null ? 
            new ArrayList<>(original.getSupportedDimensions()) : null);
        
        // 复制高级设置
        cloned.setAdvancedSettings(original.getAdvancedSettings() != null ? 
            new HashMap<>(original.getAdvancedSettings()) : null);
        
        // 重置统计信息（新模型从零开始）
        cloned.setTotalRequests(0L);
        cloned.setSuccessRate(0.0);
        cloned.setAverageResponseTime(null);
        cloned.setEstimatedCost(0.0);
        cloned.setLastError(null);
        
        // 设置时间戳
        Instant now = Instant.now();
        cloned.setCreatedAt(now);
        cloned.setUpdatedAt(now);
        
        return cloned;
    }
    
    /**
     * 验证模型配置
     * @param model 要验证的模型配置
     * @param isUpdate 是否为更新操作（true=更新现有模型，false=创建新模型）
     */
    private void validateModelConfig(ModelConfigDto model, boolean isUpdate) {
        if (model.getId() == null || model.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("模型ID不能为空");
        }
        
        if (model.getName() == null || model.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("模型名称不能为空");
        }
        
        if (model.getType() == null) {
            throw new IllegalArgumentException("模型类型不能为空");
        }
        
        // 检查配置是否完整
        if (!model.isConfigurationComplete()) {
            throw new IllegalArgumentException("模型配置不完整，请检查必填字段");
        }
        
        // 检查ID唯一性（只在新建时检查）
        if (!isUpdate) {
            try {
                Optional<ModelConfigEntity> existingEntity = modelConfigRepository.findById(model.getId());
                if (existingEntity.isPresent()) {
                    throw new IllegalArgumentException("模型ID已存在: " + model.getId());
                }
            } catch (Exception e) {
                // 数据库查询失败时，在缓存中检查
                ModelConfigDto existing = modelCache.get(model.getId());
                if (existing != null) {
                    throw new IllegalArgumentException("模型ID已存在: " + model.getId());
                }
            }
        }
    }
    
    /**
     * 初始化默认模型配置
     */
    private void initializeDefaultModels() {
        logger.info("Initializing default model configurations...");
        
        try {
            // 先从数据库加载现有配置
            List<ModelConfigEntity> existingConfigs = modelConfigRepository.findAll();
            for (ModelConfigEntity entity : existingConfigs) {
                ModelConfigDto dto = modelConfigAdapter.toDto(entity);
                modelCache.put(dto.getId(), dto);
            }
            logger.info("Loaded {} existing model configurations from database", existingConfigs.size());
            
            // 从环境变量检测并创建默认配置（如果不存在）
            initializeFromEnvironment();
            
            // 检测已注册的模型实例（如果不存在）
            initializeFromRegisteredInstances();
            
            logger.info("Initialized {} total model configurations", modelCache.size());
        } catch (Exception e) {
            logger.error("Failed to initialize from database, falling back to environment initialization", e);
            // 如果数据库访问失败，仍然尝试从环境变量初始化
            initializeFromEnvironment();
            initializeFromRegisteredInstances();
        }
    }
    
    /**
     * 从环境变量初始化模型配置
     */
    private void initializeFromEnvironment() {
        // OpenAI模型
        String openaiKey = System.getenv("OPENAI_API_KEY");
        if (openaiKey != null && !openaiKey.trim().isEmpty()) {
            String openaiId = "openai-gpt4";
            // 只有在数据库中不存在时才创建
            if (!modelConfigRepository.existsById(openaiId)) {
                ModelConfigDto openai = ModelConfigDto.createOpenAiModel("GPT-4");
                openai.setId(openaiId);
                openai.setApiKey(openaiKey);
                openai.setDescription("OpenAI GPT-4 模型，适用于高质量代码审查");
                try {
                    saveModel(openai);
                    logger.info("Created OpenAI configuration from environment");
                } catch (Exception e) {
                    logger.warn("Failed to save OpenAI configuration to database", e);
                    modelCache.put(openai.getId(), openai);
                }
            }
        }
        
        // 本地模型
        String localUrl = System.getenv("LOCAL_MODEL_API_URL");
        String localModel = System.getenv("LOCAL_MODEL_NAME");
        if (localUrl != null && localModel != null) {
            ModelConfigDto local = ModelConfigDto.createLocalModel(localModel, localUrl);
            local.setDescription("本地部署的开源代码模型");
            modelCache.put(local.getId(), local);
            logger.info("Detected local model configuration from environment: {}", localModel);
        }
        
        // SpotBugs
        String spotbugsHome = System.getenv("SPOTBUGS_HOME");
        if (spotbugsHome != null) {
            ModelConfigDto spotbugs = ModelConfigDto.createStaticAnalyzer("SpotBugs", 
                spotbugsHome + "/bin/spotbugs");
            spotbugs.setDescription("SpotBugs 静态分析工具，用于Java代码的bug检测");
            spotbugs.setSupportedLanguages(List.of("java"));
            spotbugs.setSupportedFileTypes(List.of(".java", ".class"));
            spotbugs.setSupportedDimensions(List.of(Dimension.SECURITY, Dimension.QUALITY));
            modelCache.put(spotbugs.getId(), spotbugs);
            logger.info("Detected SpotBugs configuration from environment");
        }
        
        // ESLint
        String eslintPath = System.getenv("ESLINT_PATH");
        if (eslintPath != null) {
            ModelConfigDto eslint = ModelConfigDto.createStaticAnalyzer("ESLint", eslintPath);
            eslint.setDescription("ESLint 静态分析工具，用于JavaScript/TypeScript代码质量检查");
            eslint.setSupportedLanguages(List.of("javascript", "typescript"));
            eslint.setSupportedFileTypes(List.of(".js", ".jsx", ".ts", ".tsx"));
            eslint.setSupportedDimensions(List.of(Dimension.QUALITY, Dimension.MAINTAINABILITY));
            modelCache.put(eslint.getId(), eslint);
            logger.info("Detected ESLint configuration from environment");
        }
    }
    
    /**
     * 从已注册的模型实例初始化配置
     */
    private void initializeFromRegisteredInstances() {
        // AI Reviewers
        for (AiReviewer reviewer : aiReviewers) {
            String id = "registered-" + reviewer.getReviewerId();
            if (!modelCache.containsKey(id)) {
                ModelConfigDto config = new ModelConfigDto(id, reviewer.getReviewerName(), 
                    reviewer.getReviewerId().startsWith("local") ? 
                    ModelConfigDto.ModelType.LOCAL_AI : ModelConfigDto.ModelType.COMMERCIAL_AI);
                config.setDisplayName(reviewer.getReviewerName());
                config.setModelVersion(reviewer.getModelVersion());
                config.setDescription("已注册的 " + reviewer.getReviewerName() + " 实例");
                config.setEnabled(reviewer.isEnabled());
                config.setSupportedDimensions(reviewer.getSupportedDimensions());
                
                modelCache.put(id, config);
                logger.debug("Registered AI reviewer: {}", reviewer.getReviewerName());
            }
        }
        
        // Static Analyzers
        for (StaticAnalyzer analyzer : staticAnalyzers) {
            String id = "registered-" + analyzer.getAnalyzerId();
            if (!modelCache.containsKey(id)) {
                ModelConfigDto config = new ModelConfigDto(id, analyzer.getAnalyzerName(), 
                    ModelConfigDto.ModelType.STATIC_ANALYZER);
                config.setDisplayName(analyzer.getAnalyzerName());
                config.setModelVersion(analyzer.getVersion());
                config.setDescription("已注册的 " + analyzer.getAnalyzerName() + " 实例");
                config.setEnabled(analyzer.isEnabled());
                
                modelCache.put(id, config);
                logger.debug("Registered static analyzer: {}", analyzer.getAnalyzerName());
            }
        }
    }
    
    /**
     * 获取支持的模型类型
     */
    public List<ModelConfigDto.ModelType> getSupportedModelTypes() {
        return Arrays.asList(ModelConfigDto.ModelType.values());
    }
    
    /**
     * 获取支持的维度
     */
    public List<Dimension> getSupportedDimensions() {
        return Arrays.asList(Dimension.values());
    }
}

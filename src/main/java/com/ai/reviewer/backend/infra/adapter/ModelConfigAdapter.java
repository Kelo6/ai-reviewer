package com.ai.reviewer.backend.infra.adapter;

import com.ai.reviewer.backend.infra.jpa.entity.ModelConfigEntity;
import com.ai.reviewer.backend.web.dto.ModelConfigDto;
import com.ai.reviewer.shared.enums.Dimension;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 模型配置实体与DTO之间的转换适配器
 */
@Component
public class ModelConfigAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(ModelConfigAdapter.class);
    
    private final ObjectMapper objectMapper;
    
    @Autowired
    public ModelConfigAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * 将实体转换为DTO
     */
    public ModelConfigDto toDto(ModelConfigEntity entity) {
        if (entity == null) {
            return null;
        }
        
        ModelConfigDto dto = new ModelConfigDto();
        
        // 基础字段
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDisplayName(entity.getDisplayName());
        dto.setType(convertType(entity.getType()));
        dto.setDescription(entity.getDescription());
        dto.setEnabled(entity.getEnabled());
        dto.setStatus(convertStatus(entity.getStatus()));
        
        // AI模型配置
        dto.setApiUrl(entity.getApiUrl());
        dto.setApiKey(entity.getApiKey());
        dto.setModelName(entity.getModelName());
        dto.setModelVersion(entity.getModelVersion());
        dto.setMaxTokens(entity.getMaxTokens());
        dto.setTemperature(entity.getTemperature());
        dto.setTopP(entity.getTopP());
        dto.setTimeoutSeconds(entity.getTimeoutSeconds());
        dto.setMaxConcurrentRequests(entity.getMaxConcurrentRequests());
        
        // 静态分析工具配置
        dto.setToolPath(entity.getToolPath());
        dto.setConfigFile(entity.getConfigFile());
        dto.setSupportedLanguages(parseStringList(entity.getSupportedLanguages()));
        dto.setSupportedFileTypes(parseStringList(entity.getSupportedFileTypes()));
        dto.setSupportedDimensions(parseDimensionList(entity.getSupportedDimensions()));
        dto.setExcludePatterns(parseStringList(entity.getExcludePatterns()));
        
        // 高级配置
        dto.setAdvancedSettings(parseStringMap(entity.getAdvancedSettings()));
        
        // 统计信息
        dto.setTotalRequests(entity.getTotalRequests());
        dto.setSuccessRate(entity.getSuccessRate());
        dto.setAverageResponseTime(entity.getAverageResponseTime());
        dto.setEstimatedCost(entity.getEstimatedCost());
        dto.setLastError(entity.getLastError());
        
        // 时间戳
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        
        return dto;
    }
    
    /**
     * 将DTO转换为实体
     */
    public ModelConfigEntity toEntity(ModelConfigDto dto) {
        if (dto == null) {
            return null;
        }
        
        ModelConfigEntity entity = new ModelConfigEntity();
        
        // 基础字段
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setDisplayName(dto.getDisplayName());
        entity.setType(convertType(dto.getType()));
        entity.setDescription(dto.getDescription());
        entity.setEnabled(dto.isEnabled());
        entity.setStatus(convertStatus(dto.getStatus()));
        
        // AI模型配置
        entity.setApiUrl(dto.getApiUrl());
        entity.setApiKey(dto.getApiKey());
        entity.setModelName(dto.getModelName());
        entity.setModelVersion(dto.getModelVersion());
        entity.setMaxTokens(dto.getMaxTokens());
        entity.setTemperature(dto.getTemperature());
        entity.setTopP(dto.getTopP());
        entity.setTimeoutSeconds(dto.getTimeoutSeconds());
        entity.setMaxConcurrentRequests(dto.getMaxConcurrentRequests());
        
        // 静态分析工具配置
        entity.setToolPath(dto.getToolPath());
        entity.setConfigFile(dto.getConfigFile());
        entity.setSupportedLanguages(toJsonString(dto.getSupportedLanguages()));
        entity.setSupportedFileTypes(toJsonString(dto.getSupportedFileTypes()));
        entity.setSupportedDimensions(dimensionsToJsonString(dto.getSupportedDimensions()));
        entity.setExcludePatterns(toJsonString(dto.getExcludePatterns()));
        
        // 高级配置
        entity.setAdvancedSettings(mapToJsonString(dto.getAdvancedSettings()));
        
        // 统计信息
        entity.setTotalRequests(dto.getTotalRequests());
        entity.setSuccessRate(dto.getSuccessRate());
        entity.setAverageResponseTime(dto.getAverageResponseTime());
        entity.setEstimatedCost(dto.getEstimatedCost());
        entity.setLastError(dto.getLastError());
        
        // 时间戳
        entity.setCreatedAt(dto.getCreatedAt());
        entity.setUpdatedAt(dto.getUpdatedAt());
        
        return entity;
    }
    
    /**
     * 更新实体字段（用于更新操作）
     */
    public void updateEntity(ModelConfigEntity entity, ModelConfigDto dto) {
        if (entity == null || dto == null) {
            return;
        }
        
        // 更新可变字段
        entity.setName(dto.getName());
        entity.setDisplayName(dto.getDisplayName());
        entity.setDescription(dto.getDescription());
        entity.setEnabled(dto.isEnabled());
        entity.setStatus(convertStatus(dto.getStatus()));
        
        // 更新配置字段
        entity.setApiUrl(dto.getApiUrl());
        entity.setApiKey(dto.getApiKey());
        entity.setModelName(dto.getModelName());
        entity.setModelVersion(dto.getModelVersion());
        entity.setMaxTokens(dto.getMaxTokens());
        entity.setTemperature(dto.getTemperature());
        entity.setTopP(dto.getTopP());
        entity.setTimeoutSeconds(dto.getTimeoutSeconds());
        entity.setMaxConcurrentRequests(dto.getMaxConcurrentRequests());
        
        entity.setToolPath(dto.getToolPath());
        entity.setConfigFile(dto.getConfigFile());
        entity.setSupportedLanguages(toJsonString(dto.getSupportedLanguages()));
        entity.setSupportedFileTypes(toJsonString(dto.getSupportedFileTypes()));
        entity.setSupportedDimensions(dimensionsToJsonString(dto.getSupportedDimensions()));
        entity.setExcludePatterns(toJsonString(dto.getExcludePatterns()));
        
        entity.setAdvancedSettings(mapToJsonString(dto.getAdvancedSettings()));
        
        // 更新统计信息
        entity.setTotalRequests(dto.getTotalRequests());
        entity.setSuccessRate(dto.getSuccessRate());
        entity.setAverageResponseTime(dto.getAverageResponseTime());
        entity.setEstimatedCost(dto.getEstimatedCost());
        entity.setLastError(dto.getLastError());
    }
    
    // 类型转换方法
    private ModelConfigDto.ModelType convertType(ModelConfigEntity.ModelType entityType) {
        if (entityType == null) return null;
        return ModelConfigDto.ModelType.valueOf(entityType.name());
    }
    
    private ModelConfigEntity.ModelType convertType(ModelConfigDto.ModelType dtoType) {
        if (dtoType == null) return null;
        return ModelConfigEntity.ModelType.valueOf(dtoType.name());
    }
    
    private ModelConfigDto.ModelStatus convertStatus(ModelConfigEntity.ModelStatus entityStatus) {
        if (entityStatus == null) return null;
        return ModelConfigDto.ModelStatus.valueOf(entityStatus.name());
    }
    
    private ModelConfigEntity.ModelStatus convertStatus(ModelConfigDto.ModelStatus dtoStatus) {
        if (dtoStatus == null) return null;
        return ModelConfigEntity.ModelStatus.valueOf(dtoStatus.name());
    }
    
    // JSON序列化/反序列化方法
    private List<String> parseStringList(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            logger.warn("Failed to parse string list from JSON: {}", json, e);
            return Collections.emptyList();
        }
    }
    
    private List<Dimension> parseDimensionList(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Dimension>>() {});
        } catch (JsonProcessingException e) {
            logger.warn("Failed to parse dimension list from JSON: {}", json, e);
            return Collections.emptyList();
        }
    }
    
    private Map<String, Object> parseStringMap(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            logger.warn("Failed to parse string map from JSON: {}", json, e);
            return Collections.emptyMap();
        }
    }
    
    private String toJsonString(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to serialize string list to JSON: {}", list, e);
            return null;
        }
    }
    
    private String dimensionsToJsonString(List<Dimension> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(dimensions);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to serialize dimension list to JSON: {}", dimensions, e);
            return null;
        }
    }
    
    private String mapToJsonString(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to serialize map to JSON: {}", map, e);
            return null;
        }
    }
}

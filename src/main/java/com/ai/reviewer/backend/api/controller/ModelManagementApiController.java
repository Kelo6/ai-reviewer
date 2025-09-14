package com.ai.reviewer.backend.api.controller;

import com.ai.reviewer.backend.api.dto.ApiResponse;
import com.ai.reviewer.backend.web.dto.ModelConfigDto;
import com.ai.reviewer.backend.web.service.ModelManagementService;
import com.ai.reviewer.shared.enums.Dimension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 模型管理API控制器
 * 
 * 专门处理模型配置的REST API请求，返回JSON数据
 */
@RestController
@RequestMapping("/api/models")
@CrossOrigin(origins = "*")
public class ModelManagementApiController {
    
    private static final Logger logger = LoggerFactory.getLogger(ModelManagementApiController.class);
    
    private final ModelManagementService modelService;
    
    @Autowired
    public ModelManagementApiController(ModelManagementService modelService) {
        this.modelService = modelService;
    }
    
    /**
     * 获取所有模型配置
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<List<ModelConfigDto>> getAllModels() {
        try {
            logger.info("API request: Getting all models");
            List<ModelConfigDto> models = modelService.getAllModels();
            logger.info("API response: Found {} models", models.size());
            return ApiResponse.success(models);
        } catch (Exception e) {
            logger.error("Failed to get all models", e);
            return ApiResponse.error("FETCH_ERROR", "获取模型列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据ID获取特定模型配置
     */
    @GetMapping(value = "/{modelId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ModelConfigDto> getModel(@PathVariable String modelId) {
        try {
            logger.info("API request: Getting model {}", modelId);
            ModelConfigDto model = modelService.getModel(modelId);
            if (model != null) {
                return ApiResponse.success(model);
            } else {
                return ApiResponse.error("NOT_FOUND", "模型配置不存在: " + modelId);
            }
        } catch (Exception e) {
            logger.error("Failed to get model: {}", modelId, e);
            return ApiResponse.error("FETCH_ERROR", "获取模型配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 保存模型配置
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<String> saveModel(@RequestBody ModelConfigDto model) {
        String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);
        
        try {
            logger.info("[{}] API request: Save model {} (type: {})", 
                requestId, model != null ? model.getName() : "null", 
                model != null ? model.getType() : "null");
            
            if (model == null) {
                return ApiResponse.error("INVALID_CONFIG", "模型配置不能为空");
            }
            
            // 验证基础字段
            if (model.getName() == null || model.getName().trim().isEmpty()) {
                return ApiResponse.error("INVALID_NAME", "模型名称不能为空");
            }
            
            if (model.getType() == null) {
                return ApiResponse.error("INVALID_TYPE", "模型类型不能为空");
            }
            
            logger.info("[{}] Pre-validation passed, calling service", requestId);
            
            modelService.saveModel(model);
            
            logger.info("[{}] API response: Successfully saved model {}", requestId, model.getName());
            return ApiResponse.success("模型配置保存成功");
            
        } catch (IllegalArgumentException e) {
            logger.warn("[{}] Validation error: {}", requestId, e.getMessage());
            return ApiResponse.error("VALIDATION_ERROR", e.getMessage());
        } catch (Exception e) {
            logger.error("[{}] Failed to save model", requestId, e);
            return ApiResponse.error("SAVE_ERROR", "保存模型配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新模型配置
     */
    @PutMapping(value = "/{modelId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<String> updateModel(@PathVariable String modelId, @RequestBody ModelConfigDto model) {
        try {
            logger.info("API request: Update model {}", modelId);
            
            if (model == null) {
                return ApiResponse.error("INVALID_CONFIG", "模型配置不能为空");
            }
            
            // 确保ID一致
            model.setId(modelId);
            
            modelService.saveModel(model);
            
            logger.info("API response: Successfully updated model {}", modelId);
            return ApiResponse.success("模型配置更新成功");
            
        } catch (IllegalArgumentException e) {
            logger.warn("Validation error for model {}: {}", modelId, e.getMessage());
            return ApiResponse.error("VALIDATION_ERROR", e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to update model: {}", modelId, e);
            return ApiResponse.error("UPDATE_ERROR", "更新模型配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除模型配置
     */
    @DeleteMapping(value = "/{modelId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<String> deleteModel(@PathVariable String modelId) {
        try {
            logger.info("API request: Delete model {}", modelId);
            
            modelService.deleteModel(modelId);
            
            logger.info("API response: Successfully deleted model {}", modelId);
            return ApiResponse.success("模型配置删除成功");
            
        } catch (IllegalArgumentException e) {
            logger.warn("Model not found for deletion: {}", modelId);
            return ApiResponse.error("NOT_FOUND", e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to delete model: {}", modelId, e);
            return ApiResponse.error("DELETE_ERROR", "删除模型配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 启用/禁用模型
     */
    @PostMapping(value = "/{modelId}/toggle", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Boolean> toggleModel(@PathVariable String modelId) {
        try {
            logger.info("API request: Toggle model {}", modelId);
            
            boolean newState = modelService.toggleModel(modelId);
            
            logger.info("API response: Model {} toggled to {}", modelId, newState ? "enabled" : "disabled");
            return ApiResponse.success(newState);
            
        } catch (IllegalArgumentException e) {
            logger.warn("Model not found for toggle: {}", modelId);
            return ApiResponse.error("NOT_FOUND", e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to toggle model: {}", modelId, e);
            return ApiResponse.error("TOGGLE_ERROR", "切换模型状态失败: " + e.getMessage());
        }
    }
    
    /**
     * 测试模型连接（使用提供的配置）
     */
    @PostMapping(value = "/test", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String, Object>> testModelConfiguration(@RequestBody ModelConfigDto model) {
        String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);
        
        try {
            logger.info("[{}] API request: Test model configuration for {} (type: {})", 
                requestId, model != null ? model.getName() : "null", 
                model != null ? model.getType() : "null");
            
            if (model == null) {
                return ApiResponse.error("INVALID_CONFIG", "模型配置不能为空");
            }
            
            Map<String, Object> result = modelService.testConnection(model);
            
            logger.info("[{}] API response: Test completed successfully", requestId);
            return ApiResponse.success(result);
            
        } catch (Exception e) {
            logger.error("[{}] Failed to test model configuration", requestId, e);
            return ApiResponse.error("TEST_ERROR", "测试模型连接失败: " + e.getMessage());
        }
    }
    
    /**
     * 测试已保存的模型连接（通过模型ID）
     */
    @PostMapping(value = "/{modelId}/test", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String, Object>> testModel(@PathVariable String modelId) {
        try {
            logger.info("API request: Test model {}", modelId);
            
            ModelConfigDto model = modelService.getModel(modelId);
            if (model == null) {
                return ApiResponse.error("NOT_FOUND", "模型配置不存在: " + modelId);
            }
            
            Map<String, Object> result = modelService.testConnection(model);
            
            logger.info("API response: Test completed for model {}", modelId);
            return ApiResponse.success(result);
            
        } catch (Exception e) {
            logger.error("Failed to test model: {}", modelId, e);
            return ApiResponse.error("TEST_ERROR", "测试模型连接失败: " + e.getMessage());
        }
    }
    
    /**
     * 按类型获取模型
     */
    @GetMapping(value = "/type/{type}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<List<ModelConfigDto>> getModelsByType(@PathVariable String type) {
        try {
            logger.info("API request: Get models by type {}", type);
            
            ModelConfigDto.ModelType modelType = ModelConfigDto.ModelType.valueOf(type.toUpperCase());
            List<ModelConfigDto> models = modelService.getModelsByType(modelType);
            
            logger.info("API response: Found {} models of type {}", models.size(), type);
            return ApiResponse.success(models);
            
        } catch (IllegalArgumentException e) {
            return ApiResponse.error("INVALID_TYPE", "无效的模型类型: " + type);
        } catch (Exception e) {
            logger.error("Failed to get models by type: {}", type, e);
            return ApiResponse.error("FETCH_ERROR", "获取模型列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取模型统计信息
     */
    @GetMapping(value = "/statistics", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String, Object>> getStatistics() {
        try {
            logger.info("API request: Get model statistics");
            
            Map<String, Object> statistics = modelService.getModelStatistics();
            
            logger.info("API response: Statistics retrieved");
            return ApiResponse.success(statistics);
            
        } catch (Exception e) {
            logger.error("Failed to get model statistics", e);
            return ApiResponse.error("FETCH_ERROR", "获取统计信息失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取支持的模型类型
     */
    @GetMapping(value = "/types", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<List<ModelConfigDto.ModelType>> getSupportedTypes() {
        try {
            logger.info("API request: Get supported model types");
            
            List<ModelConfigDto.ModelType> types = modelService.getSupportedModelTypes();
            
            return ApiResponse.success(types);
            
        } catch (Exception e) {
            logger.error("Failed to get supported types", e);
            return ApiResponse.error("FETCH_ERROR", "获取支持的模型类型失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取支持的维度
     */
    @GetMapping(value = "/dimensions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<List<Dimension>> getSupportedDimensions() {
        try {
            logger.info("API request: Get supported dimensions");
            
            List<Dimension> dimensions = modelService.getSupportedDimensions();
            
            return ApiResponse.success(dimensions);
            
        } catch (Exception e) {
            logger.error("Failed to get supported dimensions", e);
            return ApiResponse.error("FETCH_ERROR", "获取支持的维度失败: " + e.getMessage());
        }
    }
    
    /**
     * 刷新所有模型状态
     */
    @PostMapping(value = "/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<String> refreshModelStatuses() {
        try {
            logger.info("API request: Refresh all model statuses");
            
            modelService.refreshModelStatuses();
            
            logger.info("API response: All model statuses refreshed");
            return ApiResponse.success("模型状态刷新完成");
            
        } catch (Exception e) {
            logger.error("Failed to refresh model statuses", e);
            return ApiResponse.error("REFRESH_ERROR", "刷新模型状态失败: " + e.getMessage());
        }
    }
}

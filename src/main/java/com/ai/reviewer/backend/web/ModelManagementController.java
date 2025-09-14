package com.ai.reviewer.backend.web;

import com.ai.reviewer.backend.web.dto.ModelConfigDto;
import com.ai.reviewer.backend.web.service.ModelManagementService;
import com.ai.reviewer.shared.enums.Dimension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型管理Web页面控制器
 * 
 * 只处理Web页面渲染，所有API调用请使用 ModelManagementApiController
 */
@Controller
@RequestMapping("/models")
public class ModelManagementController {
    
    private static final Logger logger = LoggerFactory.getLogger(ModelManagementController.class);
    
    private final ModelManagementService modelService;
    
    @Autowired
    public ModelManagementController(ModelManagementService modelService) {
        this.modelService = modelService;
    }
    
    /**
     * 模型管理主页面
     */
    @GetMapping
    public String modelManagementPage(Model model) {
        try {
            logger.info("Loading model management page");
            
            // 获取所有模型配置（使用安全的数据获取）
            List<ModelConfigDto> allModels = Collections.emptyList();
            Map<String, Object> stats = createDefaultStatistics();
            
            try {
                allModels = modelService.getAllModels();
                logger.debug("Loaded {} models", allModels.size());
            } catch (Exception e) {
                logger.warn("Failed to load models, using empty list", e);
                allModels = Collections.emptyList();
            }
            
            try {
                stats = modelService.getModelStatistics();
                logger.debug("Loaded statistics: {}", stats);
            } catch (Exception e) {
                logger.warn("Failed to load statistics, using default values", e);
                stats = createDefaultStatistics();
            }
            
            // 按类型分组
            List<ModelConfigDto> aiModels = allModels.stream()
                .filter(m -> m.getType() == ModelConfigDto.ModelType.COMMERCIAL_AI || 
                           m.getType() == ModelConfigDto.ModelType.LOCAL_AI)
                .toList();
            
            List<ModelConfigDto> staticAnalyzers = allModels.stream()
                .filter(m -> m.getType() == ModelConfigDto.ModelType.STATIC_ANALYZER)
                .toList();
            
            // 设置模板变量
            model.addAttribute("aiModels", aiModels);
            model.addAttribute("staticAnalyzers", staticAnalyzers);
            model.addAttribute("statistics", stats);
            model.addAttribute("modelTypes", Arrays.asList(ModelConfigDto.ModelType.values()));
            model.addAttribute("dimensions", Arrays.asList(Dimension.values()));
            
            logger.info("Successfully loaded model management page with {} AI models and {} analyzers", 
                aiModels.size(), staticAnalyzers.size());
                
            return "models/model-management";
            
        } catch (Exception e) {
            logger.error("Failed to load model management page", e);
            
            // 设置安全的默认值
            model.addAttribute("aiModels", Collections.emptyList());
            model.addAttribute("staticAnalyzers", Collections.emptyList());
            model.addAttribute("statistics", createDefaultStatistics());
            model.addAttribute("modelTypes", Arrays.asList(ModelConfigDto.ModelType.values()));
            model.addAttribute("dimensions", Arrays.asList(Dimension.values()));
            model.addAttribute("error", "加载页面失败: " + e.getMessage());
            
            return "models/model-management";
        }
    }
    
    /**
     * 获取模型配置表单（新增和编辑通用）
     */
    @GetMapping("/form")
    public String getModelForm(@RequestParam(required = false) String id, 
                              @RequestParam(defaultValue = "COMMERCIAL_AI") String type, 
                              Model model) {
        try {
            logger.info("Loading model form - id: {}, type: {}", id, type);
            
            if (id != null && !id.trim().isEmpty()) {
                // 编辑模式：加载现有模型配置
                ModelConfigDto existingModel = modelService.getModel(id);
                if (existingModel != null) {
                    model.addAttribute("model", existingModel);
                    model.addAttribute("isNew", false);
                    logger.debug("Loaded existing model for editing: {}", id);
                } else {
                    logger.warn("Model not found for editing: {}, creating new model", id);
                    // 创建默认模型作为后备
                    ModelConfigDto.ModelType modelType = ModelConfigDto.ModelType.valueOf(type.toUpperCase());
                    ModelConfigDto defaultConfig = createDefaultModelConfig(modelType);
                    model.addAttribute("model", defaultConfig);
                    model.addAttribute("isNew", true);
                    model.addAttribute("error", "模型配置不存在，已创建新的配置模板");
                }
            } else {
                // 新增模式：创建默认模型配置
                ModelConfigDto.ModelType modelType = ModelConfigDto.ModelType.valueOf(type.toUpperCase());
                ModelConfigDto config = createDefaultModelConfig(modelType);
                model.addAttribute("model", config);
                model.addAttribute("isNew", true);
                logger.debug("Created new model for adding with type: {}", modelType);
            }
            
            // 添加表单所需的数据
            model.addAttribute("modelTypes", Arrays.asList(ModelConfigDto.ModelType.values()));
            model.addAttribute("dimensions", Arrays.asList(Dimension.values()));
            
            return "models/fragments/model-form";
            
        } catch (Exception e) {
            logger.error("Error loading model form", e);
            model.addAttribute("error", "加载表单失败: " + e.getMessage());
            
            // 提供安全的默认值
            ModelConfigDto defaultConfig = createDefaultModelConfig(ModelConfigDto.ModelType.COMMERCIAL_AI);
            model.addAttribute("model", defaultConfig);
            model.addAttribute("isNew", true);
            model.addAttribute("modelTypes", Arrays.asList(ModelConfigDto.ModelType.values()));
            model.addAttribute("dimensions", Arrays.asList(Dimension.values()));
            
            return "models/fragments/model-form";
        }
    }

    /**
     * 新增模型配置表单
     */
    @GetMapping("/new")
    public String newModelForm(@RequestParam(defaultValue = "COMMERCIAL_AI") String type, Model model) {
        try {
            logger.info("Loading new model form for type: {}", type);
            
            ModelConfigDto.ModelType modelType = ModelConfigDto.ModelType.valueOf(type.toUpperCase());
            ModelConfigDto config = createDefaultModelConfig(modelType);
            
            model.addAttribute("model", config);
            model.addAttribute("isNew", true);
            model.addAttribute("modelTypes", Arrays.asList(ModelConfigDto.ModelType.values()));
            model.addAttribute("dimensions", Arrays.asList(Dimension.values()));
            
            return "models/fragments/model-form";
            
        } catch (Exception e) {
            logger.error("Error loading new model form", e);
            model.addAttribute("error", "创建新模型表单失败: " + e.getMessage());
            model.addAttribute("model", new ModelConfigDto());
            model.addAttribute("isNew", true);
            model.addAttribute("modelTypes", Arrays.asList(ModelConfigDto.ModelType.values()));
            model.addAttribute("dimensions", Arrays.asList(Dimension.values()));
            return "models/fragments/model-form";
        }
    }
    
    /**
     * 编辑模型配置表单
     */
    @GetMapping("/{id}/edit")
    public String editModelForm(@PathVariable String id, Model model) {
        try {
            logger.info("Loading edit form for model: {}", id);
            
            ModelConfigDto config = modelService.getModel(id);
            boolean isNew = (config == null);
            
            if (config == null) {
                logger.warn("Model not found: {}, creating new config", id);
                config = new ModelConfigDto();
                config.setId(id);
                config.setName("新建模型");
                config.setType(ModelConfigDto.ModelType.COMMERCIAL_AI);
            }
            
            model.addAttribute("model", config);
            model.addAttribute("isNew", isNew);
            model.addAttribute("modelTypes", Arrays.asList(ModelConfigDto.ModelType.values()));
            model.addAttribute("dimensions", Arrays.asList(Dimension.values()));
            
            return "models/fragments/model-form";
            
        } catch (Exception e) {
            logger.error("Error loading model form for ID: {}", id, e);
            model.addAttribute("error", "加载模型表单失败: " + e.getMessage());
            model.addAttribute("model", new ModelConfigDto());
            model.addAttribute("isNew", true);
            model.addAttribute("modelTypes", Arrays.asList(ModelConfigDto.ModelType.values()));
            model.addAttribute("dimensions", Arrays.asList(Dimension.values()));
            return "models/fragments/model-form";
        }
    }
    
    /**
     * 获取模型详情片段
     */
    @GetMapping("/{id}/details")
    public String getModelDetails(@PathVariable String id, Model model) {
        try {
            logger.info("Loading model details for ID: {}", id);
            
            ModelConfigDto modelConfig = modelService.getModel(id);
            if (modelConfig == null) {
                model.addAttribute("error", "模型配置不存在: " + id);
                return "models/fragments/model-details";
            }
            
            model.addAttribute("model", modelConfig);
            return "models/fragments/model-details";
            
        } catch (Exception e) {
            logger.error("Error loading model details: {}", id, e);
            model.addAttribute("error", "加载模型详情失败: " + e.getMessage());
            return "models/fragments/model-details";
        }
    }
    
    /**
     * 创建默认模型配置
     */
    private ModelConfigDto createDefaultModelConfig(ModelConfigDto.ModelType type) {
        switch (type) {
            case COMMERCIAL_AI:
                ModelConfigDto openai = ModelConfigDto.createOpenAiModel("新建AI模型");
                openai.setId("new-commercial-ai-" + System.currentTimeMillis());
                return openai;
                
            case LOCAL_AI:
                ModelConfigDto local = ModelConfigDto.createLocalModel("新建本地模型", "http://localhost:11434/api/generate");
                local.setId("new-local-ai-" + System.currentTimeMillis());
                return local;
                
            case STATIC_ANALYZER:
                ModelConfigDto analyzer = ModelConfigDto.createStaticAnalyzer("新建静态分析工具", "/path/to/tool");
                analyzer.setId("new-static-analyzer-" + System.currentTimeMillis());
                return analyzer;
                
            default:
                throw new IllegalArgumentException("不支持的模型类型: " + type);
        }
    }
    
    /**
     * 创建默认统计数据对象
     * 用于在获取统计数据失败时提供安全的默认值
     */
    private Map<String, Object> createDefaultStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalModels", 0);
        stats.put("enabledModels", 0);
        stats.put("connectedModels", 0);
        stats.put("averageResponseTime", 0.0);
        stats.put("totalEstimatedCost", 0.0);
        stats.put("typeStatistics", Collections.emptyMap());
        stats.put("statusStatistics", Collections.emptyMap());
        return stats;
    }
    
    /**
     * 复制模型配置
     */
    @PostMapping("/{id}/clone")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> cloneModel(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            logger.info("Cloning model configuration: {}", id);
            
            ModelConfigDto clonedModel = modelService.cloneModel(id);
            
            result.put("ok", true);
            result.put("data", Map.of(
                "id", clonedModel.getId(),
                "name", clonedModel.getName(),
                "message", "模型复制成功"
            ));
            
            logger.info("Successfully cloned model: {} -> {}", id, clonedModel.getId());
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Failed to clone model: {}", id, e);
            result.put("ok", false);
            result.put("error", Map.of(
                "code", "CLONE_FAILED",
                "message", "复制失败: " + e.getMessage()
            ));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 切换模型启用状态
     */
    @PostMapping("/{id}/toggle")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleModel(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            logger.info("Toggling model status: {}", id);
            
            boolean newState = modelService.toggleModel(id);
            String message = newState ? "模型已启用" : "模型已禁用";
            
            result.put("ok", true);
            result.put("data", message);
            
            logger.info("Successfully toggled model: {} to {}", id, newState ? "enabled" : "disabled");
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Failed to toggle model: {}", id, e);
            result.put("ok", false);
            result.put("error", Map.of(
                "code", "TOGGLE_FAILED",
                "message", "切换状态失败: " + e.getMessage()
            ));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 删除模型配置
     */
    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteModel(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            logger.info("Deleting model configuration: {}", id);
            
            modelService.deleteModel(id);
            
            result.put("ok", true);
            result.put("data", "模型删除成功");
            
            logger.info("Successfully deleted model: {}", id);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Failed to delete model: {}", id, e);
            result.put("ok", false);
            result.put("error", Map.of(
                "code", "DELETE_FAILED",
                "message", "删除失败: " + e.getMessage()
            ));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 刷新模型状态
     */
    @PostMapping("/refresh")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> refreshModels() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            logger.info("Refreshing all model statuses");
            
            modelService.refreshModelStatuses();
            
            result.put("ok", true);
            result.put("data", "模型状态刷新完成");
            
            logger.info("Successfully refreshed all model statuses");
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Failed to refresh model statuses", e);
            result.put("ok", false);
            result.put("error", Map.of(
                "code", "REFRESH_FAILED",
                "message", "刷新失败: " + e.getMessage()
            ));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
}
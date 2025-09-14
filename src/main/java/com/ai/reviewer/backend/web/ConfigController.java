package com.ai.reviewer.backend.web;

import com.ai.reviewer.backend.api.dto.ApiResponse;
import com.ai.reviewer.backend.web.dto.ScmConfigDto;
import com.ai.reviewer.backend.web.service.ScmConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 配置管理控制器
 */
@Controller
@RequestMapping("/config")
public class ConfigController {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigController.class);
    
    private final ScmConfigService configService;
    
    @Autowired
    public ConfigController(ScmConfigService configService) {
        this.configService = configService;
    }
    
    /**
     * 配置管理主页面
     */
    @GetMapping
    public String configPage(Model model) {
        try {
            List<ScmConfigDto> scmConfigs = configService.getAllScmConfigs();
            model.addAttribute("scmConfigs", scmConfigs);
            model.addAttribute("supportedProviders", configService.getSupportedProviders());
            
            return "config/enhanced-scm-config";
        } catch (Exception e) {
            logger.error("Error loading config page", e);
            model.addAttribute("error", "加载配置页面失败: " + e.getMessage());
            return "config/enhanced-scm-config";
        }
    }
    
    /**
     * 获取SCM配置列表 (HTMX)
     */
    @GetMapping("/scm/list")
    public String getScmConfigList(Model model) {
        try {
            List<ScmConfigDto> scmConfigs = configService.getAllScmConfigs();
            model.addAttribute("scmConfigs", scmConfigs);
            return "fragments/scm-config-list";
        } catch (Exception e) {
            logger.error("Error loading SCM config list", e);
            model.addAttribute("error", "加载配置列表失败: " + e.getMessage());
            return "fragments/scm-config-list";
        }
    }
    
    /**
     * 获取特定SCM配置表单 (HTMX)
     */
    @GetMapping("/scm/{provider}/form")
    public String getScmConfigForm(@PathVariable String provider, Model model) {
        try {
            ScmConfigDto config = configService.getScmConfig(provider);
            if (config == null) {
                // 创建默认配置
                config = configService.createDefaultConfig(provider);
            }
            model.addAttribute("config", config);
            model.addAttribute("provider", provider);
            
            // 根据提供商返回相应的表单片段
            switch (provider) {
                case "github":
                    return "fragments/enhanced-scm-config-form :: github-form";
                case "gitlab":
                    return "fragments/enhanced-scm-config-form :: gitlab-form";
                case "bitbucket":
                    return "fragments/enhanced-scm-config-form :: bitbucket-form";
                case "gitea":
                    return "fragments/enhanced-scm-config-form :: gitea-form";
                default:
                    if (provider.startsWith("custom-")) {
                        return "fragments/enhanced-scm-config-form :: gitea-form";
                    }
                    return "fragments/enhanced-scm-config-form :: default-form";
            }
        } catch (Exception e) {
            logger.error("Error loading SCM config form for provider: {}", provider, e);
            model.addAttribute("error", "加载配置表单失败: " + e.getMessage());
            return "fragments/enhanced-scm-config-form :: default-form";
        }
    }
    
    /**
     * 测试SCM配置连接 (AJAX)
     */
    @PostMapping("/scm/test")
    @ResponseBody
    public ApiResponse<Map<String, Object>> testScmConfig(@RequestBody ScmConfigDto config) {
        try {
            logger.info("Starting connection test for provider: {}", 
                config != null ? config.getProvider() : "null");
            
            if (config == null) {
                logger.error("Received null config object for connection test");
                return ApiResponse.error("INVALID_CONFIG", "配置对象不能为空");
            }
            
            Map<String, Object> result = configService.testConnection(config);
            
            if (result != null && Boolean.TRUE.equals(result.get("success"))) {
                logger.info("Connection test successful for provider: {}", config.getProvider());
            } else {
                logger.warn("Connection test failed for provider: {}, result: {}", 
                    config.getProvider(), result);
            }
            
            return ApiResponse.success(result);
            
        } catch (IllegalArgumentException e) {
            logger.warn("Connection test validation failed for provider: {}, error: {}", 
                config != null ? config.getProvider() : "unknown", e.getMessage());
            return ApiResponse.error("VALIDATION_FAILED", e.getMessage());
            
        } catch (Exception e) {
            logger.error("Unexpected error during connection test for provider: {}", 
                config != null ? config.getProvider() : "unknown", e);
            
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                errorMessage = "连接测试时发生未知错误";
            }
            
            return ApiResponse.error("TEST_FAILED", "连接测试失败: " + errorMessage);
        }
    }
    
    /**
     * 保存SCM配置 (AJAX)
     */
    @PostMapping("/scm")
    @ResponseBody
    public ApiResponse<String> saveScmConfig(@RequestBody ScmConfigDto config) {
        String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);
        try {
            logger.info("[{}] Received save request for SCM config", requestId);
            
            // 详细记录请求内容（不包括敏感信息）
            if (config != null) {
                logger.info("[{}] Config details - Provider: {}, API Base: {}, Display Name: {}, Has Token: {}", 
                    requestId,
                    config.getProvider(),
                    config.getApiBase(), 
                    config.getDisplayName(),
                    config.getToken() != null && !config.getToken().trim().isEmpty());
            } else {
                logger.error("[{}] Received null config object", requestId);
                return ApiResponse.error("INVALID_CONFIG", "配置对象不能为空");
            }
            
            // 预验证
            if (config.getProvider() == null || config.getProvider().trim().isEmpty()) {
                logger.error("[{}] Provider is null or empty", requestId);
                return ApiResponse.error("INVALID_PROVIDER", "提供商信息不能为空");
            }
            
            if (config.getApiBase() == null || config.getApiBase().trim().isEmpty()) {
                logger.error("[{}] API base is null or empty", requestId);
                return ApiResponse.error("INVALID_API_BASE", "API地址不能为空");
            }
            
            logger.info("[{}] Pre-validation passed, calling service", requestId);
            
            configService.saveScmConfig(config);
            
            logger.info("[{}] Successfully saved SCM config for provider: {}", requestId, config.getProvider());
            return ApiResponse.success("配置保存成功");
            
        } catch (IllegalArgumentException e) {
            logger.warn("[{}] Configuration validation failed for provider: {}, error: {}", 
                requestId, config != null ? config.getProvider() : "unknown", e.getMessage());
            return ApiResponse.error("VALIDATION_FAILED", e.getMessage());
            
        } catch (java.lang.NullPointerException e) {
            logger.error("[{}] Null pointer exception for provider: {}, stack trace:", 
                requestId, config != null ? config.getProvider() : "unknown", e);
            return ApiResponse.error("NULL_POINTER", "发生空指针异常，请检查必填字段");
            
        } catch (java.util.concurrent.RejectedExecutionException e) {
            logger.error("[{}] Execution rejected for provider: {}", 
                requestId, config != null ? config.getProvider() : "unknown", e);
            return ApiResponse.error("EXECUTION_REJECTED", "服务器繁忙，请稍后重试");
            
        } catch (RuntimeException e) {
            logger.error("[{}] Runtime exception for provider: {}, class: {}, message: {}", 
                requestId, config != null ? config.getProvider() : "unknown", 
                e.getClass().getSimpleName(), e.getMessage(), e);
            
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                errorMessage = "运行时异常: " + e.getClass().getSimpleName();
            }
            
            return ApiResponse.error("RUNTIME_ERROR", "运行时错误: " + errorMessage);
            
        } catch (Throwable t) {
            logger.error("[{}] Unexpected throwable for provider: {}, class: {}, message: {}", 
                requestId, config != null ? config.getProvider() : "unknown", 
                t.getClass().getName(), t.getMessage(), t);
            
            String errorMessage = t.getMessage();
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                errorMessage = "系统异常: " + t.getClass().getSimpleName();
            }
            
            return ApiResponse.error("SYSTEM_ERROR", "系统错误: " + errorMessage);
        }
    }
    
    /**
     * 保存SCM配置 (HTMX)
     */
    @PostMapping("/scm/save")
    public String saveScmConfig(@ModelAttribute ScmConfigDto config, Model model) {
        try {
            configService.saveScmConfig(config);
            
            // 返回成功消息和更新的配置列表
            model.addAttribute("success", "配置保存成功");
            List<ScmConfigDto> scmConfigs = configService.getAllScmConfigs();
            model.addAttribute("scmConfigs", scmConfigs);
            
            return "fragments/scm-config-list";
        } catch (Exception e) {
            logger.error("Error saving SCM config: {}", config.getProvider(), e);
            model.addAttribute("error", "保存配置失败: " + e.getMessage());
            model.addAttribute("config", config);
            model.addAttribute("provider", config.getProvider());
            model.addAttribute("isNew", false);
            
            return "fragments/scm-config-form";
        }
    }
    
    /**
     * 删除SCM配置 (HTMX)
     */
    @DeleteMapping("/scm/{provider}")
    public String deleteScmConfig(@PathVariable String provider, Model model) {
        try {
            configService.deleteScmConfig(provider);
            
            // 返回更新的配置列表
            List<ScmConfigDto> scmConfigs = configService.getAllScmConfigs();
            model.addAttribute("scmConfigs", scmConfigs);
            model.addAttribute("success", "配置删除成功");
            
            return "fragments/scm-config-list";
        } catch (Exception e) {
            logger.error("Error deleting SCM config: {}", provider, e);
            List<ScmConfigDto> scmConfigs = configService.getAllScmConfigs();
            model.addAttribute("scmConfigs", scmConfigs);
            model.addAttribute("error", "删除配置失败: " + e.getMessage());
            
            return "fragments/scm-config-list";
        }
    }
    
    /**
     * 启用/禁用SCM配置 (AJAX)
     */
    @PostMapping("/scm/{provider}/toggle")
    @ResponseBody
    public ApiResponse<String> toggleScmConfig(@PathVariable String provider) {
        try {
            boolean enabled = configService.toggleScmConfig(provider);
            String status = enabled ? "启用" : "禁用";
            return ApiResponse.success("配置已" + status);
        } catch (Exception e) {
            logger.error("Error toggling SCM config: {}", provider, e);
            return ApiResponse.error("TOGGLE_FAILED", "切换配置状态失败: " + e.getMessage());
        }
    }
}

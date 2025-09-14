package com.ai.reviewer.backend.web;

import com.ai.reviewer.backend.web.dto.ModelConfigDto;
import com.ai.reviewer.backend.web.service.ModelManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Map;

/**
 * 测试控制器
 */
@Controller
@RequestMapping("/test")
public class TestController {
    
    private static final Logger logger = LoggerFactory.getLogger(TestController.class);
    
    private final ModelManagementService modelService;
    
    @Autowired
    public TestController(ModelManagementService modelService) {
        this.modelService = modelService;
    }
    
    /**
     * 测试页面
     */
    @GetMapping("/models")
    public String testModelsPage(Model model) {
        try {
            logger.info("Loading test models page");
            
            // 获取所有模型配置
            List<ModelConfigDto> allModels = modelService.getAllModels();
            model.addAttribute("models", allModels);
            
            // 统计信息
            Map<String, Object> stats = modelService.getModelStatistics();
            model.addAttribute("statistics", stats);
            
            logger.info("Test models page loaded successfully with {} models", allModels.size());
            return "models/test-page";
            
        } catch (Exception e) {
            logger.error("Error loading test models page", e);
            model.addAttribute("error", "加载测试页面失败: " + e.getMessage());
            return "models/test-page";
        }
    }
}

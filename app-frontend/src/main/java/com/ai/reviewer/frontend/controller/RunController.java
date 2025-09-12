package com.ai.reviewer.frontend.controller;

import com.ai.reviewer.frontend.dto.ReviewRunDto;
import com.ai.reviewer.frontend.service.ReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Optional;

/**
 * 运行详情控制器。
 */
@Controller
public class RunController {
    
    private static final Logger logger = LoggerFactory.getLogger(RunController.class);
    
    private final ReviewService reviewService;
    
    @Autowired
    public RunController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }
    
    /**
     * 显示运行详情页面。
     */
    @GetMapping("/runs/{runId}")
    public String runDetails(@PathVariable String runId, Model model) {
        logger.debug("Rendering run details: runId={}", runId);
        
        try {
            Optional<ReviewRunDto> runOpt = reviewService.getReviewRun(runId);
            
            if (runOpt.isEmpty()) {
                logger.warn("Review run not found: runId={}", runId);
                model.addAttribute("error", "评审运行未找到: " + runId);
                return "error/404";
            }
            
            ReviewRunDto run = runOpt.get();
            
            // 添加基本信息
            model.addAttribute("run", run);
            model.addAttribute("runId", runId);
            
            // 计算统计数据
            if (run.findings() != null) {
                long criticalCount = run.findings().stream()
                    .filter(f -> f.severity().name().equals("CRITICAL"))
                    .count();
                long majorCount = run.findings().stream()
                    .filter(f -> f.severity().name().equals("MAJOR"))
                    .count();
                long minorCount = run.findings().stream()
                    .filter(f -> f.severity().name().equals("MINOR"))
                    .count();
                long infoCount = run.findings().stream()
                    .filter(f -> f.severity().name().equals("INFO"))
                    .count();
                
                model.addAttribute("criticalCount", criticalCount);
                model.addAttribute("majorCount", majorCount);
                model.addAttribute("minorCount", minorCount);
                model.addAttribute("infoCount", infoCount);
            }
            
            // 格式化时间和成本
            if (run.stats() != null) {
                long latencySeconds = run.stats().latencyMs() / 1000;
                model.addAttribute("latencySeconds", latencySeconds);
                
                if (run.stats().tokenCostUsd() != null) {
                    model.addAttribute("formattedCost", String.format("$%.3f", run.stats().tokenCostUsd()));
                }
            }
            
            return "run-details";
            
        } catch (Exception e) {
            logger.error("Error rendering run details: runId={}", runId, e);
            model.addAttribute("error", "加载运行详情失败: " + e.getMessage());
            model.addAttribute("runId", runId);
            return "error/500";
        }
    }
    
    /**
     * HTMX 获取问题列表。
     */
    @GetMapping("/runs/{runId}/findings")
    public String runFindings(@PathVariable String runId, Model model) {
        logger.debug("HTMX findings request: runId={}", runId);
        
        try {
            Optional<ReviewRunDto> runOpt = reviewService.getReviewRun(runId);
            
            if (runOpt.isEmpty()) {
                model.addAttribute("error", "评审运行未找到");
                return "fragments/findings :: error";
            }
            
            ReviewRunDto run = runOpt.get();
            model.addAttribute("findings", run.findings());
            
            return "fragments/findings :: findings-list";
            
        } catch (Exception e) {
            logger.error("Error loading findings: runId={}", runId, e);
            model.addAttribute("error", "加载问题列表失败: " + e.getMessage());
            return "fragments/findings :: error";
        }
    }
}

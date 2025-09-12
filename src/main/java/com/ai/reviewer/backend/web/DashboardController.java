package com.ai.reviewer.backend.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 仪表板控制器。
 */
@Controller
public class DashboardController {
    
    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);
    
    private final DashboardService dashboardService;
    
    @Autowired
    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }
    
    /**
     * 仪表板主页 - 显示历史运行记录。
     */
    @GetMapping({"/", "/dashboard"})
    public String dashboard(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "repo", required = false) String repoFilter,
            @RequestParam(name = "platform", required = false) String platformFilter,
            Model model) {
        
        logger.debug("Rendering dashboard: page={}, size={}, repoFilter={}, platformFilter={}", 
            page, size, repoFilter, platformFilter);
        
        try {
            // 创建分页请求，按创建时间降序排列
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            
            // 获取历史运行数据
            Page<DashboardService.ReviewRunSummary> runs = dashboardService.getHistoryRuns(
                pageable, repoFilter, platformFilter);
            
            // 添加模型数据
            model.addAttribute("runs", runs);
            model.addAttribute("currentPage", page);
            model.addAttribute("pageSize", size);
            model.addAttribute("repoFilter", repoFilter != null ? repoFilter : "");
            model.addAttribute("platformFilter", platformFilter != null ? platformFilter : "");
            model.addAttribute("totalPages", runs.getTotalPages());
            model.addAttribute("totalElements", runs.getTotalElements());
            model.addAttribute("hasNext", runs.hasNext());
            model.addAttribute("hasPrevious", runs.hasPrevious());
            
            return "dashboard";
            
        } catch (Exception e) {
            logger.error("Error rendering dashboard", e);
            model.addAttribute("error", "加载数据失败: " + e.getMessage());
            return "dashboard";
        }
    }
    
    /**
     * HTMX 分页加载。
     */
    @GetMapping("/dashboard/page")
    public String dashboardPage(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "repo", required = false) String repoFilter,
            @RequestParam(name = "platform", required = false) String platformFilter,
            Model model) {
        
        logger.debug("HTMX page request: page={}, size={}, repoFilter={}, platformFilter={}", 
            page, size, repoFilter, platformFilter);
        
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<DashboardService.ReviewRunSummary> runs = dashboardService.getHistoryRuns(
                pageable, repoFilter, platformFilter);
            
            model.addAttribute("runs", runs);
            model.addAttribute("currentPage", page);
            model.addAttribute("pageSize", size);
            model.addAttribute("repoFilter", repoFilter != null ? repoFilter : "");
            model.addAttribute("platformFilter", platformFilter != null ? platformFilter : "");
            model.addAttribute("totalPages", runs.getTotalPages());
            model.addAttribute("hasNext", runs.hasNext());
            model.addAttribute("hasPrevious", runs.hasPrevious());
            
            return "fragments/runs-table :: runs-table";
            
        } catch (Exception e) {
            logger.error("Error loading dashboard page", e);
            model.addAttribute("error", "加载数据失败: " + e.getMessage());
            return "fragments/runs-table :: error";
        }
    }
}

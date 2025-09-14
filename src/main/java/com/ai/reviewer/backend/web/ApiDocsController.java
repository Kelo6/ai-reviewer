package com.ai.reviewer.backend.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API 文档控制器
 */
@Controller
@RequestMapping("/api-docs")
public class ApiDocsController {

    @GetMapping("")
    public String apiDocs(Model model) {
        model.addAttribute("pageTitle", "API 文档");
        
        // 构建面包屑导航
        List<Map<String, String>> breadcrumbs = new ArrayList<>();
        breadcrumbs.add(Map.of("text", "API文档"));
        model.addAttribute("breadcrumbs", breadcrumbs);
        
        // 构建API端点列表
        List<Map<String, Object>> endpoints = buildApiEndpoints();
        model.addAttribute("endpoints", endpoints);
        
        return "api-docs";
    }
    
    private List<Map<String, Object>> buildApiEndpoints() {
        List<Map<String, Object>> endpoints = new ArrayList<>();
        
        // 健康检查
        endpoints.add(createEndpoint("GET", "/api/health", "健康检查", 
            "获取应用程序健康状态", "无需参数"));
        
        // 审查相关API
        endpoints.add(createEndpoint("POST", "/api/review/trigger", "触发代码审查", 
            "触发新的代码审查任务", "repositoryUrl, pullRequestId"));
        endpoints.add(createEndpoint("GET", "/api/review/status/{runId}", "获取审查状态", 
            "获取指定审查任务的状态", "runId (路径参数)"));
        endpoints.add(createEndpoint("GET", "/api/review/results/{runId}", "获取审查结果", 
            "获取指定审查任务的结果", "runId (路径参数)"));
        
        // 配置相关API
        endpoints.add(createEndpoint("GET", "/api/config/scm", "获取SCM配置", 
            "获取所有SCM平台配置", "无需参数"));
        endpoints.add(createEndpoint("POST", "/api/config/scm", "保存SCM配置", 
            "保存SCM平台配置", "provider, token, apiBase 等"));
        
        // Webhook相关API
        endpoints.add(createEndpoint("POST", "/api/webhooks/github", "GitHub Webhook", 
            "处理GitHub webhook事件", "GitHub webhook payload"));
        endpoints.add(createEndpoint("POST", "/api/webhooks/gitlab", "GitLab Webhook", 
            "处理GitLab webhook事件", "GitLab webhook payload"));
        endpoints.add(createEndpoint("POST", "/api/webhooks/bitbucket", "Bitbucket Webhook", 
            "处理Bitbucket webhook事件", "Bitbucket webhook payload"));
        
        // 报告相关API
        endpoints.add(createEndpoint("GET", "/api/reports/{runId}", "获取报告", 
            "获取指定审查任务的报告", "runId (路径参数), format (可选: json, html, pdf)"));
        endpoints.add(createEndpoint("GET", "/api/reports/{runId}/download", "下载报告", 
            "下载指定格式的报告", "runId (路径参数), format (json, html, pdf)"));
        
        return endpoints;
    }
    
    private Map<String, Object> createEndpoint(String method, String path, String name, 
                                              String description, String parameters) {
        Map<String, Object> endpoint = new HashMap<>();
        endpoint.put("method", method);
        endpoint.put("path", path);
        endpoint.put("name", name);
        endpoint.put("description", description);
        endpoint.put("parameters", parameters);
        return endpoint;
    }
}

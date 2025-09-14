package com.ai.reviewer.backend.api.controller;

import com.ai.reviewer.backend.api.dto.ApiResponse;
import com.ai.reviewer.backend.domain.adapter.scm.MultiRepoAdapter;
import com.ai.reviewer.backend.domain.adapter.scm.ParsedEvent;
import com.ai.reviewer.backend.domain.adapter.scm.ScmAdapterException;
import com.ai.reviewer.backend.service.orchestrator.ReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 统一的Webhook控制器
 * 
 * 处理来自各种Git平台的webhook事件：
 * - GitHub
 * - GitLab  
 * - Bitbucket
 * - Gitea
 * - 自建Git服务器
 */
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {
    
    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);
    
    private final MultiRepoAdapter multiRepoAdapter;
    private final ReviewService reviewService;
    
    @Autowired
    public WebhookController(MultiRepoAdapter multiRepoAdapter, ReviewService reviewService) {
        this.multiRepoAdapter = multiRepoAdapter;
        this.reviewService = reviewService;
    }
    
    /**
     * GitHub webhook端点
     */
    @PostMapping("/github")
    public ResponseEntity<ApiResponse<String>> handleGitHubWebhook(
            HttpServletRequest request,
            @RequestBody byte[] payload) {
        
        return handleWebhook("github", request, payload);
    }
    
    /**
     * GitLab webhook端点
     */
    @PostMapping("/gitlab")
    public ResponseEntity<ApiResponse<String>> handleGitLabWebhook(
            HttpServletRequest request,
            @RequestBody byte[] payload) {
        
        return handleWebhook("gitlab", request, payload);
    }
    
    /**
     * Bitbucket webhook端点
     */
    @PostMapping("/bitbucket")
    public ResponseEntity<ApiResponse<String>> handleBitbucketWebhook(
            HttpServletRequest request,
            @RequestBody byte[] payload) {
        
        return handleWebhook("bitbucket", request, payload);
    }
    
    /**
     * Gitea webhook端点
     */
    @PostMapping("/gitea")
    public ResponseEntity<ApiResponse<String>> handleGiteaWebhook(
            HttpServletRequest request,
            @RequestBody byte[] payload) {
        
        return handleWebhook("gitea", request, payload);
    }
    
    /**
     * 通用webhook端点 (自动检测provider)
     */
    @PostMapping("/generic")
    public ResponseEntity<ApiResponse<String>> handleGenericWebhook(
            HttpServletRequest request,
            @RequestBody byte[] payload,
            @RequestParam(required = false) String repoUrl) {
        
        return handleWebhook("auto-detect", request, payload, repoUrl);
    }
    
    /**
     * 获取支持的SCM提供商列表
     */
    @GetMapping("/providers")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSupportedProviders() {
        try {
            Map<String, Object> providersInfo = new HashMap<>();
            providersInfo.put("supported", multiRepoAdapter.getSupportedProviders());
            providersInfo.put("health", multiRepoAdapter.getAdapterHealthStatus());
            providersInfo.put("adapters", multiRepoAdapter.getAdapterInfo());
            
            return ResponseEntity.ok(ApiResponse.success(providersInfo));
            
        } catch (Exception e) {
            logger.error("Error getting supported providers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("PROVIDER_ERROR", "Failed to get supported providers: " + e.getMessage()));
        }
    }
    
    /**
     * 检查仓库是否支持
     */
    @GetMapping("/check-support")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkRepositorySupport(
            @RequestParam String repoUrl) {
        
        try {
            boolean supported = multiRepoAdapter.isRepositorySupported(repoUrl);
            
            Map<String, Object> result = new HashMap<>();
            result.put("repoUrl", repoUrl);
            result.put("supported", supported);
            result.put("supportedProviders", multiRepoAdapter.getSupportedProviders());
            
            return ResponseEntity.ok(ApiResponse.success(result));
            
        } catch (Exception e) {
            logger.error("Error checking repository support for: {}", repoUrl, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("INVALID_REPO", "Invalid repository URL: " + e.getMessage()));
        }
    }
    
    // Private helper methods
    
    private ResponseEntity<ApiResponse<String>> handleWebhook(String expectedProvider, 
                                                            HttpServletRequest request, 
                                                            byte[] payload) {
        return handleWebhook(expectedProvider, request, payload, null);
    }
    
    private ResponseEntity<ApiResponse<String>> handleWebhook(String expectedProvider, 
                                                            HttpServletRequest request, 
                                                            byte[] payload, 
                                                            String repoUrl) {
        try {
            // Extract headers
            Map<String, String> headers = extractHeaders(request);
            
            // Log webhook reception
            String eventType = getEventType(headers);
            logger.info("Received {} webhook: event={}, size={}bytes", 
                    expectedProvider, eventType, payload.length);
            
            // Verify webhook signature
            if (!verifyWebhookSignature(repoUrl, headers, payload)) {
                logger.warn("Webhook signature verification failed for {}", expectedProvider);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("INVALID_SIGNATURE", "Webhook signature verification failed"));
            }
            
            // Parse webhook event
            ParsedEvent event = parseWebhookEvent(repoUrl, payload, headers);
            
            if (event == null) {
                logger.warn("Failed to parse webhook event from {}", expectedProvider);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("PARSE_ERROR", "Failed to parse webhook event"));
            }
            
            // Log parsed event
            logger.info("Parsed webhook event: type={}, repo={}/{}, pull={}", 
                    event.type(), 
                    event.repo() != null ? event.repo().owner() : "unknown",
                    event.repo() != null ? event.repo().name() : "unknown",
                    event.pull() != null ? event.pull().number() : "N/A");
            
            // Process the event based on type
            String result = processWebhookEvent(event);
            
            return ResponseEntity.ok(ApiResponse.success(result));
            
        } catch (ScmAdapterException e) {
            logger.error("SCM adapter error handling {} webhook: {}", expectedProvider, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("SCM_ERROR", e.getMessage()));
            
        } catch (Exception e) {
            logger.error("Unexpected error handling {} webhook", expectedProvider, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("WEBHOOK_ERROR", "Internal error processing webhook: " + e.getMessage()));
        }
    }
    
    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        
        // Extract relevant headers
        var headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            headers.put(headerName, headerValue);
        }
        
        return headers;
    }
    
    private String getEventType(Map<String, String> headers) {
        // Try different event type headers
        return headers.getOrDefault("X-GitHub-Event", 
               headers.getOrDefault("X-Gitlab-Event",
               headers.getOrDefault("X-Event-Key",
               headers.getOrDefault("X-Gitea-Event", "unknown"))));
    }
    
    private boolean verifyWebhookSignature(String repoUrl, Map<String, String> headers, byte[] payload) {
        try {
            if (repoUrl != null && !repoUrl.trim().isEmpty()) {
                return multiRepoAdapter.verifyWebhookSignature(repoUrl, headers, payload);
            } else {
                // For provider-specific endpoints, try to verify without explicit repo URL
                // This depends on the ability to detect provider from headers
                logger.debug("No repo URL provided, attempting signature verification from headers");
                return true; // For now, allow without verification if no URL provided
            }
        } catch (Exception e) {
            logger.error("Error verifying webhook signature", e);
            return false;
        }
    }
    
    private ParsedEvent parseWebhookEvent(String repoUrl, byte[] payload, Map<String, String> headers) {
        try {
            if (repoUrl != null && !repoUrl.trim().isEmpty()) {
                return multiRepoAdapter.parseWebhookEvent(repoUrl, payload, headers);
            } else {
                // Try to parse without explicit repo URL (auto-detection)
                return multiRepoAdapter.parseWebhookEvent("", payload, headers);
            }
        } catch (Exception e) {
            logger.error("Error parsing webhook event", e);
            return null;
        }
    }
    
    private String processWebhookEvent(ParsedEvent event) {
        String eventType = event.type();
        if (eventType == null) {
            return "Unknown event type - ignored";
        }
        
        if (eventType.equals("pull_request.opened") || eventType.equals("pull_request.synchronize")) {
            return processPullRequestEvent(event);
        } else if (eventType.equals("pull_request.closed")) {
            return "Pull request closed/merged - no action needed";
        } else if (eventType.equals("push")) {
            return "Push event received - no action needed";
        } else {
            return "Unknown event type - ignored";
        }
    }
    
    private String processPullRequestEvent(ParsedEvent event) {
        try {
            if (event.repo() == null || event.pull() == null) {
                return "Missing repository or pull request information";
            }
            
            // Trigger AI review asynchronously with diff info
            if (event.diffInfo() != null) {
                logger.debug("Passing diff info to review service: {} files", 
                    event.diffInfo().files() != null ? event.diffInfo().files().size() : 0);
            }
            reviewService.triggerReview(event.repo(), event.pull(), event.diffInfo());
            
            return String.format("AI review triggered for PR %s/%s#%s", 
                    event.repo().owner(), 
                    event.repo().name(), 
                    event.pull().number());
                    
        } catch (Exception e) {
            logger.error("Error processing pull request event", e);
            return "Error triggering AI review: " + e.getMessage();
        }
    }
}

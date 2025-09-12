package com.ai.reviewer.backend.infra.adapter.gitea;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Gitea API 配置
 */
@Configuration
public class GiteaConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(GiteaConfig.class);
    
    @Value("${ai-reviewer.scm.gitea.api-base:}")
    private String apiBase;
    
    @Value("${ai-reviewer.scm.gitea.web-base:}")
    private String webBase;
    
    @Value("${ai-reviewer.scm.gitea.token:}")
    private String token;
    
    @Value("${ai-reviewer.scm.gitea.webhook-secret:}")
    private String webhookSecret;
    
    @Value("${ai-reviewer.scm.gitea.connect-timeout:30000}")
    private int connectTimeout;
    
    @Value("${ai-reviewer.scm.gitea.read-timeout:60000}")
    private int readTimeout;
    
    @Value("${ai-reviewer.scm.gitea.max-retries:3}")
    private int maxRetries;
    
    @Value("${ai-reviewer.scm.gitea.ssl-verify:true}")
    private boolean sslVerify;
    
    public String getApiBase() {
        return apiBase;
    }
    
    public String getWebBase() {
        return webBase != null && !webBase.trim().isEmpty() ? webBase : apiBase;
    }
    
    public String getToken() {
        return token;
    }
    
    public String getWebhookSecret() {
        return webhookSecret;
    }
    
    public int getConnectTimeout() {
        return connectTimeout;
    }
    
    public int getReadTimeout() {
        return readTimeout;
    }
    
    public int getMaxRetries() {
        return maxRetries;
    }
    
    public boolean isSslVerify() {
        return sslVerify;
    }
    
    public boolean isConfigured() {
        boolean hasApiBase = apiBase != null && !apiBase.trim().isEmpty();
        boolean hasToken = token != null && !token.trim().isEmpty();
        
        if (hasApiBase && hasToken) {
            logger.info("Gitea configuration initialized - API base: {}, SSL verification: {}", 
                apiBase, sslVerify ? "ENABLED" : "DISABLED");
        } else if (hasApiBase) {
            logger.warn("Gitea API base configured but missing token");
        } else {
            logger.debug("Gitea not configured - missing API base URL");
        }
        
        return hasApiBase && hasToken;
    }
    
    public String getApiUrl(String path) {
        if (apiBase == null || apiBase.trim().isEmpty()) {
            throw new IllegalStateException("Gitea API base URL not configured");
        }
        
        String base = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        String cleanPath = path.startsWith("/") ? path : "/" + path;
        
        return base + cleanPath;
    }
    
    @Override
    public String toString() {
        return String.format("Gitea Configuration:\n" +
                "  API Base: %s\n" +
                "  Web Base: %s\n" +
                "  Authentication: %s\n" +
                "  Webhook Secret: %s\n" +
                "  SSL Verification: %s\n" +
                "  Connect Timeout: %dms\n" +
                "  Read Timeout: %dms\n" +
                "  Max Retries: %d",
                apiBase,
                getWebBase(),
                (token != null && !token.trim().isEmpty()) ? "Token" : "Not configured",
                (webhookSecret != null && !webhookSecret.trim().isEmpty()) ? "Configured" : "Not configured",
                sslVerify ? "ENABLED" : "DISABLED",
                connectTimeout,
                readTimeout,
                maxRetries);
    }
}

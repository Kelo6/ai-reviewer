package com.ai.reviewer.backend.infra.adapter.bitbucket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Bitbucket API 配置
 */
@Configuration
public class BitbucketConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(BitbucketConfig.class);
    
    @Value("${ai-reviewer.scm.bitbucket.api-base:https://api.bitbucket.org/2.0}")
    private String apiBase;
    
    @Value("${ai-reviewer.scm.bitbucket.web-base:https://bitbucket.org}")
    private String webBase;
    
    @Value("${ai-reviewer.scm.bitbucket.app-password:}")
    private String appPassword;
    
    @Value("${ai-reviewer.scm.bitbucket.username:}")
    private String username;
    
    @Value("${ai-reviewer.scm.bitbucket.webhook-secret:}")
    private String webhookSecret;
    
    @Value("${ai-reviewer.scm.bitbucket.connect-timeout:30000}")
    private int connectTimeout;
    
    @Value("${ai-reviewer.scm.bitbucket.read-timeout:60000}")
    private int readTimeout;
    
    @Value("${ai-reviewer.scm.bitbucket.max-retries:3}")
    private int maxRetries;
    
    public String getApiBase() {
        return apiBase;
    }
    
    public String getWebBase() {
        return webBase;
    }
    
    public String getAppPassword() {
        return appPassword;
    }
    
    public String getUsername() {
        return username;
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
    
    public boolean isConfigured() {
        boolean hasCredentials = username != null && !username.trim().isEmpty() 
                               && appPassword != null && !appPassword.trim().isEmpty();
        
        if (hasCredentials) {
            logger.info("Bitbucket configuration initialized - API base: {}, Authentication: App Password", apiBase);
        } else {
            logger.warn("Bitbucket not configured - missing username or app password");
        }
        
        return hasCredentials;
    }
    
    @Override
    public String toString() {
        return String.format("Bitbucket Configuration:\n" +
                "  API Base: %s\n" +
                "  Web Base: %s\n" +
                "  Authentication: %s\n" +
                "  Webhook Secret: %s\n" +
                "  Connect Timeout: %dms\n" +
                "  Read Timeout: %dms\n" +
                "  Max Retries: %d",
                apiBase,
                webBase,
                (username != null && !username.trim().isEmpty()) ? "App Password" : "Not configured",
                (webhookSecret != null && !webhookSecret.trim().isEmpty()) ? "Configured" : "Not configured",
                connectTimeout,
                readTimeout,
                maxRetries);
    }
}

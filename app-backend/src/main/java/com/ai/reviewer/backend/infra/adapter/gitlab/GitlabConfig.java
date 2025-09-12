package com.ai.reviewer.backend.infra.adapter.gitlab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * GitLab adapter configuration supporting both GitLab.com and self-hosted instances.
 * 
 * <p>This configuration class handles environment-based configuration for GitLab integration,
 * supporting both GitLab.com and self-hosted GitLab installations with optional SSL
 * certificate verification bypass for self-signed certificates.
 * 
 * <h3>Authentication</h3>
 * <ul>
 *   <li><strong>Personal Access Token</strong>: Set {@code GITLAB_TOKEN}</li>
 * </ul>
 * 
 * <h3>Environment Variables</h3>
 * <ul>
 *   <li>{@code GITLAB_API_BASE} - GitLab API base URL (defaults to https://gitlab.com/api/v4)</li>
 *   <li>{@code GITLAB_TOKEN} - Personal Access Token for authentication</li>
 *   <li>{@code GITLAB_WEBHOOK_SECRET} - Webhook signature verification secret</li>
 *   <li>{@code GITLAB_IGNORE_CERT_ERRORS} - Ignore SSL certificate errors (default: false)</li>
 * </ul>
 */
@Configuration
@ConfigurationProperties(prefix = "gitlab")
public class GitlabConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(GitlabConfig.class);
    
    /** Default GitLab API base URL */
    public static final String DEFAULT_API_BASE = "https://gitlab.com/api/v4";
    
    /** Default GitLab web base URL */
    public static final String DEFAULT_WEB_BASE = "https://gitlab.com";
    
    /** Environment variable names */
    public static final String ENV_API_BASE = "GITLAB_API_BASE";
    public static final String ENV_WEB_BASE = "GITLAB_WEB_BASE";
    public static final String ENV_TOKEN = "GITLAB_TOKEN";
    public static final String ENV_WEBHOOK_SECRET = "GITLAB_WEBHOOK_SECRET";
    public static final String ENV_IGNORE_CERT_ERRORS = "GITLAB_IGNORE_CERT_ERRORS";
    
    private String apiBase;
    private String webBase;
    private String token;
    private String webhookSecret;
    private boolean ignoreCertErrors;
    
    // Connection settings
    private int connectTimeout = 30000; // 30 seconds
    private int readTimeout = 60000;    // 60 seconds
    private int maxRetries = 3;
    private long retryDelay = 1000;     // 1 second
    
    /**
     * Default constructor that initializes from environment variables.
     */
    public GitlabConfig() {
        initializeFromEnvironment();
    }
    
    /**
     * Initialize configuration from environment variables.
     */
    private void initializeFromEnvironment() {
        this.apiBase = getEnvironmentVariable(ENV_API_BASE, DEFAULT_API_BASE);
        this.webBase = getEnvironmentVariable(ENV_WEB_BASE, DEFAULT_WEB_BASE);
        this.token = getEnvironmentVariable(ENV_TOKEN, null);
        this.webhookSecret = getEnvironmentVariable(ENV_WEBHOOK_SECRET, null);
        
        // Parse boolean for SSL certificate errors
        String ignoreCertStr = getEnvironmentVariable(ENV_IGNORE_CERT_ERRORS, "false");
        this.ignoreCertErrors = Boolean.parseBoolean(ignoreCertStr);
        
        // Normalize API base URL (remove trailing slash)
        if (this.apiBase.endsWith("/")) {
            this.apiBase = this.apiBase.substring(0, this.apiBase.length() - 1);
        }
        
        // Normalize web base URL (remove trailing slash)  
        if (this.webBase.endsWith("/")) {
            this.webBase = this.webBase.substring(0, this.webBase.length() - 1);
        }
        
        logger.info("GitLab configuration initialized - API base: {}, SSL verification: {}", 
            this.apiBase, ignoreCertErrors ? "DISABLED" : "ENABLED");
    }
    
    /**
     * Get environment variable with default value.
     *
     * @param name environment variable name
     * @param defaultValue default value if not set
     * @return environment variable value or default
     */
    private String getEnvironmentVariable(String name, String defaultValue) {
        String value = System.getenv(name);
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }
    
    /**
     * Get the GitLab API base URL.
     *
     * @return API base URL
     */
    public String getApiBase() {
        return apiBase;
    }
    
    public void setApiBase(String apiBase) {
        this.apiBase = apiBase;
    }
    
    /**
     * Get the GitLab web base URL.
     *
     * @return web base URL
     */
    public String getWebBase() {
        return webBase;
    }
    
    public void setWebBase(String webBase) {
        this.webBase = webBase;
    }
    
    /**
     * Get the Personal Access Token.
     *
     * @return PAT or null if not configured
     */
    public String getToken() {
        return token;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
    
    /**
     * Get the webhook signature secret.
     *
     * @return webhook secret or null if not configured
     */
    public String getWebhookSecret() {
        return webhookSecret;
    }
    
    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }
    
    /**
     * Check if SSL certificate errors should be ignored.
     *
     * @return true if SSL certificate errors should be ignored
     */
    public boolean isIgnoreCertErrors() {
        return ignoreCertErrors;
    }
    
    public void setIgnoreCertErrors(boolean ignoreCertErrors) {
        this.ignoreCertErrors = ignoreCertErrors;
    }
    
    public int getConnectTimeout() {
        return connectTimeout;
    }
    
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }
    
    public int getReadTimeout() {
        return readTimeout;
    }
    
    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }
    
    public int getMaxRetries() {
        return maxRetries;
    }
    
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
    
    public long getRetryDelay() {
        return retryDelay;
    }
    
    public void setRetryDelay(long retryDelay) {
        this.retryDelay = retryDelay;
    }
    
    /**
     * Check if Personal Access Token authentication is configured.
     *
     * @return true if PAT is available
     */
    public boolean hasToken() {
        return StringUtils.hasText(token);
    }
    
    /**
     * Check if webhook secret is configured.
     *
     * @return true if webhook secret is available
     */
    public boolean hasWebhookSecret() {
        return StringUtils.hasText(webhookSecret);
    }
    
    /**
     * Check if any form of authentication is configured.
     *
     * @return true if token is configured
     */
    public boolean hasAuthentication() {
        return hasToken();
    }
    
    /**
     * Get the authentication type being used.
     *
     * @return authentication type description
     */
    public String getAuthenticationType() {
        if (hasToken()) {
            return "Personal Access Token";
        } else {
            return "None";
        }
    }
    
    /**
     * Check if this is a self-hosted GitLab instance.
     *
     * @return true if using self-hosted GitLab (non-default API base)
     */
    public boolean isSelfHosted() {
        return !DEFAULT_API_BASE.equals(apiBase);
    }
    
    /**
     * Get the GitLab server host from API base URL.
     *
     * @return server host
     */
    public String getServerHost() {
        try {
            return new java.net.URL(apiBase).getHost();
        } catch (java.net.MalformedURLException e) {
            return "unknown";
        }
    }
    
    /**
     * Validate the configuration and throw exception if invalid.
     *
     * @throws GitlabException if configuration is invalid
     */
    public void validate() throws GitlabException {
        if (!hasAuthentication()) {
            throw GitlabException.credentialsMissing();
        }
        
        if (!isValidUrl(apiBase)) {
            throw new GitlabException(GitlabException.ErrorCode.API_CONFIG_ERROR, 
                "Invalid API base URL: " + apiBase);
        }
        
        if (!isValidUrl(webBase)) {
            throw new GitlabException(GitlabException.ErrorCode.API_CONFIG_ERROR, 
                "Invalid web base URL: " + webBase);
        }
        
        // Validate API base ends with expected GitLab API path
        if (!apiBase.contains("/api/v")) {
            logger.warn("API base URL '{}' does not contain expected '/api/v' path. " +
                       "Make sure it points to GitLab API endpoint.", apiBase);
        }
    }
    
    /**
     * Basic URL validation.
     *
     * @param url URL to validate
     * @return true if URL appears valid
     */
    private boolean isValidUrl(String url) {
        try {
            return StringUtils.hasText(url) && 
                   (url.startsWith("http://") || url.startsWith("https://"));
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get configuration summary for logging (without sensitive data).
     *
     * @return configuration summary
     */
    public String getConfigSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("GitLab Configuration:\n");
        summary.append("  API Base: ").append(apiBase).append("\n");
        summary.append("  Web Base: ").append(webBase).append("\n");
        summary.append("  Authentication: ").append(getAuthenticationType()).append("\n");
        summary.append("  Webhook Secret: ").append(hasWebhookSecret() ? "Configured" : "Not configured").append("\n");
        summary.append("  Self-hosted: ").append(isSelfHosted() ? "Yes" : "No").append("\n");
        summary.append("  SSL Verification: ").append(ignoreCertErrors ? "DISABLED" : "ENABLED").append("\n");
        summary.append("  Connect Timeout: ").append(connectTimeout).append("ms\n");
        summary.append("  Read Timeout: ").append(readTimeout).append("ms\n");
        summary.append("  Max Retries: ").append(maxRetries);
        return summary.toString();
    }
    
    /**
     * Get the project path from a GitLab URL.
     * 
     * @param url GitLab project URL
     * @return project path (owner/repo) or null if invalid
     */
    public String extractProjectPath(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        
        try {
            // Handle various GitLab URL formats:
            // https://gitlab.com/owner/repo
            // https://gitlab.com/owner/repo.git  
            // https://gitlab.example.com/owner/repo
            String path = new java.net.URL(url).getPath();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.endsWith(".git")) {
                path = path.substring(0, path.length() - 4);
            }
            
            // Validate it looks like owner/repo
            if (path.contains("/") && !path.startsWith("/") && !path.endsWith("/")) {
                return path;
            }
        } catch (java.net.MalformedURLException e) {
            logger.debug("Failed to parse GitLab URL: {}", url);
        }
        
        return null;
    }
    
    @Override
    public String toString() {
        return "GitlabConfig{" +
               "apiBase='" + apiBase + '\'' +
               ", webBase='" + webBase + '\'' +
               ", authType='" + getAuthenticationType() + '\'' +
               ", hasWebhookSecret=" + hasWebhookSecret() +
               ", ignoreCertErrors=" + ignoreCertErrors +
               ", connectTimeout=" + connectTimeout +
               ", readTimeout=" + readTimeout +
               ", maxRetries=" + maxRetries +
               '}';
    }
}

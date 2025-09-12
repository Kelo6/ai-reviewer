package com.ai.reviewer.backend.infra.adapter.github;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * GitHub adapter configuration supporting both GitHub App and Personal Access Token authentication.
 * 
 * <p>This configuration class handles environment-based configuration for GitHub integration,
 * supporting both GitHub.com and GitHub Enterprise Server (GHES) deployments.
 * 
 * <h3>Authentication Modes</h3>
 * <ul>
 *   <li><strong>Personal Access Token (PAT)</strong>: Set {@code GITHUB_TOKEN}</li>
 *   <li><strong>GitHub App</strong>: Set {@code GITHUB_APP_ID} and {@code GITHUB_PRIVATE_KEY}</li>
 * </ul>
 * 
 * <h3>Environment Variables</h3>
 * <ul>
 *   <li>{@code GITHUB_API_BASE} - GitHub API base URL (defaults to https://api.github.com)</li>
 *   <li>{@code GITHUB_TOKEN} - Personal Access Token for authentication</li>
 *   <li>{@code GITHUB_APP_ID} - GitHub App ID for app-based authentication</li>
 *   <li>{@code GITHUB_PRIVATE_KEY} - GitHub App private key (PEM format)</li>
 *   <li>{@code GITHUB_WEBHOOK_SECRET} - Webhook signature verification secret</li>
 * </ul>
 */
@Configuration
@ConfigurationProperties(prefix = "github")
public class GithubConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(GithubConfig.class);
    
    /** Default GitHub API base URL */
    public static final String DEFAULT_API_BASE = "https://api.github.com";
    
    /** Default GitHub web base URL */
    public static final String DEFAULT_WEB_BASE = "https://github.com";
    
    /** Environment variable names */
    public static final String ENV_API_BASE = "GITHUB_API_BASE";
    public static final String ENV_WEB_BASE = "GITHUB_WEB_BASE";
    public static final String ENV_TOKEN = "GITHUB_TOKEN";
    public static final String ENV_APP_ID = "GITHUB_APP_ID";
    public static final String ENV_PRIVATE_KEY = "GITHUB_PRIVATE_KEY";
    public static final String ENV_WEBHOOK_SECRET = "GITHUB_WEBHOOK_SECRET";
    
    private String apiBase;
    private String webBase;
    private String token;
    private String appId;
    private String privateKey;
    private String webhookSecret;
    
    // Connection settings
    private int connectTimeout = 30000; // 30 seconds
    private int readTimeout = 60000;    // 60 seconds
    private int maxRetries = 3;
    private long retryDelay = 1000;     // 1 second
    
    /**
     * Default constructor that initializes from environment variables.
     */
    public GithubConfig() {
        initializeFromEnvironment();
    }
    
    /**
     * Initialize configuration from environment variables.
     */
    private void initializeFromEnvironment() {
        this.apiBase = getEnvironmentVariable(ENV_API_BASE, DEFAULT_API_BASE);
        this.webBase = getEnvironmentVariable(ENV_WEB_BASE, DEFAULT_WEB_BASE);
        this.token = getEnvironmentVariable(ENV_TOKEN, null);
        this.appId = getEnvironmentVariable(ENV_APP_ID, null);
        this.privateKey = getEnvironmentVariable(ENV_PRIVATE_KEY, null);
        this.webhookSecret = getEnvironmentVariable(ENV_WEBHOOK_SECRET, null);
        
        // Normalize API base URL (remove trailing slash)
        if (this.apiBase.endsWith("/")) {
            this.apiBase = this.apiBase.substring(0, this.apiBase.length() - 1);
        }
        
        // Normalize web base URL (remove trailing slash)
        if (this.webBase.endsWith("/")) {
            this.webBase = this.webBase.substring(0, this.webBase.length() - 1);
        }
        
        logger.info("GitHub configuration initialized - API base: {}, Authentication: {}", 
            this.apiBase, getAuthenticationType());
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
     * Get the GitHub API base URL.
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
     * Get the GitHub web base URL.
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
     * Get the GitHub App ID.
     *
     * @return App ID or null if not configured
     */
    public String getAppId() {
        return appId;
    }
    
    public void setAppId(String appId) {
        this.appId = appId;
    }
    
    /**
     * Get the GitHub App private key.
     *
     * @return private key in PEM format or null if not configured
     */
    public String getPrivateKey() {
        return privateKey;
    }
    
    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
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
     * Check if GitHub App authentication is configured.
     *
     * @return true if both App ID and private key are available
     */
    public boolean hasAppAuth() {
        return StringUtils.hasText(appId) && StringUtils.hasText(privateKey);
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
     * @return true if either PAT or App auth is configured
     */
    public boolean hasAuthentication() {
        return hasToken() || hasAppAuth();
    }
    
    /**
     * Get the authentication type being used.
     *
     * @return authentication type description
     */
    public String getAuthenticationType() {
        if (hasToken()) {
            return "Personal Access Token";
        } else if (hasAppAuth()) {
            return "GitHub App";
        } else {
            return "None";
        }
    }
    
    /**
     * Check if this is a GitHub Enterprise Server configuration.
     *
     * @return true if using GHES (non-default API base)
     */
    public boolean isGitHubEnterprise() {
        return !DEFAULT_API_BASE.equals(apiBase);
    }
    
    /**
     * Validate the configuration and throw exception if invalid.
     *
     * @throws GithubException if configuration is invalid
     */
    public void validate() throws GithubException {
        if (!hasAuthentication()) {
            throw GithubException.credentialsMissing();
        }
        
        if (hasAppAuth()) {
            validateAppAuth();
        }
        
        if (!isValidUrl(apiBase)) {
            throw GithubException.appConfigInvalid("Invalid API base URL: " + apiBase);
        }
        
        if (!isValidUrl(webBase)) {
            throw GithubException.appConfigInvalid("Invalid web base URL: " + webBase);
        }
    }
    
    /**
     * Validate GitHub App authentication configuration.
     *
     * @throws GithubException if App auth is invalid
     */
    private void validateAppAuth() throws GithubException {
        if (!StringUtils.hasText(appId)) {
            throw GithubException.appConfigInvalid("App ID is required for GitHub App authentication");
        }
        
        try {
            Long.parseLong(appId);
        } catch (NumberFormatException e) {
            throw GithubException.appConfigInvalid("App ID must be a valid number: " + appId);
        }
        
        if (!StringUtils.hasText(privateKey)) {
            throw GithubException.appConfigInvalid("Private key is required for GitHub App authentication");
        }
        
        // Basic PEM format validation
        String trimmedKey = privateKey.trim();
        if (!trimmedKey.startsWith("-----BEGIN") || !trimmedKey.endsWith("-----")) {
            throw GithubException.appConfigInvalid("Private key must be in PEM format");
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
        summary.append("GitHub Configuration:\n");
        summary.append("  API Base: ").append(apiBase).append("\n");
        summary.append("  Web Base: ").append(webBase).append("\n");
        summary.append("  Authentication: ").append(getAuthenticationType()).append("\n");
        summary.append("  Webhook Secret: ").append(hasWebhookSecret() ? "Configured" : "Not configured").append("\n");
        summary.append("  Enterprise: ").append(isGitHubEnterprise() ? "Yes" : "No").append("\n");
        summary.append("  Connect Timeout: ").append(connectTimeout).append("ms\n");
        summary.append("  Read Timeout: ").append(readTimeout).append("ms\n");
        summary.append("  Max Retries: ").append(maxRetries);
        return summary.toString();
    }
    
    @Override
    public String toString() {
        return "GithubConfig{" +
               "apiBase='" + apiBase + '\'' +
               ", webBase='" + webBase + '\'' +
               ", authType='" + getAuthenticationType() + '\'' +
               ", hasWebhookSecret=" + hasWebhookSecret() +
               ", connectTimeout=" + connectTimeout +
               ", readTimeout=" + readTimeout +
               ", maxRetries=" + maxRetries +
               '}';
    }
}

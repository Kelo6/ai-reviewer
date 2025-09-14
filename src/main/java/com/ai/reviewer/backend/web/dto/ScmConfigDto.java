package com.ai.reviewer.backend.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * SCM配置数据传输对象
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScmConfigDto {
    
    private String provider;
    private String displayName;
    private String apiBase;
    private String webBase;
    private String token;
    private String username;
    private String appPassword;
    private String clientId;
    private String clientSecret;
    private String webhookSecret;
    private Boolean sslVerify;
    private String apiType; // For custom git
    private Boolean enabled;
    private String status; // "connected", "error", "not_configured"
    private String statusMessage;
    
    // Constructors
    public ScmConfigDto() {}
    
    public ScmConfigDto(String provider, String displayName) {
        this.provider = provider;
        this.displayName = displayName;
        this.enabled = false;
        this.status = "not_configured";
    }
    
    // Provider-specific factory methods
    public static ScmConfigDto github() {
        ScmConfigDto config = new ScmConfigDto("github", "GitHub");
        config.setApiBase("https://api.github.com");
        config.setWebBase("https://github.com");
        return config;
    }
    
    public static ScmConfigDto gitlab() {
        ScmConfigDto config = new ScmConfigDto("gitlab", "GitLab");
        config.setApiBase("https://gitlab.com/api/v4");
        config.setWebBase("https://gitlab.com");
        return config;
    }
    
    public static ScmConfigDto bitbucket() {
        ScmConfigDto config = new ScmConfigDto("bitbucket", "Bitbucket");
        config.setApiBase("https://api.bitbucket.org/2.0");
        config.setWebBase("https://bitbucket.org");
        return config;
    }
    
    public static ScmConfigDto gitea() {
        ScmConfigDto config = new ScmConfigDto("gitea", "Gitea");
        config.setSslVerify(true);
        return config;
    }
    
    public static ScmConfigDto customGit() {
        ScmConfigDto config = new ScmConfigDto("custom-git", "自建Git");
        config.setSslVerify(true);
        config.setApiType("gitea");
        return config;
    }
    
    // Getters and Setters
    public String getProvider() {
        return provider;
    }
    
    public void setProvider(String provider) {
        this.provider = provider;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    public String getApiBase() {
        return apiBase;
    }
    
    public void setApiBase(String apiBase) {
        this.apiBase = apiBase;
    }
    
    public String getWebBase() {
        return webBase;
    }
    
    public void setWebBase(String webBase) {
        this.webBase = webBase;
    }
    
    public String getToken() {
        return token;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getAppPassword() {
        return appPassword;
    }
    
    public void setAppPassword(String appPassword) {
        this.appPassword = appPassword;
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    
    public String getClientSecret() {
        return clientSecret;
    }
    
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }
    
    public String getWebhookSecret() {
        return webhookSecret;
    }
    
    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }
    
    public Boolean getSslVerify() {
        return sslVerify;
    }
    
    public void setSslVerify(Boolean sslVerify) {
        this.sslVerify = sslVerify;
    }
    
    public String getApiType() {
        return apiType;
    }
    
    public void setApiType(String apiType) {
        this.apiType = apiType;
    }
    
    public Boolean getEnabled() {
        return enabled;
    }
    
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getStatusMessage() {
        return statusMessage;
    }
    
    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }
    
    // Helper methods
    public boolean isConfigured() {
        return !"not_configured".equals(status);
    }
    
    public boolean isConnected() {
        return "connected".equals(status);
    }
    
    public boolean hasError() {
        return "error".equals(status);
    }
    
    public String getWebhookUrl() {
        // This would be generated based on the base URL of the application
        return "/api/webhooks/" + provider;
    }
    
    /**
     * Check if this configuration has all required fields for the provider
     */
    public boolean isComplete() {
        // 基础检查：provider和apiBase不能为空
        if (provider == null || provider.trim().isEmpty() || 
            apiBase == null || apiBase.trim().isEmpty()) {
            return false;
        }
        
        return switch (provider) {
            case "github", "gitlab", "gitea" -> 
                token != null && !token.trim().isEmpty();
                
            case "bitbucket" -> 
                username != null && !username.trim().isEmpty() &&
                appPassword != null && !appPassword.trim().isEmpty();
                
            case "custom-git" ->
                token != null && !token.trim().isEmpty() &&
                (apiType == null || !apiType.trim().isEmpty()); // apiType可以为空，默认使用gitea
                
            default -> {
                // 对于自定义provider（custom-xxx），检查token
                if (provider.startsWith("custom-")) {
                    yield token != null && !token.trim().isEmpty();
                }
                yield false;
            }
        };
    }
    
    /**
     * Get required fields for this provider
     */
    public String[] getRequiredFields() {
        return switch (provider) {
            case "github", "gitlab", "gitea" -> new String[]{"apiBase", "token"};
            case "bitbucket" -> new String[]{"apiBase", "username", "appPassword"};
            case "custom-git" -> new String[]{"apiBase", "token", "apiType"};
            default -> new String[]{};
        };
    }
}

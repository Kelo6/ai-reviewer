package com.ai.reviewer.backend.infra.jpa.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * SCM配置数据库实体
 */
@Entity
@Table(name = "scm_config")
public class ScmConfigEntity {
    
    @Id
    @Column(name = "provider", length = 50)
    private String provider;
    
    @Column(name = "display_name", length = 100)
    private String displayName;
    
    @Column(name = "api_base", length = 500)
    private String apiBase;
    
    @Column(name = "web_base", length = 500)
    private String webBase;
    
    @Column(name = "token", columnDefinition = "TEXT")
    private String token;
    
    @Column(name = "username", length = 200)
    private String username;
    
    @Column(name = "app_password", columnDefinition = "TEXT")
    private String appPassword;
    
    @Column(name = "client_id", length = 200)
    private String clientId;
    
    @Column(name = "client_secret", columnDefinition = "TEXT")
    private String clientSecret;
    
    @Column(name = "webhook_secret", columnDefinition = "TEXT")
    private String webhookSecret;
    
    @Column(name = "ssl_verify")
    private Boolean sslVerify = true;
    
    @Column(name = "api_type", length = 50)
    private String apiType; // For custom git
    
    @Column(name = "enabled")
    private Boolean enabled = false;
    
    @Column(name = "status", length = 50)
    private String status = "not_configured"; // "connected", "error", "not_configured", "disabled"
    
    @Column(name = "status_message", length = 500)
    private String statusMessage;
    
    // 时间戳
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    // 构造函数
    public ScmConfigEntity() {}
    
    public ScmConfigEntity(String provider, String displayName) {
        this.provider = provider;
        this.displayName = displayName;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    // Getters and Setters
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    
    public String getApiBase() { return apiBase; }
    public void setApiBase(String apiBase) { this.apiBase = apiBase; }
    
    public String getWebBase() { return webBase; }
    public void setWebBase(String webBase) { this.webBase = webBase; }
    
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getAppPassword() { return appPassword; }
    public void setAppPassword(String appPassword) { this.appPassword = appPassword; }
    
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    
    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
    
    public Boolean getSslVerify() { return sslVerify; }
    public void setSslVerify(Boolean sslVerify) { this.sslVerify = sslVerify; }
    
    public String getApiType() { return apiType; }
    public void setApiType(String apiType) { this.apiType = apiType; }
    
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

package com.ai.reviewer.backend.infra.adapter.gitlab;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.net.ssl.*;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * GitLab API client wrapper providing authentication and error handling.
 * 
 * <p>Handles Personal Access Token authentication and SSL configuration
 * for both GitLab.com and self-hosted GitLab instances, with optional
 * SSL certificate verification bypass for self-signed certificates.
 */
@Component
public class GitlabApiClient {
    
    private static final Logger logger = LoggerFactory.getLogger(GitlabApiClient.class);
    
    private final GitlabConfig config;
    private final Environment environment;
    private volatile GitLabApi gitLabApi;
    
    @Autowired
    public GitlabApiClient(GitlabConfig config, Environment environment) {
        this.config = config;
        this.environment = environment;
        
        // Skip validation in test and dev profiles
        if (!isTestProfile()) {
            config.validate(); // Validate configuration on startup
        } else {
            logger.info("Skipping GitLab configuration validation in test/dev profile");
        }
        
        logger.info("GitLab API client initialized: {}", config.getConfigSummary());
    }
    
    private boolean isTestProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            if ("test".equals(profile) || "dev".equals(profile)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get GitLab API client instance.
     * 
     * <p>Creates and caches a GitLabApi instance with proper authentication
     * and SSL configuration based on the current configuration.
     *
     * @return GitLabApi client
     * @throws GitlabException if client creation fails
     */
    public GitLabApi getGitLabApi() throws GitlabException {
        if (gitLabApi == null) {
            synchronized (this) {
                if (gitLabApi == null) {
                    gitLabApi = createGitLabApi();
                }
            }
        }
        return gitLabApi;
    }
    
    /**
     * Create a new GitLab API client instance.
     *
     * @return configured GitLabApi instance
     * @throws GitlabException if creation fails
     */
    private GitLabApi createGitLabApi() throws GitlabException {
        if (!config.hasToken()) {
            throw GitlabException.credentialsMissing();
        }
        
        try {
            // Configure SSL if needed
            if (config.isIgnoreCertErrors()) {
                configureSslIgnoreErrors();
                logger.warn("SSL certificate verification is DISABLED for GitLab API client. " +
                           "This should only be used with self-signed certificates in trusted environments.");
            }
            
            // Create GitLab API client
            GitLabApi api = new GitLabApi(config.getApiBase(), config.getToken());
            
            // Configure timeouts
            api.setRequestTimeout(config.getConnectTimeout(), config.getReadTimeout());
            
            // Test the connection
            testConnection(api);
            
            logger.debug("GitLab API client created successfully for: {}", config.getServerHost());
            return api;
            
        } catch (GitLabApiException e) {
            clearCache();
            
            if (e.getHttpStatus() == 401) {
                throw new GitlabException(GitlabException.ErrorCode.AUTH_FAILED, 
                    "Invalid GitLab token", 401, e);
            } else if (e.getHttpStatus() == 403) {
                throw new GitlabException(GitlabException.ErrorCode.RATE_LIMIT_EXCEEDED, 
                    "GitLab API rate limit exceeded", 403, e);
            } else if (e.getHttpStatus() == 404) {
                throw new GitlabException(GitlabException.ErrorCode.API_ENDPOINT_NOT_FOUND, 
                    "GitLab API endpoint not found: " + config.getApiBase(), 404, e);
            } else {
                throw new GitlabException(GitlabException.ErrorCode.API_CONFIG_ERROR, 
                    "Failed to connect to GitLab API: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            clearCache();
            
            // Check for SSL-related exceptions
            if (isSslException(e)) {
                throw GitlabException.sslVerificationFailed(config.getApiBase(), e);
            }
            
            throw new GitlabException(GitlabException.ErrorCode.API_CONFIG_ERROR, 
                "Failed to create GitLab API client: " + e.getMessage(), e);
        }
    }
    
    /**
     * Test GitLab API connection by making a simple API call.
     *
     * @param api GitLab API client to test
     * @throws GitLabApiException if connection test fails
     */
    private void testConnection(GitLabApi api) throws GitLabApiException {
        try {
            // Test connection by getting current user info
            api.getUserApi().getCurrentUser();
            logger.debug("GitLab API connection test successful");
        } catch (GitLabApiException e) {
            logger.error("GitLab API connection test failed: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * Configure SSL context to ignore certificate errors.
     * 
     * <p>This method configures the default SSL context to accept all certificates
     * and hostnames, which is useful for self-hosted GitLab instances with
     * self-signed certificates.
     * 
     * <p><strong>WARNING:</strong> This disables SSL security and should only be used
     * in trusted environments with self-signed certificates.
     */
    private void configureSslIgnoreErrors() {
        try {
            // Create a trust manager that accepts all certificates
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                    
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        // Trust all client certificates
                    }
                    
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        // Trust all server certificates
                    }
                }
            };
            
            // Create SSL context with the trust-all manager
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            
            // Set as default SSL context
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            
            // Create hostname verifier that accepts all hostnames
            HostnameVerifier allHostsValid = (hostname, session) -> true;
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
            
            logger.debug("SSL certificate verification disabled");
            
        } catch (Exception e) {
            logger.error("Failed to configure SSL to ignore certificate errors", e);
            throw new RuntimeException("SSL configuration failed", e);
        }
    }
    
    /**
     * Check if an exception is SSL-related.
     *
     * @param e exception to check
     * @return true if the exception is SSL-related
     */
    private boolean isSslException(Throwable e) {
        if (e == null) {
            return false;
        }
        
        String className = e.getClass().getName();
        String message = e.getMessage();
        
        // Check for common SSL exception types and messages
        return className.contains("SSL") ||
               className.contains("Certificate") ||
               className.contains("TrustManager") ||
               (message != null && (
                   message.contains("certificate") ||
                   message.contains("SSL") ||
                   message.contains("PKIX") ||
                   message.contains("trust")
               )) ||
               isSslException(e.getCause()); // Check cause recursively
    }
    
    /**
     * Clear the cached GitLab API client.
     * 
     * <p>Forces recreation of the client on next access, useful for
     * error recovery or configuration changes.
     */
    public void clearCache() {
        synchronized (this) {
            if (gitLabApi != null) {
                try {
                    gitLabApi.close();
                } catch (Exception e) {
                    logger.debug("Error closing GitLab API client", e);
                }
                gitLabApi = null;
            }
        }
        logger.debug("GitLab API client cache cleared");
    }
    
    /**
     * Check if the API is accessible and credentials are valid.
     *
     * @return true if API is accessible
     */
    public boolean isApiAccessible() {
        try {
            GitLabApi api = getGitLabApi();
            api.getUserApi().getCurrentUser();
            return true;
        } catch (Exception e) {
            logger.debug("API accessibility check failed", e);
            return false;
        }
    }
    
    /**
     * Get the current user information to verify authentication.
     *
     * @return user information
     * @throws GitlabException if user information cannot be retrieved
     */
    public org.gitlab4j.api.models.User getCurrentUser() throws GitlabException {
        try {
            return getGitLabApi().getUserApi().getCurrentUser();
        } catch (GitLabApiException e) {
            throw mapApiException(e, "getCurrentUser");
        }
    }
    
    /**
     * Get a project by ID or path.
     *
     * @param projectIdOrPath project ID or path (owner/repo)
     * @return project information
     * @throws GitlabException if project cannot be retrieved
     */
    public org.gitlab4j.api.models.Project getProject(String projectIdOrPath) throws GitlabException {
        try {
            return getGitLabApi().getProjectApi().getProject(projectIdOrPath);
        } catch (GitLabApiException e) {
            if (e.getHttpStatus() == 404) {
                throw GitlabException.repositoryNotFound(projectIdOrPath);
            }
            throw mapApiException(e, "getProject");
        }
    }
    
    /**
     * Get a merge request by project and merge request IID.
     *
     * @param projectIdOrPath project ID or path
     * @param mergeRequestIid merge request IID
     * @return merge request information
     * @throws GitlabException if merge request cannot be retrieved
     */
    public org.gitlab4j.api.models.MergeRequest getMergeRequest(String projectIdOrPath, Long mergeRequestIid) 
            throws GitlabException {
        try {
            return getGitLabApi().getMergeRequestApi().getMergeRequest(projectIdOrPath, mergeRequestIid);
        } catch (GitLabApiException e) {
            if (e.getHttpStatus() == 404) {
                throw GitlabException.mergeRequestNotFound(projectIdOrPath, String.valueOf(mergeRequestIid));
            }
            throw mapApiException(e, "getMergeRequest");
        }
    }
    
    /**
     * Map GitLab4J API exception to GitlabException.
     *
     * @param e GitLab4J API exception
     * @param operation operation that failed
     * @return mapped GitlabException
     */
    private GitlabException mapApiException(GitLabApiException e, String operation) {
        int httpStatus = e.getHttpStatus();
        String message = e.getMessage();
        
        GitlabException.ErrorCode errorCode = switch (httpStatus) {
            case 401 -> GitlabException.ErrorCode.AUTH_FAILED;
            case 403 -> GitlabException.ErrorCode.RATE_LIMIT_EXCEEDED;
            case 404 -> GitlabException.ErrorCode.API_ENDPOINT_NOT_FOUND;
            case 422 -> GitlabException.ErrorCode.API_RESPONSE_INVALID;
            case 429 -> GitlabException.ErrorCode.RATE_LIMIT_EXCEEDED;
            default -> GitlabException.ErrorCode.API_CONFIG_ERROR;
        };
        
        return new GitlabException(errorCode, 
            String.format("%s failed: %s", operation, message), httpStatus, e);
    }
    
    /**
     * Get cache statistics for monitoring.
     *
     * @return cache statistics
     */
    public String getCacheStats() {
        return String.format("GitLab API Client - Cached: %s", gitLabApi != null ? "Yes" : "No");
    }
    
    /**
     * Force refresh of the API client.
     * 
     * <p>Useful for configuration changes or error recovery.
     */
    public void refresh() {
        clearCache();
        // Next call to getGitLabApi() will create a new instance
        logger.info("GitLab API client refreshed");
    }
}

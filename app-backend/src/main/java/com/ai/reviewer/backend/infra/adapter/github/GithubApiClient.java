package com.ai.reviewer.backend.infra.adapter.github;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * GitHub API client wrapper providing authentication and error handling.
 * 
 * <p>Handles both Personal Access Token and GitHub App authentication,
 * with automatic token generation and caching for GitHub Apps.
 * Provides consistent error handling and retry logic for API calls.
 */
@Component
public class GithubApiClient {
    
    private static final Logger logger = LoggerFactory.getLogger(GithubApiClient.class);
    
    /** JWT expiration time for GitHub App tokens (10 minutes max) */
    private static final long JWT_EXPIRATION_MINUTES = 9;
    
    /** Installation token cache duration (50 minutes, tokens are valid for 60) */
    private static final long INSTALLATION_TOKEN_CACHE_MINUTES = 50;
    
    private final GithubConfig config;
    
    /** Cached GitHub instances per installation ID */
    private final ConcurrentMap<Integer, CachedGitHub> githubCache = new ConcurrentHashMap<>();
    
    /** Cached installation tokens */
    private final ConcurrentMap<Integer, CachedToken> tokenCache = new ConcurrentHashMap<>();
    
    @Autowired
    public GithubApiClient(GithubConfig config) {
        this.config = config;
        config.validate(); // Validate configuration on startup
        
        logger.info("GitHub API client initialized: {}", config.getConfigSummary());
    }
    
    /**
     * Get GitHub client instance using Personal Access Token.
     *
     * @return GitHub client
     * @throws GithubException if PAT is not configured or invalid
     */
    public GitHub getGitHubWithToken() throws GithubException {
        if (!config.hasToken()) {
            throw GithubException.credentialsMissing();
        }
        
        try {
            GitHubBuilder builder = new GitHubBuilder()
                .withOAuthToken(config.getToken())
                .withEndpoint(config.getApiBase());
            
            configureClient(builder);
            
            GitHub github = builder.build();
            
            // Test authentication
            github.checkApiUrlValidity();
            logger.debug("GitHub PAT authentication successful");
            
            return github;
            
        } catch (IOException e) {
            if (e.getMessage().contains("401")) {
                throw new GithubException(GithubException.ErrorCode.AUTH_FAILED, 
                    "Invalid GitHub token", 401);
            } else if (e.getMessage().contains("403")) {
                throw new GithubException(GithubException.ErrorCode.RATE_LIMIT_EXCEEDED, 
                    "GitHub API rate limit exceeded", 403);
            } else {
                throw new GithubException(GithubException.ErrorCode.API_CONFIG_ERROR, 
                    "Failed to connect to GitHub API: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Get GitHub client instance using GitHub App authentication for a specific installation.
     *
     * @param installationId GitHub App installation ID
     * @return GitHub client authenticated for the installation
     * @throws GithubException if App auth is not configured or fails
     */
    public GitHub getGitHubWithAppAuth(int installationId) throws GithubException {
        if (!config.hasAppAuth()) {
            throw GithubException.appConfigInvalid("GitHub App credentials are not configured");
        }
        
        // Check cache first
        CachedGitHub cached = githubCache.get(installationId);
        if (cached != null && cached.isValid()) {
            logger.debug("Using cached GitHub client for installation {}", installationId);
            return cached.github;
        }
        
        try {
            // Get installation access token
            String installationToken = getInstallationToken(installationId);
            
            // Create GitHub client with installation token
            GitHubBuilder builder = new GitHubBuilder()
                .withOAuthToken(installationToken)
                .withEndpoint(config.getApiBase());
            
            configureClient(builder);
            
            GitHub github = builder.build();
            
            // Test authentication
            github.checkApiUrlValidity();
            
            // Cache the client
            githubCache.put(installationId, new CachedGitHub(github, Instant.now()));
            
            logger.debug("GitHub App authentication successful for installation {}", installationId);
            return github;
            
        } catch (IOException e) {
            clearCache(installationId);
            
            if (e.getMessage().contains("401")) {
            throw new GithubException(GithubException.ErrorCode.AUTH_FAILED, 
                "GitHub App authentication failed for installation " + installationId, 401);
            } else if (e.getMessage().contains("404")) {
            throw new GithubException(GithubException.ErrorCode.API_ENDPOINT_NOT_FOUND, 
                "GitHub App installation not found: " + installationId, 404);
            } else {
                throw new GithubException(GithubException.ErrorCode.API_CONFIG_ERROR, 
                    "Failed to authenticate GitHub App: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Get GitHub client instance using best available authentication method.
     * 
     * <p>Prefers GitHub App authentication if available, falls back to PAT.
     * For GitHub App, requires installation ID to be provided.
     *
     * @param installationId optional installation ID for GitHub App auth
     * @return GitHub client
     * @throws GithubException if no authentication method works
     */
    public GitHub getGitHub(Integer installationId) throws GithubException {
        if (config.hasAppAuth() && installationId != null) {
            return getGitHubWithAppAuth(installationId);
        } else if (config.hasToken()) {
            return getGitHubWithToken();
        } else {
            throw GithubException.credentialsMissing();
        }
    }
    
    /**
     * Get installation access token for GitHub App.
     *
     * @param installationId installation ID
     * @return installation access token
     * @throws GithubException if token generation fails
     */
    private String getInstallationToken(int installationId) throws GithubException {
        // Check token cache
        CachedToken cached = tokenCache.get(installationId);
        if (cached != null && cached.isValid()) {
            logger.debug("Using cached installation token for installation {}", installationId);
            return cached.token;
        }
        
        try {
            // Generate JWT token for app authentication
            String jwtToken = generateJwtToken();
            
            // Create GitHub client with JWT
            GitHub appGitHub = new GitHubBuilder()
                .withJwtToken(jwtToken)
                .withEndpoint(config.getApiBase())
                .build();
            
            // Get installation and create access token
            GHAppInstallation installation = appGitHub.getApp().getInstallationById(installationId);
            GHAppInstallationToken token = installation.createToken().create();
            
            String accessToken = token.getToken();
            
            // Cache the token
            tokenCache.put(installationId, new CachedToken(accessToken, Instant.now()));
            
            logger.debug("Generated new installation token for installation {}", installationId);
            return accessToken;
            
        } catch (IOException e) {
            throw new GithubException(GithubException.ErrorCode.AUTH_FAILED, 
                "Failed to get installation token for installation " + installationId, e);
        }
    }
    
    /**
     * Generate JWT token for GitHub App authentication.
     *
     * @return JWT token
     * @throws GithubException if JWT generation fails
     */
    private String generateJwtToken() throws GithubException {
        try {
            // Parse private key
            PrivateKey privateKey = parsePrivateKey(config.getPrivateKey());
            
            // Create JWT token
            Instant now = Instant.now();
            Instant expiration = now.plus(JWT_EXPIRATION_MINUTES, ChronoUnit.MINUTES);
            
            return Jwts.builder()
                .setIssuer(config.getAppId())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiration))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
            
        } catch (Exception e) {
            throw GithubException.appConfigInvalid("Failed to generate JWT token: " + e.getMessage());
        }
    }
    
    /**
     * Parse PEM-formatted private key.
     *
     * @param privateKeyPem private key in PEM format
     * @return PrivateKey instance
     * @throws Exception if parsing fails
     */
    private PrivateKey parsePrivateKey(String privateKeyPem) throws Exception {
        // Remove PEM headers and footers, and whitespace
        String privateKeyContent = privateKeyPem
            .replaceAll("-----BEGIN[^-]+-----", "")
            .replaceAll("-----END[^-]+-----", "")
            .replaceAll("\\s+", "");
        
        // Decode base64
        byte[] keyBytes = Base64.getDecoder().decode(privateKeyContent);
        
        // Create private key
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(spec);
    }
    
    /**
     * Configure GitHub client with timeouts and other settings.
     *
     * @param builder GitHub builder
     */
    private void configureClient(GitHubBuilder builder) {
            // Configure with default connector and handlers
            builder.withRateLimitHandler(RateLimitHandler.WAIT)
                   .withAbuseLimitHandler(AbuseLimitHandler.WAIT);
    }
    
    /**
     * Clear cache for specific installation.
     *
     * @param installationId installation ID
     */
    public void clearCache(int installationId) {
        githubCache.remove(installationId);
        tokenCache.remove(installationId);
        logger.debug("Cleared cache for installation {}", installationId);
    }
    
    /**
     * Clear all caches.
     */
    public void clearAllCaches() {
        githubCache.clear();
        tokenCache.clear();
        logger.debug("Cleared all GitHub client caches");
    }
    
    /**
     * Get cache statistics for monitoring.
     *
     * @return cache statistics
     */
    public String getCacheStats() {
        return String.format("GitHub Client Cache - Clients: %d, Tokens: %d", 
            githubCache.size(), tokenCache.size());
    }
    
    /**
     * Check if the API is accessible and credentials are valid.
     *
     * @return true if API is accessible
     */
    public boolean isApiAccessible() {
        try {
            GitHub github = config.hasToken() ? getGitHubWithToken() : null;
            if (github != null) {
                github.checkApiUrlValidity();
                return true;
            }
        } catch (Exception e) {
            logger.debug("API accessibility check failed", e);
        }
        return false;
    }
    
    /**
     * Cached GitHub client instance.
     */
    private static class CachedGitHub {
        final GitHub github;
        final Instant timestamp;
        
        CachedGitHub(GitHub github, Instant timestamp) {
            this.github = github;
            this.timestamp = timestamp;
        }
        
        boolean isValid() {
            return Instant.now().isBefore(timestamp.plus(INSTALLATION_TOKEN_CACHE_MINUTES, ChronoUnit.MINUTES));
        }
    }
    
    /**
     * Cached installation token.
     */
    private static class CachedToken {
        final String token;
        final Instant timestamp;
        
        CachedToken(String token, Instant timestamp) {
            this.token = token;
            this.timestamp = timestamp;
        }
        
        boolean isValid() {
            return Instant.now().isBefore(timestamp.plus(INSTALLATION_TOKEN_CACHE_MINUTES, ChronoUnit.MINUTES));
        }
    }
}

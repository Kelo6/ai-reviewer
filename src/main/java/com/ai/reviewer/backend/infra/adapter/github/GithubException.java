package com.ai.reviewer.backend.infra.adapter.github;

import com.ai.reviewer.backend.domain.adapter.scm.ScmAdapterException;

/**
 * GitHub-specific adapter exception with detailed error codes and context.
 * 
 * <p>Extends {@link ScmAdapterException} to provide GitHub-specific error
 * handling including authentication failures, rate limiting, and API errors.
 */
public class GithubException extends ScmAdapterException {
    
    /**
     * GitHub-specific error codes for consistent error handling.
     */
    public enum ErrorCode {
        /** GitHub credentials are missing or invalid */
        CREDENTIALS_MISSING("GITHUB_001", "GitHub credentials are missing"),
        
        /** GitHub App configuration is invalid */
        APP_CONFIG_INVALID("GITHUB_002", "GitHub App configuration is invalid"),
        
        /** GitHub API authentication failed */
        AUTH_FAILED("GITHUB_003", "GitHub API authentication failed"),
        
        /** GitHub API rate limit exceeded */
        RATE_LIMIT_EXCEEDED("GITHUB_004", "GitHub API rate limit exceeded"),
        
        /** Repository not found or access denied */
        REPO_NOT_FOUND("GITHUB_005", "Repository not found or access denied"),
        
        /** Pull request not found */
        PULL_REQUEST_NOT_FOUND("GITHUB_006", "Pull request not found"),
        
        /** Webhook signature verification failed */
        WEBHOOK_SIGNATURE_INVALID("GITHUB_007", "Webhook signature verification failed"),
        
        /** GitHub API returned invalid response */
        API_RESPONSE_INVALID("GITHUB_008", "GitHub API returned invalid response"),
        
        /** GitHub API endpoint not found */
        API_ENDPOINT_NOT_FOUND("GITHUB_009", "GitHub API endpoint not found"),
        
        /** Check run creation/update failed */
        CHECK_RUN_FAILED("GITHUB_010", "Check run creation/update failed"),
        
        /** Comment creation/update failed */
        COMMENT_FAILED("GITHUB_011", "Comment creation/update failed"),
        
        /** File content retrieval failed */
        FILE_CONTENT_FAILED("GITHUB_012", "File content retrieval failed"),
        
        /** Diff retrieval failed */
        DIFF_RETRIEVAL_FAILED("GITHUB_013", "Diff retrieval failed"),
        
        /** GitHub API configuration error */
        API_CONFIG_ERROR("GITHUB_014", "GitHub API configuration error");
        
        private final String code;
        private final String defaultMessage;
        
        ErrorCode(String code, String defaultMessage) {
            this.code = code;
            this.defaultMessage = defaultMessage;
        }
        
        public String getCode() {
            return code;
        }
        
        public String getDefaultMessage() {
            return defaultMessage;
        }
    }
    
    private final ErrorCode errorCode;
    private final String repositoryInfo;
    
    /**
     * Create GitHub exception with error code.
     *
     * @param errorCode GitHub-specific error code
     * @param message detailed error message
     */
    public GithubException(ErrorCode errorCode, String message) {
        super("github", errorCode.name().toLowerCase(), message);
        this.errorCode = errorCode;
        this.repositoryInfo = null;
    }
    
    /**
     * Create GitHub exception with error code and cause.
     *
     * @param errorCode GitHub-specific error code
     * @param message detailed error message
     * @param cause underlying cause
     */
    public GithubException(ErrorCode errorCode, String message, Throwable cause) {
        super("github", errorCode.name().toLowerCase(), message, cause);
        this.errorCode = errorCode;
        this.repositoryInfo = null;
    }
    
    /**
     * Create GitHub exception with HTTP status code.
     *
     * @param errorCode GitHub-specific error code
     * @param message detailed error message
     * @param statusCode HTTP status code
     */
    public GithubException(ErrorCode errorCode, String message, int statusCode) {
        super("github", errorCode.name().toLowerCase(), message, statusCode);
        this.errorCode = errorCode;
        this.repositoryInfo = null;
    }
    
    /**
     * Create GitHub exception with HTTP status code and cause.
     *
     * @param errorCode GitHub-specific error code
     * @param message detailed error message
     * @param statusCode HTTP status code
     * @param cause underlying cause
     */
    public GithubException(ErrorCode errorCode, String message, int statusCode, Throwable cause) {
        super("github", errorCode.name().toLowerCase(), message, statusCode, cause);
        this.errorCode = errorCode;
        this.repositoryInfo = null;
    }
    
    /**
     * Create GitHub exception with repository context.
     *
     * @param errorCode GitHub-specific error code
     * @param message detailed error message
     * @param repositoryInfo repository information for context
     */
    public GithubException(ErrorCode errorCode, String message, String repositoryInfo) {
        super("github", errorCode.name().toLowerCase(), 
              String.format("%s (repo: %s)", message, repositoryInfo));
        this.errorCode = errorCode;
        this.repositoryInfo = repositoryInfo;
    }
    
    /**
     * Create GitHub exception with full context.
     *
     * @param errorCode GitHub-specific error code
     * @param message detailed error message
     * @param repositoryInfo repository information
     * @param statusCode HTTP status code
     * @param cause underlying cause
     */
    public GithubException(ErrorCode errorCode, String message, String repositoryInfo, 
                          int statusCode, Throwable cause) {
        super("github", errorCode.name().toLowerCase(), 
              String.format("%s (repo: %s)", message, repositoryInfo), statusCode, cause);
        this.errorCode = errorCode;
        this.repositoryInfo = repositoryInfo;
    }
    
    /**
     * Get the GitHub-specific error code.
     *
     * @return error code
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
    
    /**
     * Get the error code string.
     *
     * @return error code string
     */
    public String getCode() {
        return errorCode.getCode();
    }
    
    /**
     * Get repository information if available.
     *
     * @return repository info or null
     */
    public String getRepositoryInfo() {
        return repositoryInfo;
    }
    
    /**
     * Check if this is a credentials-related error.
     *
     * @return true if credentials are missing or invalid
     */
    public boolean isCredentialsError() {
        return errorCode == ErrorCode.CREDENTIALS_MISSING || 
               errorCode == ErrorCode.APP_CONFIG_INVALID ||
               errorCode == ErrorCode.AUTH_FAILED;
    }
    
    /**
     * Check if this is a configuration error.
     *
     * @return true if this is a configuration-related error
     */
    public boolean isConfigurationError() {
        return errorCode == ErrorCode.APP_CONFIG_INVALID ||
               errorCode == ErrorCode.API_CONFIG_ERROR;
    }
    
    /**
     * Check if this is a rate limit error.
     *
     * @return true if rate limit was exceeded
     */
    public boolean isRateLimitError() {
        return errorCode == ErrorCode.RATE_LIMIT_EXCEEDED || getStatusCode() == 429;
    }
    
    /**
     * Check if this is a "not found" error.
     *
     * @return true if resource was not found
     */
    public boolean isNotFoundError() {
        return errorCode == ErrorCode.REPO_NOT_FOUND ||
               errorCode == ErrorCode.PULL_REQUEST_NOT_FOUND ||
               errorCode == ErrorCode.API_ENDPOINT_NOT_FOUND ||
               getStatusCode() == 404;
    }
    
    /**
     * Get a user-friendly error message.
     *
     * @return user-friendly message
     */
    public String getUserFriendlyMessage() {
        return switch (errorCode) {
            case CREDENTIALS_MISSING -> 
                "GitHub authentication is not configured. Please set GITHUB_TOKEN or GitHub App credentials.";
            case APP_CONFIG_INVALID -> 
                "GitHub App configuration is invalid. Please check GITHUB_APP_ID and GITHUB_PRIVATE_KEY.";
            case AUTH_FAILED -> 
                "GitHub authentication failed. Please verify your credentials.";
            case RATE_LIMIT_EXCEEDED -> 
                "GitHub API rate limit exceeded. Please try again later.";
            case REPO_NOT_FOUND -> 
                "Repository not found or access denied. Please check repository permissions.";
            case PULL_REQUEST_NOT_FOUND -> 
                "Pull request not found. It may have been deleted or you lack access.";
            case WEBHOOK_SIGNATURE_INVALID -> 
                "Webhook signature verification failed. Please check your webhook secret.";
            default -> errorCode.getDefaultMessage();
        };
    }
    
    /**
     * Create a credentials missing exception.
     *
     * @return credentials missing exception
     */
    public static GithubException credentialsMissing() {
        return new GithubException(ErrorCode.CREDENTIALS_MISSING, 
            "Neither GITHUB_TOKEN nor GitHub App credentials (GITHUB_APP_ID + GITHUB_PRIVATE_KEY) are configured");
    }
    
    /**
     * Create an invalid GitHub App config exception.
     *
     * @param reason specific reason for the invalid config
     * @return app config invalid exception
     */
    public static GithubException appConfigInvalid(String reason) {
        return new GithubException(ErrorCode.APP_CONFIG_INVALID, 
            "GitHub App configuration is invalid: " + reason);
    }
    
    /**
     * Create a webhook signature validation exception.
     *
     * @param reason specific validation failure reason
     * @return webhook signature exception
     */
    public static GithubException webhookSignatureInvalid(String reason) {
        return new GithubException(ErrorCode.WEBHOOK_SIGNATURE_INVALID, 
            "Webhook signature validation failed: " + reason);
    }
    
    /**
     * Create a repository not found exception.
     *
     * @param owner repository owner
     * @param name repository name
     * @return repository not found exception
     */
    public static GithubException repositoryNotFound(String owner, String name) {
        return new GithubException(ErrorCode.REPO_NOT_FOUND, 
            "Repository not found or access denied", 
            String.format("%s/%s", owner, name));
    }
    
    /**
     * Create a pull request not found exception.
     *
     * @param owner repository owner
     * @param name repository name
     * @param number pull request number
     * @return pull request not found exception
     */
    public static GithubException pullRequestNotFound(String owner, String name, String number) {
        return new GithubException(ErrorCode.PULL_REQUEST_NOT_FOUND, 
            String.format("Pull request #%s not found", number), 
            String.format("%s/%s", owner, name));
    }
}

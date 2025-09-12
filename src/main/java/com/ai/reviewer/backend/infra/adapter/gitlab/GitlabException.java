package com.ai.reviewer.backend.infra.adapter.gitlab;

import com.ai.reviewer.backend.domain.adapter.scm.ScmAdapterException;

/**
 * GitLab-specific adapter exception with detailed error codes and context.
 * 
 * <p>Extends {@link ScmAdapterException} to provide GitLab-specific error
 * handling including authentication failures, rate limiting, and API errors.
 */
public class GitlabException extends ScmAdapterException {
    
    /**
     * GitLab-specific error codes for consistent error handling.
     */
    public enum ErrorCode {
        /** GitLab credentials are missing or invalid */
        CREDENTIALS_MISSING("GITLAB_001", "GitLab credentials are missing"),
        
        /** GitLab API authentication failed */
        AUTH_FAILED("GITLAB_002", "GitLab API authentication failed"),
        
        /** GitLab API rate limit exceeded */
        RATE_LIMIT_EXCEEDED("GITLAB_003", "GitLab API rate limit exceeded"),
        
        /** Repository not found or access denied */
        REPO_NOT_FOUND("GITLAB_004", "Repository not found or access denied"),
        
        /** Merge request not found */
        MERGE_REQUEST_NOT_FOUND("GITLAB_005", "Merge request not found"),
        
        /** Webhook signature verification failed */
        WEBHOOK_SIGNATURE_INVALID("GITLAB_006", "Webhook signature verification failed"),
        
        /** GitLab API returned invalid response */
        API_RESPONSE_INVALID("GITLAB_007", "GitLab API returned invalid response"),
        
        /** GitLab API endpoint not found */
        API_ENDPOINT_NOT_FOUND("GITLAB_008", "GitLab API endpoint not found"),
        
        /** Pipeline/commit status creation/update failed */
        STATUS_UPDATE_FAILED("GITLAB_009", "Status update failed"),
        
        /** Discussion/note creation/update failed */
        DISCUSSION_FAILED("GITLAB_010", "Discussion creation/update failed"),
        
        /** File content retrieval failed */
        FILE_CONTENT_FAILED("GITLAB_011", "File content retrieval failed"),
        
        /** Changes/diff retrieval failed */
        CHANGES_RETRIEVAL_FAILED("GITLAB_012", "Changes retrieval failed"),
        
        /** GitLab API configuration error */
        API_CONFIG_ERROR("GITLAB_013", "GitLab API configuration error"),
        
        /** SSL certificate verification failed */
        SSL_VERIFICATION_FAILED("GITLAB_014", "SSL certificate verification failed");
        
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
     * Create GitLab exception with error code.
     *
     * @param errorCode GitLab-specific error code
     * @param message detailed error message
     */
    public GitlabException(ErrorCode errorCode, String message) {
        super("gitlab", errorCode.name().toLowerCase(), message);
        this.errorCode = errorCode;
        this.repositoryInfo = null;
    }
    
    /**
     * Create GitLab exception with error code and cause.
     *
     * @param errorCode GitLab-specific error code
     * @param message detailed error message
     * @param cause underlying cause
     */
    public GitlabException(ErrorCode errorCode, String message, Throwable cause) {
        super("gitlab", errorCode.name().toLowerCase(), message, cause);
        this.errorCode = errorCode;
        this.repositoryInfo = null;
    }
    
    /**
     * Create GitLab exception with HTTP status code.
     *
     * @param errorCode GitLab-specific error code
     * @param message detailed error message
     * @param statusCode HTTP status code
     */
    public GitlabException(ErrorCode errorCode, String message, int statusCode) {
        super("gitlab", errorCode.name().toLowerCase(), message, statusCode);
        this.errorCode = errorCode;
        this.repositoryInfo = null;
    }
    
    /**
     * Create GitLab exception with HTTP status code and cause.
     *
     * @param errorCode GitLab-specific error code
     * @param message detailed error message
     * @param statusCode HTTP status code
     * @param cause underlying cause
     */
    public GitlabException(ErrorCode errorCode, String message, int statusCode, Throwable cause) {
        super("gitlab", errorCode.name().toLowerCase(), message, statusCode, cause);
        this.errorCode = errorCode;
        this.repositoryInfo = null;
    }
    
    /**
     * Create GitLab exception with repository context.
     *
     * @param errorCode GitLab-specific error code
     * @param message detailed error message
     * @param repositoryInfo repository information for context
     */
    public GitlabException(ErrorCode errorCode, String message, String repositoryInfo) {
        super("gitlab", errorCode.name().toLowerCase(), 
              String.format("%s (repo: %s)", message, repositoryInfo));
        this.errorCode = errorCode;
        this.repositoryInfo = repositoryInfo;
    }
    
    /**
     * Get the GitLab-specific error code.
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
               errorCode == ErrorCode.AUTH_FAILED;
    }
    
    /**
     * Check if this is a configuration error.
     *
     * @return true if this is a configuration-related error
     */
    public boolean isConfigurationError() {
        return errorCode == ErrorCode.API_CONFIG_ERROR ||
               errorCode == ErrorCode.SSL_VERIFICATION_FAILED;
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
               errorCode == ErrorCode.MERGE_REQUEST_NOT_FOUND ||
               errorCode == ErrorCode.API_ENDPOINT_NOT_FOUND ||
               getStatusCode() == 404;
    }
    
    /**
     * Check if this is an SSL-related error.
     *
     * @return true if SSL verification failed
     */
    public boolean isSslError() {
        return errorCode == ErrorCode.SSL_VERIFICATION_FAILED;
    }
    
    /**
     * Get a user-friendly error message.
     *
     * @return user-friendly message
     */
    public String getUserFriendlyMessage() {
        return switch (errorCode) {
            case CREDENTIALS_MISSING -> 
                "GitLab authentication is not configured. Please set GITLAB_TOKEN.";
            case AUTH_FAILED -> 
                "GitLab authentication failed. Please verify your token.";
            case RATE_LIMIT_EXCEEDED -> 
                "GitLab API rate limit exceeded. Please try again later.";
            case REPO_NOT_FOUND -> 
                "Repository not found or access denied. Please check repository permissions.";
            case MERGE_REQUEST_NOT_FOUND -> 
                "Merge request not found. It may have been deleted or you lack access.";
            case WEBHOOK_SIGNATURE_INVALID -> 
                "Webhook signature verification failed. Please check your webhook secret.";
            case SSL_VERIFICATION_FAILED ->
                "SSL certificate verification failed. Consider setting GITLAB_IGNORE_CERT_ERRORS=true for self-signed certificates.";
            default -> errorCode.getDefaultMessage();
        };
    }
    
    /**
     * Create a credentials missing exception.
     *
     * @return credentials missing exception
     */
    public static GitlabException credentialsMissing() {
        return new GitlabException(ErrorCode.CREDENTIALS_MISSING, 
            "GITLAB_TOKEN is not configured");
    }
    
    /**
     * Create a webhook signature validation exception.
     *
     * @param reason specific validation failure reason
     * @return webhook signature exception
     */
    public static GitlabException webhookSignatureInvalid(String reason) {
        return new GitlabException(ErrorCode.WEBHOOK_SIGNATURE_INVALID, 
            "Webhook signature validation failed: " + reason);
    }
    
    /**
     * Create a repository not found exception.
     *
     * @param projectId project ID or path
     * @return repository not found exception
     */
    public static GitlabException repositoryNotFound(String projectId) {
        return new GitlabException(ErrorCode.REPO_NOT_FOUND, 
            "Repository not found or access denied", projectId);
    }
    
    /**
     * Create a merge request not found exception.
     *
     * @param projectId project ID or path
     * @param mergeRequestIid merge request IID
     * @return merge request not found exception
     */
    public static GitlabException mergeRequestNotFound(String projectId, String mergeRequestIid) {
        return new GitlabException(ErrorCode.MERGE_REQUEST_NOT_FOUND, 
            String.format("Merge request !%s not found", mergeRequestIid), projectId);
    }
    
    /**
     * Create an SSL verification failure exception.
     *
     * @param serverUrl server URL that failed SSL verification
     * @param cause underlying SSL exception
     * @return SSL verification exception
     */
    public static GitlabException sslVerificationFailed(String serverUrl, Throwable cause) {
        return new GitlabException(ErrorCode.SSL_VERIFICATION_FAILED,
            "SSL certificate verification failed for " + serverUrl +
            ". Consider setting GITLAB_IGNORE_CERT_ERRORS=true for self-signed certificates.", cause);
    }
}

package com.ai.reviewer.backend.domain.adapter.scm;

/**
 * Exception thrown by SCM adapter operations.
 * 
 * <p>Wraps provider-specific exceptions and errors into a common
 * exception type that can be handled uniformly by the application.
 */
public class ScmAdapterException extends RuntimeException {

    private final String provider;
    private final String operation;
    private final int statusCode;

    /**
     * Create an SCM adapter exception.
     *
     * @param provider SCM provider name
     * @param operation operation that failed
     * @param message error message
     */
    public ScmAdapterException(String provider, String operation, String message) {
        super(String.format("[%s] %s failed: %s", provider, operation, message));
        this.provider = provider;
        this.operation = operation;
        this.statusCode = -1;
    }

    /**
     * Create an SCM adapter exception with a cause.
     *
     * @param provider SCM provider name
     * @param operation operation that failed
     * @param message error message
     * @param cause underlying cause
     */
    public ScmAdapterException(String provider, String operation, String message, Throwable cause) {
        super(String.format("[%s] %s failed: %s", provider, operation, message), cause);
        this.provider = provider;
        this.operation = operation;
        this.statusCode = -1;
    }

    /**
     * Create an SCM adapter exception with HTTP status code.
     *
     * @param provider SCM provider name
     * @param operation operation that failed
     * @param message error message
     * @param statusCode HTTP status code
     */
    public ScmAdapterException(String provider, String operation, String message, int statusCode) {
        super(String.format("[%s] %s failed with status %d: %s", provider, operation, statusCode, message));
        this.provider = provider;
        this.operation = operation;
        this.statusCode = statusCode;
    }

    /**
     * Create an SCM adapter exception with status code and cause.
     *
     * @param provider SCM provider name
     * @param operation operation that failed
     * @param message error message
     * @param statusCode HTTP status code
     * @param cause underlying cause
     */
    public ScmAdapterException(String provider, String operation, String message, int statusCode, Throwable cause) {
        super(String.format("[%s] %s failed with status %d: %s", provider, operation, statusCode, message), cause);
        this.provider = provider;
        this.operation = operation;
        this.statusCode = statusCode;
    }

    /**
     * Get the SCM provider name.
     *
     * @return provider name
     */
    public String getProvider() {
        return provider;
    }

    /**
     * Get the operation that failed.
     *
     * @return operation name
     */
    public String getOperation() {
        return operation;
    }

    /**
     * Get the HTTP status code if available.
     *
     * @return status code or -1 if not available
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Check if this exception includes an HTTP status code.
     *
     * @return true if status code is available
     */
    public boolean hasStatusCode() {
        return statusCode > 0;
    }

    /**
     * Check if this is a client error (4xx status code).
     *
     * @return true if this is a client error
     */
    public boolean isClientError() {
        return statusCode >= 400 && statusCode < 500;
    }

    /**
     * Check if this is a server error (5xx status code).
     *
     * @return true if this is a server error
     */
    public boolean isServerError() {
        return statusCode >= 500 && statusCode < 600;
    }

    /**
     * Check if this error indicates authentication/authorization failure.
     *
     * @return true if this is an auth-related error
     */
    public boolean isAuthError() {
        return statusCode == 401 || statusCode == 403;
    }

    /**
     * Check if this error indicates a rate limit was exceeded.
     *
     * @return true if this is a rate limit error
     */
    public boolean isRateLimitError() {
        return statusCode == 429;
    }
}

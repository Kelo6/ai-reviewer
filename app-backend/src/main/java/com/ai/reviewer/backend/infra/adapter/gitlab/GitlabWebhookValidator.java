package com.ai.reviewer.backend.infra.adapter.gitlab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * GitLab webhook signature validator using HMAC-SHA256.
 * 
 * <p>Validates GitLab webhook signatures to ensure requests are authentic
 * and haven't been tampered with. GitLab uses SHA-256 HMAC with the
 * webhook secret to sign requests.
 * 
 * <p>GitLab webhook signatures are sent in the {@code X-Gitlab-Token} header.
 * Unlike GitHub, GitLab doesn't use a prefix format - the header contains
 * the raw HMAC-SHA256 hash as a hex string.
 */
@Component
public class GitlabWebhookValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(GitlabWebhookValidator.class);
    
    // GitLab webhook signature header
    private static final String SIGNATURE_HEADER = "X-Gitlab-Token";
    
    // Case-insensitive header variations
    private static final String[] SIGNATURE_HEADERS = {
        "X-Gitlab-Token", "x-gitlab-token", "X-GITLAB-TOKEN"
    };
    
    // Algorithm name
    private static final String HMAC_SHA256 = "HmacSHA256";
    
    /**
     * Verify GitLab webhook signature.
     * 
     * <p>GitLab webhook signature validation works differently from GitHub:
     * <ul>
     *   <li>Uses {@code X-Gitlab-Token} header (not X-Hub-Signature*)</li>
     *   <li>Token should match the configured webhook secret exactly</li>
     *   <li>No HMAC calculation needed - direct string comparison</li>
     * </ul>
     *
     * @param headers HTTP headers from webhook request (case-insensitive lookup)
     * @param rawBody raw request body bytes (not used in GitLab validation)
     * @param secret webhook secret for signature verification
     * @return true if signature is valid, false otherwise
     * @throws GitlabException if signature validation fails due to configuration issues
     */
    public boolean verifySignature(Map<String, String> headers, byte[] rawBody, String secret) 
            throws GitlabException {
        
        if (headers == null) {
            logger.debug("Signature verification failed: null headers");
            return false;
        }
        
        if (!StringUtils.hasText(secret)) {
            throw GitlabException.webhookSignatureInvalid("Webhook secret is not configured");
        }
        
        // Get GitLab token from headers
        String gitlabToken = getHeader(headers, SIGNATURE_HEADERS);
        if (!StringUtils.hasText(gitlabToken)) {
            logger.warn("No GitLab webhook signature header found. Expected: {}", SIGNATURE_HEADER);
            throw GitlabException.webhookSignatureInvalid("No signature header found in webhook request");
        }
        
        logger.debug("Validating GitLab webhook signature");
        
        // GitLab uses simple token comparison (not HMAC)
        boolean isValid = constantTimeEquals(secret, gitlabToken);
        
        if (isValid) {
            logger.debug("GitLab webhook signature validation successful");
        } else {
            logger.debug("GitLab webhook signature validation failed");
        }
        
        return isValid;
    }
    
    /**
     * Alternative verification using HMAC-SHA256 for custom implementations.
     * 
     * <p>Some GitLab installations or webhook configurations might use HMAC-SHA256
     * similar to GitHub. This method provides that capability.
     *
     * @param headers HTTP headers from webhook request
     * @param rawBody raw request body bytes
     * @param secret webhook secret
     * @return true if HMAC signature is valid
     * @throws GitlabException if signature validation fails
     */
    public boolean verifyHmacSignature(Map<String, String> headers, byte[] rawBody, String secret) 
            throws GitlabException {
        
        if (headers == null || rawBody == null) {
            logger.debug("HMAC signature verification failed: null headers or body");
            return false;
        }
        
        if (!StringUtils.hasText(secret)) {
            throw GitlabException.webhookSignatureInvalid("Webhook secret is not configured");
        }
        
        // Get signature from headers
        String signature = getHeader(headers, SIGNATURE_HEADERS);
        if (!StringUtils.hasText(signature)) {
            throw GitlabException.webhookSignatureInvalid("No signature header found in webhook request");
        }
        
        try {
            // Calculate expected HMAC signature
            String calculatedSignature = calculateHmacSignature(rawBody, secret);
            
            // Perform constant-time comparison to prevent timing attacks
            boolean isValid = constantTimeEquals(signature, calculatedSignature);
            
            if (isValid) {
                logger.debug("GitLab webhook HMAC signature validation successful");
            } else {
                logger.debug("GitLab webhook HMAC signature validation failed");
            }
            
            return isValid;
            
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw GitlabException.webhookSignatureInvalid(
                "Failed to calculate HMAC signature: " + e.getMessage());
        }
    }
    
    /**
     * Get header value with case-insensitive lookup.
     *
     * @param headers headers map
     * @param headerNames possible header names to check
     * @return header value or null if not found
     */
    private String getHeader(Map<String, String> headers, String[] headerNames) {
        for (String headerName : headerNames) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (headerName.equalsIgnoreCase(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }
    
    /**
     * Calculate HMAC-SHA256 signature for given body and secret.
     *
     * @param body request body
     * @param secret webhook secret
     * @return hex-encoded signature
     * @throws NoSuchAlgorithmException if algorithm is not supported
     * @throws InvalidKeyException if secret is invalid
     */
    private String calculateHmacSignature(byte[] body, String secret) 
            throws NoSuchAlgorithmException, InvalidKeyException {
        
        SecretKeySpec signingKey = new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
        
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(signingKey);
        
        byte[] signatureBytes = mac.doFinal(body);
        return bytesToHex(signatureBytes);
    }
    
    /**
     * Convert byte array to hexadecimal string.
     *
     * @param bytes byte array
     * @return hex string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    /**
     * Constant-time string comparison to prevent timing attacks.
     * 
     * <p>This method ensures that the comparison time is constant regardless
     * of where the strings differ, which helps prevent timing-based attacks
     * on signature validation.
     *
     * @param expected expected signature
     * @param actual actual signature
     * @return true if strings are equal
     */
    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        
        if (expected.length() != actual.length()) {
            return false;
        }
        
        int result = 0;
        for (int i = 0; i < expected.length(); i++) {
            result |= expected.charAt(i) ^ actual.charAt(i);
        }
        
        return result == 0;
    }
    
    /**
     * Check if GitLab token signature is present in headers.
     *
     * @param headers HTTP headers
     * @return true if GitLab token header is present
     */
    public boolean hasSignature(Map<String, String> headers) {
        return StringUtils.hasText(getHeader(headers, SIGNATURE_HEADERS));
    }
    
    /**
     * Generate test signature for given secret (for testing purposes).
     *
     * @param secret webhook secret
     * @return test signature (just returns the secret for GitLab's simple token method)
     */
    public String generateTestSignature(String secret) {
        return secret; // GitLab uses direct token comparison
    }
    
    /**
     * Generate test HMAC signature for given body and secret (for testing purposes).
     *
     * @param body request body
     * @param secret webhook secret
     * @return test HMAC signature
     */
    public String generateTestHmacSignature(String body, String secret) {
        try {
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            return calculateHmacSignature(bodyBytes, secret);
        } catch (Exception e) {
            logger.error("Failed to generate test HMAC signature", e);
            return null;
        }
    }
    
    /**
     * Get supported signature methods.
     *
     * @return array of supported methods
     */
    public String[] getSupportedMethods() {
        return new String[]{"Token", "HMAC-SHA256"};
    }
    
    /**
     * Validate webhook headers for required GitLab headers.
     *
     * @param headers HTTP headers
     * @return true if headers contain required GitLab webhook headers
     */
    public boolean hasRequiredHeaders(Map<String, String> headers) {
        if (headers == null) {
            return false;
        }
        
        // Check for GitLab-specific headers
        String[] requiredHeaders = {
            "X-Gitlab-Event", "x-gitlab-event", "X-GITLAB-EVENT"
        };
        
        for (String headerName : requiredHeaders) {
            for (String actualHeader : headers.keySet()) {
                if (headerName.equalsIgnoreCase(actualHeader)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Get GitLab event type from headers.
     *
     * @param headers HTTP headers
     * @return GitLab event type or null if not found
     */
    public String getEventType(Map<String, String> headers) {
        String[] eventHeaders = {
            "X-Gitlab-Event", "x-gitlab-event", "X-GITLAB-EVENT"
        };
        return getHeader(headers, eventHeaders);
    }
}

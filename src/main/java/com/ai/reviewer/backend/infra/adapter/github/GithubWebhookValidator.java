package com.ai.reviewer.backend.infra.adapter.github;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * GitHub webhook signature validator using HMAC-SHA256.
 * 
 * <p>Validates GitHub webhook signatures to ensure requests are authentic
 * and haven't been tampered with. Supports both SHA-1 (legacy) and SHA-256
 * signature algorithms.
 * 
 * <p>GitHub webhook signatures are sent in the following headers:
 * <ul>
 *   <li>{@code X-Hub-Signature} - SHA-1 signature (deprecated)</li>
 *   <li>{@code X-Hub-Signature-256} - SHA-256 signature (recommended)</li>
 * </ul>
 */
@Component
public class GithubWebhookValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(GithubWebhookValidator.class);
    
    // GitHub webhook signature headers
    private static final String SIGNATURE_HEADER_SHA1 = "X-Hub-Signature";
    private static final String SIGNATURE_HEADER_SHA256 = "X-Hub-Signature-256";
    
    // Case-insensitive header variations
    private static final String[] SIGNATURE_HEADERS_SHA1 = {
        "X-Hub-Signature", "x-hub-signature", "X-HUB-SIGNATURE"
    };
    private static final String[] SIGNATURE_HEADERS_SHA256 = {
        "X-Hub-Signature-256", "x-hub-signature-256", "X-HUB-SIGNATURE-256"
    };
    
    // Signature prefixes
    private static final String SHA1_PREFIX = "sha1=";
    private static final String SHA256_PREFIX = "sha256=";
    
    // Algorithm names
    private static final String HMAC_SHA1 = "HmacSHA1";
    private static final String HMAC_SHA256 = "HmacSHA256";
    
    /**
     * Verify GitHub webhook signature.
     * 
     * <p>Validates webhook signature using HMAC-SHA256 (preferred) or HMAC-SHA1 (fallback).
     * The method will try SHA-256 first, then fall back to SHA-1 if SHA-256 is not available.
     *
     * @param headers HTTP headers from webhook request (case-insensitive lookup)
     * @param rawBody raw request body bytes
     * @param secret webhook secret for signature calculation
     * @return true if signature is valid, false otherwise
     * @throws GithubException if signature validation fails due to configuration issues
     */
    public boolean verifySignature(Map<String, String> headers, byte[] rawBody, String secret) 
            throws GithubException {
        
        if (headers == null || rawBody == null) {
            logger.debug("Signature verification failed: null headers or body");
            return false;
        }
        
        if (!StringUtils.hasText(secret)) {
            throw GithubException.webhookSignatureInvalid("Webhook secret is not configured");
        }
        
        // Try SHA-256 signature first (recommended)
        String sha256Signature = getHeader(headers, SIGNATURE_HEADERS_SHA256);
        if (StringUtils.hasText(sha256Signature)) {
            logger.debug("Validating SHA-256 webhook signature");
            return verifySignature(rawBody, secret, sha256Signature, SHA256_PREFIX, HMAC_SHA256);
        }
        
        // Fallback to SHA-1 signature (deprecated but still supported)
        String sha1Signature = getHeader(headers, SIGNATURE_HEADERS_SHA1);
        if (StringUtils.hasText(sha1Signature)) {
            logger.debug("Validating SHA-1 webhook signature (deprecated)");
            logger.warn("GitHub webhook is using deprecated SHA-1 signature. Please configure GitHub to use SHA-256.");
            return verifySignature(rawBody, secret, sha1Signature, SHA1_PREFIX, HMAC_SHA1);
        }
        
        // No signature headers found
        logger.warn("No GitHub webhook signature headers found. Expected: {} or {}", 
            SIGNATURE_HEADER_SHA256, SIGNATURE_HEADER_SHA1);
        
        throw GithubException.webhookSignatureInvalid("No signature headers found in webhook request");
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
     * Verify signature using specified algorithm.
     *
     * @param body request body bytes
     * @param secret webhook secret
     * @param signature signature from header
     * @param prefix signature prefix (e.g., "sha256=")
     * @param algorithm HMAC algorithm name
     * @return true if signature is valid
     * @throws GithubException if signature validation fails
     */
    private boolean verifySignature(byte[] body, String secret, String signature, 
                                   String prefix, String algorithm) throws GithubException {
        
        if (!signature.startsWith(prefix)) {
            logger.debug("Invalid signature format: expected prefix '{}', got: {}", 
                prefix, signature.substring(0, Math.min(10, signature.length())));
            return false;
        }
        
        // Extract hex signature (remove prefix)
        String expectedSignature = signature.substring(prefix.length());
        
        try {
            // Calculate expected signature
            String calculatedSignature = calculateSignature(body, secret, algorithm);
            
            // Perform constant-time comparison to prevent timing attacks
            boolean isValid = constantTimeEquals(expectedSignature, calculatedSignature);
            
            if (isValid) {
                logger.debug("Webhook signature validation successful using {}", algorithm);
            } else {
                logger.debug("Webhook signature validation failed using {}", algorithm);
            }
            
            return isValid;
            
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw GithubException.webhookSignatureInvalid(
                "Failed to calculate signature using " + algorithm + ": " + e.getMessage());
        }
    }
    
    /**
     * Calculate HMAC signature for given body and secret.
     *
     * @param body request body
     * @param secret webhook secret
     * @param algorithm HMAC algorithm
     * @return hex-encoded signature
     * @throws NoSuchAlgorithmException if algorithm is not supported
     * @throws InvalidKeyException if secret is invalid
     */
    private String calculateSignature(byte[] body, String secret, String algorithm) 
            throws NoSuchAlgorithmException, InvalidKeyException {
        
        SecretKeySpec signingKey = new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8), algorithm);
        
        Mac mac = Mac.getInstance(algorithm);
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
     * Verify signature using SHA-256 (recommended).
     *
     * @param headers HTTP headers
     * @param rawBody request body
     * @param secret webhook secret
     * @return true if SHA-256 signature is valid
     * @throws GithubException if validation fails
     */
    public boolean verifySha256Signature(Map<String, String> headers, byte[] rawBody, String secret) 
            throws GithubException {
        
        String signature = getHeader(headers, SIGNATURE_HEADERS_SHA256);
        if (!StringUtils.hasText(signature)) {
            throw GithubException.webhookSignatureInvalid("SHA-256 signature header not found");
        }
        
        return verifySignature(rawBody, secret, signature, SHA256_PREFIX, HMAC_SHA256);
    }
    
    /**
     * Verify signature using SHA-1 (deprecated, for backward compatibility).
     *
     * @param headers HTTP headers
     * @param rawBody request body
     * @param secret webhook secret
     * @return true if SHA-1 signature is valid
     * @throws GithubException if validation fails
     */
    public boolean verifySha1Signature(Map<String, String> headers, byte[] rawBody, String secret) 
            throws GithubException {
        
        String signature = getHeader(headers, SIGNATURE_HEADERS_SHA1);
        if (!StringUtils.hasText(signature)) {
            throw GithubException.webhookSignatureInvalid("SHA-1 signature header not found");
        }
        
        return verifySignature(rawBody, secret, signature, SHA1_PREFIX, HMAC_SHA1);
    }
    
    /**
     * Get supported signature algorithms.
     *
     * @return array of supported algorithms
     */
    public String[] getSupportedAlgorithms() {
        return new String[]{HMAC_SHA256, HMAC_SHA1};
    }
    
    /**
     * Check if SHA-256 signature is present in headers.
     *
     * @param headers HTTP headers
     * @return true if SHA-256 signature header is present
     */
    public boolean hasSha256Signature(Map<String, String> headers) {
        return StringUtils.hasText(getHeader(headers, SIGNATURE_HEADERS_SHA256));
    }
    
    /**
     * Check if SHA-1 signature is present in headers.
     *
     * @param headers HTTP headers
     * @return true if SHA-1 signature header is present
     */
    public boolean hasSha1Signature(Map<String, String> headers) {
        return StringUtils.hasText(getHeader(headers, SIGNATURE_HEADERS_SHA1));
    }
    
    /**
     * Generate test signature for given body and secret (for testing purposes).
     *
     * @param body request body
     * @param secret webhook secret
     * @return test signature with SHA-256 prefix
     */
    public String generateTestSignature(String body, String secret) {
        try {
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            String signature = calculateSignature(bodyBytes, secret, HMAC_SHA256);
            return SHA256_PREFIX + signature;
        } catch (Exception e) {
            logger.error("Failed to generate test signature", e);
            return null;
        }
    }
}

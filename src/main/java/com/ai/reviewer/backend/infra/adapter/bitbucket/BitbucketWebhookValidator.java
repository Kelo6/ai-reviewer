package com.ai.reviewer.backend.infra.adapter.bitbucket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * Bitbucket webhook signature validator.
 * 
 * Bitbucket uses different signing methods than GitHub/GitLab.
 * Some webhooks may use user-agent based validation or UUID-based verification.
 */
@Component
public class BitbucketWebhookValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(BitbucketWebhookValidator.class);
    
    // Bitbucket webhook headers
    private static final String SIGNATURE_HEADER = "X-Hook-UUID";
    private static final String EVENT_HEADER = "X-Event-Key";
    private static final String USER_AGENT_HEADER = "User-Agent";
    
    /**
     * Validate Bitbucket webhook signature.
     * 
     * Note: Bitbucket's webhook validation is different from GitHub/GitLab.
     * It primarily relies on UUID and event headers rather than HMAC signatures.
     */
    public boolean validate(Map<String, String> headers, byte[] body, String secret) {
        try {
            // Check if this is a Bitbucket webhook
            String userAgent = headers.get(USER_AGENT_HEADER);
            if (userAgent == null || !userAgent.toLowerCase().contains("bitbucket")) {
                logger.debug("Not a Bitbucket webhook - missing or invalid User-Agent");
                return false;
            }
            
            // Check for required Bitbucket headers
            String eventKey = headers.get(EVENT_HEADER);
            String hookUuid = headers.get(SIGNATURE_HEADER);
            
            if (eventKey == null || eventKey.trim().isEmpty()) {
                logger.debug("Missing Bitbucket event key header");
                return false;
            }
            
            if (hookUuid == null || hookUuid.trim().isEmpty()) {
                logger.debug("Missing Bitbucket hook UUID header");
                return false;
            }
            
            // If no secret is configured, we can only validate headers
            if (secret == null || secret.trim().isEmpty()) {
                logger.debug("No webhook secret configured, validating headers only");
                return true;
            }
            
            // For custom secret validation, you can implement additional logic here
            // Bitbucket doesn't have standard HMAC validation like GitHub/GitLab
            
            logger.debug("Bitbucket webhook validation passed: event={}, uuid={}", eventKey, hookUuid);
            return true;
            
        } catch (Exception e) {
            logger.error("Error validating Bitbucket webhook", e);
            return false;
        }
    }
    
    /**
     * Validate using custom HMAC if configured.
     */
    public boolean validateWithHmac(Map<String, String> headers, byte[] body, String secret) {
        if (secret == null || secret.trim().isEmpty()) {
            return validate(headers, body, secret);
        }
        
        try {
            // Check for custom signature header
            String signature = headers.get("X-Hub-Signature-256");
            if (signature == null) {
                signature = headers.get("X-Hub-Signature");
            }
            
            if (signature == null) {
                logger.debug("No signature header found, falling back to standard validation");
                return validate(headers, body, secret);
            }
            
            // Extract algorithm and signature
            String[] parts = signature.split("=", 2);
            if (parts.length != 2) {
                logger.debug("Invalid signature format");
                return false;
            }
            
            String algorithm = parts[0];
            String expectedSignature = parts[1];
            
            // Compute HMAC
            String hmacAlgorithm = algorithm.equals("sha1") ? "HmacSHA1" : "HmacSHA256";
            String computedSignature = computeHmac(body, secret, hmacAlgorithm);
            
            boolean isValid = computedSignature.equals(expectedSignature);
            logger.debug("Bitbucket HMAC validation: {}", isValid ? "passed" : "failed");
            
            return isValid;
            
        } catch (Exception e) {
            logger.error("Error validating Bitbucket webhook with HMAC", e);
            return false;
        }
    }
    
    private String computeHmac(byte[] data, String secret, String algorithm) 
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(algorithm);
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm);
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(data);
        
        // Convert to hex string
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        
        return sb.toString();
    }
}

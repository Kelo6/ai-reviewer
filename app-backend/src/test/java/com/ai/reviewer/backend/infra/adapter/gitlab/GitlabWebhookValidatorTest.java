package com.ai.reviewer.backend.infra.adapter.gitlab;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GitLab webhook signature validation.
 */
class GitlabWebhookValidatorTest {
    
    private GitlabWebhookValidator validator;
    private static final String TEST_SECRET = "my-webhook-secret";
    private static final String TEST_PAYLOAD = "Hello, World!";
    
    @BeforeEach
    void setUp() {
        validator = new GitlabWebhookValidator();
    }
    
    @Test
    void testGenerateTestSignature() {
        String signature = validator.generateTestSignature(TEST_SECRET);
        
        assertNotNull(signature);
        assertEquals(TEST_SECRET, signature); // GitLab uses direct token comparison
    }
    
    @Test
    void testGenerateTestHmacSignature() {
        String signature = validator.generateTestHmacSignature(TEST_PAYLOAD, TEST_SECRET);
        
        assertNotNull(signature);
        assertTrue(signature.length() > 10);
        // Should be a hex string
        assertTrue(signature.matches("[0-9a-f]+"));
    }
    
    @Nested
    class SignatureValidationTest {
        
        @Test
        void testValidTokenSignature() throws GitlabException {
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Gitlab-Token", TEST_SECRET);
            
            byte[] payload = TEST_PAYLOAD.getBytes();
            
            boolean result = validator.verifySignature(headers, payload, TEST_SECRET);
            assertTrue(result);
        }
        
        @Test
        void testInvalidTokenSignature() throws GitlabException {
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Gitlab-Token", "wrong-secret");
            
            byte[] payload = TEST_PAYLOAD.getBytes();
            
            boolean result = validator.verifySignature(headers, payload, TEST_SECRET);
            assertFalse(result);
        }
        
        @Test
        void testCaseInsensitiveHeaders() throws GitlabException {
            Map<String, String> headers = new HashMap<>();
            headers.put("x-gitlab-token", TEST_SECRET);
            
            byte[] payload = TEST_PAYLOAD.getBytes();
            
            boolean result = validator.verifySignature(headers, payload, TEST_SECRET);
            assertTrue(result);
        }
        
        @Test
        void testNoSignatureHeaders() {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            
            byte[] payload = TEST_PAYLOAD.getBytes();
            
            GitlabException exception = assertThrows(GitlabException.class, () ->
                validator.verifySignature(headers, payload, TEST_SECRET)
            );
            
            assertEquals(GitlabException.ErrorCode.WEBHOOK_SIGNATURE_INVALID, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("No signature header found"));
        }
        
        @Test
        void testEmptySecret() {
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Gitlab-Token", "some-token");
            
            byte[] payload = TEST_PAYLOAD.getBytes();
            
            GitlabException exception = assertThrows(GitlabException.class, () ->
                validator.verifySignature(headers, payload, "")
            );
            
            assertEquals(GitlabException.ErrorCode.WEBHOOK_SIGNATURE_INVALID, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("Webhook secret is not configured"));
        }
        
        @Test
        void testNullInputs() throws GitlabException {
            // Null headers should return false
            assertFalse(validator.verifySignature(null, TEST_PAYLOAD.getBytes(), TEST_SECRET));
        }
    }
    
    @Nested
    class HmacSignatureTest {
        
        @Test
        void testValidHmacSignature() throws GitlabException {
            Map<String, String> headers = new HashMap<>();
            String expectedSignature = validator.generateTestHmacSignature(TEST_PAYLOAD, TEST_SECRET);
            headers.put("X-Gitlab-Token", expectedSignature);
            
            byte[] payload = TEST_PAYLOAD.getBytes();
            
            boolean result = validator.verifyHmacSignature(headers, payload, TEST_SECRET);
            assertTrue(result);
        }
        
        @Test
        void testInvalidHmacSignature() throws GitlabException {
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Gitlab-Token", "invalid-hmac-signature");
            
            byte[] payload = TEST_PAYLOAD.getBytes();
            
            boolean result = validator.verifyHmacSignature(headers, payload, TEST_SECRET);
            assertFalse(result);
        }
        
        @Test
        void testHmacSignatureNoHeader() {
            Map<String, String> headers = new HashMap<>();
            byte[] payload = TEST_PAYLOAD.getBytes();
            
            GitlabException exception = assertThrows(GitlabException.class, () ->
                validator.verifyHmacSignature(headers, payload, TEST_SECRET)
            );
            
            assertTrue(exception.getMessage().contains("No signature header found"));
        }
    }
    
    @Nested
    class UtilityMethodsTest {
        
        @Test
        void testGetSupportedMethods() {
            String[] methods = validator.getSupportedMethods();
            
            assertEquals(2, methods.length);
            assertTrue(java.util.Arrays.asList(methods).contains("Token"));
            assertTrue(java.util.Arrays.asList(methods).contains("HMAC-SHA256"));
        }
        
        @Test
        void testHasSignature() {
            Map<String, String> headers = new HashMap<>();
            
            assertFalse(validator.hasSignature(headers));
            
            headers.put("X-Gitlab-Token", "some-token");
            assertTrue(validator.hasSignature(headers));
            
            // Test case insensitive
            headers.clear();
            headers.put("x-gitlab-token", "some-token");
            assertTrue(validator.hasSignature(headers));
        }
        
        @Test
        void testHasRequiredHeaders() {
            Map<String, String> headers = new HashMap<>();
            
            assertFalse(validator.hasRequiredHeaders(headers));
            
            headers.put("X-Gitlab-Event", "Merge Request Hook");
            assertTrue(validator.hasRequiredHeaders(headers));
            
            // Test case insensitive
            headers.clear();
            headers.put("x-gitlab-event", "Push Hook");
            assertTrue(validator.hasRequiredHeaders(headers));
        }
        
        @Test
        void testGetEventType() {
            Map<String, String> headers = new HashMap<>();
            
            assertNull(validator.getEventType(headers));
            
            headers.put("X-Gitlab-Event", "Merge Request Hook");
            assertEquals("Merge Request Hook", validator.getEventType(headers));
            
            // Test case insensitive
            headers.clear();
            headers.put("x-gitlab-event", "Push Hook");
            assertEquals("Push Hook", validator.getEventType(headers));
        }
    }
    
    @Nested
    class SecurityTest {
        
        @Test
        void testConstantTimeComparison() throws GitlabException {
            // This test ensures that signature validation takes roughly the same time
            // regardless of where the signature differs (to prevent timing attacks)
            
            Map<String, String> headers1 = new HashMap<>();
            Map<String, String> headers2 = new HashMap<>();
            
            // Two different invalid tokens of same length
            headers1.put("X-Gitlab-Token", "invalid-token-000000000000");
            headers2.put("X-Gitlab-Token", "invalid-token-111111111111");
            
            byte[] payload = TEST_PAYLOAD.getBytes();
            
            // Both should return false, and timing should be similar
            long start1 = System.nanoTime();
            boolean result1 = validator.verifySignature(headers1, payload, TEST_SECRET);
            long time1 = System.nanoTime() - start1;
            
            long start2 = System.nanoTime();
            boolean result2 = validator.verifySignature(headers2, payload, TEST_SECRET);
            long time2 = System.nanoTime() - start2;
            
            assertFalse(result1);
            assertFalse(result2);
            
            // Times should be relatively close (within 50% of each other)
            // This is a rough test - in practice, constant-time comparison
            // is more important for preventing sophisticated timing attacks
            double ratio = (double) Math.max(time1, time2) / Math.min(time1, time2);
            assertTrue(ratio < 2.0, "Timing difference too large: " + ratio);
        }
        
        @Test
        void testDifferentTokenSizes() throws GitlabException {
            String shortToken = "short";
            String longToken = "this-is-a-much-longer-token-with-more-characters-for-testing";
            
            Map<String, String> headers = new HashMap<>();
            
            headers.put("X-Gitlab-Token", shortToken);
            assertTrue(validator.verifySignature(headers, TEST_PAYLOAD.getBytes(), shortToken));
            
            headers.put("X-Gitlab-Token", longToken);
            assertTrue(validator.verifySignature(headers, TEST_PAYLOAD.getBytes(), longToken));
        }
        
        @Test
        void testSpecialCharactersInToken() throws GitlabException {
            String specialToken = "token!@#$%^&*()_+-=[]{}|;:,.<>?";
            
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Gitlab-Token", specialToken);
            
            assertTrue(validator.verifySignature(headers, TEST_PAYLOAD.getBytes(), specialToken));
        }
    }
}

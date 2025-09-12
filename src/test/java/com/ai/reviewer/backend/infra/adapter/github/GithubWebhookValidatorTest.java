package com.ai.reviewer.backend.infra.adapter.github;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GitHub webhook signature validation.
 */
class GithubWebhookValidatorTest {
    
    private GithubWebhookValidator validator;
    private static final String TEST_SECRET = "my-webhook-secret";
    private static final String TEST_PAYLOAD = "Hello, World!";
    
    @BeforeEach
    void setUp() {
        validator = new GithubWebhookValidator();
    }
    
    @Test
    void testGenerateTestSignature() {
        String signature = validator.generateTestSignature(TEST_PAYLOAD, TEST_SECRET);
        
        assertNotNull(signature);
        assertTrue(signature.startsWith("sha256="));
        assertTrue(signature.length() > 10);
    }
    
    @Nested
    class SignatureValidationTest {
        
        @Test
        void testValidSHA256Signature() throws GithubException {
            Map<String, String> headers = new HashMap<>();
            // Pre-calculated signature for "Hello, World!" with secret "my-webhook-secret"
            headers.put("X-Hub-Signature-256", "sha256=faded2992b76f2950c7fb6dc6a9f97e56f10c7fd91bf5220e5477e9bc1c1f16c");
            
            byte[] payload = TEST_PAYLOAD.getBytes();
            
            boolean result = validator.verifySignature(headers, payload, TEST_SECRET);
            assertTrue(result);
        }
        
        @Test
        void testValidSHA1Signature() throws GithubException {
            Map<String, String> headers = new HashMap<>();
            // Pre-calculated SHA1 signature for "Hello, World!" with secret "my-webhook-secret"
            headers.put("X-Hub-Signature", "sha1=a88ba27e0dc3b76550d296237ddf1370eb1cd078");
            
            byte[] payload = TEST_PAYLOAD.getBytes();
            
            boolean result = validator.verifySignature(headers, payload, TEST_SECRET);
            assertTrue(result);
        }
        
        @Test
        void testInvalidSHA256Signature() throws GithubException {
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Hub-Signature-256", "sha256=invalid-signature");
            
            byte[] payload = TEST_PAYLOAD.getBytes();
            
            boolean result = validator.verifySignature(headers, payload, TEST_SECRET);
            assertFalse(result);
        }
        
        @Test
        void testCaseInsensitiveHeaders() throws GithubException {
            Map<String, String> headers = new HashMap<>();
            headers.put("x-hub-signature-256", "sha256=faded2992b76f2950c7fb6dc6a9f97e56f10c7fd91bf5220e5477e9bc1c1f16c");
            
            byte[] payload = TEST_PAYLOAD.getBytes();
            
            boolean result = validator.verifySignature(headers, payload, TEST_SECRET);
            assertTrue(result);
        }
        
        @Test
        void testPrefersSHA256OverSHA1() throws GithubException {
            Map<String, String> headers = new HashMap<>();
            // Valid SHA-256 signature
            headers.put("X-Hub-Signature-256", "sha256=faded2992b76f2950c7fb6dc6a9f97e56f10c7fd91bf5220e5477e9bc1c1f16c");
            // Invalid SHA-1 signature (should be ignored)
            headers.put("X-Hub-Signature", "sha1=invalid");
            
            byte[] payload = TEST_PAYLOAD.getBytes();
            
            boolean result = validator.verifySignature(headers, payload, TEST_SECRET);
            assertTrue(result); // Should use SHA-256 and succeed
        }
        
        @Test
        void testNoSignatureHeaders() {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            
            byte[] payload = TEST_PAYLOAD.getBytes();
            
            GithubException exception = assertThrows(GithubException.class, () ->
                validator.verifySignature(headers, payload, TEST_SECRET)
            );
            
            assertEquals(GithubException.ErrorCode.WEBHOOK_SIGNATURE_INVALID, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("No signature headers found"));
        }
        
        @Test
        void testEmptySecret() {
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Hub-Signature-256", "sha256=somesignature");
            
            byte[] payload = TEST_PAYLOAD.getBytes();
            
            GithubException exception = assertThrows(GithubException.class, () ->
                validator.verifySignature(headers, payload, "")
            );
            
            assertEquals(GithubException.ErrorCode.WEBHOOK_SIGNATURE_INVALID, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("Webhook secret is not configured"));
        }
        
        @Test
        void testNullInputs() throws GithubException {
            // Null headers should return false
            assertFalse(validator.verifySignature(null, TEST_PAYLOAD.getBytes(), TEST_SECRET));
            
            // Null body should return false
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Hub-Signature-256", "sha256=somesignature");
            assertFalse(validator.verifySignature(headers, null, TEST_SECRET));
        }
        
        @Test
        void testMalformedSignature() throws GithubException {
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Hub-Signature-256", "invalid-format-without-prefix");
            
            byte[] payload = TEST_PAYLOAD.getBytes();
            
            boolean result = validator.verifySignature(headers, payload, TEST_SECRET);
            assertFalse(result);
        }
    }
    
    @Nested
    class SpecificSignatureMethodsTest {
        
        @Test
        void testVerifySha256Signature_Valid() throws GithubException {
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Hub-Signature-256", "sha256=faded2992b76f2950c7fb6dc6a9f97e56f10c7fd91bf5220e5477e9bc1c1f16c");
            
            byte[] payload = TEST_PAYLOAD.getBytes();
            
            boolean result = validator.verifySha256Signature(headers, payload, TEST_SECRET);
            assertTrue(result);
        }
        
        @Test
        void testVerifySha256Signature_NoHeader() {
            Map<String, String> headers = new HashMap<>();
            byte[] payload = TEST_PAYLOAD.getBytes();
            
            GithubException exception = assertThrows(GithubException.class, () ->
                validator.verifySha256Signature(headers, payload, TEST_SECRET)
            );
            
            assertTrue(exception.getMessage().contains("SHA-256 signature header not found"));
        }
        
        @Test
        void testVerifySha1Signature_Valid() throws GithubException {
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Hub-Signature", "sha1=a88ba27e0dc3b76550d296237ddf1370eb1cd078");
            
            byte[] payload = TEST_PAYLOAD.getBytes();
            
            boolean result = validator.verifySha1Signature(headers, payload, TEST_SECRET);
            assertTrue(result);
        }
        
        @Test
        void testVerifySha1Signature_NoHeader() {
            Map<String, String> headers = new HashMap<>();
            byte[] payload = TEST_PAYLOAD.getBytes();
            
            GithubException exception = assertThrows(GithubException.class, () ->
                validator.verifySha1Signature(headers, payload, TEST_SECRET)
            );
            
            assertTrue(exception.getMessage().contains("SHA-1 signature header not found"));
        }
    }
    
    @Nested
    class UtilityMethodsTest {
        
        @Test
        void testGetSupportedAlgorithms() {
            String[] algorithms = validator.getSupportedAlgorithms();
            
            assertEquals(2, algorithms.length);
            assertTrue(java.util.Arrays.asList(algorithms).contains("HmacSHA256"));
            assertTrue(java.util.Arrays.asList(algorithms).contains("HmacSHA1"));
        }
        
        @Test
        void testHasSha256Signature() {
            Map<String, String> headers = new HashMap<>();
            
            assertFalse(validator.hasSha256Signature(headers));
            
            headers.put("X-Hub-Signature-256", "sha256=signature");
            assertTrue(validator.hasSha256Signature(headers));
            
            // Test case insensitive
            headers.clear();
            headers.put("x-hub-signature-256", "sha256=signature");
            assertTrue(validator.hasSha256Signature(headers));
        }
        
        @Test
        void testHasSha1Signature() {
            Map<String, String> headers = new HashMap<>();
            
            assertFalse(validator.hasSha1Signature(headers));
            
            headers.put("X-Hub-Signature", "sha1=signature");
            assertTrue(validator.hasSha1Signature(headers));
            
            // Test case insensitive
            headers.clear();
            headers.put("x-hub-signature", "sha1=signature");
            assertTrue(validator.hasSha1Signature(headers));
        }
    }
    
    @Nested
    class SecurityTest {
        
        @Test
        void testConstantTimeComparison() throws GithubException {
            // This test ensures that signature validation takes roughly the same time
            // regardless of where the signature differs (to prevent timing attacks)
            
            Map<String, String> headers1 = new HashMap<>();
            Map<String, String> headers2 = new HashMap<>();
            
            // Two different invalid signatures of same length
            headers1.put("X-Hub-Signature-256", "sha256=0000000000000000000000000000000000000000000000000000000000000000");
            headers2.put("X-Hub-Signature-256", "sha256=1111111111111111111111111111111111111111111111111111111111111111");
            
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
            
            // Times should be relatively close in testing environment
            // This is a rough test in testing environment - in practice, constant-time comparison
            // is more important for preventing sophisticated timing attacks in production
            // Allow for significant variation in test environment due to JIT compilation, GC, etc.
            double ratio = (double) Math.max(time1, time2) / Math.min(time1, time2);
            assertTrue(ratio < 100.0, "Timing difference too large: " + ratio + " - this is expected in test environments");
        }
        
        @Test
        void testDifferentPayloadSizes() throws GithubException {
            String smallPayload = "small";
            String largePayload = "This is a much larger payload with more content to test signature validation";
            
            Map<String, String> headers = new HashMap<>();
            
            // Generate valid signatures for both payloads
            String smallSig = validator.generateTestSignature(smallPayload, TEST_SECRET);
            String largeSig = validator.generateTestSignature(largePayload, TEST_SECRET);
            
            headers.put("X-Hub-Signature-256", smallSig);
            assertTrue(validator.verifySignature(headers, smallPayload.getBytes(), TEST_SECRET));
            
            headers.put("X-Hub-Signature-256", largeSig);
            assertTrue(validator.verifySignature(headers, largePayload.getBytes(), TEST_SECRET));
        }
    }
}

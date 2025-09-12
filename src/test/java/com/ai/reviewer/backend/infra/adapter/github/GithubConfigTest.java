package com.ai.reviewer.backend.infra.adapter.github;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GitHub configuration.
 */
class GithubConfigTest {
    
    private GithubConfig config;
    
    @BeforeEach
    void setUp() {
        config = new GithubConfig();
    }
    
    @Test
    void testDefaultValues() {
        // Reset config to use only defaults (no env vars)
        resetConfigFields();
        
        assertEquals("https://api.github.com", config.getApiBase());
        assertEquals("https://github.com", config.getWebBase());
        assertNull(config.getToken());
        assertNull(config.getAppId());
        assertNull(config.getPrivateKey());
        assertNull(config.getWebhookSecret());
        
        assertEquals(30000, config.getConnectTimeout());
        assertEquals(60000, config.getReadTimeout());
        assertEquals(3, config.getMaxRetries());
        assertEquals(1000, config.getRetryDelay());
    }
    
    @Test
    void testSettersAndGetters() {
        config.setApiBase("https://github.enterprise.com/api/v3");
        config.setWebBase("https://github.enterprise.com");
        config.setToken("test-token");
        config.setAppId("12345");
        config.setPrivateKey("test-private-key");
        config.setWebhookSecret("test-secret");
        config.setConnectTimeout(15000);
        config.setReadTimeout(30000);
        config.setMaxRetries(5);
        config.setRetryDelay(2000);
        
        assertEquals("https://github.enterprise.com/api/v3", config.getApiBase());
        assertEquals("https://github.enterprise.com", config.getWebBase());
        assertEquals("test-token", config.getToken());
        assertEquals("12345", config.getAppId());
        assertEquals("test-private-key", config.getPrivateKey());
        assertEquals("test-secret", config.getWebhookSecret());
        assertEquals(15000, config.getConnectTimeout());
        assertEquals(30000, config.getReadTimeout());
        assertEquals(5, config.getMaxRetries());
        assertEquals(2000, config.getRetryDelay());
    }
    
    @Nested
    class AuthenticationTest {
        
        @Test
        void testHasToken() {
            assertFalse(config.hasToken());
            
            config.setToken("test-token");
            assertTrue(config.hasToken());
            
            config.setToken("");
            assertFalse(config.hasToken());
            
            config.setToken("   ");
            assertFalse(config.hasToken());
        }
        
        @Test
        void testHasAppAuth() {
            assertFalse(config.hasAppAuth());
            
            config.setAppId("12345");
            assertFalse(config.hasAppAuth()); // Need both app ID and private key
            
            config.setPrivateKey("private-key");
            assertTrue(config.hasAppAuth());
            
            config.setAppId("");
            assertFalse(config.hasAppAuth());
        }
        
        @Test
        void testHasAuthentication() {
            assertFalse(config.hasAuthentication());
            
            config.setToken("token");
            assertTrue(config.hasAuthentication());
            
            config.setToken(null);
            config.setAppId("123");
            config.setPrivateKey("key");
            assertTrue(config.hasAuthentication());
        }
        
        @Test
        void testGetAuthenticationType() {
            assertEquals("None", config.getAuthenticationType());
            
            config.setToken("token");
            assertEquals("Personal Access Token", config.getAuthenticationType());
            
            config.setToken(null);
            config.setAppId("123");
            config.setPrivateKey("key");
            assertEquals("GitHub App", config.getAuthenticationType());
            
            // Token takes precedence
            config.setToken("token");
            assertEquals("Personal Access Token", config.getAuthenticationType());
        }
    }
    
    @Nested
    class ConfigurationTest {
        
        @Test
        void testHasWebhookSecret() {
            assertFalse(config.hasWebhookSecret());
            
            config.setWebhookSecret("secret");
            assertTrue(config.hasWebhookSecret());
            
            config.setWebhookSecret("");
            assertFalse(config.hasWebhookSecret());
        }
        
        @Test
        void testIsGitHubEnterprise() {
            assertFalse(config.isGitHubEnterprise());
            
            config.setApiBase("https://github.enterprise.com/api/v3");
            assertTrue(config.isGitHubEnterprise());
            
            config.setApiBase("https://api.github.com");
            assertFalse(config.isGitHubEnterprise());
        }
    }
    
    @Nested
    class ValidationTest {
        
        @Test
        void testValidateWithToken() throws GithubException {
            config.setToken("valid-token");
            
            assertDoesNotThrow(() -> config.validate());
        }
        
        @Test
        void testValidateWithAppAuth() throws GithubException {
            config.setAppId("12345");
            config.setPrivateKey("-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASC\n-----END PRIVATE KEY-----");
            
            assertDoesNotThrow(() -> config.validate());
        }
        
        @Test
        void testValidateNoAuthentication() {
            GithubException exception = assertThrows(GithubException.class, () -> config.validate());
            assertEquals(GithubException.ErrorCode.CREDENTIALS_MISSING, exception.getErrorCode());
        }
        
        @Test
        void testValidateInvalidAppId() {
            config.setAppId("not-a-number");
            config.setPrivateKey("-----BEGIN PRIVATE KEY-----\nkey\n-----END PRIVATE KEY-----");
            
            GithubException exception = assertThrows(GithubException.class, () -> config.validate());
            assertEquals(GithubException.ErrorCode.APP_CONFIG_INVALID, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("App ID must be a valid number"));
        }
        
        @Test
        void testValidateInvalidPrivateKey() {
            config.setAppId("12345");
            config.setPrivateKey("invalid-key-format");
            
            GithubException exception = assertThrows(GithubException.class, () -> config.validate());
            assertEquals(GithubException.ErrorCode.APP_CONFIG_INVALID, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("Private key must be in PEM format"));
        }
        
        @Test
        void testValidateInvalidApiBase() {
            config.setToken("token");
            config.setApiBase("invalid-url");
            
            GithubException exception = assertThrows(GithubException.class, () -> config.validate());
            assertEquals(GithubException.ErrorCode.APP_CONFIG_INVALID, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("Invalid API base URL"));
        }
    }
    
    @Nested
    class UtilityMethodsTest {
        
        @Test
        void testGetConfigSummary() {
            config.setToken("token");
            config.setWebhookSecret("secret");
            
            String summary = config.getConfigSummary();
            
            assertTrue(summary.contains("GitHub Configuration:"));
            assertTrue(summary.contains("API Base: https://api.github.com"));
            assertTrue(summary.contains("Authentication: Personal Access Token"));
            assertTrue(summary.contains("Webhook Secret: Configured"));
            assertTrue(summary.contains("Enterprise: No"));
        }
        
        @Test
        void testToString() {
            config.setToken("token");
            
            String toString = config.toString();
            
            assertTrue(toString.contains("GithubConfig{"));
            assertTrue(toString.contains("apiBase='https://api.github.com'"));
            assertTrue(toString.contains("authType='Personal Access Token'"));
            assertFalse(toString.contains("token")); // Should not expose sensitive data
        }
    }
    
    /**
     * Helper method to reset config fields to null/defaults for testing.
     */
    private void resetConfigFields() {
        ReflectionTestUtils.setField(config, "apiBase", "https://api.github.com");
        ReflectionTestUtils.setField(config, "webBase", "https://github.com");
        ReflectionTestUtils.setField(config, "token", null);
        ReflectionTestUtils.setField(config, "appId", null);
        ReflectionTestUtils.setField(config, "privateKey", null);
        ReflectionTestUtils.setField(config, "webhookSecret", null);
    }
}

package com.ai.reviewer.backend.infra.adapter.gitlab;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GitLab configuration.
 */
class GitlabConfigTest {
    
    private GitlabConfig config;
    
    @BeforeEach
    void setUp() {
        config = new GitlabConfig();
    }
    
    @Test
    void testDefaultValues() {
        // Reset config to use only defaults (no env vars)
        resetConfigFields();
        
        assertEquals("https://gitlab.com/api/v4", config.getApiBase());
        assertEquals("https://gitlab.com", config.getWebBase());
        assertNull(config.getToken());
        assertNull(config.getWebhookSecret());
        assertFalse(config.isIgnoreCertErrors());
        
        assertEquals(30000, config.getConnectTimeout());
        assertEquals(60000, config.getReadTimeout());
        assertEquals(3, config.getMaxRetries());
        assertEquals(1000, config.getRetryDelay());
    }
    
    @Test
    void testSettersAndGetters() {
        config.setApiBase("https://gitlab.example.com/api/v4");
        config.setWebBase("https://gitlab.example.com");
        config.setToken("test-token");
        config.setWebhookSecret("test-secret");
        config.setIgnoreCertErrors(true);
        config.setConnectTimeout(15000);
        config.setReadTimeout(30000);
        config.setMaxRetries(5);
        config.setRetryDelay(2000);
        
        assertEquals("https://gitlab.example.com/api/v4", config.getApiBase());
        assertEquals("https://gitlab.example.com", config.getWebBase());
        assertEquals("test-token", config.getToken());
        assertEquals("test-secret", config.getWebhookSecret());
        assertTrue(config.isIgnoreCertErrors());
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
        void testHasAuthentication() {
            assertFalse(config.hasAuthentication());
            
            config.setToken("token");
            assertTrue(config.hasAuthentication());
        }
        
        @Test
        void testGetAuthenticationType() {
            assertEquals("None", config.getAuthenticationType());
            
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
        void testIsSelfHosted() {
            assertFalse(config.isSelfHosted());
            
            config.setApiBase("https://gitlab.example.com/api/v4");
            assertTrue(config.isSelfHosted());
            
            config.setApiBase("https://gitlab.com/api/v4");
            assertFalse(config.isSelfHosted());
        }
        
        @Test
        void testGetServerHost() {
            assertEquals("gitlab.com", config.getServerHost());
            
            config.setApiBase("https://gitlab.example.com/api/v4");
            assertEquals("gitlab.example.com", config.getServerHost());
        }
        
        @Test
        void testGetServerHostInvalidUrl() {
            config.setApiBase("invalid-url");
            assertEquals("unknown", config.getServerHost());
        }
    }
    
    @Nested
    class ValidationTest {
        
        @Test
        void testValidateWithToken() throws GitlabException {
            config.setToken("valid-token");
            
            assertDoesNotThrow(() -> config.validate());
        }
        
        @Test
        void testValidateNoAuthentication() {
            GitlabException exception = assertThrows(GitlabException.class, () -> config.validate());
            assertEquals(GitlabException.ErrorCode.CREDENTIALS_MISSING, exception.getErrorCode());
        }
        
        @Test
        void testValidateInvalidApiBase() {
            config.setToken("token");
            config.setApiBase("invalid-url");
            
            GitlabException exception = assertThrows(GitlabException.class, () -> config.validate());
            assertEquals(GitlabException.ErrorCode.API_CONFIG_ERROR, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("Invalid API base URL"));
        }
        
        @Test
        void testValidateInvalidWebBase() {
            config.setToken("token");
            config.setWebBase("invalid-url");
            
            GitlabException exception = assertThrows(GitlabException.class, () -> config.validate());
            assertEquals(GitlabException.ErrorCode.API_CONFIG_ERROR, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("Invalid web base URL"));
        }
    }
    
    @Nested
    class ProjectPathExtractionTest {
        
        @Test
        void testExtractProjectPathValidUrls() {
            assertEquals("owner/repo", config.extractProjectPath("https://gitlab.com/owner/repo"));
            assertEquals("owner/repo", config.extractProjectPath("https://gitlab.com/owner/repo.git"));
            assertEquals("owner/repo", config.extractProjectPath("https://gitlab.example.com/owner/repo"));
            assertEquals("group/subgroup/repo", config.extractProjectPath("https://gitlab.com/group/subgroup/repo"));
        }
        
        @Test
        void testExtractProjectPathInvalidUrls() {
            assertNull(config.extractProjectPath(""));
            assertNull(config.extractProjectPath(null));
            assertNull(config.extractProjectPath("not-a-url"));
            assertNull(config.extractProjectPath("https://gitlab.com/"));
            assertNull(config.extractProjectPath("https://gitlab.com/single-part"));
        }
        
        @Test
        void testExtractProjectPathEdgeCases() {
            assertEquals("owner/repo-name", config.extractProjectPath("https://gitlab.com/owner/repo-name.git"));
            assertEquals("owner/repo_name", config.extractProjectPath("https://gitlab.com/owner/repo_name"));
        }
    }
    
    @Nested
    class UtilityMethodsTest {
        
        @Test
        void testGetConfigSummary() {
            config.setToken("token");
            config.setWebhookSecret("secret");
            config.setIgnoreCertErrors(true);
            
            String summary = config.getConfigSummary();
            
            assertTrue(summary.contains("GitLab Configuration:"));
            assertTrue(summary.contains("API Base: https://gitlab.com/api/v4"));
            assertTrue(summary.contains("Authentication: Personal Access Token"));
            assertTrue(summary.contains("Webhook Secret: Configured"));
            assertTrue(summary.contains("Self-hosted: No"));
            assertTrue(summary.contains("SSL Verification: DISABLED"));
        }
        
        @Test
        void testGetConfigSummaryDefault() {
            String summary = config.getConfigSummary();
            
            assertTrue(summary.contains("Authentication: None"));
            assertTrue(summary.contains("Webhook Secret: Not configured"));
            assertTrue(summary.contains("SSL Verification: ENABLED"));
        }
        
        @Test
        void testToString() {
            config.setToken("token");
            config.setIgnoreCertErrors(true);
            
            String toString = config.toString();
            
            assertTrue(toString.contains("GitlabConfig{"));
            assertTrue(toString.contains("apiBase='https://gitlab.com/api/v4'"));
            assertTrue(toString.contains("authType='Personal Access Token'"));
            assertTrue(toString.contains("ignoreCertErrors=true"));
            assertFalse(toString.contains("token")); // Should not expose sensitive data
        }
    }
    
    @Nested
    class SslConfigurationTest {
        
        @Test
        void testIgnoreCertErrorsDefault() {
            assertFalse(config.isIgnoreCertErrors());
        }
        
        @Test
        void testIgnoreCertErrorsEnabled() {
            config.setIgnoreCertErrors(true);
            assertTrue(config.isIgnoreCertErrors());
        }
        
        @Test
        void testSelfHostedWithSslErrors() {
            config.setApiBase("https://gitlab.internal.com/api/v4");
            config.setIgnoreCertErrors(true);
            
            assertTrue(config.isSelfHosted());
            assertTrue(config.isIgnoreCertErrors());
        }
    }
    
    /**
     * Helper method to reset config fields to null/defaults for testing.
     */
    private void resetConfigFields() {
        ReflectionTestUtils.setField(config, "apiBase", "https://gitlab.com/api/v4");
        ReflectionTestUtils.setField(config, "webBase", "https://gitlab.com");
        ReflectionTestUtils.setField(config, "token", null);
        ReflectionTestUtils.setField(config, "webhookSecret", null);
        ReflectionTestUtils.setField(config, "ignoreCertErrors", false);
    }
}

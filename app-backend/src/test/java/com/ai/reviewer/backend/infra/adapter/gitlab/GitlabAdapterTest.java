package com.ai.reviewer.backend.infra.adapter.gitlab;

import com.ai.reviewer.backend.domain.adapter.scm.*;
import com.ai.reviewer.shared.model.DiffHunk;
import com.ai.reviewer.shared.model.PullRef;
import com.ai.reviewer.shared.model.RepoRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GitLab adapter using mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class GitlabAdapterTest {

    @Mock
    private GitlabApiClient mockApiClient;
    
    @Mock
    private GitlabWebhookValidator mockWebhookValidator;
    
    private GitlabAdapter gitlabAdapter;
    private GitlabConfig gitlabConfig;
    private ObjectMapper objectMapper;
    
    // Test data
    private static final String TEST_REPO_OWNER = "test-org";
    private static final String TEST_REPO_NAME = "test-repo";
    private static final String TEST_MR_IID = "1";
    private static final String TEST_WEBHOOK_SECRET = "test-secret";
    
    @BeforeEach
    void setUp() {
        // Create real config for basic functionality
        gitlabConfig = new GitlabConfig();
        ReflectionTestUtils.setField(gitlabConfig, "apiBase", "https://gitlab.example.com/api/v4");
        ReflectionTestUtils.setField(gitlabConfig, "token", "test-token");
        ReflectionTestUtils.setField(gitlabConfig, "webhookSecret", TEST_WEBHOOK_SECRET);
        ReflectionTestUtils.setField(gitlabConfig, "ignoreCertErrors", false);
        
        objectMapper = new ObjectMapper();
        
        // Create adapter with mocked dependencies
        gitlabAdapter = new GitlabAdapter(gitlabConfig, mockApiClient, mockWebhookValidator, objectMapper);
    }
    
    @Test
    void testGetProvider() {
        assertEquals("gitlab", gitlabAdapter.getProvider());
    }
    
    @Test
    void testSupports() {
        RepoRef gitlabRepo = new RepoRef("gitlab", "owner", "repo", "https://gitlab.com/owner/repo");
        RepoRef githubRepo = new RepoRef("github", "owner", "repo", "https://github.com/owner/repo");
        
        assertTrue(gitlabAdapter.supports(gitlabRepo));
        assertFalse(gitlabAdapter.supports(githubRepo));
    }
    
    @Test
    void testVerifyWebhookSignature_Success() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Gitlab-Token", "test-secret");
        byte[] payload = "test-payload".getBytes();
        
        when(mockWebhookValidator.verifySignature(headers, payload, TEST_WEBHOOK_SECRET))
            .thenReturn(true);
        
        boolean result = gitlabAdapter.verifyWebhookSignature(headers, payload);
        
        assertTrue(result);
        verify(mockWebhookValidator).verifySignature(headers, payload, TEST_WEBHOOK_SECRET);
    }
    
    @Test
    void testVerifyWebhookSignature_NoSecret() {
        // Config without webhook secret
        ReflectionTestUtils.setField(gitlabConfig, "webhookSecret", null);
        
        Map<String, String> headers = new HashMap<>();
        byte[] payload = "test-payload".getBytes();
        
        boolean result = gitlabAdapter.verifyWebhookSignature(headers, payload);
        
        // Should return true when no secret is configured (warning logged)
        assertTrue(result);
        verifyNoInteractions(mockWebhookValidator);
    }
    
    @Test
    void testParseEvent_MergeRequest() throws Exception {
        String payload = loadFixture("merge_request_event.json");
        
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Gitlab-Event", "Merge Request Hook");
        
        when(mockWebhookValidator.getEventType(headers)).thenReturn("Merge Request Hook");
        
        ParsedEvent event = gitlabAdapter.parseEvent(payload.getBytes(), headers);
        
        assertNotNull(event);
        assertEquals("merge_request_hook.open", event.type());
        assertNotNull(event.repo());
        assertEquals("gitlab", event.repo().provider());
        assertEquals("test-org", event.repo().owner());
        assertEquals("test-repo", event.repo().name());
        assertNotNull(event.pull());
        assertEquals("1", event.pull().number());
        assertEquals("feature-branch", event.pull().sourceBranch());
        assertEquals("main", event.pull().targetBranch());
        assertEquals("abc123def456", event.pull().sha());
        assertFalse(event.pull().draft());
    }
    
    @Test
    void testParseEvent_Push() throws Exception {
        String payload = loadFixture("push_event.json");
        
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Gitlab-Event", "Push Hook");
        
        when(mockWebhookValidator.getEventType(headers)).thenReturn("Push Hook");
        
        ParsedEvent event = gitlabAdapter.parseEvent(payload.getBytes(), headers);
        
        assertNotNull(event);
        assertEquals("push", event.type());
        assertNotNull(event.repo());
        assertEquals("gitlab", event.repo().provider());
        assertNull(event.pull());
    }
    
    @Test
    void testParseEvent_InvalidJson() {
        String payload = "invalid-json";
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Gitlab-Event", "Merge Request Hook");
        
        when(mockWebhookValidator.getEventType(headers)).thenReturn("Merge Request Hook");
        
        ScmAdapterException exception = assertThrows(ScmAdapterException.class, () ->
            gitlabAdapter.parseEvent(payload.getBytes(), headers)
        );
        
        assertTrue(exception.getMessage().contains("Failed to parse webhook payload"));
    }
    
    private RepoRef createTestRepo() {
        return new RepoRef("gitlab", TEST_REPO_OWNER, TEST_REPO_NAME, 
            "https://gitlab.example.com/" + TEST_REPO_OWNER + "/" + TEST_REPO_NAME);
    }
    
    private PullRef createTestPull() {
        return new PullRef("456", TEST_MR_IID, "Test MR", "feature", "main", "abc123def456", false);
    }
    
    /**
     * Load fixture file from test resources.
     */
    private String loadFixture(String filename) throws Exception {
        Path fixturePath = Paths.get("app-backend/src/test/resources/fixtures/gitlab/" + filename);
        if (!Files.exists(fixturePath)) {
            // Try alternative path for different execution contexts
            fixturePath = Paths.get("src/test/resources/fixtures/gitlab/" + filename);
        }
        return Files.readString(fixturePath);
    }
    
    @Nested
    class WebhookValidationTest {
        
        @Test
        void testValidSignature() {
            Map<String, String> headers = Map.of("X-Gitlab-Token", "valid-token");
            byte[] payload = "test-payload".getBytes();
            
            when(mockWebhookValidator.verifySignature(headers, payload, TEST_WEBHOOK_SECRET))
                .thenReturn(true);
            
            assertTrue(gitlabAdapter.verifyWebhookSignature(headers, payload));
        }
        
        @Test
        void testInvalidSignature() {
            Map<String, String> headers = Map.of("X-Gitlab-Token", "invalid-token");
            byte[] payload = "test-payload".getBytes();
            
            when(mockWebhookValidator.verifySignature(headers, payload, TEST_WEBHOOK_SECRET))
                .thenReturn(false);
            
            assertFalse(gitlabAdapter.verifyWebhookSignature(headers, payload));
        }
        
        @Test
        void testSignatureValidationException() {
            Map<String, String> headers = Map.of("X-Gitlab-Token", "test-token");
            byte[] payload = "test-payload".getBytes();
            
            when(mockWebhookValidator.verifySignature(headers, payload, TEST_WEBHOOK_SECRET))
                .thenThrow(GitlabException.webhookSignatureInvalid("test error"));
            
            assertFalse(gitlabAdapter.verifyWebhookSignature(headers, payload));
        }
    }
    
    @Nested
    class EventParsingTest {
        
        @Test
        void testMergeRequestOpenedEvent() throws Exception {
            ParsedEvent event = createParsedEvent("open");
            assertTrue(event.shouldTriggerReview());
        }
        
        @Test
        void testMergeRequestUpdatedEvent() throws Exception {
            ParsedEvent event = createParsedEvent("update"); 
            assertTrue(event.shouldTriggerReview());
        }
        
        @Test
        void testMergeRequestClosedEvent() throws Exception {
            ParsedEvent event = createParsedEvent("close");
            assertFalse(event.shouldTriggerReview());
        }
        
        @Test
        void testMergeRequestMergedEvent() throws Exception {
            ParsedEvent event = createParsedEvent("merge");
            assertFalse(event.shouldTriggerReview());
        }
        
        private ParsedEvent createParsedEvent(String action) throws Exception {
            String payload = loadFixture("merge_request_event.json")
                .replace("\"action\": \"open\"", "\"action\": \"" + action + "\"");
            
            Map<String, String> headers = Map.of("X-Gitlab-Event", "Merge Request Hook");
            when(mockWebhookValidator.getEventType(headers)).thenReturn("Merge Request Hook");
            
            return gitlabAdapter.parseEvent(payload.getBytes(), headers);
        }
    }
    
    @Nested
    class CheckSummaryTest {
        
        @Test
        void testSuccessCheckSummary() {
            CheckSummary summary = CheckSummary.success("AI Review", "https://example.com/details");
            assertTrue(summary.isSuccess());
            assertFalse(summary.isFailure());
            assertFalse(summary.isPending());
            assertTrue(summary.hasDetailsUrl());
        }
        
        @Test
        void testFailureCheckSummary() {
            CheckSummary summary = CheckSummary.failure("AI Review", "https://example.com/details");
            assertFalse(summary.isSuccess());
            assertTrue(summary.isFailure());
            assertFalse(summary.isPending());
        }
        
        @Test
        void testPendingCheckSummary() {
            CheckSummary summary = CheckSummary.pending("AI Review");
            assertFalse(summary.isSuccess());
            assertFalse(summary.isFailure());
            assertTrue(summary.isPending());
            assertFalse(summary.hasDetailsUrl());
        }
    }
    
    @Nested
    class InlineCommentTest {
        
        @Test
        void testSingleLineComment() {
            InlineComment comment = InlineComment.onNewLine("test.java", 42, "Test comment");
            
            assertFalse(comment.isMultiLine());
            assertTrue(comment.isOnNewSide());
            assertFalse(comment.isOnOldSide());
            assertEquals(42, comment.getEffectiveStartLine());
            assertEquals(1, comment.getLineCount());
            assertTrue(comment.hasValidContent());
        }
        
        @Test
        void testMultiLineComment() {
            InlineComment comment = InlineComment.onNewLines("test.java", 10, 15, "Multi-line comment");
            
            assertTrue(comment.isMultiLine());
            assertEquals(10, comment.getEffectiveStartLine());
            assertEquals(6, comment.getLineCount());
        }
        
        @Test
        void testOldSideComment() {
            InlineComment comment = InlineComment.onOldLine("test.java", 5, "Old side comment");
            
            assertTrue(comment.isOnOldSide());
            assertFalse(comment.isOnNewSide());
        }
        
        @Test
        void testInvalidComment() {
            InlineComment comment = new InlineComment("test.java", 1, "", null, null);
            
            assertFalse(comment.hasValidContent());
            assertEquals("", comment.getSanitizedBody());
        }
    }
    
    @Nested
    class RangeTest {
        
        @Test
        void testSingleLineRange() {
            Range range = Range.singleLine(42);
            
            assertTrue(range.isSingleLine());
            assertEquals(1, range.getLineCount());
            assertTrue(range.contains(42));
            assertFalse(range.contains(41));
            assertFalse(range.contains(43));
        }
        
        @Test
        void testMultiLineRange() {
            Range range = Range.of(10, 20);
            
            assertFalse(range.isSingleLine());
            assertEquals(11, range.getLineCount());
            assertTrue(range.contains(10));
            assertTrue(range.contains(15));
            assertTrue(range.contains(20));
            assertFalse(range.contains(9));
            assertFalse(range.contains(21));
        }
        
        @Test
        void testRangeWithContext() {
            Range range = Range.withContext(15, 3);
            
            assertEquals(12, range.startLine());
            assertEquals(18, range.endLine());
            assertEquals(7, range.getLineCount());
        }
        
        @Test
        void testInvalidRanges() {
            assertThrows(IllegalArgumentException.class, () -> Range.of(0, 10));
            assertThrows(IllegalArgumentException.class, () -> Range.of(10, 5));
            assertThrows(IllegalArgumentException.class, () -> Range.fromCount(5, 0));
        }
    }
}

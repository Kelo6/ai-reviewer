package com.ai.reviewer.backend.infra.adapter.github;

import com.ai.reviewer.backend.domain.adapter.scm.*;
import com.ai.reviewer.shared.model.DiffHunk;
import com.ai.reviewer.shared.model.PullRef;
import com.ai.reviewer.shared.model.RepoRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GitHub adapter using WireMock to simulate GitHub API responses.
 */
@ExtendWith(MockitoExtension.class)
class GithubAdapterTest {

    private static WireMockServer wireMockServer;
    
    @Mock
    private GithubApiClient mockApiClient;
    
    @Mock
    private GithubWebhookValidator mockWebhookValidator;
    
    private GithubAdapter githubAdapter;
    private GithubConfig githubConfig;
    private ObjectMapper objectMapper;
    
    // Test data
    private static final String TEST_REPO_OWNER = "test-org";
    private static final String TEST_REPO_NAME = "test-repo";
    private static final String TEST_PR_NUMBER = "123";
    private static final String TEST_WEBHOOK_SECRET = "test-secret";
    
    @BeforeAll
    static void setUpWireMock() {
        wireMockServer = new WireMockServer(options().port(8089));
        wireMockServer.start();
        WireMock.configureFor("localhost", 8089);
    }
    
    @AfterAll
    static void tearDownWireMock() {
        wireMockServer.stop();
    }
    
    @BeforeEach
    void setUp() {
        // Reset WireMock
        wireMockServer.resetAll();
        
        // Create real config pointing to WireMock server
        githubConfig = new GithubConfig();
        ReflectionTestUtils.setField(githubConfig, "apiBase", "http://localhost:8089");
        ReflectionTestUtils.setField(githubConfig, "token", "test-token");
        ReflectionTestUtils.setField(githubConfig, "webhookSecret", TEST_WEBHOOK_SECRET);
        
        objectMapper = new ObjectMapper();
        
        // Create adapter with mocked dependencies
        githubAdapter = new GithubAdapter(githubConfig, mockApiClient, mockWebhookValidator, objectMapper);
    }
    
    @Test
    void testGetProvider() {
        assertEquals("github", githubAdapter.getProvider());
    }
    
    @Test
    void testSupports() {
        RepoRef githubRepo = new RepoRef("github", "owner", "repo", "https://github.com/owner/repo");
        RepoRef gitlabRepo = new RepoRef("gitlab", "owner", "repo", "https://gitlab.com/owner/repo");
        
        assertTrue(githubAdapter.supports(githubRepo));
        assertFalse(githubAdapter.supports(gitlabRepo));
    }
    
    @Test
    void testVerifyWebhookSignature_Success() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Hub-Signature-256", "sha256=test-signature");
        byte[] payload = "test-payload".getBytes();
        
        when(mockWebhookValidator.verifySignature(headers, payload, TEST_WEBHOOK_SECRET))
            .thenReturn(true);
        
        boolean result = githubAdapter.verifyWebhookSignature(headers, payload);
        
        assertTrue(result);
        verify(mockWebhookValidator).verifySignature(headers, payload, TEST_WEBHOOK_SECRET);
    }
    
    @Test
    void testVerifyWebhookSignature_NoSecret() {
        // Config without webhook secret
        ReflectionTestUtils.setField(githubConfig, "webhookSecret", null);
        
        Map<String, String> headers = new HashMap<>();
        byte[] payload = "test-payload".getBytes();
        
        boolean result = githubAdapter.verifyWebhookSignature(headers, payload);
        
        // Should return true when no secret is configured (warning logged)
        assertTrue(result);
        verifyNoInteractions(mockWebhookValidator);
    }
    
    @Test
    void testParseEvent_PullRequest() {
        String payload = """
            {
                "action": "opened",
                "pull_request": {
                    "id": 1,
                    "number": 123,
                    "head": {
                        "ref": "feature-branch",
                        "sha": "abc123"
                    },
                    "base": {
                        "ref": "main"
                    },
                    "draft": false
                },
                "repository": {
                    "name": "test-repo",
                    "owner": {
                        "login": "test-org"
                    },
                    "html_url": "https://github.com/test-org/test-repo"
                }
            }
            """;
        
        Map<String, String> headers = new HashMap<>();
        headers.put("X-GitHub-Event", "pull_request");
        
        ParsedEvent event = githubAdapter.parseEvent(payload.getBytes(), headers);
        
        assertNotNull(event);
        assertEquals("pull_request.opened", event.type());
        assertNotNull(event.repo());
        assertEquals("github", event.repo().provider());
        assertEquals("test-org", event.repo().owner());
        assertEquals("test-repo", event.repo().name());
        assertNotNull(event.pull());
        assertEquals("123", event.pull().number());
        assertEquals("feature-branch", event.pull().sourceBranch());
        assertEquals("main", event.pull().targetBranch());
        assertEquals("abc123", event.pull().sha());
        assertFalse(event.pull().draft());
    }
    
    @Test
    void testParseEvent_Push() {
        String payload = """
            {
                "ref": "refs/heads/main",
                "repository": {
                    "name": "test-repo",
                    "owner": {
                        "login": "test-org"
                    },
                    "html_url": "https://github.com/test-org/test-repo"
                }
            }
            """;
        
        Map<String, String> headers = new HashMap<>();
        headers.put("X-GitHub-Event", "push");
        
        ParsedEvent event = githubAdapter.parseEvent(payload.getBytes(), headers);
        
        assertNotNull(event);
        assertEquals("push", event.type());
        assertNotNull(event.repo());
        assertEquals("github", event.repo().provider());
        assertNull(event.pull());
    }
    
    @Test
    void testParseEvent_InvalidJson() {
        String payload = "invalid-json";
        Map<String, String> headers = new HashMap<>();
        headers.put("X-GitHub-Event", "pull_request");
        
        ScmAdapterException exception = assertThrows(ScmAdapterException.class, () ->
            githubAdapter.parseEvent(payload.getBytes(), headers)
        );
        
        assertTrue(exception.getMessage().contains("Failed to parse webhook payload"));
    }
    
    private RepoRef createTestRepo() {
        return new RepoRef("github", TEST_REPO_OWNER, TEST_REPO_NAME, 
            "https://github.com/" + TEST_REPO_OWNER + "/" + TEST_REPO_NAME);
    }
    
    private PullRef createTestPull() {
        return new PullRef("1", TEST_PR_NUMBER, "Test PR", "feature", "main", "abc123", false);
    }
    
    @Nested
    class WebhookValidationTest {
        
        @Test
        void testValidSHA256Signature() {
            Map<String, String> headers = Map.of("X-Hub-Signature-256", "sha256=valid-signature");
            byte[] payload = "test-payload".getBytes();
            
            when(mockWebhookValidator.verifySignature(headers, payload, TEST_WEBHOOK_SECRET))
                .thenReturn(true);
            
            assertTrue(githubAdapter.verifyWebhookSignature(headers, payload));
        }
        
        @Test
        void testInvalidSignature() {
            Map<String, String> headers = Map.of("X-Hub-Signature-256", "sha256=invalid-signature");
            byte[] payload = "test-payload".getBytes();
            
            when(mockWebhookValidator.verifySignature(headers, payload, TEST_WEBHOOK_SECRET))
                .thenReturn(false);
            
            assertFalse(githubAdapter.verifyWebhookSignature(headers, payload));
        }
        
        @Test
        void testSignatureValidationException() {
            Map<String, String> headers = Map.of("X-Hub-Signature-256", "sha256=test-signature");
            byte[] payload = "test-payload".getBytes();
            
            when(mockWebhookValidator.verifySignature(headers, payload, TEST_WEBHOOK_SECRET))
                .thenThrow(GithubException.webhookSignatureInvalid("test error"));
            
            assertFalse(githubAdapter.verifyWebhookSignature(headers, payload));
        }
    }
    
    @Nested
    class EventParsingTest {
        
        @Test
        void testPullRequestOpenedEvent() {
            assertTrue(createParsedEvent("opened").shouldTriggerReview());
        }
        
        @Test
        void testPullRequestSynchronizeEvent() {
            assertTrue(createParsedEvent("synchronize").shouldTriggerReview());
        }
        
        @Test
        void testPullRequestClosedEvent() {
            assertFalse(createParsedEvent("closed").shouldTriggerReview());
        }
        
        private ParsedEvent createParsedEvent(String action) {
            String payload = String.format("""
                {
                    "action": "%s",
                    "pull_request": {
                        "id": 1,
                        "number": 123,
                        "head": {"ref": "feature", "sha": "abc123"},
                        "base": {"ref": "main"},
                        "draft": false
                    },
                    "repository": {
                        "name": "test-repo",
                        "owner": {"login": "test-org"},
                        "html_url": "https://github.com/test-org/test-repo"
                    }
                }
                """, action);
            
            Map<String, String> headers = Map.of("X-GitHub-Event", "pull_request");
            return githubAdapter.parseEvent(payload.getBytes(), headers);
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
        void testRangeFromCount() {
            Range range = Range.fromCount(5, 10);
            
            assertEquals(5, range.startLine());
            assertEquals(14, range.endLine());
            assertEquals(10, range.getLineCount());
        }
        
        @Test
        void testInvalidRanges() {
            assertThrows(IllegalArgumentException.class, () -> Range.of(0, 10));
            assertThrows(IllegalArgumentException.class, () -> Range.of(10, 5));
            assertThrows(IllegalArgumentException.class, () -> Range.fromCount(5, 0));
        }
        
        @Test
        void testRangeOverlap() {
            Range range1 = Range.of(10, 20);
            Range range2 = Range.of(15, 25);
            Range range3 = Range.of(30, 40);
            
            assertTrue(range1.overlaps(range2));
            assertFalse(range1.overlaps(range3));
            
            Range intersection = range1.intersection(range2);
            assertNotNull(intersection);
            assertEquals(15, intersection.startLine());
            assertEquals(20, intersection.endLine());
            
            assertNull(range1.intersection(range3));
        }
        
        @Test
        void testRangeExpansion() {
            Range range = Range.of(10, 15);
            Range expanded = range.expand(2);
            
            assertEquals(8, expanded.startLine());
            assertEquals(17, expanded.endLine());
            
            // Test boundary case (cannot go below line 1)
            Range smallRange = Range.of(2, 3);
            Range expandedSmall = smallRange.expand(5);
            assertEquals(1, expandedSmall.startLine());
        }
    }
}

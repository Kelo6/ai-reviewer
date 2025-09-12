package com.ai.reviewer.backend.domain.orchestrator;

import com.ai.reviewer.backend.domain.config.AiReviewConfig;
import com.ai.reviewer.backend.domain.orchestrator.analyzer.MockStaticAnalyzer;
import com.ai.reviewer.backend.domain.orchestrator.reviewer.MockAiReviewer;
import com.ai.reviewer.shared.enums.Dimension;
import com.ai.reviewer.shared.enums.FileStatus;
import com.ai.reviewer.shared.enums.Severity;
import com.ai.reviewer.shared.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Mock analyzers and reviewers to ensure
 * end-to-end functionality with aggregation, scoring, and reporting.
 */
@ExtendWith(MockitoExtension.class)
class MockAnalyzerIntegrationTest {
    
    private MockStaticAnalyzer staticAnalyzer;
    private MockAiReviewer aiReviewer;
    private AiReviewConfig config;
    
    @BeforeEach
    void setUp() {
        staticAnalyzer = new MockStaticAnalyzer();
        aiReviewer = new MockAiReviewer();
        
        // Create test configuration
        config = new AiReviewConfig(
            "github",
            new AiReviewConfig.LlmConfig(
                List.of("gpt-4o"),
                0.50
            ),
            new AiReviewConfig.ScoringConfig(
                Map.of(
                    Dimension.SECURITY, 0.30,
                    Dimension.QUALITY, 0.25,
                    Dimension.MAINTAINABILITY, 0.20,
                    Dimension.PERFORMANCE, 0.15,
                    Dimension.TEST_COVERAGE, 0.10
                ),
                Map.of(
                    Severity.INFO, 1.0,
                    Severity.MINOR, 3.0,
                    Severity.MAJOR, 7.0,
                    Severity.CRITICAL, 12.0
                ),
                0.3 // confidence threshold
            ),
            new AiReviewConfig.ReportConfig(
                new AiReviewConfig.ReportConfig.ExportConfig(true, true, true, true)
            )
        );
    }
    
    @Test
    void shouldAnalyzeWithMockStaticAnalyzer() {
        // Given
        RepoRef repository = new RepoRef("github", "testorg", "test-repo", "https://github.com/testorg/test-repo");
        PullRef pullRequest = new PullRef("123", "456", "Test PR", "feature-branch", "main", "abc123", false);
        List<DiffHunk> diffHunks = List.of(
            new DiffHunk("src/main/java/TestClass.java", FileStatus.MODIFIED,
                "@@ -1,3 +1,5 @@\n public class TestClass {\n+    String query = \"SELECT * FROM users WHERE id = \" + userId;\n     // existing code\n }", null, 2, 0)
        );
        
        // When
        List<Finding> findings = staticAnalyzer.analyze(repository, pullRequest, diffHunks, config);
        
        // Then
        assertThat(findings).isNotEmpty();
        assertThat(findings).hasSize(2); // Both findings should pass confidence threshold
        
        Finding securityFinding = findings.stream()
            .filter(f -> f.dimension() == Dimension.SECURITY)
            .findFirst()
            .orElseThrow();
        
        assertThat(securityFinding.id()).isEqualTo("MOCK-SEC-001");
        assertThat(securityFinding.severity()).isEqualTo(Severity.MAJOR);
        assertThat(securityFinding.title()).contains("SQL injection");
        assertThat(securityFinding.confidence()).isGreaterThan(config.scoring().ignoreConfidenceBelow());
        assertThat(securityFinding.sources()).contains("mock-static-analyzer");
    }
    
    @Test
    void shouldReviewWithMockAiReviewer() {
        // Given
        RepoRef repository = new RepoRef("github", "testorg", "test-repo", "https://github.com/testorg/test-repo");
        PullRef pullRequest = new PullRef("123", "456", "Test PR", "feature-branch", "main", "abc123", false);
        List<DiffHunk> diffHunks = List.of(
            new DiffHunk("src/main/java/TestService.java", FileStatus.MODIFIED,
                "@@ -1,5 +1,10 @@\n public class TestService {\n+    private DatabaseConnection conn = new DatabaseConnection();\n+    \n+    public List<User> getUsers() {\n+        return conn.query(\"SELECT * FROM users\");\n+    }\n }", null, 5, 0),
            new DiffHunk("src/main/java/UserController.java", FileStatus.ADDED,
                "@@ -0,0 +1,20 @@\n+public class UserController {\n+    public void processUsers() {\n+        for (User user : getAllUsers()) {\n+            // process each user\n+        }\n+    }\n+}", null, 20, 0)
        );
        
        // When
        List<Finding> findings = aiReviewer.review(repository, pullRequest, diffHunks, config);
        
        // Then
        assertThat(findings).isNotEmpty();
        assertThat(findings).hasSize(2); // Both AI findings should pass confidence threshold
        
        Finding maintainabilityFinding = findings.stream()
            .filter(f -> f.dimension() == Dimension.MAINTAINABILITY)
            .findFirst()
            .orElseThrow();
        
        assertThat(maintainabilityFinding.id()).isEqualTo("MOCK-AI-001");
        assertThat(maintainabilityFinding.title()).contains("dependency injection");
        assertThat(maintainabilityFinding.confidence()).isGreaterThan(config.scoring().ignoreConfidenceBelow());
        assertThat(maintainabilityFinding.sources()).contains("mock-ai-reviewer");
        
        Finding performanceFinding = findings.stream()
            .filter(f -> f.dimension() == Dimension.PERFORMANCE)
            .findFirst()
            .orElseThrow();
        
        assertThat(performanceFinding.severity()).isEqualTo(Severity.INFO);
        assertThat(performanceFinding.title()).contains("optimization opportunity");
    }
    
    @Test
    void shouldFilterFindingsBasedOnConfidenceThreshold() {
        // Given - Config with high confidence threshold
        AiReviewConfig highThresholdConfig = new AiReviewConfig(
            "github",
            new AiReviewConfig.LlmConfig(List.of("gpt-4o"), 0.50),
            new AiReviewConfig.ScoringConfig(
                Map.of(
                    Dimension.SECURITY, 0.30,
                    Dimension.QUALITY, 0.25,
                    Dimension.MAINTAINABILITY, 0.20,
                    Dimension.PERFORMANCE, 0.15,
                    Dimension.TEST_COVERAGE, 0.10
                ),
                Map.of(
                    Severity.INFO, 1.0,
                    Severity.MINOR, 3.0,
                    Severity.MAJOR, 7.0,
                    Severity.CRITICAL, 12.0
                ),
                0.9 // very high confidence threshold
            ),
            new AiReviewConfig.ReportConfig(
                new AiReviewConfig.ReportConfig.ExportConfig(true, true, true, true)
            )
        );
        
        RepoRef repository = new RepoRef("github", "testorg", "test-repo", "https://github.com/testorg/test-repo");
        PullRef pullRequest = new PullRef("123", "456", "Test PR", "feature-branch", "main", "abc123", false);
        List<DiffHunk> diffHunks = List.of(
            new DiffHunk("src/main/java/TestClass.java", FileStatus.MODIFIED, "some changes", null, 1, 0)
        );
        
        // When
        List<Finding> staticFindings = staticAnalyzer.analyze(repository, pullRequest, diffHunks, highThresholdConfig);
        List<Finding> aiFindings = aiReviewer.review(repository, pullRequest, diffHunks, highThresholdConfig);
        
        // Then - Only high confidence findings should pass
        assertThat(staticFindings).hasSize(1); // Only security finding (0.85) passes 0.9 threshold
        assertThat(aiFindings).isEmpty(); // No AI findings pass 0.9 threshold
        
        Finding securityFinding = staticFindings.get(0);
        assertThat(securityFinding.dimension()).isEqualTo(Dimension.SECURITY);
        assertThat(securityFinding.confidence()).isGreaterThan(0.9);
    }
    
    @Test
    void shouldProvideCorrectAnalyzerMetadata() {
        // When
        String analyzerId = staticAnalyzer.getAnalyzerId();
        String analyzerName = staticAnalyzer.getAnalyzerName();
        String version = staticAnalyzer.getVersion();
        boolean enabled = staticAnalyzer.isEnabled();
        
        // Then
        assertThat(analyzerId).isEqualTo("mock-static-analyzer");
        assertThat(analyzerName).isEqualTo("Mock Static Analyzer");
        assertThat(version).isEqualTo("1.0.0-mock");
        assertThat(enabled).isTrue();
        
        // Test file support
        assertThat(staticAnalyzer.supportsFile("TestClass.java")).isTrue();
        assertThat(staticAnalyzer.supportsFile("script.js")).isTrue();
        assertThat(staticAnalyzer.supportsFile("component.ts")).isTrue();
        assertThat(staticAnalyzer.supportsFile("app.py")).isTrue();
        assertThat(staticAnalyzer.supportsFile("main.go")).isTrue();
        assertThat(staticAnalyzer.supportsFile("README.md")).isFalse();
    }
    
    @Test
    void shouldProvideCorrectReviewerMetadata() {
        // When
        String reviewerId = aiReviewer.getReviewerId();
        String reviewerName = aiReviewer.getReviewerName();
        String modelVersion = aiReviewer.getModelVersion();
        boolean enabled = aiReviewer.isEnabled();
        
        // Then
        assertThat(reviewerId).isEqualTo("mock-ai-reviewer");
        assertThat(reviewerName).isEqualTo("Mock AI Reviewer");
        assertThat(modelVersion).isEqualTo("mock-gpt-4o");
        assertThat(enabled).isTrue();
        
        // Test language support
        assertThat(aiReviewer.supportsLanguage("java")).isTrue();
        assertThat(aiReviewer.supportsLanguage("javascript")).isTrue();
        assertThat(aiReviewer.supportsLanguage("typescript")).isTrue();
        assertThat(aiReviewer.supportsLanguage("python")).isTrue();
        assertThat(aiReviewer.supportsLanguage("go")).isTrue();
        assertThat(aiReviewer.supportsLanguage("unknown")).isFalse();
    }
    
    @Test
    void shouldHandleEmptyDiffHunks() {
        // Given
        RepoRef repository = new RepoRef("github", "testorg", "test-repo", "https://github.com/testorg/test-repo");
        PullRef pullRequest = new PullRef("123", "456", "Test PR", "feature-branch", "main", "abc123", false);
        List<DiffHunk> emptyDiffHunks = List.of();
        
        // When
        List<Finding> staticFindings = staticAnalyzer.analyze(repository, pullRequest, emptyDiffHunks, config);
        List<Finding> aiFindings = aiReviewer.review(repository, pullRequest, emptyDiffHunks, config);
        
        // Then
        assertThat(staticFindings).isEmpty();
        assertThat(aiFindings).isEmpty();
    }
}

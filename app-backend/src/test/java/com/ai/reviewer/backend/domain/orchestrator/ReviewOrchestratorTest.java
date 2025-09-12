package com.ai.reviewer.backend.domain.orchestrator;

import com.ai.reviewer.backend.domain.adapter.scm.*;
import com.ai.reviewer.backend.domain.orchestrator.aggregator.FindingAggregator;
import com.ai.reviewer.backend.domain.orchestrator.analyzer.StaticAnalyzer;
import com.ai.reviewer.backend.domain.orchestrator.report.ReportGenerator;
import com.ai.reviewer.backend.domain.orchestrator.reviewer.AiReviewer;
import com.ai.reviewer.backend.domain.orchestrator.scoring.ScoringEngine;
import com.ai.reviewer.backend.domain.orchestrator.splitter.CodeSplitter;
import com.ai.reviewer.backend.domain.config.AiReviewConfig;
import com.ai.reviewer.backend.domain.config.ConfigService;
import com.ai.reviewer.backend.domain.report.ReportService;
import com.ai.reviewer.backend.service.orchestrator.ReviewService;
import com.ai.reviewer.shared.model.*;
import com.ai.reviewer.shared.enums.FileStatus;
import com.ai.reviewer.shared.enums.Severity;
import com.ai.reviewer.shared.enums.Dimension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReviewOrchestrator.
 */
@ExtendWith(MockitoExtension.class)
class ReviewOrchestratorTest {
    
    @Mock
    private ScmAdapterRouter scmAdapterRouter;
    
    @Mock
    private ScmAdapter scmAdapter;
    
    @Mock
    private CodeSplitter codeSplitter;
    
    @Mock
    private FindingAggregator findingAggregator;
    
    @Mock
    private ScoringEngine scoringEngine;
    
    @Mock
    private ReportGenerator reportGenerator;
    
    @Mock
    private ReportService reportService;
    
    @Mock
    private ConfigService configService;
    
    @Mock
    private ReviewService reviewService;
    
    @Mock
    private StaticAnalyzer staticAnalyzer;
    
    @Mock
    private AiReviewer aiReviewer;
    
    private ReviewOrchestrator orchestrator;
    private RepoRef testRepo;
    private PullRef testPull;
    private ReviewOrchestrator.ReviewConfig testConfig;
    
    @BeforeEach
    void setUp() {
        orchestrator = new ReviewOrchestrator(
            scmAdapterRouter,
            codeSplitter,
            findingAggregator,
            scoringEngine,
            reportGenerator,
            reportService,
            configService,
            reviewService,
            List.of(staticAnalyzer),
            List.of(aiReviewer)
        );
        
        testRepo = new RepoRef("github", "owner", "repo", "https://github.com/owner/repo");
        testPull = new PullRef("123", "456", "Test PR", "feature", "main", "abc123", false);
        testConfig = ReviewOrchestrator.ReviewConfig.defaultConfig();
    }
    
    @Test
    void testSuccessfulReviewRun() throws Exception {
        // Setup mocks
        List<DiffHunk> mockDiffs = createMockDiffHunks();
        List<StaticAnalyzer.CodeSegment> mockSegments = createMockCodeSegments();
        List<Finding> staticFindings = createMockStaticFindings();
        List<Finding> aiFindings = createMockAiFindings();
        FindingAggregator.AggregationResult mockAggregation = createMockAggregationResult();
        Scores mockScores = createMockScores();
        ScoringEngine.ScoreSummary mockSummary = createMockScoreSummary(mockScores);
        
        // Mock SCM adapter
        when(scmAdapterRouter.getAdapter(testRepo)).thenReturn(scmAdapter);
        when(scmAdapter.listDiff(testRepo, testPull)).thenReturn(mockDiffs);
        
        // Mock code splitter
        when(codeSplitter.split(eq(mockDiffs), any())).thenReturn(mockSegments);
        
        // Mock analyzers
        when(staticAnalyzer.isEnabled()).thenReturn(true);
        when(staticAnalyzer.getAnalyzerId()).thenReturn("test-static");
        when(staticAnalyzer.supportsFile(anyString())).thenReturn(true);
        when(staticAnalyzer.analyzeBatchAsync(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(staticFindings));
        
        when(aiReviewer.isEnabled()).thenReturn(true);
        when(aiReviewer.getReviewerId()).thenReturn("test-ai");
        when(aiReviewer.supportsLanguage(anyString())).thenReturn(true);
        when(aiReviewer.reviewBatchAsync(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(aiFindings));
        
        // Mock aggregation and scoring
        when(findingAggregator.aggregate(eq(staticFindings), eq(aiFindings), any()))
            .thenReturn(mockAggregation);
        when(scoringEngine.mapFindingsToDimensions(any(), any()))
            .thenReturn(mockAggregation.findings());
        when(scoringEngine.calculateScores(any(List.class), any(ScoringEngine.ScoringConfig.class))).thenReturn(mockScores);
        when(scoringEngine.generateScoreSummary(eq(mockScores), any(), any()))
            .thenReturn(mockSummary);
        
        // Mock report generation
        when(reportGenerator.generateReport(any(), eq(ReportGenerator.ReportFormat.JSON), any()))
            .thenReturn("{\"test\": \"report\"}");
        when(reportGenerator.generateReport(any(), eq(ReportGenerator.ReportFormat.MARKDOWN), any()))
            .thenReturn("# Test Report");
        
        // Execute
        CompletableFuture<ReviewRun> future = orchestrator.runReview(testRepo, testPull, "github", testConfig);
        ReviewRun result = future.get();
        
        // Verify
        assertNotNull(result);
        assertNotNull(result.runId());
        assertEquals(testRepo, result.repository());
        assertEquals(testPull, result.pullRequest());
        assertNotNull(result.findings());
        assertNotNull(result.scores());
        assertNotNull(result.startTime());
        assertNotNull(result.endTime());
        assertNotNull(result.stats());
        
        // Verify interactions
        verify(scmAdapterRouter).getAdapter(testRepo);
        verify(scmAdapter).listDiff(testRepo, testPull);
        verify(codeSplitter).split(eq(mockDiffs), any());
        verify(staticAnalyzer).analyzeBatchAsync(any(), any());
        verify(aiReviewer).reviewBatchAsync(any(), any());
        verify(findingAggregator).aggregate(any(), any(), any());
        verify(scoringEngine).calculateScores(any(List.class), any(ScoringEngine.ScoringConfig.class));
    }
    
    @Test
    void testEmptyDiffHandling() throws Exception {
        // Setup - empty diffs
        when(scmAdapterRouter.getAdapter(testRepo)).thenReturn(scmAdapter);
        when(scmAdapter.listDiff(testRepo, testPull)).thenReturn(List.of());
        
        // Execute
        CompletableFuture<ReviewRun> future = orchestrator.runReview(testRepo, testPull, "github", testConfig);
        ReviewRun result = future.get();
        
        // Verify - should return empty review run with perfect scores
        assertNotNull(result);
        assertEquals(0, result.findings().size());
        assertEquals(100.0, result.scores().totalScore());
        assertNotNull(result.stats());
        assertEquals(0, result.stats().totalFindings());
        
        // Should not call analyzers for empty diffs
        verify(staticAnalyzer, never()).analyzeBatchAsync(any(), any());
        verify(aiReviewer, never()).reviewBatchAsync(any(), any());
    }
    
    @Test
    void testValidationFailure() {
        // Test with null repository
        CompletableFuture<ReviewRun> future = orchestrator.runReview(null, testPull, "github", testConfig);
        
        // Should complete exceptionally
        assertThrows(Exception.class, () -> future.get());
    }
    
    @Test
    void testStaticAnalyzerFailure() throws Exception {
        // Setup mocks - static analyzer fails
        List<DiffHunk> mockDiffs = createMockDiffHunks();
        List<StaticAnalyzer.CodeSegment> mockSegments = createMockCodeSegments();
        List<Finding> aiFindings = createMockAiFindings();
        FindingAggregator.AggregationResult mockAggregation = createMockAggregationResult();
        
        when(scmAdapterRouter.getAdapter(testRepo)).thenReturn(scmAdapter);
        when(scmAdapter.listDiff(testRepo, testPull)).thenReturn(mockDiffs);
        when(codeSplitter.split(eq(mockDiffs), any())).thenReturn(mockSegments);
        
        when(staticAnalyzer.isEnabled()).thenReturn(true);
        when(staticAnalyzer.getAnalyzerId()).thenReturn("failing-static");
        when(staticAnalyzer.supportsFile(anyString())).thenReturn(true);
        when(staticAnalyzer.analyzeBatchAsync(any(), any()))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Static analyzer failed")));
        
        when(aiReviewer.isEnabled()).thenReturn(true);
        when(aiReviewer.getReviewerId()).thenReturn("test-ai");
        when(aiReviewer.supportsLanguage(anyString())).thenReturn(true);
        when(aiReviewer.reviewBatchAsync(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(aiFindings));
        
        // Mock aggregation with only AI findings (static should return empty list due to failure)
        when(findingAggregator.aggregate(eq(List.of()), eq(aiFindings), any()))
            .thenReturn(mockAggregation);
        when(scoringEngine.mapFindingsToDimensions(any(), any()))
            .thenReturn(mockAggregation.findings());
        when(scoringEngine.calculateScores(any(List.class), any(ScoringEngine.ScoringConfig.class))).thenReturn(createMockScores());
        when(scoringEngine.generateScoreSummary(any(), any(), any()))
            .thenReturn(createMockScoreSummary(createMockScores()));
        
        when(reportGenerator.generateReport(any(), any(), any())).thenReturn("test report");
        
        // Execute - should complete successfully despite static analyzer failure
        CompletableFuture<ReviewRun> future = orchestrator.runReview(testRepo, testPull, "github", testConfig);
        ReviewRun result = future.get();
        
        assertNotNull(result);
        // Should have continued with AI findings only
        verify(findingAggregator).aggregate(eq(List.of()), eq(aiFindings), any());
    }
    
    @Test
    void testAiReviewerFailure() throws Exception {
        // Setup mocks - AI reviewer fails
        List<DiffHunk> mockDiffs = createMockDiffHunks();
        List<StaticAnalyzer.CodeSegment> mockSegments = createMockCodeSegments();
        List<Finding> staticFindings = createMockStaticFindings();
        FindingAggregator.AggregationResult mockAggregation = createMockAggregationResult();
        
        when(scmAdapterRouter.getAdapter(testRepo)).thenReturn(scmAdapter);
        when(scmAdapter.listDiff(testRepo, testPull)).thenReturn(mockDiffs);
        when(codeSplitter.split(eq(mockDiffs), any())).thenReturn(mockSegments);
        
        when(staticAnalyzer.isEnabled()).thenReturn(true);
        when(staticAnalyzer.getAnalyzerId()).thenReturn("test-static");
        when(staticAnalyzer.supportsFile(anyString())).thenReturn(true);
        when(staticAnalyzer.analyzeBatchAsync(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(staticFindings));
        
        when(aiReviewer.isEnabled()).thenReturn(true);
        when(aiReviewer.getReviewerId()).thenReturn("failing-ai");
        when(aiReviewer.supportsLanguage(anyString())).thenReturn(true);
        when(aiReviewer.reviewBatchAsync(any(), any()))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("AI reviewer failed")));
        
        // Mock aggregation with only static findings
        when(findingAggregator.aggregate(eq(staticFindings), eq(List.of()), any()))
            .thenReturn(mockAggregation);
        when(scoringEngine.mapFindingsToDimensions(any(), any()))
            .thenReturn(mockAggregation.findings());
        when(scoringEngine.calculateScores(any(List.class), any(ScoringEngine.ScoringConfig.class))).thenReturn(createMockScores());
        when(scoringEngine.generateScoreSummary(any(), any(), any()))
            .thenReturn(createMockScoreSummary(createMockScores()));
        
        when(reportGenerator.generateReport(any(), any(), any())).thenReturn("test report");
        
        // Execute - should complete successfully despite AI reviewer failure
        CompletableFuture<ReviewRun> future = orchestrator.runReview(testRepo, testPull, "github", testConfig);
        ReviewRun result = future.get();
        
        assertNotNull(result);
        // Should have continued with static findings only
        verify(findingAggregator).aggregate(eq(staticFindings), eq(List.of()), any());
    }
    
    @Test
    void testDisabledAnalyzers() throws Exception {
        // Setup - analyzers disabled
        List<DiffHunk> mockDiffs = createMockDiffHunks();
        List<StaticAnalyzer.CodeSegment> mockSegments = createMockCodeSegments();
        FindingAggregator.AggregationResult emptyAggregation = new FindingAggregator.AggregationResult(
            List.of(), null, List.of());
        
        when(scmAdapterRouter.getAdapter(testRepo)).thenReturn(scmAdapter);
        when(scmAdapter.listDiff(testRepo, testPull)).thenReturn(mockDiffs);
        when(codeSplitter.split(eq(mockDiffs), any())).thenReturn(mockSegments);
        
        when(staticAnalyzer.isEnabled()).thenReturn(false);
        when(aiReviewer.isEnabled()).thenReturn(false);
        
        when(findingAggregator.aggregate(eq(List.of()), eq(List.of()), any()))
            .thenReturn(emptyAggregation);
        when(scoringEngine.mapFindingsToDimensions(any(), any())).thenReturn(List.of());
        when(scoringEngine.calculateScores(any(List.class), any(ScoringEngine.ScoringConfig.class))).thenReturn(createPerfectScores());
        when(scoringEngine.generateScoreSummary(any(), any(), any()))
            .thenReturn(createMockScoreSummary(createPerfectScores()));
        
        when(reportGenerator.generateReport(any(), any(), any())).thenReturn("empty report");
        
        // Execute
        CompletableFuture<ReviewRun> future = orchestrator.runReview(testRepo, testPull, "github", testConfig);
        ReviewRun result = future.get();
        
        // Should complete with no findings
        assertNotNull(result);
        assertEquals(0, result.findings().size());
        
        // Should not call disabled analyzers
        verify(staticAnalyzer, never()).analyzeBatchAsync(any(), any());
        verify(aiReviewer, never()).reviewBatchAsync(any(), any());
    }
    
    // Helper methods to create mock objects
    
    private List<DiffHunk> createMockDiffHunks() {
        return List.of(
            new DiffHunk("src/main/java/Test.java", FileStatus.MODIFIED, 
                "@@ -1,3 +1,4 @@\n public class Test {\n+    // new comment\n     public void test() {", null, 1, 0),
            new DiffHunk("src/main/java/Another.java", FileStatus.ADDED, 
                "@@ -0,0 +1,5 @@\n+public class Another {\n+    public void method() {\n+    }\n+}", null, 5, 0)
        );
    }
    
    private List<StaticAnalyzer.CodeSegment> createMockCodeSegments() {
        return List.of(
            StaticAnalyzer.CodeSegment.of("src/main/java/Test.java", 
                "public class Test {\n    // new comment\n    public void test() {", 1, 3),
            StaticAnalyzer.CodeSegment.of("src/main/java/Another.java", 
                "public class Another {\n    public void method() {\n    }\n}", 1, 4)
        );
    }
    
    private List<Finding> createMockStaticFindings() {
        return List.of(
            new Finding("finding-1", "src/main/java/Test.java", 2, 2, 
                Severity.MINOR, Dimension.QUALITY, "Unused comment", 
                "// new comment", "Remove unused comment", null, List.of("checkstyle"), 0.8),
            new Finding("finding-2", "src/main/java/Another.java", 2, 2, 
                Severity.MINOR, Dimension.QUALITY, "Empty method", 
                "public void method() {}", "Add method implementation", null, List.of("spotbugs"), 0.7)
        );
    }
    
    private List<Finding> createMockAiFindings() {
        return List.of(
            new Finding("finding-3", "src/main/java/Test.java", 3, 3, 
                Severity.INFO, Dimension.MAINTAINABILITY, "Consider adding documentation", 
                "public void test() {", "Add JavaDoc comment", null, List.of("ai-reviewer"), 0.6),
            new Finding("finding-4", "src/main/java/Another.java", 1, 4, 
                Severity.MINOR, Dimension.MAINTAINABILITY, "Class could benefit from better structure", 
                "public class Another {\n    public void method() {\n    }\n}", "Add more descriptive methods", null, List.of("ai-reviewer"), 0.75)
        );
    }
    
    private FindingAggregator.AggregationResult createMockAggregationResult() {
        List<Finding> aggregatedFindings = List.of(
            new Finding("finding-5", "src/main/java/Test.java", 2, 2, 
                Severity.MINOR, Dimension.QUALITY, "Code quality issue", 
                "// new comment", "Remove or improve comment", null, List.of("checkstyle", "ai-reviewer"), 0.8),
            new Finding("finding-6", "src/main/java/Another.java", 2, 2, 
                Severity.MINOR, Dimension.MAINTAINABILITY, "Maintainability concern", 
                "public void method() {}", "Improve method implementation", null, List.of("ai-reviewer"), 0.75)
        );
        
        return new FindingAggregator.AggregationResult(aggregatedFindings, null, List.of());
    }
    
    private Scores createMockScores() {
        Map<Dimension, Double> dimensionScores = Map.of(
            Dimension.SECURITY, 100.0,
            Dimension.QUALITY, 85.0,
            Dimension.MAINTAINABILITY, 80.0,
            Dimension.PERFORMANCE, 95.0,
            Dimension.TEST_COVERAGE, 90.0
        );
        
        Map<Dimension, Double> weights = Map.of(
            Dimension.SECURITY, 0.25,
            Dimension.QUALITY, 0.30,
            Dimension.MAINTAINABILITY, 0.25,
            Dimension.PERFORMANCE, 0.15,
            Dimension.TEST_COVERAGE, 0.05
        );
        
        return new Scores(87.5, dimensionScores, weights);
    }
    
    private Scores createPerfectScores() {
        Map<Dimension, Double> dimensionScores = Map.of(
            Dimension.SECURITY, 100.0,
            Dimension.QUALITY, 100.0,
            Dimension.MAINTAINABILITY, 100.0,
            Dimension.PERFORMANCE, 100.0,
            Dimension.TEST_COVERAGE, 100.0
        );
        
        Map<Dimension, Double> weights = Map.of(
            Dimension.SECURITY, 0.25,
            Dimension.QUALITY, 0.30,
            Dimension.MAINTAINABILITY, 0.25,
            Dimension.PERFORMANCE, 0.15,
            Dimension.TEST_COVERAGE, 0.05
        );
        
        return new Scores(100.0, dimensionScores, weights);
    }
    
    private ScoringEngine.ScoreSummary createMockScoreSummary(Scores scores) {
        return new ScoringEngine.ScoreSummary(
            scores,
            ScoringEngine.ScoreGrade.B,
            List.of(Dimension.MAINTAINABILITY),  // problem areas
            List.of(Dimension.SECURITY, Dimension.PERFORMANCE), // strong areas
            10.0, // improvement potential
            List.of("Focus on code maintainability", "Consider refactoring complex methods"),
            Map.of("timestamp", Instant.now())
        );
    }
}

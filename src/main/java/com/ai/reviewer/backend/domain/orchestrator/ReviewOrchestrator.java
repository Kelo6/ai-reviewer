package com.ai.reviewer.backend.domain.orchestrator;

import com.ai.reviewer.backend.domain.adapter.scm.*;
import com.ai.reviewer.backend.domain.config.AiReviewConfig;
import com.ai.reviewer.backend.domain.config.ConfigService;
import com.ai.reviewer.backend.domain.orchestrator.aggregator.FindingAggregator;
import com.ai.reviewer.backend.domain.orchestrator.analyzer.StaticAnalyzer;
import com.ai.reviewer.backend.domain.orchestrator.report.ReportGenerator;
import com.ai.reviewer.backend.domain.orchestrator.reviewer.AiReviewer;
import com.ai.reviewer.backend.domain.orchestrator.scoring.ScoringEngine;
import com.ai.reviewer.backend.domain.orchestrator.splitter.CodeSplitter;
import com.ai.reviewer.backend.domain.report.ReportService;
import com.ai.reviewer.backend.service.orchestrator.ReviewService;
import com.ai.reviewer.shared.model.*;
import com.ai.reviewer.shared.enums.Dimension;
import com.ai.reviewer.shared.enums.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Main orchestrator for the code review process.
 * 
 * <p>Coordinates the entire review workflow:
 * 1. Validation and preparation
 * 2. Code retrieval and splitting
 * 3. Parallel analysis (static tools + AI)
 * 4. Result aggregation and deduplication
 * 5. Dimension mapping and scoring
 * 6. Report generation
 * 7. Feedback publication
 * 8. Data persistence
 */
@Component
public class ReviewOrchestrator {
    
    private static final Logger logger = LoggerFactory.getLogger(ReviewOrchestrator.class);
    
    private final ScmAdapterRouter scmAdapterRouter;
    private final CodeSplitter codeSplitter;
    private final FindingAggregator findingAggregator;
    private final ScoringEngine scoringEngine;
    private final ReportGenerator reportGenerator;
    private final ReportService reportService;
    private final ConfigService configService;
    private final ReviewService reviewService;
    
    // Analyzer and reviewer collections
    private final List<StaticAnalyzer> staticAnalyzers;
    private final List<AiReviewer> aiReviewers;
    
    @Autowired
    public ReviewOrchestrator(ScmAdapterRouter scmAdapterRouter,
                            CodeSplitter codeSplitter,
                            FindingAggregator findingAggregator,
                            ScoringEngine scoringEngine,
                            ReportGenerator reportGenerator,
                            ReportService reportService,
                            ConfigService configService,
                            ReviewService reviewService,
                            List<StaticAnalyzer> staticAnalyzers,
                            List<AiReviewer> aiReviewers) {
        this.scmAdapterRouter = scmAdapterRouter;
        this.codeSplitter = codeSplitter;
        this.findingAggregator = findingAggregator;
        this.scoringEngine = scoringEngine;
        this.reportGenerator = reportGenerator;
        this.reportService = reportService;
        this.configService = configService;
        this.reviewService = reviewService;
        this.staticAnalyzers = staticAnalyzers != null ? staticAnalyzers : List.of();
        this.aiReviewers = aiReviewers != null ? aiReviewers : List.of();
        
        logger.info("ReviewOrchestrator initialized with {} static analyzers and {} AI reviewers",
            this.staticAnalyzers.size(), this.aiReviewers.size());
    }
    
    /**
     * 简化的代码评审方法，从仓库根目录加载配置。
     * 
     * @param repository 仓库引用
     * @param pullRequest Pull Request引用
     * @param providerKeys 提供商列表
     * @return 评审运行结果
     */
    public ReviewRun runReview(RepoRef repository, PullRef pullRequest, List<String> providerKeys) {
        return runReviewWithConfig(repository, pullRequest, null, providerKeys);
    }

    /**
     * 使用指定仓库路径加载配置的代码评审方法。
     * 
     * @param repository 仓库引用
     * @param pullRequest Pull Request引用
     * @param repoPath 仓库本地路径（用于加载配置）
     * @param providerKeys 提供商列表
     * @return 评审运行结果
     */
    public ReviewRun runReviewWithConfig(RepoRef repository, PullRef pullRequest, String repoPath, List<String> providerKeys) {
        String runId = generateRunId();
        Instant startTime = Instant.now();
        
        logger.info("Starting review run {} for {}/{}#{}", runId, 
            repository.owner(), repository.name(), pullRequest.number());
        
        try {
            // 1. 加载配置
            AiReviewConfig aiReviewConfig = configService.loadConfig(repoPath);
            logger.debug("Loaded configuration: provider={}, llm.adapters={}", 
                aiReviewConfig.provider(), aiReviewConfig.llm().adapters());
            
            // 2. 获取SCM适配器
            ScmAdapter scmAdapter = scmAdapterRouter.getAdapter(repository);
            
            // 3. 获取代码差异
            List<DiffHunk> diffHunks = scmAdapter.listDiff(repository, pullRequest);
            
            if (diffHunks.isEmpty()) {
                logger.warn("No diff hunks found for PR {}#{}", repository.name(), pullRequest.number());
                return createEmptyReviewRun(runId, repository, pullRequest, startTime);
            }
            
            // 4. 模拟代码分析 - 简化版本，实际项目中需要实现完整的分析流程
            // 这里使用模拟数据展示配置如何使用
            List<Finding> findings = createMockFindings(diffHunks, aiReviewConfig);
            
            // 5. 计算评分（使用配置）
            int linesChanged = diffHunks.stream()
                .mapToInt(hunk -> Math.abs(hunk.linesAdded() - hunk.linesDeleted()))
                .sum();
            
            Scores scores;
            if (aiReviewConfig.scoring() != null) {
                // 使用配置的评分逻辑
                scores = scoringEngine.calculateScores(findings, linesChanged, aiReviewConfig.scoring());
            } else {
                // 使用默认评分逻辑
                scores = scoringEngine.calculateScores(findings, linesChanged);
            }
            
            // 6. 生成报告（使用配置）
            ReviewRun.Artifacts artifacts = null;
            if (aiReviewConfig.report() != null) {
                ReviewRun tempReviewRun = new ReviewRun(
                    runId, repository, pullRequest, startTime,
                    providerKeys, createMockStats(startTime, diffHunks.size(), findings.size()),
                    findings, scores, null
                );
                artifacts = reportService.generateReports(tempReviewRun);
            }
            
            // 7. 创建最终结果
            Instant endTime = Instant.now();
            ReviewRun.Stats stats = createMockStats(startTime, diffHunks.size(), findings.size());
            
            ReviewRun reviewRun = new ReviewRun(
                runId, repository, pullRequest, startTime,
                providerKeys, stats, findings, scores, artifacts
            );
            
            logger.info("Review run {} completed successfully in {}ms with {} findings", 
                runId, stats.latencyMs(), findings.size());
            
            return reviewRun;
            
        } catch (Exception e) {
            logger.error("Review run {} failed", runId, e);
            throw new ReviewOrchestrationException("Review run failed: " + e.getMessage(), e);
        }
    }

    /**
     * Run complete code review process.
     * 
     * @param repository repository reference
     * @param pullRequest pull request reference
     * @param providerKey SCM provider identifier
     * @param config review configuration
     * @return review run result with generated runId
     */
    @Async("reviewExecutor")
    public CompletableFuture<ReviewRun> runReview(RepoRef repository, PullRef pullRequest, 
                                                 String providerKey, ReviewConfig config) {
        String runId = generateRunId();
        Instant startTime = Instant.now();
        
        // Early validation for null parameters
        if (repository == null || pullRequest == null) {
            logger.error("Review run {} failed: null repository or pull request", runId);
            return CompletableFuture.failedFuture(
                new ReviewOrchestrationException("Repository and pull request cannot be null"));
        }
        
        logger.info("Starting review run {} for {}/{}#{}", runId, 
            repository.owner(), repository.name(), pullRequest.number());
        
        try {
            // Step 1: Validation
            ValidationResult validation = validateInput(repository, pullRequest, providerKey, config);
            if (!validation.isValid()) {
                throw new ReviewOrchestrationException("Validation failed: " + 
                    String.join(", ", validation.errors()));
            }
            
            // Step 2: Get SCM adapter and retrieve diff
            ScmAdapter scmAdapter = scmAdapterRouter.getAdapter(repository);
            List<DiffHunk> diffHunks = scmAdapter.listDiff(repository, pullRequest);
            
            if (diffHunks.isEmpty()) {
                logger.warn("No diff hunks found for PR {}#{}", repository.name(), pullRequest.number());
                return CompletableFuture.completedFuture(createEmptyReviewRun(runId, repository, pullRequest, startTime));
            }
            
            // Step 3: Code splitting
            List<StaticAnalyzer.CodeSegment> codeSegments = codeSplitter.split(diffHunks, config.splittingStrategy());
            
            if (codeSegments.isEmpty()) {
                logger.warn("No code segments generated for PR {}#{}", repository.name(), pullRequest.number());
                return CompletableFuture.completedFuture(createEmptyReviewRun(runId, repository, pullRequest, startTime));
            }
            
            logger.debug("Generated {} code segments for analysis", codeSegments.size());
            
            // Step 4: Parallel analysis execution
            logger.info("🔍 Starting parallel analysis: static + AI for {} segments", codeSegments.size());
            CompletableFuture<List<Finding>> staticAnalysisFuture = runStaticAnalysis(codeSegments, repository, pullRequest, runId, config);
            CompletableFuture<List<Finding>> aiReviewFuture = runAiReview(codeSegments, repository, pullRequest, runId, config);
            
            // Wait for both analyses to complete
            CompletableFuture<Void> allAnalyses = CompletableFuture.allOf(staticAnalysisFuture, aiReviewFuture);
            
            return allAnalyses.thenCompose(v -> {
                try {
                    List<Finding> staticFindings = staticAnalysisFuture.join();
                    List<Finding> aiFindings = aiReviewFuture.join();
                    
                    logger.debug("Analysis completed: {} static findings, {} AI findings", 
                        staticFindings.size(), aiFindings.size());
                    
                    // Step 5: Aggregation and deduplication
                    FindingAggregator.AggregationResult aggregationResult = 
                        findingAggregator.aggregate(staticFindings, aiFindings, config.aggregationConfig());
                    
                    List<Finding> aggregatedFindings = aggregationResult.findings();
                    
                    // Step 6: Dimension mapping
                    List<Finding> mappedFindings = scoringEngine.mapFindingsToDimensions(
                        aggregatedFindings, config.dimensionMappingConfig());
                    
                    // Step 7: Scoring
                    Scores scores = scoringEngine.calculateScores(mappedFindings, config.scoringConfig());
                    ScoringEngine.ScoreSummary scoreSummary = scoringEngine.generateScoreSummary(
                        scores, mappedFindings, config.scoringConfig());
                    
                    // Step 8: Report generation
                    ReviewRun.Artifacts artifacts = generateReports(repository, pullRequest, runId, 
                        mappedFindings, scores, scoreSummary, startTime, config);
                    
                    // Step 9: Create final review run
                    Instant endTime = Instant.now();
                ReviewRun.Stats stats = new ReviewRun.Stats(
                    diffHunks.size(),                    // filesChanged
                    0,                                   // linesAdded (placeholder)
                    0,                                   // linesDeleted (placeholder)
                    endTime.toEpochMilli() - startTime.toEpochMilli(), // latencyMs
                    null                                 // tokenCostUsd
                );
                    
                    ReviewRun reviewRun = new ReviewRun(
                        runId,
                        repository,
                        pullRequest,
                        startTime,
                        List.of(), // providerKeys placeholder
                        stats,
                        mappedFindings,
                        scores,
                        artifacts
                    );
                    
                    // Step 10: Publish feedback (async)
                    publishFeedbackAsync(scmAdapter, reviewRun, scoreSummary, config)
                        .exceptionally(ex -> {
                            logger.warn("Failed to publish feedback for run {}: {}", runId, ex.getMessage());
                            return null;
                        });
                    
                    logger.info("Review run {} completed successfully in {}ms with {} findings", 
                        runId, stats.processingTimeMs(), mappedFindings.size());
                    
                    return CompletableFuture.completedFuture(reviewRun);
                    
                } catch (Exception e) {
                    logger.error("Review run {} failed during post-analysis processing", runId, e);
                    throw new ReviewOrchestrationException("Review processing failed: " + e.getMessage(), e);
                }
            });
            
        } catch (Exception e) {
            logger.error("Review run {} failed", runId, e);
            return CompletableFuture.failedFuture(new ReviewOrchestrationException("Review run failed: " + e.getMessage(), e));
        }
    }
    
    /**
     * Run static analysis on code segments.
     */
    private CompletableFuture<List<Finding>> runStaticAnalysis(List<StaticAnalyzer.CodeSegment> segments,
                                                             RepoRef repository, PullRef pullRequest,
                                                             String runId, ReviewConfig config) {
        if (staticAnalyzers.isEmpty()) {
            logger.debug("No static analyzers configured, skipping static analysis");
            return CompletableFuture.completedFuture(List.of());
        }
        
        List<CompletableFuture<List<Finding>>> analyzerFutures = new ArrayList<>();
        
        for (StaticAnalyzer analyzer : staticAnalyzers) {
            if (!analyzer.isEnabled()) {
                logger.debug("Skipping disabled static analyzer: {}", analyzer.getAnalyzerId());
                continue;
            }
            
            // Filter segments supported by this analyzer
            List<StaticAnalyzer.CodeSegment> supportedSegments = segments.stream()
                .filter(segment -> analyzer.supportsFile(segment.filePath()))
                .collect(Collectors.toList());
            
            if (supportedSegments.isEmpty()) {
                logger.debug("No supported segments for analyzer: {}", analyzer.getAnalyzerId());
                continue;
            }
            
            logger.debug("Running static analyzer {} on {} segments", 
                analyzer.getAnalyzerId(), supportedSegments.size());
            
            StaticAnalyzer.AnalysisContext context = new StaticAnalyzer.AnalysisContext(
                repository, pullRequest, runId,
                config.analyzerConfigs().getOrDefault(analyzer.getAnalyzerId(), 
                    StaticAnalyzer.AnalyzerConfig.empty(analyzer.getAnalyzerId())),
                Instant.now()
            );
            
            CompletableFuture<List<Finding>> analyzerFuture = analyzer.analyzeBatchAsync(supportedSegments, context)
                .exceptionally(ex -> {
                    logger.warn("Static analyzer {} failed: {}", analyzer.getAnalyzerId(), ex.getMessage(), ex);
                    return List.of();
                });
            
            analyzerFutures.add(analyzerFuture);
        }
        
        if (analyzerFutures.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        
        return CompletableFuture.allOf(analyzerFutures.toArray(new CompletableFuture[0]))
            .thenApply(v -> analyzerFutures.stream()
                .flatMap(future -> future.join().stream())
                .collect(Collectors.toList()));
    }
    
    /**
     * Run AI review on code segments.
     */
    private CompletableFuture<List<Finding>> runAiReview(List<StaticAnalyzer.CodeSegment> segments,
                                                       RepoRef repository, PullRef pullRequest,
                                                       String runId, ReviewConfig config) {
        logger.info("🤖 Starting AI review with {} reviewers for {} segments", 
            aiReviewers.size(), segments.size());
        
        if (aiReviewers.isEmpty()) {
            logger.warn("❌ No AI reviewers configured, skipping AI review");
            return CompletableFuture.completedFuture(List.of());
        }
        
        List<CompletableFuture<List<Finding>>> reviewerFutures = new ArrayList<>();
        
        for (AiReviewer reviewer : aiReviewers) {
            if (!reviewer.isEnabled()) {
                logger.debug("Skipping disabled AI reviewer: {}", reviewer.getReviewerId());
                continue;
            }
            
            // Filter segments by supported language
            List<StaticAnalyzer.CodeSegment> supportedSegments = segments.stream()
                .filter(segment -> reviewer.supportsLanguage(segment.language()))
                .collect(Collectors.toList());
            
            if (supportedSegments.isEmpty()) {
                logger.debug("No supported segments for reviewer: {}", reviewer.getReviewerId());
                continue;
            }
            
            logger.info("🚀 Running AI reviewer {} on {} segments", 
                reviewer.getReviewerId(), supportedSegments.size());
            
            AiReviewer.ReviewContext context = AiReviewer.ReviewContext.basic(
                repository, pullRequest, runId,
                config.reviewerConfigs().getOrDefault(reviewer.getReviewerId(),
                    AiReviewer.ReviewerConfig.empty(reviewer.getReviewerId()))
            );
            
            CompletableFuture<List<Finding>> reviewerFuture = reviewer.reviewBatchAsync(supportedSegments, context)
                .exceptionally(ex -> {
                    logger.warn("AI reviewer {} failed: {}", reviewer.getReviewerId(), ex.getMessage(), ex);
                    return List.of();
                });
            
            reviewerFutures.add(reviewerFuture);
        }
        
        if (reviewerFutures.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        
        return CompletableFuture.allOf(reviewerFutures.toArray(new CompletableFuture[0]))
            .thenApply(v -> reviewerFutures.stream()
                .flatMap(future -> future.join().stream())
                .collect(Collectors.toList()));
    }
    
    /**
     * Generate reports in multiple formats.
     */
    private ReviewRun.Artifacts generateReports(RepoRef repository, PullRef pullRequest, String runId,
                                              List<Finding> findings, Scores scores, 
                                              ScoringEngine.ScoreSummary scoreSummary,
                                              Instant startTime, ReviewConfig config) {
        try {
            ReviewRun reviewRun = new ReviewRun(
                runId, repository, pullRequest, startTime,
                List.of(), // providerKeys placeholder
                null, // stats placeholder  
                findings, scores, null // artifacts placeholder
            );
            
            String jsonReport = reportGenerator.generateReport(reviewRun, 
                ReportGenerator.ReportFormat.JSON, config.reportConfig());
            
            String markdownReport = reportGenerator.generateReport(reviewRun, 
                ReportGenerator.ReportFormat.MARKDOWN, config.reportConfig());
            
            String htmlReport = null;
            String sarifReport = null;
            
            if (config.generateDetailedReports()) {
                htmlReport = reportGenerator.generateReport(reviewRun, 
                    ReportGenerator.ReportFormat.HTML, config.reportConfig());
                    
                sarifReport = reportGenerator.generateReport(reviewRun, 
                    ReportGenerator.ReportFormat.SARIF, config.reportConfig());
            }
            
            return new ReviewRun.Artifacts(sarifReport, markdownReport, htmlReport, null);
            
        } catch (Exception e) {
            logger.error("Failed to generate reports for run {}", runId, e);
            throw new ReviewOrchestrationException("Report generation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Publish feedback to SCM platform asynchronously.
     */
    @Async("reviewExecutor")
    public CompletableFuture<Void> publishFeedbackAsync(ScmAdapter scmAdapter, ReviewRun reviewRun,
                                                       ScoringEngine.ScoreSummary scoreSummary,
                                                       ReviewConfig config) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (!config.publishFeedback()) {
                    logger.debug("Feedback publishing disabled for run {}", reviewRun.runId());
                    return;
                }
                
                logger.debug("Publishing feedback for run {}", reviewRun.runId());
                
                // Update status
                CheckSummary checkSummary = createCheckSummary(scoreSummary, reviewRun, config);
                scmAdapter.upsertCheck(reviewRun.repository(), reviewRun.pullRequest(), checkSummary);
                
                // Post inline comments
                if (config.postInlineComments() && reviewRun.findings() != null) {
                    List<InlineComment> inlineComments = createInlineComments(reviewRun.findings(), config);
                    if (!inlineComments.isEmpty()) {
                        scmAdapter.postInlineComments(reviewRun.repository(), reviewRun.pullRequest(), inlineComments);
                    }
                }
                
                // Post summary comment
                if (config.postSummaryComment()) {
                    String summaryComment = createSummaryComment(scoreSummary, reviewRun, config);
                    scmAdapter.createOrUpdateSummaryComment(reviewRun.repository(), reviewRun.pullRequest(), 
                        "ai-review-" + reviewRun.runId(), summaryComment);
                }
                
                logger.info("Feedback published successfully for run {}", reviewRun.runId());
                
            } catch (Exception e) {
                logger.error("Failed to publish feedback for run {}", reviewRun.runId(), e);
                throw new RuntimeException("Feedback publishing failed", e);
            }
        });
    }
    
    /**
     * Validate input parameters.
     */
    private ValidationResult validateInput(RepoRef repository, PullRef pullRequest, 
                                         String providerKey, ReviewConfig config) {
        List<String> errors = new ArrayList<>();
        
        if (repository == null) {
            errors.add("Repository reference is required");
        } else {
            if (repository.owner() == null || repository.owner().trim().isEmpty()) {
                errors.add("Repository owner is required");
            }
            if (repository.name() == null || repository.name().trim().isEmpty()) {
                errors.add("Repository name is required");
            }
        }
        
        if (pullRequest == null) {
            errors.add("Pull request reference is required");
        } else {
            if (pullRequest.number() == null || pullRequest.number().trim().isEmpty()) {
                errors.add("Pull request number is required");
            }
        }
        
        if (providerKey == null || providerKey.trim().isEmpty()) {
            errors.add("Provider key is required");
        }
        
        if (config == null) {
            errors.add("Review configuration is required");
        }
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    /**
     * Generate unique run ID.
     */
    private String generateRunId() {
        return "run-" + System.currentTimeMillis() + "-" + 
               Integer.toHexString(new Random().nextInt(0x10000));
    }
    
    /**
     * Create empty review run for cases with no findings.
     */
    private ReviewRun createEmptyReviewRun(String runId, RepoRef repository, 
                                         PullRef pullRequest, Instant startTime) {
        Instant endTime = Instant.now();
        Scores perfectScores = new Scores(100.0, 
            Arrays.stream(Dimension.values()).collect(Collectors.toMap(d -> d, d -> 100.0)),
            Map.of());
        
        ReviewRun.Stats stats = new ReviewRun.Stats(
            0,                                                     // filesChanged
            0,                                                     // linesAdded 
            0,                                                     // linesDeleted
            endTime.toEpochMilli() - startTime.toEpochMilli(),     // latencyMs
            0.0                                                    // tokenCostUsd
        );
        
        return new ReviewRun(
            runId, repository, pullRequest, startTime,
            List.of(), stats, List.of(), perfectScores, null
        );
    }

    /**
     * 创建模拟问题发现（用于演示配置使用）。
     */
    private List<Finding> createMockFindings(List<DiffHunk> diffHunks, AiReviewConfig config) {
        if (diffHunks.isEmpty()) {
            return List.of();
        }
        
        // 创建一些示例问题以展示配置的使用
        List<Finding> findings = new ArrayList<>();
        
        findings.add(new Finding(
            "MOCK-SEC-001",
            diffHunks.get(0).filePath(),
            10, 15,
            Severity.MAJOR,
            Dimension.SECURITY,
            "Potential SQL injection vulnerability",
            "Direct string concatenation in SQL query",
            "Use parameterized queries",
            null,
            List.of("mock-analyzer"),
            0.95  // High confidence for threshold filtering tests
        ));
        
        findings.add(new Finding(
            "MOCK-QUA-001", 
            diffHunks.get(0).filePath(),
            25, 30,
            Severity.MINOR,
            Dimension.QUALITY,
            "Code complexity too high",
            "Method has cyclomatic complexity of 15",
            "Consider breaking down into smaller methods",
            null,
            List.of("mock-analyzer"),
            0.70
        ));
        
        // 根据配置过滤低置信度的问题
        return findings.stream()
            .filter(finding -> finding.confidence() >= config.scoring().ignoreConfidenceBelow())
            .toList();
    }

    /**
     * 创建模拟统计信息。
     */
    private ReviewRun.Stats createMockStats(Instant startTime, int filesChanged, int findingsCount) {
        Instant endTime = Instant.now();
        return new ReviewRun.Stats(
            filesChanged,                                         // filesChanged
            Math.max(10, filesChanged * 20),                     // linesAdded
            Math.max(5, filesChanged * 10),                      // linesDeleted
            endTime.toEpochMilli() - startTime.toEpochMilli(),    // latencyMs
            0.25                                                  // tokenCostUsd
        );
    }
    
    /**
     * Create check summary for status update.
     */
    private CheckSummary createCheckSummary(ScoringEngine.ScoreSummary scoreSummary, 
                                          ReviewRun reviewRun, ReviewConfig config) {
        String title = String.format("AI Code Review - %s (%.1f/100)", 
            scoreSummary.grade(), scoreSummary.scores().totalScore());
        
        String conclusion;
        if (scoreSummary.scores().totalScore() >= 90) {
            conclusion = "success";
        } else if (scoreSummary.hasProblems()) {
            conclusion = "failure";
        } else {
            conclusion = "success";
        }
        
        String detailsUrl = config.detailsUrl() != null ? 
            config.detailsUrl().replace("{runId}", reviewRun.runId()) : null;
        
        return new CheckSummary(title, conclusion, detailsUrl);
    }
    
    /**
     * Create inline comments from findings.
     */
    private List<InlineComment> createInlineComments(List<Finding> findings, ReviewConfig config) {
        return findings.stream()
            .filter(f -> f.line() != null)
            .filter(f -> f.confidence() >= config.minConfidenceForComments())
            .limit(config.maxInlineComments())
            .map(this::createInlineCommentFromFinding)
            .collect(Collectors.toList());
    }
    
    /**
     * Create inline comment from finding.
     */
    private InlineComment createInlineCommentFromFinding(Finding finding) {
        String body = String.format("**%s** (%s)\n\n%s\n\n*Confidence: %.0f%% | Source: %s*",
            finding.severity().name(),
            finding.dimension().name(),
            finding.message(),
            finding.confidence() * 100,
            String.join(", ", finding.sources())
        );
        
        return new InlineComment(finding.file(), finding.line(), body, "RIGHT", finding.endLine());
    }
    
    /**
     * Create summary comment.
     */
    private String createSummaryComment(ScoringEngine.ScoreSummary scoreSummary, 
                                      ReviewRun reviewRun, ReviewConfig config) {
        StringBuilder comment = new StringBuilder();
        
        comment.append("## 🤖 AI Code Review Summary\n\n");
        comment.append(String.format("**Overall Grade:** %s (%.1f/100)\n\n", 
            scoreSummary.grade(), scoreSummary.scores().totalScore()));
        
        // Dimension breakdown
        comment.append("### 📊 Quality Dimensions\n\n");
        for (Dimension dimension : Dimension.values()) {
            Double score = scoreSummary.scores().dimensions().get(dimension);
            if (score != null) {
                String emoji = score >= 90 ? "✅" : score >= 70 ? "⚠️" : "❌";
                comment.append(String.format("%s **%s:** %.1f/100\n", 
                    emoji, dimension.name(), score));
            }
        }
        
        // Problem areas
        if (!scoreSummary.problemAreas().isEmpty()) {
            comment.append("\n### ⚠️ Areas for Improvement\n\n");
            for (Dimension problem : scoreSummary.problemAreas()) {
                comment.append("- ").append(problem.name()).append("\n");
            }
        }
        
        // Recommendations
        if (!scoreSummary.recommendations().isEmpty()) {
            comment.append("\n### 💡 Recommendations\n\n");
            for (String recommendation : scoreSummary.recommendations()) {
                comment.append("- ").append(recommendation).append("\n");
            }
        }
        
        // Statistics
        if (reviewRun.stats() != null) {
            comment.append(String.format("\n### 📈 Analysis Statistics\n\n"));
            comment.append(String.format("- **Files Analyzed:** %d\n", reviewRun.stats().analyzedFiles()));
            comment.append(String.format("- **Findings:** %d\n", reviewRun.stats().totalFindings()));
            comment.append(String.format("- **Processing Time:** %dms\n", reviewRun.stats().processingTimeMs()));
        }
        
        comment.append(String.format("\n*Run ID: %s*", reviewRun.runId()));
        
        return comment.toString();
    }
    
    /**
     * Validation result holder.
     */
    private record ValidationResult(boolean isValid, List<String> errors) {}
    
    /**
     * Review orchestration exception.
     */
    public static class ReviewOrchestrationException extends RuntimeException {
        public ReviewOrchestrationException(String message) {
            super(message);
        }
        
        public ReviewOrchestrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Review configuration containing all orchestration settings.
     */
    public static class ReviewConfig {
        private final CodeSplitter.SplittingStrategy splittingStrategy;
        private final FindingAggregator.AggregationConfig aggregationConfig;
        private final ScoringEngine.DimensionMappingConfig dimensionMappingConfig;
        private final ScoringEngine.ScoringConfig scoringConfig;
        private final ReportGenerator.ReportConfig reportConfig;
        private final Map<String, StaticAnalyzer.AnalyzerConfig> analyzerConfigs;
        private final Map<String, AiReviewer.ReviewerConfig> reviewerConfigs;
        private final boolean publishFeedback;
        private final boolean postInlineComments;
        private final boolean postSummaryComment;
        private final boolean generateDetailedReports;
        private final double minConfidenceForComments;
        private final int maxInlineComments;
        private final String detailsUrl;
        
        public ReviewConfig(CodeSplitter.SplittingStrategy splittingStrategy,
                           FindingAggregator.AggregationConfig aggregationConfig,
                           ScoringEngine.DimensionMappingConfig dimensionMappingConfig,
                           ScoringEngine.ScoringConfig scoringConfig,
                           ReportGenerator.ReportConfig reportConfig,
                           Map<String, StaticAnalyzer.AnalyzerConfig> analyzerConfigs,
                           Map<String, AiReviewer.ReviewerConfig> reviewerConfigs,
                           boolean publishFeedback,
                           boolean postInlineComments,
                           boolean postSummaryComment,
                           boolean generateDetailedReports,
                           double minConfidenceForComments,
                           int maxInlineComments,
                           String detailsUrl) {
            this.splittingStrategy = splittingStrategy;
            this.aggregationConfig = aggregationConfig;
            this.dimensionMappingConfig = dimensionMappingConfig;
            this.scoringConfig = scoringConfig;
            this.reportConfig = reportConfig;
            this.analyzerConfigs = analyzerConfigs;
            this.reviewerConfigs = reviewerConfigs;
            this.publishFeedback = publishFeedback;
            this.postInlineComments = postInlineComments;
            this.postSummaryComment = postSummaryComment;
            this.generateDetailedReports = generateDetailedReports;
            this.minConfidenceForComments = minConfidenceForComments;
            this.maxInlineComments = maxInlineComments;
            this.detailsUrl = detailsUrl;
        }
        
        public static ReviewConfig defaultConfig() {
            return new Builder()
                .splittingStrategy(CodeSplitter.SplittingStrategy.intelligent())
                .aggregationConfig(FindingAggregator.AggregationConfig.defaultConfig())
                .dimensionMappingConfig(ScoringEngine.DimensionMappingConfig.defaultConfig())
                .scoringConfig(ScoringEngine.ScoringConfig.defaultConfig())
                .reportConfig(ReportGenerator.ReportConfig.defaultConfig())
                .publishFeedback(true)
                .postInlineComments(true)
                .postSummaryComment(true)
                .generateDetailedReports(false)
                .minConfidenceForComments(0.7)
                .maxInlineComments(20)
                .build();
        }
        
        // Getters
        public CodeSplitter.SplittingStrategy splittingStrategy() { return splittingStrategy; }
        public FindingAggregator.AggregationConfig aggregationConfig() { return aggregationConfig; }
        public ScoringEngine.DimensionMappingConfig dimensionMappingConfig() { return dimensionMappingConfig; }
        public ScoringEngine.ScoringConfig scoringConfig() { return scoringConfig; }
        public ReportGenerator.ReportConfig reportConfig() { return reportConfig; }
        public Map<String, StaticAnalyzer.AnalyzerConfig> analyzerConfigs() { return analyzerConfigs; }
        public Map<String, AiReviewer.ReviewerConfig> reviewerConfigs() { return reviewerConfigs; }
        public boolean publishFeedback() { return publishFeedback; }
        public boolean postInlineComments() { return postInlineComments; }
        public boolean postSummaryComment() { return postSummaryComment; }
        public boolean generateDetailedReports() { return generateDetailedReports; }
        public double minConfidenceForComments() { return minConfidenceForComments; }
        public int maxInlineComments() { return maxInlineComments; }
        public String detailsUrl() { return detailsUrl; }
        
        /**
         * Builder for ReviewConfig.
         */
        public static class Builder {
            private CodeSplitter.SplittingStrategy splittingStrategy = CodeSplitter.SplittingStrategy.intelligent();
            private FindingAggregator.AggregationConfig aggregationConfig = FindingAggregator.AggregationConfig.defaultConfig();
            private ScoringEngine.DimensionMappingConfig dimensionMappingConfig = ScoringEngine.DimensionMappingConfig.defaultConfig();
            private ScoringEngine.ScoringConfig scoringConfig = ScoringEngine.ScoringConfig.defaultConfig();
            private ReportGenerator.ReportConfig reportConfig = ReportGenerator.ReportConfig.defaultConfig();
            private Map<String, StaticAnalyzer.AnalyzerConfig> analyzerConfigs = new HashMap<>();
            private Map<String, AiReviewer.ReviewerConfig> reviewerConfigs = new HashMap<>();
            private boolean publishFeedback = true;
            private boolean postInlineComments = true;
            private boolean postSummaryComment = true;
            private boolean generateDetailedReports = false;
            private double minConfidenceForComments = 0.7;
            private int maxInlineComments = 20;
            private String detailsUrl;
            
            public Builder splittingStrategy(CodeSplitter.SplittingStrategy strategy) {
                this.splittingStrategy = strategy;
                return this;
            }
            
            public Builder aggregationConfig(FindingAggregator.AggregationConfig config) {
                this.aggregationConfig = config;
                return this;
            }
            
            public Builder dimensionMappingConfig(ScoringEngine.DimensionMappingConfig config) {
                this.dimensionMappingConfig = config;
                return this;
            }
            
            public Builder scoringConfig(ScoringEngine.ScoringConfig config) {
                this.scoringConfig = config;
                return this;
            }
            
            public Builder reportConfig(ReportGenerator.ReportConfig config) {
                this.reportConfig = config;
                return this;
            }
            
            public Builder analyzerConfigs(Map<String, StaticAnalyzer.AnalyzerConfig> configs) {
                this.analyzerConfigs = configs;
                return this;
            }
            
            public Builder reviewerConfigs(Map<String, AiReviewer.ReviewerConfig> configs) {
                this.reviewerConfigs = configs;
                return this;
            }
            
            public Builder publishFeedback(boolean publish) {
                this.publishFeedback = publish;
                return this;
            }
            
            public Builder postInlineComments(boolean post) {
                this.postInlineComments = post;
                return this;
            }
            
            public Builder postSummaryComment(boolean post) {
                this.postSummaryComment = post;
                return this;
            }
            
            public Builder generateDetailedReports(boolean generate) {
                this.generateDetailedReports = generate;
                return this;
            }
            
            public Builder minConfidenceForComments(double confidence) {
                this.minConfidenceForComments = confidence;
                return this;
            }
            
            public Builder maxInlineComments(int max) {
                this.maxInlineComments = max;
                return this;
            }
            
            public Builder detailsUrl(String url) {
                this.detailsUrl = url;
                return this;
            }
            
            public ReviewConfig build() {
                return new ReviewConfig(
                    splittingStrategy, aggregationConfig, dimensionMappingConfig,
                    scoringConfig, reportConfig, analyzerConfigs, reviewerConfigs,
                    publishFeedback, postInlineComments, postSummaryComment,
                    generateDetailedReports, minConfidenceForComments, maxInlineComments,
                    detailsUrl
                );
            }
        }
    }
    
    /**
     * Get a review run by its ID.
     * 
     * @param runId the review run ID
     * @return the review run, or null if not found
     */
    public ReviewRun getReviewRun(String runId) {
        // Delegate to ReviewService to get the review run by ID
        logger.debug("Fetching review run with ID: {}", runId);
        return reviewService.findByRunId(runId).orElse(null);
    }
}

package com.ai.reviewer.backend.domain.report;

import com.ai.reviewer.shared.enums.Dimension;
import com.ai.reviewer.shared.enums.Severity;
import com.ai.reviewer.shared.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReportService 单元测试。
 */
class ReportServiceTest {

    private ReportService reportService;
    private ObjectMapper objectMapper;
    private Configuration freemarkerConfig;
    
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        
        // 配置 Freemarker
        freemarkerConfig = new Configuration(Configuration.VERSION_2_3_32);
        freemarkerConfig.setClassForTemplateLoading(this.getClass(), "/templates/");
        freemarkerConfig.setDefaultEncoding("UTF-8");
        
        reportService = new ReportService(freemarkerConfig, objectMapper);
        
        // 设置临时输出目录
        ReflectionTestUtils.setField(reportService, "reportsOutputDir", tempDir.toString());
    }

    @Test
    void testGenerateReports_Complete() throws IOException, TemplateException {
        // Given
        ReviewRun reviewRun = createSampleReviewRun();

        // When
        ReviewRun.Artifacts artifacts = reportService.generateReports(reviewRun);

        // Then
        assertNotNull(artifacts);
        assertNotNull(artifacts.sarifPath());
        assertNotNull(artifacts.reportMdPath());
        assertNotNull(artifacts.reportHtmlPath());
        assertNotNull(artifacts.reportPdfPath());

        // 验证文件是否存在
        assertTrue(Files.exists(Paths.get(artifacts.sarifPath())));
        assertTrue(Files.exists(Paths.get(artifacts.reportMdPath())));
        assertTrue(Files.exists(Paths.get(artifacts.reportHtmlPath())));
        assertTrue(Files.exists(Paths.get(artifacts.reportPdfPath())));
    }

    @Test
    void testGenerateReportJson_ContentValidation() throws IOException, TemplateException {
        // Given
        ReviewRun reviewRun = createSampleReviewRun();

        // When
        ReviewRun.Artifacts artifacts = reportService.generateReports(reviewRun);

        // Then
        String jsonContent = Files.readString(Paths.get(artifacts.sarifPath()).getParent().resolve("report.json"));
        JsonNode jsonNode = objectMapper.readTree(jsonContent);
        
        assertEquals(reviewRun.runId(), jsonNode.get("runId").asText());
        assertEquals(reviewRun.repo().owner(), jsonNode.get("repository").get("owner").asText());
        assertEquals(reviewRun.repo().name(), jsonNode.get("repository").get("name").asText());
        assertEquals(reviewRun.findings().size(), jsonNode.get("findings").size());
    }

    @Test
    void testGenerateMarkdownReport_ContentValidation() throws IOException, TemplateException {
        // Given
        ReviewRun reviewRun = createSampleReviewRun();

        // When
        ReviewRun.Artifacts artifacts = reportService.generateReports(reviewRun);

        // Then
        String markdownContent = Files.readString(Paths.get(artifacts.reportMdPath()));
        
        assertTrue(markdownContent.contains("# AI 代码评审报告"));
        assertTrue(markdownContent.contains(reviewRun.runId()));
        assertTrue(markdownContent.contains(reviewRun.repo().owner() + "/" + reviewRun.repo().name()));
        assertTrue(markdownContent.contains("总分: " + String.format("%.1f", reviewRun.scores().totalScore())));
        assertTrue(markdownContent.contains("Test Security Issue"));
        assertTrue(markdownContent.contains("Test Quality Issue"));
    }

    @Test
    void testGenerateHtmlReport_ContentValidation() throws IOException, TemplateException {
        // Given
        ReviewRun reviewRun = createSampleReviewRun();

        // When
        ReviewRun.Artifacts artifacts = reportService.generateReports(reviewRun);

        // Then
        String htmlContent = Files.readString(Paths.get(artifacts.reportHtmlPath()));
        
        assertTrue(htmlContent.contains("<!DOCTYPE html>"));
        assertTrue(htmlContent.contains("<html lang=\"zh-CN\">"));
        assertTrue(htmlContent.contains("<title>AI Code Review Report</title>"));
        assertTrue(htmlContent.contains("AI 代码评审报告"));
        assertTrue(htmlContent.contains(reviewRun.runId()));
    }

    @Test
    void testGenerateSarifReport_StructureValidation() throws IOException, TemplateException {
        // Given
        ReviewRun reviewRun = createSampleReviewRun();

        // When
        ReviewRun.Artifacts artifacts = reportService.generateReports(reviewRun);

        // Then
        String sarifContent = Files.readString(Paths.get(artifacts.sarifPath()));
        JsonNode sarifNode = objectMapper.readTree(sarifContent);
        
        assertEquals("2.1.0", sarifNode.get("version").asText());
        assertTrue(sarifNode.has("$schema"));
        assertTrue(sarifNode.has("runs"));
        
        JsonNode runs = sarifNode.get("runs");
        assertEquals(1, runs.size());
        
        JsonNode run = runs.get(0);
        assertTrue(run.has("tool"));
        assertTrue(run.has("results"));
        
        JsonNode tool = run.get("tool");
        assertEquals("AI-Reviewer", tool.get("driver").get("name").asText());
        
        JsonNode results = run.get("results");
        assertEquals(reviewRun.findings().size(), results.size());
        
        // 验证第一个结果的结构
        if (results.size() > 0) {
            JsonNode firstResult = results.get(0);
            assertTrue(firstResult.has("ruleId"));
            assertTrue(firstResult.has("level"));
            assertTrue(firstResult.has("message"));
            assertTrue(firstResult.has("locations"));
            assertTrue(firstResult.has("properties"));
            
            JsonNode properties = firstResult.get("properties");
            assertTrue(properties.has("dimension"));
            assertTrue(properties.has("confidence"));
        }
    }

    @Test
    void testSarifSeverityMapping() throws IOException, TemplateException {
        // Given - 创建包含不同严重性的发现
        List<Finding> findings = List.of(
            createFinding("info-finding", Severity.INFO, Dimension.QUALITY, "Info issue", 0.8),
            createFinding("minor-finding", Severity.MINOR, Dimension.SECURITY, "Minor issue", 0.7),
            createFinding("major-finding", Severity.MAJOR, Dimension.MAINTAINABILITY, "Major issue", 0.9),
            createFinding("critical-finding", Severity.CRITICAL, Dimension.PERFORMANCE, "Critical issue", 1.0)
        );
        
        ReviewRun reviewRun = createReviewRunWithFindings(findings);

        // When
        ReviewRun.Artifacts artifacts = reportService.generateReports(reviewRun);

        // Then
        String sarifContent = Files.readString(Paths.get(artifacts.sarifPath()));
        JsonNode sarifNode = objectMapper.readTree(sarifContent);
        
        JsonNode results = sarifNode.get("runs").get(0).get("results");
        
        // 验证严重性映射
        assertEquals("note", results.get(0).get("level").asText());    // INFO -> note
        assertEquals("warning", results.get(1).get("level").asText()); // MINOR -> warning
        assertEquals("error", results.get(2).get("level").asText());   // MAJOR -> error
        assertEquals("error", results.get(3).get("level").asText());   // CRITICAL -> error
    }

    @Test
    void testReportDirectoryCreation() throws IOException, TemplateException {
        // Given
        ReviewRun reviewRun = createSampleReviewRun();
        String expectedDirName = reviewRun.runId().replaceAll("[^a-zA-Z0-9\\-_]", "_");

        // When
        reportService.generateReports(reviewRun);

        // Then
        Path expectedDir = tempDir.resolve(expectedDirName);
        assertTrue(Files.exists(expectedDir));
        assertTrue(Files.isDirectory(expectedDir));
    }

    @Test
    void testEmptyFindings() throws IOException, TemplateException {
        // Given - 没有发现的评审结果
        ReviewRun reviewRun = createReviewRunWithFindings(List.of());

        // When
        ReviewRun.Artifacts artifacts = reportService.generateReports(reviewRun);

        // Then
        assertNotNull(artifacts);
        
        // 验证 SARIF 文件
        String sarifContent = Files.readString(Paths.get(artifacts.sarifPath()));
        JsonNode sarifNode = objectMapper.readTree(sarifContent);
        JsonNode results = sarifNode.get("runs").get(0).get("results");
        assertEquals(0, results.size());
        
        // 验证 Markdown 文件包含正确信息
        String markdownContent = Files.readString(Paths.get(artifacts.reportMdPath()));
        assertTrue(markdownContent.contains("问题总数**: 0"));
    }

    @Test
    void testSpecialCharactersInRunId() throws IOException, TemplateException {
        // Given - 包含特殊字符的运行ID
        String specialRunId = "test-run/with:special*chars<>";
        ReviewRun originalReviewRun = createSampleReviewRun();
        
        // 创建带有新运行ID的ReviewRun
        ReviewRun reviewRun = new ReviewRun(
            specialRunId,
            originalReviewRun.repo(),
            originalReviewRun.pull(),
            originalReviewRun.createdAt(),
            originalReviewRun.providerKeys(),
            originalReviewRun.stats(),
            originalReviewRun.findings(),
            originalReviewRun.scores(),
            originalReviewRun.artifacts()
        );

        // When
        ReviewRun.Artifacts artifacts = reportService.generateReports(reviewRun);

        // Then
        assertNotNull(artifacts);
        
        // 验证目录名被正确清理
        String sanitizedDirName = specialRunId.replaceAll("[^a-zA-Z0-9\\-_]", "_");
        Path expectedDir = tempDir.resolve(sanitizedDirName);
        assertTrue(Files.exists(expectedDir));
    }

    /**
     * 创建示例评审运行结果。
     */
    private ReviewRun createSampleReviewRun() {
        List<Finding> findings = List.of(
            createFinding("sec-001", Severity.MAJOR, Dimension.SECURITY, "Test Security Issue", 0.9),
            createFinding("qual-001", Severity.MINOR, Dimension.QUALITY, "Test Quality Issue", 0.7)
        );
        
        return createReviewRunWithFindings(findings);
    }

    /**
     * 创建带有指定发现的评审运行结果。
     */
    private ReviewRun createReviewRunWithFindings(List<Finding> findings) {
        RepoRef repo = new RepoRef("github", "test-owner", "test-repo", "https://github.com/test-owner/test-repo");
        PullRef pull = new PullRef("123", "123", "Test Pull Request", "feature/test", "main", "abc123", false);
        
        ReviewRun.Stats stats = new ReviewRun.Stats(5, 100, 50, 5000L, 0.25);
        
        Map<Dimension, Double> dimensionScores = Map.of(
            Dimension.SECURITY, 85.0,
            Dimension.QUALITY, 78.0,
            Dimension.MAINTAINABILITY, 92.0,
            Dimension.PERFORMANCE, 88.0,
            Dimension.TEST_COVERAGE, 75.0
        );
        
        Map<Dimension, Double> weights = Map.of(
            Dimension.SECURITY, 0.30,
            Dimension.QUALITY, 0.25,
            Dimension.MAINTAINABILITY, 0.20,
            Dimension.PERFORMANCE, 0.15,
            Dimension.TEST_COVERAGE, 0.10
        );
        
        Scores scores = new Scores(83.5, dimensionScores, weights);
        
        return new ReviewRun(
            "test-run-12345",
            repo,
            pull,
            Instant.now(),
            List.of("gpt-4", "claude-3"),
            stats,
            findings,
            scores,
            null // artifacts will be generated
        );
    }

    /**
     * 创建测试用的 Finding 对象。
     */
    private Finding createFinding(String id, Severity severity, Dimension dimension, String title, double confidence) {
        return new Finding(
            id,
            "src/main/java/TestFile.java",
            10,
            15,
            severity,
            dimension,
            title,
            "This is test evidence for the finding",
            "Consider fixing this issue by applying the suggested changes",
            "- line with issue\n+ line with fix",
            List.of("test-analyzer"),
            confidence
        );
    }
}

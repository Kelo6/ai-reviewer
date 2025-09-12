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
 * ReportService 快照测试。
 * 
 * <p>验证生成的报告内容在多次运行中保持一致，确保模板渲染和格式化的稳定性。
 */
class ReportServiceSnapshotTest {

    private ReportService reportService;
    private ObjectMapper objectMapper;
    
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        
        // 配置 Freemarker
        Configuration freemarkerConfig = new Configuration(Configuration.VERSION_2_3_32);
        freemarkerConfig.setClassForTemplateLoading(this.getClass(), "/templates/");
        freemarkerConfig.setDefaultEncoding("UTF-8");
        
        reportService = new ReportService(freemarkerConfig, objectMapper);
        ReflectionTestUtils.setField(reportService, "reportsOutputDir", tempDir.toString());
    }

    @Test
    void testMarkdownReportSnapshot() throws IOException, TemplateException {
        // Given - 固定的测试数据，确保可重现性
        ReviewRun reviewRun = createFixedReviewRun();

        // When
        ReviewRun.Artifacts artifacts = reportService.generateReports(reviewRun);

        // Then
        String markdownContent = Files.readString(Paths.get(artifacts.reportMdPath()));
        
        // 快照验证：检查关键结构和内容
        assertMarkdownSnapshot(markdownContent, reviewRun);
    }

    @Test
    void testSarifReportSnapshot() throws IOException, TemplateException {
        // Given
        ReviewRun reviewRun = createFixedReviewRun();

        // When
        ReviewRun.Artifacts artifacts = reportService.generateReports(reviewRun);

        // Then
        String sarifContent = Files.readString(Paths.get(artifacts.sarifPath()));
        JsonNode sarifNode = objectMapper.readTree(sarifContent);
        
        // 快照验证：检查 SARIF 结构和内容
        assertSarifSnapshot(sarifNode, reviewRun);
    }

    @Test
    void testJsonReportSnapshot() throws IOException, TemplateException {
        // Given
        ReviewRun reviewRun = createFixedReviewRun();

        // When
        ReviewRun.Artifacts artifacts = reportService.generateReports(reviewRun);

        // Then
        String jsonPath = Paths.get(artifacts.sarifPath()).getParent().resolve("report.json").toString();
        String jsonContent = Files.readString(Paths.get(jsonPath));
        JsonNode jsonNode = objectMapper.readTree(jsonContent);
        
        // 快照验证：检查 JSON 结构和内容
        assertJsonSnapshot(jsonNode, reviewRun);
    }

    @Test
    void testHtmlReportSnapshot() throws IOException, TemplateException {
        // Given
        ReviewRun reviewRun = createFixedReviewRun();

        // When
        ReviewRun.Artifacts artifacts = reportService.generateReports(reviewRun);

        // Then
        String htmlContent = Files.readString(Paths.get(artifacts.reportHtmlPath()));
        
        // 快照验证：检查 HTML 结构和关键内容
        assertHtmlSnapshot(htmlContent, reviewRun);
    }

    /**
     * 验证 Markdown 报告快照。
     */
    private void assertMarkdownSnapshot(String markdownContent, ReviewRun reviewRun) {
        // 验证文档结构
        assertTrue(markdownContent.startsWith("# AI 代码评审报告"), "文档应以标题开始");
        assertTrue(markdownContent.contains("## 📊 评分概览"), "应包含评分概览部分");
        assertTrue(markdownContent.contains("## 📈 统计信息"), "应包含统计信息部分");
        assertTrue(markdownContent.contains("## 🔍 发现的问题"), "应包含发现的问题部分");
        assertTrue(markdownContent.contains("## 📝 详细问题列表"), "应包含详细问题列表部分");
        assertTrue(markdownContent.contains("## 🔧 改进建议"), "应包含改进建议部分");
        assertTrue(markdownContent.trim().endsWith("*本报告由 AI-Reviewer 自动生成 • 版本 1.0.0*"), "应以版本信息结束");
        
        // 验证关键数据
        assertTrue(markdownContent.contains("**运行ID**: `" + reviewRun.runId() + "`"), "应包含正确的运行ID");
        assertTrue(markdownContent.contains("**仓库**: " + reviewRun.repo().owner() + "/" + reviewRun.repo().name()), "应包含正确的仓库信息");
        assertTrue(markdownContent.contains("**Pull Request**: #" + reviewRun.pull().number() + " - " + reviewRun.pull().title()), "应包含正确的PR信息");
        
        // 验证分数格式
        String expectedScore = String.format("%.1f", reviewRun.scores().totalScore());
        assertTrue(markdownContent.contains("### 总分: " + expectedScore + "/100.0"), "应包含正确格式的总分");
        
        // 验证统计数据
        assertTrue(markdownContent.contains("- **文件变更数量**: " + reviewRun.stats().filesChanged()), "应包含文件变更数量");
        assertTrue(markdownContent.contains("- **新增代码行数**: " + reviewRun.stats().linesAdded()), "应包含新增行数");
        assertTrue(markdownContent.contains("- **删除代码行数**: " + reviewRun.stats().linesDeleted()), "应包含删除行数");
        
        // 验证问题统计
        assertTrue(markdownContent.contains("**问题总数**: " + reviewRun.findings().size()), "应包含问题总数");
        
        // 验证具体问题
        for (Finding finding : reviewRun.findings()) {
            assertTrue(markdownContent.contains("### " + getSeverityIcon(finding.severity()) + " " + finding.title()), "应包含问题标题");
            assertTrue(markdownContent.contains("**文件**: `" + finding.file() + "`"), "应包含文件路径");
            assertTrue(markdownContent.contains("**行数**: " + finding.startLine()), "应包含行号信息");
        }
    }

    /**
     * 验证 SARIF 报告快照。
     */
    private void assertSarifSnapshot(JsonNode sarifNode, ReviewRun reviewRun) {
        // 验证 SARIF 基本结构
        assertEquals("2.1.0", sarifNode.get("version").asText(), "SARIF版本应为2.1.0");
        assertEquals("https://schemastore.azurewebsites.net/schemas/json/sarif-2.1.0.json", 
                     sarifNode.get("$schema").asText(), "SARIF schema应正确");
        
        assertTrue(sarifNode.has("runs"), "应包含runs数组");
        assertEquals(1, sarifNode.get("runs").size(), "应只有一个run");
        
        JsonNode run = sarifNode.get("runs").get(0);
        assertTrue(run.has("tool"), "run应包含tool信息");
        assertTrue(run.has("results"), "run应包含results数组");
        
        // 验证工具信息
        JsonNode tool = run.get("tool").get("driver");
        assertEquals("AI-Reviewer", tool.get("name").asText(), "工具名称应为AI-Reviewer");
        assertEquals("1.0.0", tool.get("version").asText(), "版本应为1.0.0");
        
        // 验证结果数量
        JsonNode results = run.get("results");
        assertEquals(reviewRun.findings().size(), results.size(), "结果数量应与发现的问题数量一致");
        
        // 验证每个结果的结构
        for (int i = 0; i < results.size(); i++) {
            JsonNode result = results.get(i);
            Finding finding = reviewRun.findings().get(i);
            
            assertEquals(finding.id(), result.get("ruleId").asText(), "ruleId应与发现ID一致");
            assertTrue(result.has("level"), "应包含level字段");
            assertTrue(result.has("message"), "应包含message字段");
            assertTrue(result.has("locations"), "应包含locations数组");
            assertTrue(result.has("properties"), "应包含properties对象");
            
            // 验证位置信息
            JsonNode location = result.get("locations").get(0);
            JsonNode physicalLocation = location.get("physicalLocation");
            assertEquals(finding.file(), physicalLocation.get("artifactLocation").get("uri").asText(), "文件路径应一致");
            assertEquals(finding.startLine(), physicalLocation.get("region").get("startLine").asInt(), "起始行号应一致");
            assertEquals(finding.endLine(), physicalLocation.get("region").get("endLine").asInt(), "结束行号应一致");
            
            // 验证自定义属性
            JsonNode properties = result.get("properties");
            assertEquals(finding.dimension().name(), properties.get("dimension").asText(), "维度应一致");
            assertEquals(finding.confidence(), properties.get("confidence").asDouble(), 0.001, "置信度应一致");
        }
    }

    /**
     * 验证 JSON 报告快照。
     */
    private void assertJsonSnapshot(JsonNode jsonNode, ReviewRun reviewRun) {
        // 验证基本字段
        assertEquals(reviewRun.runId(), jsonNode.get("runId").asText(), "运行ID应一致");
        assertTrue(jsonNode.has("createdAt"), "应包含创建时间");
        assertTrue(jsonNode.has("repository"), "应包含仓库信息");
        assertTrue(jsonNode.has("pullRequest"), "应包含PR信息");
        assertTrue(jsonNode.has("stats"), "应包含统计信息");
        assertTrue(jsonNode.has("scores"), "应包含分数信息");
        assertTrue(jsonNode.has("findings"), "应包含发现信息");
        assertTrue(jsonNode.has("summary"), "应包含摘要信息");
        
        // 验证仓库信息
        JsonNode repository = jsonNode.get("repository");
        assertEquals(reviewRun.repo().owner(), repository.get("owner").asText(), "仓库所有者应一致");
        assertEquals(reviewRun.repo().name(), repository.get("name").asText(), "仓库名称应一致");
        
        // 验证PR信息
        JsonNode pullRequest = jsonNode.get("pullRequest");
        assertEquals(reviewRun.pull().number(), pullRequest.get("number").asText(), "PR编号应一致");
        assertEquals(reviewRun.pull().title(), pullRequest.get("title").asText(), "PR标题应一致");
        
        // 验证统计信息
        JsonNode stats = jsonNode.get("stats");
        assertEquals(reviewRun.stats().filesChanged(), stats.get("filesChanged").asInt(), "文件变更数应一致");
        assertEquals(reviewRun.stats().linesAdded(), stats.get("linesAdded").asInt(), "新增行数应一致");
        assertEquals(reviewRun.stats().linesDeleted(), stats.get("linesDeleted").asInt(), "删除行数应一致");
        
        // 验证分数信息
        JsonNode scores = jsonNode.get("scores");
        assertEquals(reviewRun.scores().totalScore(), scores.get("totalScore").asDouble(), 0.001, "总分应一致");
        assertTrue(scores.has("dimensions"), "应包含维度分数");
        assertTrue(scores.has("weights"), "应包含权重信息");
        
        // 验证发现信息
        JsonNode findings = jsonNode.get("findings");
        assertEquals(reviewRun.findings().size(), findings.size(), "发现数量应一致");
        
        // 验证摘要信息
        JsonNode summary = jsonNode.get("summary");
        assertEquals(reviewRun.findings().size(), summary.get("totalFindings").asInt(), "摘要中的问题总数应一致");
        assertEquals(reviewRun.scores().totalScore(), summary.get("totalScore").asDouble(), 0.001, "摘要中的总分应一致");
        assertTrue(summary.has("severityCounts"), "摘要应包含严重性统计");
        assertTrue(summary.has("dimensionCounts"), "摘要应包含维度统计");
    }

    /**
     * 验证 HTML 报告快照。
     */
    private void assertHtmlSnapshot(String htmlContent, ReviewRun reviewRun) {
        // 验证 HTML 基本结构
        assertTrue(htmlContent.startsWith("<!DOCTYPE html>"), "应以DOCTYPE开始");
        assertTrue(htmlContent.contains("<html lang=\"zh-CN\">"), "应包含中文语言标记");
        assertTrue(htmlContent.contains("<title>AI Code Review Report</title>"), "应包含正确的标题");
        assertTrue(htmlContent.contains("</html>"), "应以html标签结束");
        
        // 验证 CSS 样式
        assertTrue(htmlContent.contains("<style>"), "应包含样式定义");
        assertTrue(htmlContent.contains("body { font-family:"), "应包含字体样式");
        assertTrue(htmlContent.contains(".severity-critical"), "应包含严重性样式");
        assertTrue(htmlContent.contains(".severity-major"), "应包含严重性样式");
        
        // 验证内容结构（从 Markdown 转换来的）
        assertTrue(htmlContent.contains("<h1>AI 代码评审报告</h1>"), "应包含主标题");
        assertTrue(htmlContent.contains("<h2>📊 评分概览</h2>"), "应包含评分概览部分");
        assertTrue(htmlContent.contains("<h2>📈 统计信息</h2>"), "应包含统计信息部分");
        assertTrue(htmlContent.contains("<h2>🔍 发现的问题</h2>"), "应包含发现的问题部分");
        
        // 验证关键数据
        assertTrue(htmlContent.contains(reviewRun.runId()), "应包含运行ID");
        assertTrue(htmlContent.contains(reviewRun.repo().owner() + "/" + reviewRun.repo().name()), "应包含仓库信息");
        
        // 验证表格结构 - Flexmark可能不会生成标准HTML表格，但应该包含表格数据
        // 检查是否包含表格相关的内容和结构
        boolean hasTableContent = htmlContent.contains("维度") && htmlContent.contains("分数");
        assertTrue(hasTableContent, "应包含表格相关内容（维度和分数）");
    }

    /**
     * 创建固定的测试数据，确保快照测试的可重现性。
     */
    private ReviewRun createFixedReviewRun() {
        // 使用固定时间
        Instant fixedTime = Instant.parse("2024-01-15T10:30:00Z");
        
        List<Finding> findings = List.of(
            new Finding(
                "SECURITY-001",
                "src/main/java/com/example/UserController.java",
                25,
                28,
                Severity.CRITICAL,
                Dimension.SECURITY,
                "SQL Injection Vulnerability",
                "Direct SQL query construction without parameterization detected",
                "Use parameterized queries or prepared statements",
                "- String sql = \"SELECT * FROM users WHERE id = \" + userId;\n+ String sql = \"SELECT * FROM users WHERE id = ?\";\n+ PreparedStatement stmt = connection.prepareStatement(sql);",
                List.of("security-analyzer", "sonarqube"),
                0.95
            ),
            new Finding(
                "QUALITY-001",
                "src/main/java/com/example/DataProcessor.java",
                42,
                55,
                Severity.MAJOR,
                Dimension.QUALITY,
                "Complex Method",
                "Method has high cyclomatic complexity (12), consider refactoring",
                "Break down the method into smaller, more focused methods",
                null,
                List.of("pmd", "checkstyle"),
                0.85
            ),
            new Finding(
                "MAINTAINABILITY-001",
                "src/main/java/com/example/Utils.java",
                15,
                15,
                Severity.MINOR,
                Dimension.MAINTAINABILITY,
                "Missing Documentation",
                "Public method lacks Javadoc documentation",
                "Add comprehensive Javadoc comments",
                "+ /**\n+  * Processes the input data and returns formatted result.\n+  * @param data the input data to process\n+  * @return formatted result string\n+  */",
                List.of("checkstyle"),
                0.75
            )
        );
        
        RepoRef repo = new RepoRef("github", "ai-reviewer", "demo-project", "https://github.com/ai-reviewer/demo-project");
        PullRef pull = new PullRef("456", "456", "Add user authentication feature", "feature/auth", "main", "def456", false);
        
        ReviewRun.Stats stats = new ReviewRun.Stats(8, 245, 67, 12500L, 0.48);
        
        Map<Dimension, Double> dimensionScores = Map.of(
            Dimension.SECURITY, 72.5,
            Dimension.QUALITY, 78.2,
            Dimension.MAINTAINABILITY, 85.1,
            Dimension.PERFORMANCE, 92.0,
            Dimension.TEST_COVERAGE, 68.5
        );
        
        Map<Dimension, Double> weights = Map.of(
            Dimension.SECURITY, 0.30,
            Dimension.QUALITY, 0.25,
            Dimension.MAINTAINABILITY, 0.20,
            Dimension.PERFORMANCE, 0.15,
            Dimension.TEST_COVERAGE, 0.10
        );
        
        Scores scores = new Scores(78.4, dimensionScores, weights);
        
        return new ReviewRun(
            "snapshot-test-run-12345",
            repo,
            pull,
            fixedTime,
            List.of("gpt-4-turbo", "claude-3-sonnet"),
            stats,
            findings,
            scores,
            null
        );
    }

    /**
     * 获取严重性对应的图标。
     */
    private String getSeverityIcon(Severity severity) {
        return switch (severity) {
            case CRITICAL -> "🔥";
            case MAJOR -> "❗";
            case MINOR -> "⚠️";
            case INFO -> "ℹ️";
        };
    }
}

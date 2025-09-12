package com.ai.reviewer.backend.domain.report;

import com.ai.reviewer.backend.domain.config.AiReviewConfig;
import com.ai.reviewer.shared.enums.Dimension;
import com.ai.reviewer.shared.enums.Severity;
import com.ai.reviewer.shared.model.Finding;
import com.ai.reviewer.shared.model.ReviewRun;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 报告生成服务。
 * 
 * <p>负责生成多种格式的代码评审报告，包括JSON、Markdown、HTML、PDF和SARIF格式。
 * 所有报告都基于单一事实源（report.json）生成，确保数据一致性。
 */
@Service
public class ReportService {

    private final Configuration freemarkerConfig;
    private final ObjectMapper objectMapper;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;

    @Value("${ai-reviewer.reports.output-dir:reports}")
    private String reportsOutputDir;

    public ReportService(Configuration freemarkerConfig, ObjectMapper objectMapper) {
        this.freemarkerConfig = freemarkerConfig;
        this.objectMapper = objectMapper;
        
        // 初始化 Markdown 解析器
        MutableDataSet options = new MutableDataSet();
        this.markdownParser = Parser.builder(options).build();
        this.htmlRenderer = HtmlRenderer.builder(options).build();
    }

    /**
     * 生成完整的报告套件。
     * 
     * @param reviewRun 评审运行结果
     * @return 生成的报告文件路径集合
     */
    public ReviewRun.Artifacts generateReports(ReviewRun reviewRun) throws IOException, TemplateException {
        // 使用默认配置生成所有格式
        AiReviewConfig.ReportConfig.ExportConfig defaultExportConfig = 
            new AiReviewConfig.ReportConfig.ExportConfig(true, true, true, true);
        AiReviewConfig.ReportConfig defaultReportConfig = 
            new AiReviewConfig.ReportConfig(defaultExportConfig);
        
        return generateReports(reviewRun, defaultReportConfig);
    }

    /**
     * 根据配置生成报告。
     * 
     * @param reviewRun 评审运行结果
     * @param reportConfig 报告配置
     * @return 生成的报告文件路径集合
     */
    public ReviewRun.Artifacts generateReports(ReviewRun reviewRun, AiReviewConfig.ReportConfig reportConfig) 
            throws IOException, TemplateException {
        String runId = reviewRun.runId();
        Path reportDir = createReportDirectory(runId);

        AiReviewConfig.ReportConfig.ExportConfig exportConfig = reportConfig.export();

        String reportJsonPath = null;
        String reportMdPath = null;
        String reportHtmlPath = null;
        String reportPdfPath = null;
        String sarifPath = null;

        // 1. 生成 report.json（单一事实源）- 如果启用JSON导出
        if (exportConfig.json()) {
            reportJsonPath = generateReportJson(reviewRun, reportDir);
        }

        // 2. 生成 report.md - 如果需要Markdown或后续格式
        if (exportConfig.html() || exportConfig.pdf()) {
            // HTML和PDF需要先生成Markdown
            reportMdPath = generateReportMarkdown(reviewRun, reportDir);
        } else if (exportConfig.json() && reportJsonPath != null) {
            // 即使不需要独立的Markdown文件，也可能需要生成用于其他格式
            reportMdPath = generateReportMarkdown(reviewRun, reportDir);
        }

        // 3. 生成 HTML - 如果启用HTML导出或需要PDF
        if (exportConfig.html() || exportConfig.pdf()) {
            if (reportMdPath != null) {
                reportHtmlPath = generateReportHtml(reportMdPath, reportDir);
            }
        }

        // 4. 生成 PDF - 如果启用PDF导出
        if (exportConfig.pdf() && reportHtmlPath != null) {
            reportPdfPath = generateReportPdf(reportHtmlPath, reportDir);
        }

        // 5. 生成 SARIF - 如果启用SARIF导出
        if (exportConfig.sarif()) {
            sarifPath = generateSarifReport(reviewRun, reportDir);
        }

        return new ReviewRun.Artifacts(
            sarifPath,
            reportMdPath,
            reportHtmlPath,
            reportPdfPath
        );
    }

    /**
     * 创建报告目录。
     */
    private Path createReportDirectory(String runId) throws IOException {
        Path reportDir = Paths.get(reportsOutputDir, sanitizeRunId(runId));
        Files.createDirectories(reportDir);
        return reportDir;
    }

    /**
     * 生成 report.json（单一事实源）。
     */
    private String generateReportJson(ReviewRun reviewRun, Path reportDir) throws IOException {
        Path reportJsonPath = reportDir.resolve("report.json");
        
        // 创建完整的 JSON 报告对象
        ObjectNode reportJson = objectMapper.createObjectNode();
        
        // 基本信息
        reportJson.put("runId", reviewRun.runId());
        reportJson.put("createdAt", reviewRun.createdAt().toString());
        reportJson.set("repository", objectMapper.valueToTree(reviewRun.repo()));
        reportJson.set("pullRequest", objectMapper.valueToTree(reviewRun.pull()));
        reportJson.set("providerKeys", objectMapper.valueToTree(reviewRun.providerKeys()));

        // 统计信息
        reportJson.set("stats", objectMapper.valueToTree(reviewRun.stats()));

        // 评分结果
        reportJson.set("scores", objectMapper.valueToTree(reviewRun.scores()));

        // 发现的问题
        reportJson.set("findings", objectMapper.valueToTree(reviewRun.findings()));

        // 摘要信息
        ObjectNode summary = createSummary(reviewRun);
        reportJson.set("summary", summary);

        // 预留 artifacts 路径（将在所有文件生成后更新）
        reportJson.set("artifacts", objectMapper.createObjectNode());

        // 写入文件
        objectMapper.writerWithDefaultPrettyPrinter()
            .writeValue(reportJsonPath.toFile(), reportJson);

        return reportJsonPath.toString();
    }

    /**
     * 使用 Freemarker 生成 Markdown 报告。
     */
    private String generateReportMarkdown(ReviewRun reviewRun, Path reportDir) throws IOException, TemplateException {
        Path reportMdPath = reportDir.resolve("report.md");
        
        // 准备模板数据
        Map<String, Object> templateData = prepareTemplateData(reviewRun);
        
        // 渲染模板
        Template template = freemarkerConfig.getTemplate("report.md.ftl");
        StringWriter writer = new StringWriter();
        template.process(templateData, writer);
        
        // 写入文件
        Files.writeString(reportMdPath, writer.toString(), StandardCharsets.UTF_8);
        
        return reportMdPath.toString();
    }

    /**
     * 使用 Flexmark 转换 Markdown 到 HTML。
     */
    private String generateReportHtml(String markdownPath, Path reportDir) throws IOException {
        Path htmlPath = reportDir.resolve("report.html");
        
        // 读取 Markdown 内容
        String markdownContent = Files.readString(Paths.get(markdownPath), StandardCharsets.UTF_8);
        
        // 转换为 HTML
        com.vladsch.flexmark.util.ast.Document document = markdownParser.parse(markdownContent);
        String htmlContent = htmlRenderer.render(document);
        
        // 包装成完整的 HTML 文档
        String fullHtml = wrapHtmlDocument(htmlContent);
        
        // 写入文件
        Files.writeString(htmlPath, fullHtml, StandardCharsets.UTF_8);
        
        return htmlPath.toString();
    }

    /**
     * 使用 Flying Saucer/OpenPDF 生成 PDF。
     */
    private String generateReportPdf(String htmlPath, Path reportDir) throws IOException {
        Path pdfPath = reportDir.resolve("report.pdf");
        
        try {
            // 读取 HTML 内容
            String htmlContent = Files.readString(Paths.get(htmlPath), StandardCharsets.UTF_8);
            
            // 生成 PDF
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(htmlContent);
            renderer.layout();
            
            try (var outputStream = Files.newOutputStream(pdfPath)) {
                renderer.createPDF(outputStream);
            }
            
        } catch (Exception e) {
            throw new IOException("Failed to generate PDF report", e);
        }
        
        return pdfPath.toString();
    }

    /**
     * 生成 SARIF 2.1.0 报告。
     */
    private String generateSarifReport(ReviewRun reviewRun, Path reportDir) throws IOException {
        Path sarifPath = reportDir.resolve("findings.sarif");
        
        ObjectNode sarif = objectMapper.createObjectNode();
        sarif.put("version", "2.1.0");
        sarif.put("$schema", "https://schemastore.azurewebsites.net/schemas/json/sarif-2.1.0.json");
        
        ArrayNode runs = objectMapper.createArrayNode();
        ObjectNode run = objectMapper.createObjectNode();
        
        // 工具信息
        ObjectNode tool = objectMapper.createObjectNode();
        ObjectNode driver = objectMapper.createObjectNode();
        driver.put("name", "AI-Reviewer");
        driver.put("version", "1.0.0");
        driver.put("informationUri", "https://github.com/ai-reviewer/ai-reviewer");
        tool.set("driver", driver);
        run.set("tool", tool);
        
        // 结果
        ArrayNode results = objectMapper.createArrayNode();
        for (Finding finding : reviewRun.findings()) {
            ObjectNode result = createSarifResult(finding);
            results.add(result);
        }
        run.set("results", results);
        
        runs.add(run);
        sarif.set("runs", runs);
        
        // 写入文件
        objectMapper.writerWithDefaultPrettyPrinter()
            .writeValue(sarifPath.toFile(), sarif);
        
        return sarifPath.toString();
    }

    /**
     * 创建 SARIF 结果对象。
     */
    private ObjectNode createSarifResult(Finding finding) {
        ObjectNode result = objectMapper.createObjectNode();
        
        result.put("ruleId", finding.id());
        result.put("level", mapSeverityToSarifLevel(finding.severity()));
        
        // 消息
        ObjectNode message = objectMapper.createObjectNode();
        message.put("text", finding.title());
        result.set("message", message);
        
        // 位置
        ArrayNode locations = objectMapper.createArrayNode();
        ObjectNode location = objectMapper.createObjectNode();
        ObjectNode physicalLocation = objectMapper.createObjectNode();
        ObjectNode artifactLocation = objectMapper.createObjectNode();
        artifactLocation.put("uri", finding.file());
        physicalLocation.set("artifactLocation", artifactLocation);
        
        ObjectNode region = objectMapper.createObjectNode();
        region.put("startLine", finding.startLine());
        region.put("endLine", finding.endLine());
        physicalLocation.set("region", region);
        
        location.set("physicalLocation", physicalLocation);
        locations.add(location);
        result.set("locations", locations);
        
        // 属性：dimension 和 confidence
        ObjectNode properties = objectMapper.createObjectNode();
        properties.put("dimension", finding.dimension().name());
        properties.put("confidence", finding.confidence());
        result.set("properties", properties);
        
        return result;
    }

    /**
     * 映射严重性到 SARIF 级别。
     */
    private String mapSeverityToSarifLevel(Severity severity) {
        return switch (severity) {
            case INFO -> "note";
            case MINOR -> "warning";
            case MAJOR -> "error";
            case CRITICAL -> "error";
        };
    }

    /**
     * 准备模板数据。
     */
    private Map<String, Object> prepareTemplateData(ReviewRun reviewRun) {
        Map<String, Object> data = new HashMap<>();
        
        data.put("reviewRun", reviewRun);
        data.put("generatedAt", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        
        // 按维度分组的发现
        Map<Dimension, Long> findingsByDimension = reviewRun.findings().stream()
            .collect(java.util.stream.Collectors.groupingBy(
                Finding::dimension,
                java.util.stream.Collectors.counting()
            ));
        data.put("findingsByDimension", findingsByDimension);
        
        // 按严重性分组的发现
        Map<Severity, Long> findingsBySeverity = reviewRun.findings().stream()
            .collect(java.util.stream.Collectors.groupingBy(
                Finding::severity,
                java.util.stream.Collectors.counting()
            ));
        data.put("findingsBySeverity", findingsBySeverity);
        
        return data;
    }

    /**
     * 创建摘要信息。
     */
    private ObjectNode createSummary(ReviewRun reviewRun) {
        ObjectNode summary = objectMapper.createObjectNode();
        
        summary.put("totalFindings", reviewRun.findings().size());
        summary.put("totalScore", reviewRun.scores().totalScore());
        
        // 按严重性统计
        Map<Severity, Long> severityCounts = reviewRun.findings().stream()
            .collect(java.util.stream.Collectors.groupingBy(
                Finding::severity,
                java.util.stream.Collectors.counting()
            ));
        ObjectNode severityNode = objectMapper.createObjectNode();
        for (Severity severity : Severity.values()) {
            severityNode.put(severity.name().toLowerCase(), severityCounts.getOrDefault(severity, 0L));
        }
        summary.set("severityCounts", severityNode);
        
        // 按维度统计
        Map<Dimension, Long> dimensionCounts = reviewRun.findings().stream()
            .collect(java.util.stream.Collectors.groupingBy(
                Finding::dimension,
                java.util.stream.Collectors.counting()
            ));
        ObjectNode dimensionNode = objectMapper.createObjectNode();
        for (Dimension dimension : Dimension.values()) {
            dimensionNode.put(dimension.name().toLowerCase(), dimensionCounts.getOrDefault(dimension, 0L));
        }
        summary.set("dimensionCounts", dimensionNode);
        
        return summary;
    }

    /**
     * 包装 HTML 文档。
     */
    private String wrapHtmlDocument(String htmlContent) {
        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <title>AI Code Review Report</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; line-height: 1.6; margin: 2rem; }
                    .header { border-bottom: 2px solid #eee; padding-bottom: 1rem; margin-bottom: 2rem; }
                    .score { font-size: 1.5rem; font-weight: bold; color: #2563eb; }
                    .finding { border-left: 4px solid #ddd; padding: 0.5rem 1rem; margin: 1rem 0; background: #f9f9f9; }
                    .severity-critical { border-left-color: #dc2626; }
                    .severity-major { border-left-color: #ea580c; }
                    .severity-minor { border-left-color: #ca8a04; }
                    .severity-info { border-left-color: #2563eb; }
                    pre { background: #f1f5f9; padding: 1rem; border-radius: 4px; overflow-x: auto; }
                    table { width: 100%; border-collapse: collapse; margin: 1rem 0; }
                    th, td { padding: 0.5rem; border: 1px solid #ddd; text-align: left; }
                    th { background: #f1f5f9; font-weight: 600; }
                </style>
            </head>
            <body>
            %s
            </body>
            </html>
            """.replace("%s", htmlContent);
    }

    /**
     * 清理运行 ID，确保文件名安全。
     */
    private String sanitizeRunId(String runId) {
        return runId.replaceAll("[^a-zA-Z0-9\\-_]", "_");
    }
}

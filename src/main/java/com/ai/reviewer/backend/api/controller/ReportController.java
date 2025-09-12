package com.ai.reviewer.backend.api.controller;

import com.ai.reviewer.backend.api.exception.ReportNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 报告文件下载控制器。
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {
    
    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);
    
    @Value("${ai-reviewer.reports.output-dir:reports}")
    private String reportsOutputDir;
    
    /**
     * 下载报告文件。
     * 
     * @param runId 运行ID
     * @param extension 文件扩展名 (md|html|pdf|json|sarif)
     * @return 报告文件
     */
    @GetMapping("/{runId}.{extension}")
    public ResponseEntity<Resource> downloadReport(
            @PathVariable String runId, 
            @PathVariable String extension) {
        
        logger.debug("Downloading report: runId={}, extension={}", runId, extension);
        
        try {
            // 验证扩展名
            if (!isValidExtension(extension)) {
                throw new ReportNotFoundException("不支持的文件格式: " + extension);
            }
            
            // 构建文件路径
            Path reportPath = buildReportPath(runId, extension);
            
            // 检查文件是否存在
            if (!Files.exists(reportPath)) {
                throw new ReportNotFoundException(runId, extension);
            }
            
            // 创建资源
            Resource resource = new UrlResource(reportPath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ReportNotFoundException("报告文件不可读: " + reportPath);
            }
            
            // 确定内容类型和文件名
            MediaType contentType = determineContentType(extension);
            String fileName = String.format("report-%s.%s", runId, extension);
            
            logger.info("Successfully serving report: runId={}, extension={}, file={}", 
                runId, extension, reportPath);
            
            return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                    String.format("attachment; filename=\"%s\"", fileName))
                .body(resource);
                
        } catch (ReportNotFoundException e) {
            logger.warn("Report not found: runId={}, extension={}", runId, extension);
            throw e;
        } catch (Exception e) {
            logger.error("Failed to serve report: runId={}, extension={}", runId, extension, e);
            throw new ReportNotFoundException("读取报告文件失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 验证文件扩展名是否有效。
     */
    private boolean isValidExtension(String extension) {
        return extension != null && switch (extension.toLowerCase()) {
            case "md", "html", "pdf", "json", "sarif" -> true;
            default -> false;
        };
    }
    
    /**
     * 构建报告文件路径。
     */
    private Path buildReportPath(String runId, String extension) {
        // 清理runId，确保路径安全
        String sanitizedRunId = runId.replaceAll("[^a-zA-Z0-9\\-_]", "_");
        
        String fileName = switch (extension.toLowerCase()) {
            case "md" -> "report.md";
            case "html" -> "report.html";
            case "pdf" -> "report.pdf";
            case "json" -> "report.json";
            case "sarif" -> "findings.sarif";
            default -> throw new IllegalArgumentException("Unsupported extension: " + extension);
        };
        
        return Paths.get(reportsOutputDir, sanitizedRunId, fileName);
    }
    
    /**
     * 确定内容类型。
     */
    private MediaType determineContentType(String extension) {
        return switch (extension.toLowerCase()) {
            case "md" -> MediaType.TEXT_MARKDOWN;
            case "html" -> MediaType.TEXT_HTML;
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "json", "sarif" -> MediaType.APPLICATION_JSON;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}

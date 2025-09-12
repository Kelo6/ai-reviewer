package com.ai.reviewer.backend.web;

import com.ai.reviewer.backend.domain.report.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web报告控制器 - 处理报告预览和下载。
 */
@Controller("webReportController")
public class ReportController {
    
    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);
    
    private final ReportService reportService;
    
    @Autowired
    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }
    
    /**
     * 报告预览页面。
     */
    @GetMapping("/reports/{runId}")
    public String reportPreview(@PathVariable String runId, Model model) {
        logger.debug("Rendering report preview: runId={}", runId);
        
        try {
            String htmlContent = getHtmlReportContent(runId);
            model.addAttribute("runId", runId);
            model.addAttribute("htmlContent", htmlContent);
            return "report-preview";
            
        } catch (ReportNotFoundException e) {
            logger.warn("Report not found for preview: runId={}", runId);
            model.addAttribute("error", e.getMessage());
            model.addAttribute("runId", runId);
            return "error/404";
            
        } catch (Exception e) {
            logger.error("Error rendering report preview: runId={}", runId, e);
            model.addAttribute("error", "加载报告预览失败: " + e.getMessage());
            model.addAttribute("runId", runId);
            return "error/500";
        }
    }
    
    /**
     * 下载报告文件。
     */
    @GetMapping("/download/{runId}/{extension}")
    public ResponseEntity<ByteArrayResource> downloadReport(
            @PathVariable String runId, 
            @PathVariable String extension) {
        
        logger.debug("Downloading report: runId={}, extension={}", runId, extension);
        
        try {
            ReportDownload download = downloadReportFile(runId, extension);
            
            ByteArrayResource resource = new ByteArrayResource(download.content());
            
            return ResponseEntity.ok()
                .contentType(download.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                    "attachment; filename=\"" + download.filename() + "\"")
                .body(resource);
                
        } catch (ReportNotFoundException e) {
            logger.warn("Report not found for download: runId={}, extension={}", runId, extension);
            return ResponseEntity.notFound().build();
            
        } catch (Exception e) {
            logger.error("Error downloading report: runId={}, extension={}", runId, extension, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取HTML报告内容用于预览。
     */
    private String getHtmlReportContent(String runId) {
        // 对于演示，返回模拟的HTML报告内容
        if ("run-001".equals(runId)) {
            return createMockHtmlReport(runId);
        }
        
        throw new ReportNotFoundException("HTML报告文件未找到");
    }
    
    /**
     * 下载报告文件。
     */
    private ReportDownload downloadReportFile(String runId, String extension) {
        // 对于演示，创建模拟的报告文件
        if ("run-001".equals(runId)) {
            byte[] content = createMockReportContent(runId, extension);
            MediaType mediaType = determineMediaType(extension);
            String filename = String.format("report-%s.%s", runId, extension);
            return new ReportDownload(content, mediaType, filename);
        }
        
        throw new ReportNotFoundException("报告文件未找到");
    }
    
    /**
     * 创建模拟HTML报告。
     */
    private String createMockHtmlReport(String runId) {
        return String.format("""
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <title>代码评审报告 - %s</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; margin: 40px; }
                    .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 30px; border-radius: 8px; margin-bottom: 30px; }
                    .metric-card { background: #f8f9fa; border: 1px solid #dee2e6; border-radius: 6px; padding: 20px; margin: 10px 0; }
                    .finding { border-left: 4px solid #dc3545; padding: 15px; margin: 10px 0; background: #fff5f5; }
                    .finding.warning { border-left-color: #ffc107; background: #fffdf0; }
                    .code-block { background: #f1f3f4; padding: 15px; border-radius: 4px; font-family: monospace; overflow-x: auto; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>🤖 AI代码评审报告</h1>
                    <p>运行ID: %s | 生成时间: %s</p>
                </div>
                
                <div class="metric-card">
                    <h2>📊 总体评分: 86.0/100</h2>
                    <ul>
                        <li>🔐 安全性: 85.0</li>
                        <li>✨ 质量: 90.0</li>
                        <li>🔧 可维护性: 88.0</li>
                        <li>⚡ 性能: 92.0</li>
                        <li>🧪 测试覆盖: 75.0</li>
                    </ul>
                </div>
                
                <div class="metric-card">
                    <h2>📈 变更统计</h2>
                    <ul>
                        <li>文件变更: 3个</li>
                        <li>新增行数: +120</li>
                        <li>删除行数: -25</li>
                        <li>发现问题: 2个</li>
                    </ul>
                </div>
                
                <h2>🔍 发现的问题</h2>
                
                <div class="finding">
                    <h3>🔴 重要问题: 安全漏洞</h3>
                    <p><strong>文件:</strong> src/auth/User.java (第45-48行)</p>
                    <p><strong>描述:</strong> 检测到弱密码哈希算法</p>
                    <p><strong>建议:</strong> 使用bcrypt进行密码哈希</p>
                    <div class="code-block">
- password = md5(password)
+ password = bcrypt.hash(password)
                    </div>
                    <p><strong>可信度:</strong> 90%%</p>
                </div>
                
                <div class="finding warning">
                    <h3>🟡 一般问题: 潜在空指针</h3>
                    <p><strong>文件:</strong> src/utils/Helper.java (第23-25行)</p>
                    <p><strong>描述:</strong> 变量可能为空</p>
                    <p><strong>建议:</strong> 在访问前添加空值检查</p>
                    <div class="code-block">
if (obj != null) {
    obj.method();
}
                    </div>
                    <p><strong>可信度:</strong> 80%%</p>
                </div>
                
                <div class="metric-card">
                    <h2>📋 报告信息</h2>
                    <p>此报告由AI代码评审工具自动生成，用于帮助开发者提高代码质量和安全性。</p>
                    <p>如有疑问，请联系开发团队。</p>
                </div>
            </body>
            </html>
            """, runId, runId, java.time.LocalDateTime.now().toString());
    }
    
    /**
     * 创建模拟报告内容。
     */
    private byte[] createMockReportContent(String runId, String extension) {
        return switch (extension.toLowerCase()) {
            case "md" -> createMarkdownReport(runId).getBytes();
            case "html" -> createMockHtmlReport(runId).getBytes();
            case "json", "sarif" -> createJsonReport(runId).getBytes();
            case "pdf" -> "PDF content would be here".getBytes(); // 简化的PDF内容
            default -> String.format("Report for %s in %s format", runId, extension).getBytes();
        };
    }
    
    /**
     * 创建Markdown报告。
     */
    private String createMarkdownReport(String runId) {
        return String.format("""
            # 🤖 AI代码评审报告
            
            **运行ID:** %s  
            **生成时间:** %s
            
            ## 📊 总体评分: 86.0/100
            
            - 🔐 **安全性:** 85.0
            - ✨ **质量:** 90.0  
            - 🔧 **可维护性:** 88.0
            - ⚡ **性能:** 92.0
            - 🧪 **测试覆盖:** 75.0
            
            ## 📈 变更统计
            
            - **文件变更:** 3个
            - **新增行数:** +120
            - **删除行数:** -25
            - **发现问题:** 2个
            
            ## 🔍 发现的问题
            
            ### 🔴 重要问题: 安全漏洞
            
            - **文件:** src/auth/User.java (第45-48行)
            - **描述:** 检测到弱密码哈希算法
            - **建议:** 使用bcrypt进行密码哈希
            - **可信度:** 90%%
            
            ```diff
            - password = md5(password)
            + password = bcrypt.hash(password)
            ```
            
            ### 🟡 一般问题: 潜在空指针
            
            - **文件:** src/utils/Helper.java (第23-25行)
            - **描述:** 变量可能为空
            - **建议:** 在访问前添加空值检查
            - **可信度:** 80%%
            
            ```java
            if (obj != null) {
                obj.method();
            }
            ```
            
            ---
            
            *此报告由AI代码评审工具自动生成*
            """, runId, java.time.LocalDateTime.now().toString());
    }
    
    /**
     * 创建JSON报告。
     */
    private String createJsonReport(String runId) {
        return String.format("""
            {
              "runId": "%s",
              "timestamp": "%s",
              "totalScore": 86.0,
              "scores": {
                "security": 85.0,
                "quality": 90.0,
                "maintainability": 88.0,
                "performance": 92.0,
                "testCoverage": 75.0
              },
              "statistics": {
                "filesChanged": 3,
                "linesAdded": 120,
                "linesDeleted": 25,
                "issuesFound": 2
              },
              "findings": [
                {
                  "id": "find-1",
                  "file": "src/auth/User.java",
                  "startLine": 45,
                  "endLine": 48,
                  "severity": "MAJOR",
                  "dimension": "SECURITY",
                  "title": "安全漏洞",
                  "evidence": "检测到弱密码哈希算法",
                  "suggestion": "使用bcrypt进行密码哈希",
                  "confidence": 0.9
                },
                {
                  "id": "find-2", 
                  "file": "src/utils/Helper.java",
                  "startLine": 23,
                  "endLine": 25,
                  "severity": "MINOR",
                  "dimension": "QUALITY",
                  "title": "潜在空指针",
                  "evidence": "变量可能为空",
                  "suggestion": "在访问前添加空值检查",
                  "confidence": 0.8
                }
              ]
            }
            """, runId, java.time.Instant.now().toString());
    }
    
    /**
     * 确定媒体类型。
     */
    private MediaType determineMediaType(String extension) {
        return switch (extension.toLowerCase()) {
            case "md" -> MediaType.TEXT_MARKDOWN;
            case "html" -> MediaType.TEXT_HTML;
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "json", "sarif" -> MediaType.APPLICATION_JSON;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
    
    /**
     * 报告下载结果。
     */
    public record ReportDownload(
        byte[] content,
        MediaType mediaType,
        String filename
    ) {}
    
    /**
     * 报告未找到异常。
     */
    public static class ReportNotFoundException extends RuntimeException {
        public ReportNotFoundException(String message) {
            super(message);
        }
    }
}

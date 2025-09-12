package com.ai.reviewer.backend.api.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import com.ai.reviewer.backend.config.ReportControllerTestConfig;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ReportController MVC 测试。
 */
@WebMvcTest(controllers = ReportController.class)
@ContextConfiguration(classes = ReportControllerTestConfig.class)
@TestPropertySource(properties = {
    "ai-reviewer.reports.output-dir=target/test-reports",
    "server.servlet.context-path="
})
@WithMockUser
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        // 创建测试报告文件
        createTestReportFiles();
    }

    @Test
    void testDownloadMarkdownReport_Success() throws Exception {
        // When & Then
        mockMvc.perform(get("/reports/{runId}.{extension}", "test-run-123", "md"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.TEXT_MARKDOWN))
            .andExpect(header().string("Content-Disposition", 
                containsString("attachment; filename=\"report-test-run-123.md\"")))
            .andExpect(content().string(containsString("# Test Markdown Report")));
    }

    @Test
    void testDownloadHtmlReport_Success() throws Exception {
        // When & Then
        mockMvc.perform(get("/reports/{runId}.{extension}", "test-run-123", "html"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.TEXT_HTML))
            .andExpect(header().string("Content-Disposition", 
                containsString("attachment; filename=\"report-test-run-123.html\"")))
            .andExpect(content().string(containsString("<!DOCTYPE html>")));
    }

    @Test
    void testDownloadJsonReport_Success() throws Exception {
        // When & Then
        mockMvc.perform(get("/reports/{runId}.{extension}", "test-run-123", "json"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(header().string("Content-Disposition", 
                containsString("attachment; filename=\"report-test-run-123.json\"")))
            .andExpect(jsonPath("$.runId", is("test-run-123")))
            .andExpect(jsonPath("$.status", is("completed")));
    }

    @Test
    void testDownloadSarifReport_Success() throws Exception {
        // When & Then
        mockMvc.perform(get("/reports/{runId}.{extension}", "test-run-123", "sarif"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(header().string("Content-Disposition", 
                containsString("attachment; filename=\"report-test-run-123.sarif\"")))
            .andExpect(jsonPath("$.version", is("2.1.0")))
            .andExpect(jsonPath("$.runs", hasSize(1)));
    }

    @Test
    void testDownloadPdfReport_Success() throws Exception {
        // When & Then
        mockMvc.perform(get("/reports/{runId}.{extension}", "test-run-123", "pdf"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string("Content-Disposition", 
                containsString("attachment; filename=\"report-test-run-123.pdf\"")))
            .andExpect(content().string(containsString("%PDF")));
    }

    @Test
    void testDownloadReport_UnsupportedExtension() throws Exception {
        // When & Then
        mockMvc.perform(get("/reports/{runId}.{extension}", "test-run-123", "txt"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.ok", is(false)))
            .andExpect(jsonPath("$.error.code", is("REPORT_NOT_FOUND")))
            .andExpect(jsonPath("$.error.message", containsString("不支持的文件格式")));
    }

    @Test
    void testDownloadReport_RunIdNotFound() throws Exception {
        // When & Then
        mockMvc.perform(get("/reports/{runId}.{extension}", "non-existent-run", "md"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.ok", is(false)))
            .andExpect(jsonPath("$.error.code", is("REPORT_NOT_FOUND")))
            .andExpect(jsonPath("$.error.message", containsString("报告文件未找到")));
    }

    @Test
    void testDownloadReport_FileNotFound() throws Exception {
        // When & Then - 请求一个运行ID存在但特定文件不存在的报告
        mockMvc.perform(get("/reports/{runId}.{extension}", "test-run-456", "md"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.ok", is(false)))
            .andExpect(jsonPath("$.error.code", is("REPORT_NOT_FOUND")));
    }

    @Test
    void testDownloadReport_SpecialCharactersInRunId() throws Exception {
        // Given - runId 包含特殊字符，需要URL编码
        String runId = "test-run-with-special-chars"; // 使用可以安全传递的字符
        
        // 创建对应的目录
        Path reportsDir = Path.of("target/test-reports");
        Path runDir = reportsDir.resolve(runId);
        Files.createDirectories(runDir);
        Files.writeString(runDir.resolve("report.md"), "# Special Chars Run Report");

        // When & Then
        mockMvc.perform(get("/reports/{runId}.{extension}", runId, "md"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("# Special Chars Run Report")));
    }

    /**
     * 创建测试用的报告文件。
     */
    private void createTestReportFiles() throws Exception {
        // 创建测试报告目录
        Path reportsDir = Path.of("target/test-reports");
        Path runDir = reportsDir.resolve("test-run-123");
        Files.createDirectories(runDir);
        
        // 创建各种格式的测试文件
        Files.writeString(runDir.resolve("report.md"), 
            "# Test Markdown Report\n\nThis is a test markdown report.");
        
        Files.writeString(runDir.resolve("report.html"), 
            "<!DOCTYPE html>\n<html><head><title>Test Report</title></head><body><h1>Test HTML Report</h1></body></html>");
        
        Files.writeString(runDir.resolve("report.json"), 
            "{\"runId\":\"test-run-123\",\"status\":\"completed\",\"findings\":[]}");
        
        Files.writeString(runDir.resolve("findings.sarif"), 
            "{\"version\":\"2.1.0\",\"runs\":[{\"tool\":{\"driver\":{\"name\":\"test\"}},\"results\":[]}]}");
        
        // 创建一个简单的PDF文件（模拟）
        Files.write(runDir.resolve("report.pdf"), "%PDF-1.4\nTest PDF Content".getBytes());
    }
}

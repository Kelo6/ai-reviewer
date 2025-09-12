package com.ai.reviewer.frontend.controller;

import com.ai.reviewer.frontend.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 报告控制器。
 */
@Controller
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
            String htmlContent = reportService.getHtmlReportContent(runId);
            model.addAttribute("runId", runId);
            model.addAttribute("htmlContent", htmlContent);
            return "report-preview";
            
        } catch (ReportService.ReportNotFoundException e) {
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
            ReportService.ReportDownload download = reportService.downloadReport(runId, extension);
            
            ByteArrayResource resource = new ByteArrayResource(download.content());
            
            return ResponseEntity.ok()
                .contentType(download.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                    "attachment; filename=\"" + download.filename() + "\"")
                .body(resource);
                
        } catch (ReportService.ReportNotFoundException e) {
            logger.warn("Report not found for download: runId={}, extension={}", runId, extension);
            return ResponseEntity.notFound().build();
            
        } catch (Exception e) {
            logger.error("Error downloading report: runId={}, extension={}", runId, extension, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

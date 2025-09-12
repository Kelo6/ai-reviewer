package com.ai.reviewer.frontend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 报告服务 - 处理报告下载和预览。
 */
@Service
public class ReportService {
    
    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);
    
    private final WebClient webClient;
    
    @Value("${app.backend.base-url:http://localhost:8080}")
    private String backendBaseUrl;
    
    public ReportService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();
    }
    
    /**
     * 下载报告文件。
     */
    public ReportDownload downloadReport(String runId, String extension) {
        try {
            logger.debug("Downloading report: runId={}, extension={}", runId, extension);
            
            byte[] content = webClient
                .get()
                .uri(backendBaseUrl + "/api/reports/{runId}.{extension}", runId, extension)
                .retrieve()
                .bodyToMono(byte[].class)
                .timeout(Duration.ofMinutes(5))
                .block();
            
            if (content != null) {
                MediaType mediaType = determineMediaType(extension);
                String filename = String.format("report-%s.%s", runId, extension);
                return new ReportDownload(content, mediaType, filename);
            }
            
            throw new RuntimeException("Report content is null");
            
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                logger.warn("Report not found: runId={}, extension={}", runId, extension);
                throw new ReportNotFoundException("报告文件未找到");
            }
            logger.error("Error downloading report: runId={}, extension={}", runId, extension, e);
            throw new RuntimeException("Failed to download report: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error downloading report: runId={}, extension={}", runId, extension, e);
            throw new RuntimeException("Failed to download report: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取HTML报告内容用于预览。
     */
    public String getHtmlReportContent(String runId) {
        try {
            logger.debug("Fetching HTML report content: runId={}", runId);
            
            String content = webClient
                .get()
                .uri(backendBaseUrl + "/api/reports/{runId}.html", runId)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMinutes(2))
                .block();
            
            if (content != null) {
                return content;
            }
            
            throw new RuntimeException("HTML report content is null");
            
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                logger.warn("HTML report not found: runId={}", runId);
                throw new ReportNotFoundException("HTML报告文件未找到");
            }
            logger.error("Error fetching HTML report: runId={}", runId, e);
            throw new RuntimeException("Failed to fetch HTML report: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error fetching HTML report: runId={}", runId, e);
            throw new RuntimeException("Failed to fetch HTML report: " + e.getMessage(), e);
        }
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

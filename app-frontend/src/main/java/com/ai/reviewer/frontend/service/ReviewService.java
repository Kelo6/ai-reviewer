package com.ai.reviewer.frontend.service;

import com.ai.reviewer.frontend.dto.ApiResponseDto;
import com.ai.reviewer.frontend.dto.ReviewRunDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 评审服务 - 调用后端API。
 */
@Service
public class ReviewService {
    
    private static final Logger logger = LoggerFactory.getLogger(ReviewService.class);
    
    private final WebClient webClient;
    
    @Value("${app.backend.base-url:http://localhost:8080}")
    private String backendBaseUrl;
    
    public ReviewService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();
    }
    
    /**
     * 获取评审运行详情。
     */
    public Optional<ReviewRunDto> getReviewRun(String runId) {
        try {
            logger.debug("Fetching review run details: runId={}", runId);
            
            ApiResponseDto<ReviewRunDto> response = webClient
                .get()
                .uri(backendBaseUrl + "/api/runs/{runId}", runId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponseDto<ReviewRunDto>>() {})
                .timeout(Duration.ofSeconds(30))
                .block();
            
            if (response != null && response.ok() && response.data() != null) {
                return Optional.of(response.data());
            } else {
                logger.warn("Failed to fetch review run: runId={}, response={}", runId, response);
                return Optional.empty();
            }
            
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                logger.debug("Review run not found: runId={}", runId);
                return Optional.empty();
            }
            logger.error("Error fetching review run: runId={}", runId, e);
            throw new RuntimeException("Failed to fetch review run: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error fetching review run: runId={}", runId, e);
            throw new RuntimeException("Failed to fetch review run: " + e.getMessage(), e);
        }
    }
    
    /**
     * 分页获取历史运行记录。
     * 注意：这是一个模拟实现，实际需要后端提供分页API。
     */
    public Page<ReviewRunSummary> getHistoryRuns(Pageable pageable, String repoFilter, String platformFilter) {
        // 暂时返回模拟数据，实际应调用后端分页API
        logger.debug("Fetching history runs: page={}, size={}, repoFilter={}, platformFilter={}", 
            pageable.getPageNumber(), pageable.getPageSize(), repoFilter, platformFilter);
        
        // TODO: 实现真实的后端API调用
        List<ReviewRunSummary> mockData = createMockHistoryData();
        
        // 简单的过滤逻辑
        List<ReviewRunSummary> filtered = mockData.stream()
            .filter(run -> repoFilter == null || repoFilter.isEmpty() || 
                (run.repoOwner() + "/" + run.repoName()).toLowerCase().contains(repoFilter.toLowerCase()))
            .filter(run -> platformFilter == null || platformFilter.isEmpty() || 
                run.platform().toLowerCase().contains(platformFilter.toLowerCase()))
            .toList();
        
        // 简单分页
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<ReviewRunSummary> pageContent = start >= filtered.size() ? 
            List.of() : filtered.subList(start, end);
        
        return new PageImpl<>(pageContent, pageable, filtered.size());
    }
    
    /**
     * 创建模拟历史数据。
     */
    private List<ReviewRunSummary> createMockHistoryData() {
        return List.of(
            new ReviewRunSummary("run-001", "user1", "project-a", "123", "Fix security issue", 
                "GitHub", 85.5, java.time.Instant.now().minusSeconds(3600)),
            new ReviewRunSummary("run-002", "user1", "project-a", "124", "Add new feature", 
                "GitHub", 92.0, java.time.Instant.now().minusSeconds(7200)),
            new ReviewRunSummary("run-003", "user2", "project-b", "45", "Refactor code", 
                "GitLab", 78.3, java.time.Instant.now().minusSeconds(10800)),
            new ReviewRunSummary("run-004", "user2", "project-b", "46", "Update dependencies", 
                "GitLab", 88.7, java.time.Instant.now().minusSeconds(14400)),
            new ReviewRunSummary("run-005", "user3", "project-c", "67", "Performance improvement", 
                "GitHub", 94.2, java.time.Instant.now().minusSeconds(18000))
        );
    }
    
    /**
     * 历史运行摘要记录。
     */
    public record ReviewRunSummary(
        String runId,
        String repoOwner,
        String repoName,
        String pullNumber,
        String pullTitle,
        String platform,
        double totalScore,
        java.time.Instant createdAt
    ) {}
}

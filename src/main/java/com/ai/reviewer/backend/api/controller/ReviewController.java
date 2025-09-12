package com.ai.reviewer.backend.api.controller;

import com.ai.reviewer.backend.api.dto.ApiResponse;
import com.ai.reviewer.backend.api.dto.ReviewRequest;
import com.ai.reviewer.backend.api.dto.ReviewRunResponse;
import com.ai.reviewer.backend.api.exception.ReviewRunNotFoundException;
import com.ai.reviewer.backend.domain.orchestrator.ReviewOrchestrator;
import com.ai.reviewer.shared.model.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 代码评审 REST 控制器。
 */
@RestController
@RequestMapping("/api")
public class ReviewController {
    
    private static final Logger logger = LoggerFactory.getLogger(ReviewController.class);
    
    private final ReviewOrchestrator reviewOrchestrator;
    
    @Autowired
    public ReviewController(ReviewOrchestrator reviewOrchestrator) {
        this.reviewOrchestrator = reviewOrchestrator;
    }
    
    /**
     * 启动代码评审。
     * 
     * @param request 评审请求
     * @return 评审结果
     */
    @PostMapping("/review")
    public ResponseEntity<ApiResponse<ReviewRunResponse>> startReview(@Valid @RequestBody ReviewRequest request) {
        logger.info("Starting code review for {}/{} PR #{}", 
            request.repo().owner(), request.repo().name(), request.pull().number());
        
        try {
            // 转换请求参数
            RepoRef repoRef = new RepoRef(
                "github",  // provider placeholder
                request.repo().owner(), 
                request.repo().name(),
                null       // url placeholder
            );
            PullRef pullRef = new PullRef(
                request.pull().number(), // id
                request.pull().number(),
                request.pull().title(),  // title
                request.pull().sourceBranch(),
                request.pull().targetBranch(),
                null,    // sha placeholder
                false    // draft placeholder
            );
            
            // 执行评审
            ReviewRun reviewRun = reviewOrchestrator.runReview(
                repoRef, 
                pullRef, 
                Optional.ofNullable(request.providers()).orElse(List.of())
            );
            
            // 转换响应
            ReviewRunResponse response = convertToResponse(reviewRun);
            
            logger.info("Code review completed successfully: runId={}", reviewRun.runId());
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            logger.error("Failed to start code review for {}/{} PR #{}", 
                request.repo().owner(), request.repo().name(), request.pull().number(), e);
            throw e; // 让全局异常处理器处理
        }
    }
    
    /**
     * 查询评审运行详情。
     * 
     * @param runId 运行ID
     * @return 运行详情
     */
    @GetMapping("/runs/{runId}")
    public ResponseEntity<ApiResponse<ReviewRunResponse>> getReviewRun(@PathVariable String runId) {
        logger.debug("Fetching review run details: runId={}", runId);
        
        try {
            ReviewRun reviewRun = reviewOrchestrator.getReviewRun(runId);
            if (reviewRun == null) {
                throw new ReviewRunNotFoundException(runId);
            }
            
            ReviewRunResponse response = convertToResponse(reviewRun);
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (ReviewRunNotFoundException e) {
            logger.warn("Review run not found: runId={}", runId);
            throw e;
        } catch (Exception e) {
            logger.error("Failed to fetch review run details: runId={}", runId, e);
            throw e;
        }
    }
    
    /**
     * 转换 ReviewRun 到响应DTO。
     */
    private ReviewRunResponse convertToResponse(ReviewRun reviewRun) {
        return new ReviewRunResponse(
            reviewRun.runId(),
            new ReviewRunResponse.RepoInfo(
                reviewRun.repo().owner(),
                reviewRun.repo().name()
            ),
            new ReviewRunResponse.PullInfo(
                reviewRun.pull().number(),
                reviewRun.pull().title(),
                reviewRun.pull().sourceBranch(),
                reviewRun.pull().targetBranch()
            ),
            reviewRun.createdAt(),
            reviewRun.providerKeys(),
            new ReviewRunResponse.StatsInfo(
                reviewRun.stats().filesChanged(),
                reviewRun.stats().linesAdded(),
                reviewRun.stats().linesDeleted(),
                reviewRun.stats().latencyMs(),
                reviewRun.stats().tokenCostUsd()
            ),
            reviewRun.findings().stream()
                .map(finding -> new ReviewRunResponse.FindingInfo(
                    finding.id(),
                    finding.file(),
                    finding.startLine(),
                    finding.endLine(),
                    finding.severity(),
                    finding.dimension(),
                    finding.title(),
                    finding.evidence(),
                    finding.suggestion(),
                    finding.patch(),
                    finding.sources(),
                    finding.confidence()
                ))
                .toList(),
            new ReviewRunResponse.ScoresInfo(
                reviewRun.scores().totalScore(),
                reviewRun.scores().dimensions(),
                reviewRun.scores().weights()
            ),
            reviewRun.artifacts() != null ? new ReviewRunResponse.ArtifactsInfo(
                reviewRun.artifacts().sarifPath(),
                reviewRun.artifacts().reportMdPath(),
                reviewRun.artifacts().reportHtmlPath(),
                reviewRun.artifacts().reportPdfPath()
            ) : null
        );
    }
}

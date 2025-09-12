package com.ai.reviewer.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Asynchronous execution configuration for review processing.
 * 
 * <p>Configures specialized thread pools for different types of async operations:
 * - Review execution (main orchestration)
 * - Static analysis (CPU-intensive tasks)
 * - AI review (I/O-intensive with external API calls)
 * - Report generation (document processing)
 */
@Configuration
@EnableAsync
@ConfigurationProperties(prefix = "app.async")
public class AsyncConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(AsyncConfig.class);
    
    // Configuration properties with defaults
    private ReviewExecutor reviewExecutor = new ReviewExecutor();
    private StaticAnalysis staticAnalysis = new StaticAnalysis();
    private AiReview aiReview = new AiReview();
    private ReportGeneration reportGeneration = new ReportGeneration();
    
    /**
     * Main review orchestration executor.
     * 
     * <p>Handles the high-level review workflow coordination.
     * Lower concurrency since each review can spawn many sub-tasks.
     */
    @Bean("reviewExecutor")
    public Executor reviewExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        executor.setCorePoolSize(reviewExecutor.getCorePoolSize());
        executor.setMaxPoolSize(reviewExecutor.getMaxPoolSize());
        executor.setQueueCapacity(reviewExecutor.getQueueCapacity());
        executor.setThreadNamePrefix("review-exec-");
        executor.setKeepAliveSeconds(reviewExecutor.getKeepAliveSeconds());
        
        // Reject policy for when queue is full
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // Shutdown settings
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        
        executor.initialize();
        
        logger.info("Review executor configured: core={}, max={}, queue={}", 
            reviewExecutor.getCorePoolSize(), reviewExecutor.getMaxPoolSize(), reviewExecutor.getQueueCapacity());
        
        return executor;
    }
    
    /**
     * Static analysis executor.
     * 
     * <p>Optimized for CPU-intensive static analysis tools.
     * Higher concurrency to utilize multiple CPU cores.
     */
    @Bean("staticAnalysisExecutor")
    public Executor staticAnalysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        executor.setCorePoolSize(staticAnalysis.getCorePoolSize());
        executor.setMaxPoolSize(staticAnalysis.getMaxPoolSize());
        executor.setQueueCapacity(staticAnalysis.getQueueCapacity());
        executor.setThreadNamePrefix("static-analysis-");
        executor.setKeepAliveSeconds(staticAnalysis.getKeepAliveSeconds());
        
        // Custom rejection handler with logging
        executor.setRejectedExecutionHandler(new LoggingRejectedExecutionHandler("StaticAnalysis"));
        
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(45);
        
        executor.initialize();
        
        logger.info("Static analysis executor configured: core={}, max={}, queue={}", 
            staticAnalysis.getCorePoolSize(), staticAnalysis.getMaxPoolSize(), staticAnalysis.getQueueCapacity());
        
        return executor;
    }
    
    /**
     * AI review executor.
     * 
     * <p>Optimized for I/O-intensive AI API calls with rate limiting considerations.
     * Moderate concurrency to respect API rate limits.
     */
    @Bean("aiReviewExecutor")
    public Executor aiReviewExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        executor.setCorePoolSize(aiReview.getCorePoolSize());
        executor.setMaxPoolSize(aiReview.getMaxPoolSize());
        executor.setQueueCapacity(aiReview.getQueueCapacity());
        executor.setThreadNamePrefix("ai-review-");
        executor.setKeepAliveSeconds(aiReview.getKeepAliveSeconds());
        
        // Abort policy to prevent overwhelming AI APIs
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60); // Longer timeout for AI requests
        
        executor.initialize();
        
        logger.info("AI review executor configured: core={}, max={}, queue={}", 
            aiReview.getCorePoolSize(), aiReview.getMaxPoolSize(), aiReview.getQueueCapacity());
        
        return executor;
    }
    
    /**
     * Report generation executor.
     * 
     * <p>For generating various report formats (JSON, HTML, PDF, etc.).
     * Moderate concurrency as report generation can be memory-intensive.
     */
    @Bean("reportGenerationExecutor")
    public Executor reportGenerationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        executor.setCorePoolSize(reportGeneration.getCorePoolSize());
        executor.setMaxPoolSize(reportGeneration.getMaxPoolSize());
        executor.setQueueCapacity(reportGeneration.getQueueCapacity());
        executor.setThreadNamePrefix("report-gen-");
        executor.setKeepAliveSeconds(reportGeneration.getKeepAliveSeconds());
        
        executor.setRejectedExecutionHandler(new LoggingRejectedExecutionHandler("ReportGeneration"));
        
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        
        executor.initialize();
        
        logger.info("Report generation executor configured: core={}, max={}, queue={}", 
            reportGeneration.getCorePoolSize(), reportGeneration.getMaxPoolSize(), reportGeneration.getQueueCapacity());
        
        return executor;
    }
    
    /**
     * Custom rejection handler with logging.
     */
    private static class LoggingRejectedExecutionHandler implements RejectedExecutionHandler {
        private final String executorName;
        
        public LoggingRejectedExecutionHandler(String executorName) {
            this.executorName = executorName;
        }
        
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            logger.warn("{} executor rejected task: {} (active={}, pool={}, queue={})", 
                executorName, r.getClass().getSimpleName(),
                executor.getActiveCount(), executor.getPoolSize(), executor.getQueue().size());
            
            // Fallback to caller runs policy
            if (!executor.isShutdown()) {
                r.run();
            }
        }
    }
    
    // Configuration properties classes
    public static class ReviewExecutor {
        private int corePoolSize = 2;
        private int maxPoolSize = 4;
        private int queueCapacity = 10;
        private int keepAliveSeconds = 60;
        
        // Getters and setters
        public int getCorePoolSize() { return corePoolSize; }
        public void setCorePoolSize(int corePoolSize) { this.corePoolSize = corePoolSize; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
        public int getKeepAliveSeconds() { return keepAliveSeconds; }
        public void setKeepAliveSeconds(int keepAliveSeconds) { this.keepAliveSeconds = keepAliveSeconds; }
    }
    
    public static class StaticAnalysis {
        private int corePoolSize = Runtime.getRuntime().availableProcessors();
        private int maxPoolSize = Runtime.getRuntime().availableProcessors() * 2;
        private int queueCapacity = 50;
        private int keepAliveSeconds = 60;
        
        // Getters and setters
        public int getCorePoolSize() { return corePoolSize; }
        public void setCorePoolSize(int corePoolSize) { this.corePoolSize = corePoolSize; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
        public int getKeepAliveSeconds() { return keepAliveSeconds; }
        public void setKeepAliveSeconds(int keepAliveSeconds) { this.keepAliveSeconds = keepAliveSeconds; }
    }
    
    public static class AiReview {
        private int corePoolSize = 3;
        private int maxPoolSize = 8;
        private int queueCapacity = 20;
        private int keepAliveSeconds = 120;
        
        // Getters and setters
        public int getCorePoolSize() { return corePoolSize; }
        public void setCorePoolSize(int corePoolSize) { this.corePoolSize = corePoolSize; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
        public int getKeepAliveSeconds() { return keepAliveSeconds; }
        public void setKeepAliveSeconds(int keepAliveSeconds) { this.keepAliveSeconds = keepAliveSeconds; }
    }
    
    public static class ReportGeneration {
        private int corePoolSize = 2;
        private int maxPoolSize = 4;
        private int queueCapacity = 15;
        private int keepAliveSeconds = 60;
        
        // Getters and setters
        public int getCorePoolSize() { return corePoolSize; }
        public void setCorePoolSize(int corePoolSize) { this.corePoolSize = corePoolSize; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
        public int getKeepAliveSeconds() { return keepAliveSeconds; }
        public void setKeepAliveSeconds(int keepAliveSeconds) { this.keepAliveSeconds = keepAliveSeconds; }
    }
    
    // Getters and setters for configuration properties
    public ReviewExecutor getReviewExecutor() { return reviewExecutor; }
    public void setReviewExecutor(ReviewExecutor reviewExecutor) { this.reviewExecutor = reviewExecutor; }
    public StaticAnalysis getStaticAnalysis() { return staticAnalysis; }
    public void setStaticAnalysis(StaticAnalysis staticAnalysis) { this.staticAnalysis = staticAnalysis; }
    public AiReview getAiReview() { return aiReview; }
    public void setAiReview(AiReview aiReview) { this.aiReview = aiReview; }
    public ReportGeneration getReportGeneration() { return reportGeneration; }
    public void setReportGeneration(ReportGeneration reportGeneration) { this.reportGeneration = reportGeneration; }
}

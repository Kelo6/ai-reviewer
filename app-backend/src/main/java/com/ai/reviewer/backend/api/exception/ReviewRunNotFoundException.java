package com.ai.reviewer.backend.api.exception;

/**
 * 评审运行未找到异常。
 */
public class ReviewRunNotFoundException extends RuntimeException {
    
    public ReviewRunNotFoundException(String runId) {
        super(String.format("评审运行未找到: runId=%s", runId));
    }
    
    public ReviewRunNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

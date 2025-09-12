package com.ai.reviewer.backend.api.exception;

/**
 * 报告文件未找到异常。
 */
public class ReportNotFoundException extends RuntimeException {
    
    public ReportNotFoundException(String runId, String extension) {
        super(String.format("报告文件未找到: runId=%s, extension=%s", runId, extension));
    }
    
    public ReportNotFoundException(String message) {
        super(message);
    }
    
    public ReportNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

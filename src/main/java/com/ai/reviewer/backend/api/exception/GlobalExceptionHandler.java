package com.ai.reviewer.backend.api.exception;

import com.ai.reviewer.backend.api.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.regex.Pattern;

/**
 * 全局异常处理器。
 * 
 * <p>统一处理API异常，屏蔽敏感信息（密钥、提示词等），
 * 返回标准格式的错误响应。
 */
@RestControllerAdvice(basePackages = "com.ai.reviewer.backend.api")
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    // 敏感信息匹配模式
    private static final Pattern API_KEY_PATTERN = Pattern.compile(
        "(?i)(api[_-]?key|secret|token|password)[\"']?\\s*[:=]\\s*[\"']?([\\w\\-_]+)",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern PROMPT_PATTERN = Pattern.compile(
        "(?i)(user_prompt|prompt|instruction|system)[\"']?\\s*[:=]\\s*[\"']?([^\"'\\n]{20,})",
        Pattern.CASE_INSENSITIVE
    );
    
    /**
     * 处理参数验证异常。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        
        StringBuilder message = new StringBuilder("参数验证失败: ");
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            message.append(error.getField()).append(" ").append(error.getDefaultMessage()).append("; ");
        }
        
        logError("VALIDATION_ERROR", ex, request);
        
        ApiResponse<Void> response = ApiResponse.error("VALIDATION_ERROR", message.toString());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * 处理业务异常。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {
        
        logError(ex.getCode(), ex, request);
        
        String sanitizedMessage = sanitizeErrorMessage(ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error(ex.getCode(), sanitizedMessage);
        
        HttpStatus status = mapBusinessExceptionToHttpStatus(ex.getCode());
        return new ResponseEntity<>(response, status);
    }
    
    /**
     * 处理文件未找到异常。
     */
    @ExceptionHandler(ReportNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleReportNotFoundException(
            ReportNotFoundException ex, HttpServletRequest request) {
        
        logError("REPORT_NOT_FOUND", ex, request);
        
        ApiResponse<Void> response = ApiResponse.error("REPORT_NOT_FOUND", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }
    
    /**
     * 处理运行未找到异常。
     */
    @ExceptionHandler(ReviewRunNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleReviewRunNotFoundException(
            ReviewRunNotFoundException ex, HttpServletRequest request) {
        
        logError("RUN_NOT_FOUND", ex, request);
        
        ApiResponse<Void> response = ApiResponse.error("RUN_NOT_FOUND", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }
    
    /**
     * 处理静态资源未找到异常。
     * 特别处理Chrome DevTools和其他浏览器工具的自动请求，避免产生无用的错误日志。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpServletRequest request) {
        
        String requestPath = request.getRequestURI();
        
        // 检查是否是浏览器工具的自动请求
        if (isBrowserToolRequest(requestPath)) {
            // 对于浏览器工具请求，只记录DEBUG级别日志，不记录为错误
            logger.debug("Browser tool request for non-existent resource: {}", requestPath);
        } else {
            // 对于其他资源请求，记录为WARNING级别
            logger.warn("Static resource not found - Path: {}, Method: {}", 
                requestPath, request.getMethod());
        }
        
        // 返回标准的404响应
        ApiResponse<Void> response = ApiResponse.error("RESOURCE_NOT_FOUND", 
            "请求的资源不存在: " + requestPath);
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }
    
    /**
     * 处理所有其他异常。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception ex, HttpServletRequest request) {
        
        logError("INTERNAL_ERROR", ex, request);
        
        String sanitizedMessage = sanitizeErrorMessage(ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error("INTERNAL_ERROR", 
            "服务内部错误: " + (sanitizedMessage != null ? sanitizedMessage : "未知错误"));
        
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    /**
     * 清理错误消息中的敏感信息。
     */
    private String sanitizeErrorMessage(String message) {
        if (message == null) {
            return null;
        }
        
        // 屏蔽API密钥
        String sanitized = API_KEY_PATTERN.matcher(message).replaceAll("$1=***REDACTED***");
        
        // 屏蔽长提示词内容
        sanitized = PROMPT_PATTERN.matcher(sanitized).replaceAll("$1=***CONTENT_REDACTED***");
        
        return sanitized;
    }
    
    /**
     * 记录错误日志。
     */
    private void logError(String errorCode, Exception ex, HttpServletRequest request) {
        String sanitizedMessage = sanitizeErrorMessage(ex.getMessage());
        logger.error("API Error - Code: {}, Path: {}, Method: {}, Message: {}", 
            errorCode, request.getRequestURI(), request.getMethod(), sanitizedMessage, ex);
    }
    
    /**
     * 映射业务异常到HTTP状态码。
     */
    private HttpStatus mapBusinessExceptionToHttpStatus(String code) {
        return switch (code) {
            case "VALIDATION_ERROR" -> HttpStatus.BAD_REQUEST;
            case "UNAUTHORIZED" -> HttpStatus.UNAUTHORIZED;
            case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "NOT_FOUND", "RUN_NOT_FOUND", "REPORT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "RATE_LIMIT_EXCEEDED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "SERVICE_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
    
    /**
     * 检查是否是浏览器工具的自动请求。
     * 这些请求通常是浏览器或开发者工具自动发起的，不是用户的实际请求。
     */
    private boolean isBrowserToolRequest(String requestPath) {
        if (requestPath == null) {
            return false;
        }
        
        // Chrome DevTools 相关请求
        if (requestPath.contains(".well-known/appspecific/com.chrome.devtools")) {
            return true;
        }
        
        // 其他常见的浏览器工具请求
        return requestPath.matches("(?i).*/(favicon\\.ico|robots\\.txt|sitemap\\.xml|" +
                                 "apple-touch-icon.*\\.png|browserconfig\\.xml|" +
                                 "manifest\\.json|\\.well-known/.*|" +
                                 "sw\\.js|serviceworker\\.js)$");
    }
}

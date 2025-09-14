package com.ai.reviewer.backend.web.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Web页面异常处理器
 * 
 * 专门处理Web页面控制器的异常，返回错误页面而不是JSON响应
 */
@ControllerAdvice(basePackages = "com.ai.reviewer.backend.web")
public class WebExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(WebExceptionHandler.class);
    
    /**
     * 处理静态资源未找到异常
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ModelAndView handleNoResourceFoundException(
            NoResourceFoundException ex, HttpServletRequest request) {
        
        String requestPath = request.getRequestURI();
        
        // 检查是否是浏览器工具的自动请求
        if (isBrowserToolRequest(requestPath)) {
            // 对于浏览器工具请求，只记录DEBUG级别日志，不记录为错误
            logger.debug("Browser tool request for non-existent resource: {}", requestPath);
        } else {
            // 对于其他资源请求，记录为WARNING级别
            logger.warn("Web resource not found - Path: {}, Method: {}", 
                requestPath, request.getMethod());
        }
        
        // 返回404错误页面
        ModelAndView modelAndView = new ModelAndView("error/404");
        modelAndView.addObject("path", requestPath);
        modelAndView.addObject("message", "请求的页面或资源不存在");
        modelAndView.setStatus(HttpStatus.NOT_FOUND);
        return modelAndView;
    }
    
    /**
     * 处理所有其他Web异常
     */
    @ExceptionHandler(Exception.class)
    public ModelAndView handleGenericException(
            Exception ex, HttpServletRequest request) {
        
        String requestPath = request.getRequestURI();
        String errorMessage = sanitizeErrorMessage(ex.getMessage());
        
        logger.error("Web Error - Path: {}, Method: {}, Message: {}", 
            requestPath, request.getMethod(), errorMessage, ex);
        
        // 返回500错误页面
        ModelAndView modelAndView = new ModelAndView("error/500");
        modelAndView.addObject("path", requestPath);
        modelAndView.addObject("message", "页面加载失败，请稍后重试");
        modelAndView.addObject("error", errorMessage);
        modelAndView.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        return modelAndView;
    }
    
    /**
     * 清理错误消息中的敏感信息
     */
    private String sanitizeErrorMessage(String message) {
        if (message == null) {
            return "未知错误";
        }
        
        // 简单的敏感信息过滤
        String sanitized = message.replaceAll("(?i)(api[_-]?key|secret|token|password)\\s*[:=]\\s*\\w+", "$1=***");
        
        // 限制错误消息长度
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 200) + "...";
        }
        
        return sanitized;
    }
    
    /**
     * 检查是否是浏览器工具的自动请求
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

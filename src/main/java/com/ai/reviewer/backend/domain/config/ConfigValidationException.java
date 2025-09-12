package com.ai.reviewer.backend.domain.config;

/**
 * 配置验证异常。
 */
public class ConfigValidationException extends Exception {
    
    public ConfigValidationException(String message) {
        super(message);
    }
    
    public ConfigValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

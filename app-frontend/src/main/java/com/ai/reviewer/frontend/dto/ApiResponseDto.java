package com.ai.reviewer.frontend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 后端API响应DTO。
 * 
 * @param <T> 数据类型
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiResponseDto<T>(
    boolean ok,
    T data,
    ErrorInfo error
) {
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorInfo(
        String code,
        String message
    ) {}
}

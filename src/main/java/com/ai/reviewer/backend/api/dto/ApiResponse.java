package com.ai.reviewer.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一API响应格式。
 * 
 * @param <T> 数据类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    boolean ok,
    T data,
    ErrorInfo error
) {
    /**
     * 成功响应。
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /**
     * 成功响应（无数据）。
     */
    public static ApiResponse<Void> success() {
        return new ApiResponse<>(true, null, null);
    }

    /**
     * 错误响应。
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorInfo(code, message));
    }

    /**
     * 错误响应。
     */
    public static <T> ApiResponse<T> error(ErrorInfo error) {
        return new ApiResponse<>(false, null, error);
    }

    /**
     * 错误信息。
     */
    public record ErrorInfo(
        String code,
        String message
    ) {}
}

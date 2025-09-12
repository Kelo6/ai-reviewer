package com.ai.reviewer.backend.api.exception;

import com.ai.reviewer.backend.api.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * GlobalExceptionHandler 单元测试。
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private MockHttpServletRequest mockRequest;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        mockRequest = new MockHttpServletRequest();
        mockRequest.setRequestURI("/api/test");
        mockRequest.setMethod("POST");
    }

    @Test
    void testHandleValidationException() {
        // Given
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError1 = new FieldError("request", "repo.owner", "不能为空");
        FieldError fieldError2 = new FieldError("request", "pull.number", "不能为空");
        
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));
        
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);

        // When
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleValidationException(exception, mockRequest);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().ok());
        assertEquals("VALIDATION_ERROR", response.getBody().error().code());
        assertTrue(response.getBody().error().message().contains("参数验证失败"));
        assertTrue(response.getBody().error().message().contains("repo.owner"));
        assertTrue(response.getBody().error().message().contains("pull.number"));
    }

    @Test
    void testHandleBusinessException() {
        // Given
        BusinessException exception = new BusinessException("RATE_LIMIT_EXCEEDED", "API调用频率过高");

        // When
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleBusinessException(exception, mockRequest);

        // Then
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().ok());
        assertEquals("RATE_LIMIT_EXCEEDED", response.getBody().error().code());
        assertEquals("API调用频率过高", response.getBody().error().message());
    }

    @Test
    void testHandleReportNotFoundException() {
        // Given
        ReportNotFoundException exception = new ReportNotFoundException("test-run-id", "pdf");

        // When
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleReportNotFoundException(exception, mockRequest);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().ok());
        assertEquals("REPORT_NOT_FOUND", response.getBody().error().code());
        assertTrue(response.getBody().error().message().contains("test-run-id"));
        assertTrue(response.getBody().error().message().contains("pdf"));
    }

    @Test
    void testHandleReviewRunNotFoundException() {
        // Given
        ReviewRunNotFoundException exception = new ReviewRunNotFoundException("non-existent-run-id");

        // When
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleReviewRunNotFoundException(exception, mockRequest);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().ok());
        assertEquals("RUN_NOT_FOUND", response.getBody().error().code());
        assertTrue(response.getBody().error().message().contains("non-existent-run-id"));
    }

    @Test
    void testHandleGenericException() {
        // Given
        RuntimeException exception = new RuntimeException("Unexpected error occurred");

        // When
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleGenericException(exception, mockRequest);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().ok());
        assertEquals("INTERNAL_ERROR", response.getBody().error().code());
        assertTrue(response.getBody().error().message().contains("服务内部错误"));
        assertTrue(response.getBody().error().message().contains("Unexpected error occurred"));
    }

    @Test
    void testSanitizeErrorMessage_ApiKeyRedacted() {
        // Given
        String messageWithApiKey = "Failed to connect with api_key=sk-1234567890abcdef and secret=my-secret-token";
        RuntimeException exception = new RuntimeException(messageWithApiKey);

        // When
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleGenericException(exception, mockRequest);

        // Then
        String sanitizedMessage = response.getBody().error().message();
        assertFalse(sanitizedMessage.contains("sk-1234567890abcdef"));
        assertFalse(sanitizedMessage.contains("my-secret-token"));
        assertTrue(sanitizedMessage.contains("***REDACTED***"));
    }

    @Test
    void testSanitizeErrorMessage_PromptRedacted() {
        // Given
        String longPrompt = "You are a helpful assistant. Please analyze the following code and provide detailed feedback about potential issues...";
        String messageWithPrompt = "AI request failed with prompt=" + longPrompt;
        RuntimeException exception = new RuntimeException(messageWithPrompt);

        // When
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleGenericException(exception, mockRequest);

        // Then
        String sanitizedMessage = response.getBody().error().message();
        assertFalse(sanitizedMessage.contains("You are a helpful assistant"));
        assertFalse(sanitizedMessage.contains("analyze the following code"));
        assertTrue(sanitizedMessage.contains("***CONTENT_REDACTED***"));
    }

    @Test
    void testSanitizeErrorMessage_NoSensitiveContent() {
        // Given
        String normalMessage = "Database connection timeout after 30 seconds";
        RuntimeException exception = new RuntimeException(normalMessage);

        // When
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleGenericException(exception, mockRequest);

        // Then
        String sanitizedMessage = response.getBody().error().message();
        assertTrue(sanitizedMessage.contains("Database connection timeout"));
        assertFalse(sanitizedMessage.contains("***REDACTED***"));
        assertFalse(sanitizedMessage.contains("***CONTENT_REDACTED***"));
    }

    @Test
    void testMapBusinessExceptionToHttpStatus() {
        // Test various business exception codes
        assertEquals(HttpStatus.BAD_REQUEST, 
            getHttpStatusForBusinessException("VALIDATION_ERROR"));
        assertEquals(HttpStatus.UNAUTHORIZED, 
            getHttpStatusForBusinessException("UNAUTHORIZED"));
        assertEquals(HttpStatus.FORBIDDEN, 
            getHttpStatusForBusinessException("FORBIDDEN"));
        assertEquals(HttpStatus.NOT_FOUND, 
            getHttpStatusForBusinessException("NOT_FOUND"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, 
            getHttpStatusForBusinessException("RATE_LIMIT_EXCEEDED"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, 
            getHttpStatusForBusinessException("SERVICE_UNAVAILABLE"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, 
            getHttpStatusForBusinessException("UNKNOWN_ERROR"));
    }

    @Test
    void testHandleNullMessage() {
        // Given
        RuntimeException exception = new RuntimeException((String) null);

        // When
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleGenericException(exception, mockRequest);

        // Then
        assertNotNull(response.getBody());
        assertTrue(response.getBody().error().message().contains("未知错误"));
    }

    @Test
    void testMultipleSensitivePatterns() {
        // Given
        String complexMessage = "Authentication failed: api_key=secret123, user_prompt='Analyze this sensitive code...', token=xyz789";
        RuntimeException exception = new RuntimeException(complexMessage);

        // When
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleGenericException(exception, mockRequest);

        // Then
        String sanitizedMessage = response.getBody().error().message();
        assertFalse(sanitizedMessage.contains("secret123"));
        assertFalse(sanitizedMessage.contains("xyz789"));
        assertFalse(sanitizedMessage.contains("Analyze this sensitive code"));
        assertTrue(sanitizedMessage.contains("***REDACTED***"));
        assertTrue(sanitizedMessage.contains("***CONTENT_REDACTED***"));
    }

    /**
     * 辅助方法：获取业务异常对应的HTTP状态码。
     */
    private HttpStatus getHttpStatusForBusinessException(String code) {
        BusinessException exception = new BusinessException(code, "Test message");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleBusinessException(exception, mockRequest);
        return HttpStatus.valueOf(response.getStatusCode().value());
    }
}

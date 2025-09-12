package com.ai.reviewer.backend.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HealthController MVC 测试。
 */
@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DataSource dataSource;

    @Test
    void testHealth_AllComponentsHealthy() throws Exception {
        // Given - 模拟健康的数据库连接
        Connection mockConnection = mock(Connection.class);
        DatabaseMetaData mockMetaData = mock(DatabaseMetaData.class);
        
        when(dataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.isValid(5)).thenReturn(true);
        when(mockConnection.getMetaData()).thenReturn(mockMetaData);
        when(mockMetaData.getDriverName()).thenReturn("MySQL Connector/J");
        when(mockMetaData.getURL()).thenReturn("jdbc:mysql://localhost:3306/testdb");

        // When & Then
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status", is("UP")))
            .andExpect(jsonPath("$.timestamp", is(notNullValue())))
            .andExpect(jsonPath("$.components.database.status", is("UP")))
            .andExpect(jsonPath("$.components.database.message", containsString("Database connection is healthy")))
            .andExpect(jsonPath("$.components.adapters.status", is("UP")))
            .andExpect(jsonPath("$.components.memory.status", is("UP")));

        verify(mockConnection).close();
    }

    @Test
    void testHealth_DatabaseDown() throws Exception {
        // Given - 模拟数据库连接失败
        when(dataSource.getConnection()).thenThrow(new RuntimeException("Connection refused"));

        // When & Then
        mockMvc.perform(get("/health"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status", is("DOWN")))
            .andExpect(jsonPath("$.components.database.status", is("DOWN")))
            .andExpect(jsonPath("$.components.database.message", containsString("Database connection failed")));
    }

    @Test
    void testHealth_DatabaseConnectionInvalid() throws Exception {
        // Given - 模拟连接无效
        Connection mockConnection = mock(Connection.class);
        DatabaseMetaData mockMetaData = mock(DatabaseMetaData.class);
        
        when(dataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.isValid(5)).thenReturn(false);
        when(mockConnection.getMetaData()).thenReturn(mockMetaData);
        when(mockMetaData.getDriverName()).thenReturn("MySQL Connector/J");
        when(mockMetaData.getURL()).thenReturn("jdbc:mysql://localhost:3306/testdb");

        // When & Then
        mockMvc.perform(get("/health"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status", is("DOWN")))
            .andExpect(jsonPath("$.components.database.status", is("DOWN")))
            .andExpect(jsonPath("$.components.database.message", containsString("Database connection is not valid")));

        verify(mockConnection).close();
    }

    @Test
    void testHealth_ComponentDetails() throws Exception {
        // Given
        Connection mockConnection = mock(Connection.class);
        DatabaseMetaData mockMetaData = mock(DatabaseMetaData.class);
        
        when(dataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.isValid(5)).thenReturn(true);
        when(mockConnection.getMetaData()).thenReturn(mockMetaData);
        when(mockMetaData.getDriverName()).thenReturn("MySQL Connector/J");
        when(mockMetaData.getURL()).thenReturn("jdbc:mysql://localhost:3306/testdb");

        // When & Then
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.components.database.details.driver", is("MySQL Connector/J")))
            .andExpect(jsonPath("$.components.database.details.url", containsString("jdbc:mysql")))
            .andExpect(jsonPath("$.components.adapters.details.github", is("Available")))
            .andExpect(jsonPath("$.components.adapters.details.gitlab", is("Available")))
            .andExpect(jsonPath("$.components.memory.details.used", is(notNullValue())))
            .andExpect(jsonPath("$.components.memory.details.total", is(notNullValue())))
            .andExpect(jsonPath("$.components.memory.details.max", is(notNullValue())))
            .andExpect(jsonPath("$.components.memory.details.usage", containsString("%")));
    }

    @Test
    void testHealth_SensitiveInformationMasked() throws Exception {
        // Given - URL 包含敏感信息
        Connection mockConnection = mock(Connection.class);
        DatabaseMetaData mockMetaData = mock(DatabaseMetaData.class);
        
        when(dataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.isValid(5)).thenReturn(true);
        when(mockConnection.getMetaData()).thenReturn(mockMetaData);
        when(mockMetaData.getDriverName()).thenReturn("MySQL Connector/J");
        when(mockMetaData.getURL()).thenReturn("jdbc:mysql://user:secret123@localhost:3306/testdb?password=mysecret");

        // When & Then
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.components.database.details.url", not(containsString("secret123"))))
            .andExpect(jsonPath("$.components.database.details.url", not(containsString("mysecret"))))
            .andExpect(jsonPath("$.components.database.details.url", containsString("***")));
    }

    @Test
    void testHealth_TimestampFormat() throws Exception {
        // Given
        Connection mockConnection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.isValid(5)).thenReturn(true);

        // When & Then
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timestamp", matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z")));
    }

    @Test
    void testHealth_MemoryUsageCalculation() throws Exception {
        // Given
        Connection mockConnection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.isValid(5)).thenReturn(true);

        // When & Then
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.components.memory.details.usage", matchesPattern("\\d+\\.\\d+%")))
            .andExpect(jsonPath("$.components.memory.details.used", matchesPattern("\\d+(\\.\\d+)? [KMGT]?B")))
            .andExpect(jsonPath("$.components.memory.details.total", matchesPattern("\\d+(\\.\\d+)? [KMGT]?B")))
            .andExpect(jsonPath("$.components.memory.details.max", matchesPattern("\\d+(\\.\\d+)? [KMGT]?B")));
    }
}

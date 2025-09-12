package com.ai.reviewer.frontend.controller;

import com.ai.reviewer.frontend.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * DashboardController测试。
 */
@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewService reviewService;

    @Test
    @WithMockUser
    void testDashboard() throws Exception {
        // Given
        List<ReviewService.ReviewRunSummary> runs = List.of(
            new ReviewService.ReviewRunSummary(
                "run-001", "user", "repo", "123", "Test PR", 
                "GitHub", 85.5, Instant.now()
            )
        );
        Page<ReviewService.ReviewRunSummary> page = new PageImpl<>(
            runs, PageRequest.of(0, 10), 1
        );
        
        when(reviewService.getHistoryRuns(any(), anyString(), anyString()))
            .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/dashboard"))
            .andExpect(status().isOk())
            .andExpect(view().name("dashboard"))
            .andExpect(model().attributeExists("runs"))
            .andExpect(model().attribute("currentPage", 0))
            .andExpect(model().attribute("totalPages", 1));
    }

    @Test
    @WithMockUser
    void testDashboardWithFilters() throws Exception {
        // Given
        List<ReviewService.ReviewRunSummary> runs = List.of();
        Page<ReviewService.ReviewRunSummary> page = new PageImpl<>(
            runs, PageRequest.of(0, 10), 0
        );
        
        when(reviewService.getHistoryRuns(any(), anyString(), anyString()))
            .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/dashboard")
                .param("repo", "test-repo")
                .param("platform", "GitHub"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("repoFilter", "test-repo"))
            .andExpect(model().attribute("platformFilter", "GitHub"));
    }

    @Test
    @WithMockUser
    void testDashboardPageFragment() throws Exception {
        // Given
        List<ReviewService.ReviewRunSummary> runs = List.of();
        Page<ReviewService.ReviewRunSummary> page = new PageImpl<>(
            runs, PageRequest.of(1, 5), 0
        );
        
        when(reviewService.getHistoryRuns(any(), anyString(), anyString()))
            .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/dashboard/page")
                .param("page", "1")
                .param("size", "5"))
            .andExpect(status().isOk())
            .andExpect(view().name("fragments/runs-table :: runs-table"))
            .andExpect(model().attribute("currentPage", 1))
            .andExpect(model().attribute("pageSize", 5));
    }

    @Test
    void testDashboardRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/dashboard"))
            .andExpect(status().is3xxRedirection());
    }
}

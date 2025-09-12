package com.ai.reviewer.frontend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI Reviewer Frontend Application.
 * 
 * <p>This is the main entry point for the AI code review frontend web application.
 * It provides a dashboard for viewing review runs, detailed reports, and 
 * managing review configurations.
 * 
 * <p>The frontend runs on a different port than the backend to allow
 * independent deployment and scaling.
 */
@SpringBootApplication
public class FrontendApplication {

    public static void main(String[] args) {
        SpringApplication.run(FrontendApplication.class, args);
    }
}

-- Test database initialization script
CREATE DATABASE IF NOT EXISTS ai_reviewer_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ai_reviewer_test;

-- Basic tables for integration testing
-- Note: In real implementation, these would be created by Flyway migrations

CREATE TABLE IF NOT EXISTS review_run (
    id VARCHAR(255) PRIMARY KEY,
    repo_provider VARCHAR(50) NOT NULL,
    repo_owner VARCHAR(255) NOT NULL,
    repo_name VARCHAR(255) NOT NULL,
    repo_url VARCHAR(500) NOT NULL,
    pull_id VARCHAR(255) NOT NULL,
    pull_number VARCHAR(50) NOT NULL,
    pull_source_branch VARCHAR(255) NOT NULL,
    pull_target_branch VARCHAR(255) NOT NULL,
    pull_sha VARCHAR(255) NOT NULL,
    pull_draft BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    total_score DECIMAL(5,2) DEFAULT 0.0,
    files_changed INT DEFAULT 0,
    lines_added INT DEFAULT 0,
    lines_deleted INT DEFAULT 0,
    latency_ms BIGINT DEFAULT 0,
    token_cost_usd DECIMAL(10,4) DEFAULT NULL,
    INDEX idx_repo_pull (repo_provider, repo_owner, repo_name, pull_number),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB;

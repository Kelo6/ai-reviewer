-- AI Reviewer Database Schema Initialization
-- Version: 1.0.0
-- Description: Create core tables for code review system

-- Set character set and collation for UTF8MB4 support
SET NAMES utf8mb4;
SET character_set_client = utf8mb4;

-- Create review_run table - Main table for review executions
CREATE TABLE review_run (
    run_id VARCHAR(255) NOT NULL PRIMARY KEY COMMENT 'Unique review run identifier',
    
    -- Repository information (embedded from RepoRef)
    repo_provider VARCHAR(50) NOT NULL COMMENT 'SCM provider (github, gitlab, etc.)',
    repo_owner VARCHAR(255) NOT NULL COMMENT 'Repository owner/organization',
    repo_name VARCHAR(255) NOT NULL COMMENT 'Repository name',
    repo_url VARCHAR(500) NOT NULL COMMENT 'Repository URL',
    
    -- Pull request information (embedded from PullRef)  
    pull_id VARCHAR(255) NOT NULL COMMENT 'Pull request internal ID',
    pull_number VARCHAR(50) NOT NULL COMMENT 'Pull request number',
    pull_source_branch VARCHAR(255) NOT NULL COMMENT 'Source branch name',
    pull_target_branch VARCHAR(255) NOT NULL COMMENT 'Target branch name',
    pull_sha VARCHAR(255) NOT NULL COMMENT 'Latest commit SHA',
    pull_draft BOOLEAN DEFAULT FALSE COMMENT 'Whether PR is in draft state',
    
    -- Execution metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Review run creation time',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time',
    status ENUM('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED') DEFAULT 'PENDING' COMMENT 'Review run status',
    provider_keys JSON COMMENT 'List of AI providers used (JSON array)',
    
    -- Statistics
    files_changed INT DEFAULT 0 COMMENT 'Number of files changed',
    lines_added INT DEFAULT 0 COMMENT 'Lines of code added',
    lines_deleted INT DEFAULT 0 COMMENT 'Lines of code deleted',
    latency_ms BIGINT DEFAULT 0 COMMENT 'Total execution time in milliseconds',
    token_cost_usd DECIMAL(9,4) DEFAULT NULL COMMENT 'Estimated token cost in USD',
    
    -- Computed scores
    total_score DECIMAL(5,2) DEFAULT 0.00 COMMENT 'Overall weighted score (0-100)'
) ENGINE=InnoDB 
  DEFAULT CHARSET=utf8mb4 
  COLLATE=utf8mb4_unicode_ci 
  COMMENT='Main table for code review run executions';

-- Create index for repo + pull + time queries
CREATE INDEX idx_review_run_repo_pull_time 
ON review_run (repo_owner, repo_name, pull_number, created_at);

-- Create index for status-based queries
CREATE INDEX idx_review_run_status 
ON review_run (status, created_at);

-- Create index for provider-based analytics
CREATE INDEX idx_review_run_provider 
ON review_run (repo_provider, created_at);

-- Create finding table - Individual code review findings
CREATE TABLE finding (
    id VARCHAR(255) NOT NULL PRIMARY KEY COMMENT 'Unique finding identifier',
    run_id VARCHAR(255) NOT NULL COMMENT 'Reference to review run',
    
    -- File location
    file VARCHAR(1000) NOT NULL COMMENT 'File path where finding was detected',
    start_line INT NOT NULL COMMENT 'Starting line number',
    end_line INT NOT NULL COMMENT 'Ending line number',
    
    -- Classification
    severity ENUM('INFO', 'MINOR', 'MAJOR', 'CRITICAL') NOT NULL COMMENT 'Finding severity level',
    dimension ENUM('SECURITY', 'QUALITY', 'MAINTAINABILITY', 'PERFORMANCE', 'TEST_COVERAGE') NOT NULL COMMENT 'Quality dimension',
    
    -- Content
    title VARCHAR(500) NOT NULL COMMENT 'Brief finding title',
    evidence TEXT NOT NULL COMMENT 'Code evidence supporting the finding',
    suggestion TEXT NOT NULL COMMENT 'Suggested improvement',
    patch TEXT COMMENT 'Suggested code patch (optional)',
    
    -- Metadata
    sources JSON NOT NULL COMMENT 'List of sources/tools that identified this finding',
    confidence DECIMAL(4,3) NOT NULL COMMENT 'Confidence score (0.000-1.000)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Finding creation time',
    
    -- Foreign key constraint
    CONSTRAINT fk_finding_run_id 
        FOREIGN KEY (run_id) REFERENCES review_run(run_id) 
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB 
  DEFAULT CHARSET=utf8mb4 
  COLLATE=utf8mb4_unicode_ci 
  COMMENT='Individual code review findings and suggestions';

-- Create composite index for finding queries
CREATE INDEX idx_finding_run_file_severity 
ON finding (run_id, file(255), severity);

-- Create index for dimension-based analytics  
CREATE INDEX idx_finding_dimension_severity 
ON finding (dimension, severity, confidence);

-- Create score table - Dimensional scores for review runs
CREATE TABLE score (
    run_id VARCHAR(255) NOT NULL COMMENT 'Reference to review run',
    dimension ENUM('SECURITY', 'QUALITY', 'MAINTAINABILITY', 'PERFORMANCE', 'TEST_COVERAGE') NOT NULL COMMENT 'Quality dimension',
    score DECIMAL(5,2) NOT NULL COMMENT 'Dimensional score (0.00-100.00)',
    weight DECIMAL(4,3) NOT NULL COMMENT 'Weight used for this dimension (0.000-1.000)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Score calculation time',
    
    -- Composite primary key
    PRIMARY KEY (run_id, dimension),
    
    -- Foreign key constraint
    CONSTRAINT fk_score_run_id 
        FOREIGN KEY (run_id) REFERENCES review_run(run_id) 
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB 
  DEFAULT CHARSET=utf8mb4 
  COLLATE=utf8mb4_unicode_ci 
  COMMENT='Dimensional scores for code review runs';

-- Create artifact table - Generated reports and artifacts
CREATE TABLE artifact (
    run_id VARCHAR(255) NOT NULL PRIMARY KEY COMMENT 'Reference to review run',
    
    -- Report file paths
    sarif_path VARCHAR(1000) COMMENT 'SARIF report file path',
    report_md_path VARCHAR(1000) COMMENT 'Markdown report file path', 
    report_html_path VARCHAR(1000) COMMENT 'HTML report file path',
    report_pdf_path VARCHAR(1000) COMMENT 'PDF report file path',
    
    -- Additional artifacts
    raw_data_path VARCHAR(1000) COMMENT 'Raw JSON data file path',
    
    -- Metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Artifact creation time',
    file_size_bytes BIGINT DEFAULT 0 COMMENT 'Total size of all artifacts in bytes',
    
    -- Foreign key constraint
    CONSTRAINT fk_artifact_run_id 
        FOREIGN KEY (run_id) REFERENCES review_run(run_id) 
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB 
  DEFAULT CHARSET=utf8mb4 
  COLLATE=utf8mb4_unicode_ci 
  COMMENT='Generated reports and artifacts for review runs';

-- Create additional indexes for performance optimization

-- Index for time-based cleanup operations
CREATE INDEX idx_review_run_created_at ON review_run (created_at);

-- Index for finding confidence filtering
CREATE INDEX idx_finding_confidence ON finding (confidence);

-- Index for artifact file size monitoring
CREATE INDEX idx_artifact_size ON artifact (file_size_bytes, created_at);

-- Create views for common queries

-- View for review run summary with score information
CREATE VIEW review_run_summary AS
SELECT 
    rr.run_id,
    rr.repo_provider,
    rr.repo_owner, 
    rr.repo_name,
    rr.pull_number,
    rr.created_at,
    rr.status,
    rr.files_changed,
    rr.lines_added,
    rr.lines_deleted,
    rr.total_score,
    rr.latency_ms,
    rr.token_cost_usd,
    COUNT(f.id) as finding_count,
    COUNT(CASE WHEN f.severity = 'CRITICAL' THEN 1 END) as critical_count,
    COUNT(CASE WHEN f.severity = 'MAJOR' THEN 1 END) as major_count,
    COUNT(CASE WHEN f.severity = 'MINOR' THEN 1 END) as minor_count,
    COUNT(CASE WHEN f.severity = 'INFO' THEN 1 END) as info_count
FROM review_run rr
LEFT JOIN finding f ON rr.run_id = f.run_id
GROUP BY rr.run_id;

-- Insert initial data for testing (optional)
-- This can be removed in production

-- Sample configuration data could go here if needed
-- INSERT INTO configuration (key, value) VALUES ('system.version', '1.0.0');

-- Create stored procedures for common operations

DELIMITER //

-- Procedure to cleanup old review runs (older than specified days)
CREATE PROCEDURE CleanupOldRuns(IN days_to_keep INT)
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE run_id_to_delete VARCHAR(255);
    DECLARE cur CURSOR FOR 
        SELECT run_id FROM review_run 
        WHERE created_at < DATE_SUB(NOW(), INTERVAL days_to_keep DAY);
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
    
    START TRANSACTION;
    
    OPEN cur;
    cleanup_loop: LOOP
        FETCH cur INTO run_id_to_delete;
        IF done THEN
            LEAVE cleanup_loop;
        END IF;
        
        -- Delete will cascade to related tables
        DELETE FROM review_run WHERE run_id = run_id_to_delete;
    END LOOP;
    CLOSE cur;
    
    COMMIT;
END//

DELIMITER ;

-- Add comments for table relationships
ALTER TABLE finding COMMENT = 'Child table of review_run. Stores individual findings with CASCADE DELETE.';
ALTER TABLE score COMMENT = 'Child table of review_run. Stores dimensional scores with CASCADE DELETE.';  
ALTER TABLE artifact COMMENT = 'Child table of review_run. Stores generated artifacts with CASCADE DELETE.';

-- Database initialization completed successfully

-- 创建模型配置表
CREATE TABLE IF NOT EXISTS model_config (
    id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    display_name VARCHAR(200),
    type ENUM('COMMERCIAL_AI', 'LOCAL_AI', 'STATIC_ANALYZER') NOT NULL,
    description TEXT,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    status ENUM('CONFIGURED', 'CONNECTED', 'ERROR', 'DISABLED') DEFAULT 'CONFIGURED',
    
    -- AI模型配置
    api_url VARCHAR(500),
    api_key VARCHAR(500),
    model_name VARCHAR(200),
    model_version VARCHAR(100),
    max_tokens INT,
    temperature DOUBLE,
    top_p DOUBLE,
    timeout_seconds INT,
    max_concurrent_requests INT,
    
    -- 静态分析工具配置
    tool_path VARCHAR(500),
    config_file VARCHAR(500),
    supported_languages TEXT,
    supported_file_types TEXT,
    supported_dimensions TEXT,
    exclude_patterns TEXT,
    
    -- 高级配置
    advanced_settings TEXT,
    
    -- 统计信息
    total_requests BIGINT DEFAULT 0,
    success_rate DOUBLE DEFAULT 0.0,
    average_response_time DOUBLE DEFAULT 0.0,
    estimated_cost DOUBLE DEFAULT 0.0,
    last_error TEXT,
    
    -- 时间戳
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    
    -- 索引
    INDEX idx_model_config_type (type),
    INDEX idx_model_config_enabled (enabled),
    INDEX idx_model_config_status (status),
    INDEX idx_model_config_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

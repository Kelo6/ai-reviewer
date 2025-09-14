-- SCM配置表
CREATE TABLE scm_config (
    provider VARCHAR(50) NOT NULL,
    display_name VARCHAR(100),
    api_base VARCHAR(500),
    web_base VARCHAR(500),
    token TEXT,
    username VARCHAR(200),
    app_password TEXT,
    client_id VARCHAR(200),
    client_secret TEXT,
    webhook_secret TEXT,
    ssl_verify BOOLEAN DEFAULT TRUE,
    api_type VARCHAR(50),
    enabled BOOLEAN DEFAULT FALSE,
    status VARCHAR(50) DEFAULT 'not_configured',
    status_message VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建索引以提高查询性能
CREATE INDEX idx_scm_config_enabled ON scm_config(enabled);
CREATE INDEX idx_scm_config_status ON scm_config(status);
CREATE INDEX idx_scm_config_provider_pattern ON scm_config(provider);

-- 插入默认的支持的SCM提供商配置
INSERT INTO scm_config (provider, display_name, api_base, web_base, enabled, status, created_at, updated_at) VALUES
('github', 'GitHub', 'https://api.github.com', 'https://github.com', FALSE, 'not_configured', NOW(6), NOW(6)),
('gitlab', 'GitLab', 'https://gitlab.com/api/v4', 'https://gitlab.com', FALSE, 'not_configured', NOW(6), NOW(6)),
('bitbucket', 'Bitbucket', 'https://api.bitbucket.org/2.0', 'https://bitbucket.org', FALSE, 'not_configured', NOW(6), NOW(6)),
('gitea', 'Gitea', '', '', FALSE, 'not_configured', NOW(6), NOW(6)),
('custom-git', '自建Git', '', '', FALSE, 'not_configured', NOW(6), NOW(6));

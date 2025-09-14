package com.ai.reviewer.backend.infra.adapter;

import com.ai.reviewer.backend.infra.jpa.entity.ScmConfigEntity;
import com.ai.reviewer.backend.web.dto.ScmConfigDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * SCM配置实体与DTO之间的转换适配器
 */
@Component
public class ScmConfigAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(ScmConfigAdapter.class);
    
    /**
     * 将实体转换为DTO
     */
    public ScmConfigDto toDto(ScmConfigEntity entity) {
        if (entity == null) {
            return null;
        }
        
        ScmConfigDto dto = new ScmConfigDto();
        
        // 基础字段
        dto.setProvider(entity.getProvider());
        dto.setDisplayName(entity.getDisplayName());
        dto.setApiBase(entity.getApiBase());
        dto.setWebBase(entity.getWebBase());
        dto.setEnabled(entity.getEnabled());
        dto.setStatus(entity.getStatus());
        dto.setStatusMessage(entity.getStatusMessage());
        
        // 认证信息（敏感信息需要谨慎处理）
        dto.setToken(entity.getToken());
        dto.setUsername(entity.getUsername());
        dto.setAppPassword(entity.getAppPassword());
        dto.setClientId(entity.getClientId());
        dto.setClientSecret(entity.getClientSecret());
        dto.setWebhookSecret(entity.getWebhookSecret());
        
        // 配置选项
        dto.setSslVerify(entity.getSslVerify());
        dto.setApiType(entity.getApiType());
        
        return dto;
    }
    
    /**
     * 将DTO转换为实体
     */
    public ScmConfigEntity toEntity(ScmConfigDto dto) {
        if (dto == null) {
            return null;
        }
        
        ScmConfigEntity entity = new ScmConfigEntity();
        
        // 基础字段
        entity.setProvider(dto.getProvider());
        entity.setDisplayName(dto.getDisplayName());
        entity.setApiBase(dto.getApiBase());
        entity.setWebBase(dto.getWebBase());
        entity.setEnabled(dto.getEnabled());
        entity.setStatus(dto.getStatus());
        entity.setStatusMessage(dto.getStatusMessage());
        
        // 认证信息
        entity.setToken(dto.getToken());
        entity.setUsername(dto.getUsername());
        entity.setAppPassword(dto.getAppPassword());
        entity.setClientId(dto.getClientId());
        entity.setClientSecret(dto.getClientSecret());
        entity.setWebhookSecret(dto.getWebhookSecret());
        
        // 配置选项
        entity.setSslVerify(dto.getSslVerify());
        entity.setApiType(dto.getApiType());
        
        return entity;
    }
    
    /**
     * 更新实体字段（用于更新操作）
     */
    public void updateEntity(ScmConfigEntity entity, ScmConfigDto dto) {
        if (entity == null || dto == null) {
            return;
        }
        
        // 更新可变字段
        entity.setDisplayName(dto.getDisplayName());
        entity.setApiBase(dto.getApiBase());
        entity.setWebBase(dto.getWebBase());
        entity.setEnabled(dto.getEnabled());
        entity.setStatus(dto.getStatus());
        entity.setStatusMessage(dto.getStatusMessage());
        
        // 更新认证信息
        entity.setToken(dto.getToken());
        entity.setUsername(dto.getUsername());
        entity.setAppPassword(dto.getAppPassword());
        entity.setClientId(dto.getClientId());
        entity.setClientSecret(dto.getClientSecret());
        entity.setWebhookSecret(dto.getWebhookSecret());
        
        // 更新配置选项
        entity.setSslVerify(dto.getSslVerify());
        entity.setApiType(dto.getApiType());
    }
    
    /**
     * 将DTO转换为安全的DTO（隐藏敏感信息）
     */
    public ScmConfigDto toSafeDto(ScmConfigDto dto) {
        if (dto == null) {
            return null;
        }
        
        ScmConfigDto safeDto = new ScmConfigDto();
        
        // 复制非敏感字段
        safeDto.setProvider(dto.getProvider());
        safeDto.setDisplayName(dto.getDisplayName());
        safeDto.setApiBase(dto.getApiBase());
        safeDto.setWebBase(dto.getWebBase());
        safeDto.setEnabled(dto.getEnabled());
        safeDto.setStatus(dto.getStatus());
        safeDto.setStatusMessage(dto.getStatusMessage());
        safeDto.setSslVerify(dto.getSslVerify());
        safeDto.setApiType(dto.getApiType());
        
        // 敏感信息进行掩码处理
        safeDto.setToken(maskSensitiveData(dto.getToken()));
        safeDto.setUsername(dto.getUsername()); // 用户名通常不敏感
        safeDto.setAppPassword(maskSensitiveData(dto.getAppPassword()));
        safeDto.setClientId(dto.getClientId()); // Client ID 通常不敏感
        safeDto.setClientSecret(maskSensitiveData(dto.getClientSecret()));
        safeDto.setWebhookSecret(maskSensitiveData(dto.getWebhookSecret()));
        
        return safeDto;
    }
    
    /**
     * 掩码敏感数据
     */
    private String maskSensitiveData(String data) {
        if (data == null || data.trim().isEmpty()) {
            return data;
        }
        
        if (data.length() <= 4) {
            return "****";
        }
        
        // 显示前2位和后2位，中间用*代替
        return data.substring(0, 2) + "****" + data.substring(data.length() - 2);
    }
}

package com.ai.reviewer.backend.infra.jpa.repository;

import com.ai.reviewer.backend.infra.jpa.entity.ScmConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * SCM配置数据库访问接口
 */
@Repository
public interface ScmConfigRepository extends JpaRepository<ScmConfigEntity, String> {
    
    /**
     * 查找已启用的配置
     */
    List<ScmConfigEntity> findByEnabledTrue();
    
    /**
     * 根据状态查找配置
     */
    List<ScmConfigEntity> findByStatus(String status);
    
    /**
     * 查找已连接的配置
     */
    @Query("SELECT s FROM ScmConfigEntity s WHERE s.enabled = true AND s.status = 'connected'")
    List<ScmConfigEntity> findConnectedConfigs();
    
    /**
     * 根据提供商类型查找
     */
    @Query("SELECT s FROM ScmConfigEntity s WHERE s.provider LIKE :providerPattern")
    List<ScmConfigEntity> findByProviderPattern(@Param("providerPattern") String providerPattern);
    
    /**
     * 统计各状态的配置数量
     */
    @Query("SELECT s.status, COUNT(s) FROM ScmConfigEntity s GROUP BY s.status")
    List<Object[]> countByStatus();
    
    /**
     * 检查是否存在已启用的配置
     */
    boolean existsByEnabledTrue();
    
    /**
     * 查找所有Git类型的配置（包括custom-git和其他自建Git）
     */
    @Query("SELECT s FROM ScmConfigEntity s WHERE s.provider = 'custom-git' OR s.provider LIKE 'custom-%'")
    List<ScmConfigEntity> findCustomGitConfigs();
}

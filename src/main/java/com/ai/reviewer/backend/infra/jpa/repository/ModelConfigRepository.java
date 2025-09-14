package com.ai.reviewer.backend.infra.jpa.repository;

import com.ai.reviewer.backend.infra.jpa.entity.ModelConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 模型配置数据库访问接口
 */
@Repository
public interface ModelConfigRepository extends JpaRepository<ModelConfigEntity, String> {
    
    /**
     * 根据类型查找模型配置
     */
    List<ModelConfigEntity> findByType(ModelConfigEntity.ModelType type);
    
    /**
     * 查找已启用的模型配置
     */
    List<ModelConfigEntity> findByEnabledTrue();
    
    /**
     * 根据状态查找模型配置
     */
    List<ModelConfigEntity> findByStatus(ModelConfigEntity.ModelStatus status);
    
    /**
     * 根据类型和启用状态查找模型配置
     */
    List<ModelConfigEntity> findByTypeAndEnabled(ModelConfigEntity.ModelType type, Boolean enabled);
    
    /**
     * 根据名称模糊查找模型配置
     */
    @Query("SELECT m FROM ModelConfigEntity m WHERE m.name LIKE %:name% OR m.displayName LIKE %:name%")
    List<ModelConfigEntity> findByNameContaining(@Param("name") String name);
    
    /**
     * 统计各类型模型数量
     */
    @Query("SELECT m.type, COUNT(m) FROM ModelConfigEntity m GROUP BY m.type")
    List<Object[]> countByType();
    
    /**
     * 统计各状态模型数量
     */
    @Query("SELECT m.status, COUNT(m) FROM ModelConfigEntity m GROUP BY m.status")
    List<Object[]> countByStatus();
    
    /**
     * 查找所有已启用且已连接的模型
     */
    @Query("SELECT m FROM ModelConfigEntity m WHERE m.enabled = true AND m.status = :status")
    List<ModelConfigEntity> findEnabledByStatus(@Param("status") ModelConfigEntity.ModelStatus status);
    
    /**
     * 根据API URL查找模型（避免重复配置）
     */
    Optional<ModelConfigEntity> findByApiUrl(String apiUrl);
    
    /**
     * 根据工具路径查找静态分析器（避免重复配置）
     */
    Optional<ModelConfigEntity> findByToolPath(String toolPath);
    
    /**
     * 统计总的成本估算
     */
    @Query("SELECT SUM(m.estimatedCost) FROM ModelConfigEntity m WHERE m.estimatedCost IS NOT NULL")
    Double sumEstimatedCost();
    
    /**
     * 计算平均响应时间
     */
    @Query("SELECT AVG(m.averageResponseTime) FROM ModelConfigEntity m WHERE m.averageResponseTime IS NOT NULL AND m.averageResponseTime > 0")
    Double averageResponseTime();
}

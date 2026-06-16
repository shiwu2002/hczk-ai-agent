package com.hczk.hczkaiagentserver.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器
 * 在插入和更新记录时自动填充 createdAt、updatedAt 时间字段
 * 需配合实体字段上的 @TableField(fill = FieldFill.INSERT) 等注解使用
 */
@Component
public class AutoFillHandler implements MetaObjectHandler {

    /** 插入时自动填充 createdAt 和 updatedAt */
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }

    /** 更新时自动填充 updatedAt */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}

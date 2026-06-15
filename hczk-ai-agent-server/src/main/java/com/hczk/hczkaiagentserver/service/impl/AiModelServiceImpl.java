package com.hczk.hczkaiagentserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hczk.hczkaiagentserver.entity.AiModel;
import com.hczk.hczkaiagentserver.enums.ModelStatus;
import com.hczk.hczkaiagentserver.mapper.AiModelMapper;
import com.hczk.hczkaiagentserver.service.AiModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI模型服务实现类
 *
 * 提供模型的增删改查功能。
 * 模型不再设置定价，计费由API Key的unitPrice统一管理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelServiceImpl implements AiModelService {

    /** 模型数据访问 */
    private final AiModelMapper aiModelMapper;

    /**
     * 获取所有模型列表
     *
     * @return 模型列表
     */
    @Override
    public List<AiModel> getAllModels() {
        return aiModelMapper.selectList(null);
    }

    /**
     * 根据ID获取模型
     *
     * @param id 模型ID
     * @return 模型实体
     * @throws RuntimeException 模型不存在
     */
    @Override
    public AiModel getModelById(Long id) {
        AiModel model = aiModelMapper.selectById(id);
        if (model == null) {
            throw new RuntimeException("模型不存在");
        }
        return model;
    }

    /**
     * 创建模型
     *
     * 新建模型默认启用（status=0），防止Jackson反序列化覆盖Java默认值
     *
     * @param model 模型实体（前端传入，不含id和status）
     * @return 创建后的模型（含自增id）
     */
    @Override
    @Transactional
    public AiModel createModel(AiModel model) {
        // 确保新建模型默认启用
        if (model.getStatus() == null) {
            model.setStatus(ModelStatus.ACTIVE);
        }
        aiModelMapper.insert(model);
        log.info("创建模型成功: id={}, name={}, status={}", model.getId(), model.getName(), model.getStatus());
        return model;
    }

    /**
     * 更新模型配置
     *
     * 不更新status字段，状态变更通过toggleStatus接口操作
     *
     * @param id    模型ID
     * @param model 更新数据
     * @return 更新后的模型
     */
    @Override
    @Transactional
    public AiModel updateModel(Long id, AiModel model) {
        AiModel existing = getModelById(id);

        existing.setName(model.getName());
        existing.setProvider(model.getProvider());
        existing.setProviderType(model.getProviderType());
        existing.setModelId(model.getModelId());
        existing.setApiBase(model.getApiBase());
        existing.setApiKey(model.getApiKey());
        existing.setMaxTokens(model.getMaxTokens());
        existing.setThinking(model.getThinking());

        aiModelMapper.updateById(existing);
        log.info("更新模型成功: id={}, name={}", existing.getId(), existing.getName());
        return existing;
    }

    /**
     * 删除模型
     *
     * @param id 模型ID
     */
    @Override
    @Transactional
    public void deleteModel(Long id) {
        aiModelMapper.deleteById(id);
    }

    /**
     * 切换模型启停状态
     *
     * ACTIVE(0) ↔ INACTIVE(1)
     *
     * @param id 模型ID
     * @return 更新后的模型
     */
    @Override
    @Transactional
    public AiModel toggleStatus(Long id) {
        AiModel model = getModelById(id);

        model.setStatus(model.getStatus() == ModelStatus.ACTIVE ? ModelStatus.INACTIVE : ModelStatus.ACTIVE);
        aiModelMapper.updateById(model);

        log.info("切换模型状态: id={}, name={}, status={}", model.getId(), model.getName(), model.getStatus());
        return model;
    }
}
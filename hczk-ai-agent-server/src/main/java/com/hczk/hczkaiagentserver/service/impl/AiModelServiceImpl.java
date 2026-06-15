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

    private final AiModelMapper aiModelMapper;

    @Override
    public List<AiModel> getAllModels() {
        return aiModelMapper.selectList(null);
    }

    @Override
    public AiModel getModelById(Long id) {
        AiModel model = aiModelMapper.selectById(id);
        if (model == null) {
            throw new RuntimeException("模型不存在");
        }
        return model;
    }

    @Override
    @Transactional
    public AiModel createModel(AiModel model) {
        aiModelMapper.insert(model);
        log.info("创建模型成功: id={}, name={}", model.getId(), model.getName());
        return model;
    }

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

    @Override
    @Transactional
    public void deleteModel(Long id) {
        aiModelMapper.deleteById(id);
    }

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
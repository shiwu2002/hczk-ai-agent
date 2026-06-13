package com.hczk.hczkaiagentserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hczk.hczkaiagentserver.entity.AiModel;
import com.hczk.hczkaiagentserver.enums.ModelStatus;
import com.hczk.hczkaiagentserver.mapper.AiModelMapper;
import com.hczk.hczkaiagentserver.service.AiModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
        existing.setInputPrice(model.getInputPrice());
        existing.setOutputPrice(model.getOutputPrice());
        existing.setMaxTokens(model.getMaxTokens());
        existing.setThinking(model.getThinking());
        aiModelMapper.updateById(existing);
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
        return model;
    }
}

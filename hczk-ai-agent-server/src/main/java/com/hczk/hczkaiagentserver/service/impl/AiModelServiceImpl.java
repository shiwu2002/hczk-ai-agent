package com.hczk.hczkaiagentserver.service.impl;

import com.hczk.hczkaiagentserver.entity.AiModel;
import com.hczk.hczkaiagentserver.enums.ModelStatus;
import com.hczk.hczkaiagentserver.repository.AiModelRepository;
import com.hczk.hczkaiagentserver.service.AiModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiModelServiceImpl implements AiModelService {

    private final AiModelRepository aiModelRepository;

    @Override
    public List<AiModel> getAllModels() {
        return aiModelRepository.findAll();
    }

    @Override
    public AiModel getModelById(Long id) {
        return aiModelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("模型不存在"));
    }

    @Override
    @Transactional
    public AiModel createModel(AiModel model) {
        return aiModelRepository.save(model);
    }

    @Override
    @Transactional
    public AiModel updateModel(Long id, AiModel model) {
        AiModel existing = getModelById(id);
        existing.setName(model.getName());
        existing.setProvider(model.getProvider());
        existing.setModelId(model.getModelId());
        existing.setApiBase(model.getApiBase());
        existing.setApiKey(model.getApiKey());
        existing.setInputPrice(model.getInputPrice());
        existing.setOutputPrice(model.getOutputPrice());
        existing.setMaxTokens(model.getMaxTokens());
        existing.setThinking(model.getThinking());
        return aiModelRepository.save(existing);
    }

    @Override
    @Transactional
    public void deleteModel(Long id) {
        aiModelRepository.deleteById(id);
    }

    @Override
    @Transactional
    public AiModel toggleStatus(Long id) {
        AiModel model = getModelById(id);
        model.setStatus(model.getStatus() == ModelStatus.ACTIVE ? ModelStatus.INACTIVE : ModelStatus.ACTIVE);
        return aiModelRepository.save(model);
    }
}

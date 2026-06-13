package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.entity.AiModel;

import java.util.List;

public interface AiModelService {
    List<AiModel> getAllModels();
    AiModel getModelById(Long id);
    AiModel createModel(AiModel model);
    AiModel updateModel(Long id, AiModel model);
    void deleteModel(Long id);
    AiModel toggleStatus(Long id);
}

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

import java.math.BigDecimal;
import java.util.List;

/**
 * AI模型服务实现类
 * 
 * 提供模型的增删改查功能，强制要求模型必须设置定价才能启用。
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
        validatePricing(model);
        aiModelMapper.insert(model);
        log.info("创建模型成功: id={}, name={}, inputPrice={}, outputPrice={}", 
                model.getId(), model.getName(), model.getInputPrice(), model.getOutputPrice());
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
        
        if (model.getInputPrice() != null || model.getOutputPrice() != null) {
            validatePricing(model);
            existing.setInputPrice(model.getInputPrice());
            existing.setOutputPrice(model.getOutputPrice());
        }
        
        aiModelMapper.updateById(existing);
        log.info("更新模型成功: id={}, name={}, inputPrice={}, outputPrice={}", 
                existing.getId(), existing.getName(), existing.getInputPrice(), existing.getOutputPrice());
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
        
        if (model.getStatus() == ModelStatus.INACTIVE) {
            validatePricing(model);
        }
        
        model.setStatus(model.getStatus() == ModelStatus.ACTIVE ? ModelStatus.INACTIVE : ModelStatus.ACTIVE);
        aiModelMapper.updateById(model);
        
        log.info("切换模型状态: id={}, name={}, status={}", model.getId(), model.getName(), model.getStatus());
        return model;
    }

    /**
     * 验证模型定价是否有效
     * 
     * 模型必须同时设置输入价格和输出价格，且价格必须大于0。
     * 
     * @param model AI模型
     * @throws IllegalArgumentException 如果定价无效
     */
    private void validatePricing(AiModel model) {
        if (model.getInputPrice() == null) {
            throw new IllegalArgumentException("输入价格(inputPrice)不能为空");
        }
        if (model.getOutputPrice() == null) {
            throw new IllegalArgumentException("输出价格(outputPrice)不能为空");
        }
        if (model.getInputPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("输入价格必须大于0");
        }
        if (model.getOutputPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("输出价格必须大于0");
        }
    }
}
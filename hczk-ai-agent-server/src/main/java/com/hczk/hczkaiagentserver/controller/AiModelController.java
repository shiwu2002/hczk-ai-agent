package com.hczk.hczkaiagentserver.controller;

import com.hczk.hczkaiagentserver.common.Result;
import com.hczk.hczkaiagentserver.entity.AiModel;
import com.hczk.hczkaiagentserver.service.AiModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI模型管理控制器
 * 提供模型的增删改查、状态切换等管理接口
 */
@RestController
@RequestMapping("/models")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AiModelController {

    private final AiModelService aiModelService;

    /** 获取所有模型列表 */
    @GetMapping
    public Result<List<AiModel>> getAllModels() {
        return Result.success(aiModelService.getAllModels());
    }

    /** 根据ID获取模型详情 */
    @GetMapping("/{id}")
    public Result<AiModel> getModelById(@PathVariable Long id) {
        return Result.success(aiModelService.getModelById(id));
    }

    /** 新增模型 */
    @PostMapping
    public Result<AiModel> createModel(@RequestBody AiModel model) {
        return Result.success(aiModelService.createModel(model));
    }

    /** 更新模型信息 */
    @PutMapping("/{id}")
    public Result<AiModel> updateModel(@PathVariable Long id, @RequestBody AiModel model) {
        return Result.success(aiModelService.updateModel(id, model));
    }

    /** 删除模型 */
    @DeleteMapping("/{id}")
    public Result<Void> deleteModel(@PathVariable Long id) {
        aiModelService.deleteModel(id);
        return Result.success();
    }

    /** 切换模型启用/禁用状态 */
    @PostMapping("/{id}/toggle")
    public Result<AiModel> toggleStatus(@PathVariable Long id) {
        return Result.success(aiModelService.toggleStatus(id));
    }
}

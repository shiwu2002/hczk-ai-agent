package com.hczk.hczkaiagentserver.controller;

import com.hczk.hczkaiagentserver.common.Result;
import com.hczk.hczkaiagentserver.entity.AiModel;
import com.hczk.hczkaiagentserver.service.AiModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/models")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AiModelController {

    private final AiModelService aiModelService;

    @GetMapping
    public Result<List<AiModel>> getAllModels() {
        return Result.success(aiModelService.getAllModels());
    }

    @GetMapping("/{id}")
    public Result<AiModel> getModelById(@PathVariable Long id) {
        return Result.success(aiModelService.getModelById(id));
    }

    @PostMapping
    public Result<AiModel> createModel(@RequestBody AiModel model) {
        return Result.success(aiModelService.createModel(model));
    }

    @PutMapping("/{id}")
    public Result<AiModel> updateModel(@PathVariable Long id, @RequestBody AiModel model) {
        return Result.success(aiModelService.updateModel(id, model));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteModel(@PathVariable Long id) {
        aiModelService.deleteModel(id);
        return Result.success();
    }

    @PostMapping("/{id}/toggle")
    public Result<AiModel> toggleStatus(@PathVariable Long id) {
        return Result.success(aiModelService.toggleStatus(id));
    }
}

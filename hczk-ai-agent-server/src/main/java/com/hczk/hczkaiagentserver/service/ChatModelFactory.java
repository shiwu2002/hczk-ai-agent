package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.entity.AiModel;
import com.hczk.hczkaiagentserver.enums.ModelProviderType;
import com.shiwu.ai.anthropic.AnthropicChatModel;
import com.shiwu.ai.modelscope.ModelScopeWebClientChatModel;
import com.shiwu.ai.openai.OpenAiCompatibleChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ChatModelFactory {

    private final ConcurrentHashMap<Long, ChatModel> cache = new ConcurrentHashMap<>();

    public ChatModel getOrCreate(AiModel model) {
        return cache.computeIfAbsent(model.getId(), id -> createChatModel(model));
    }

    public void evict(Long modelId) {
        cache.remove(modelId);
    }

    private ChatModel createChatModel(AiModel model) {
        ModelProviderType type = model.getProviderType();
        if (type == null) {
            type = ModelProviderType.OPENAI_COMPATIBLE;
        }

        String apiBase = model.getApiBase();
        if (apiBase == null || apiBase.isBlank()) {
            throw new RuntimeException("模型未配置 API Base URL");
        }
        String fullUrl = apiBase.endsWith("/") ? apiBase + "chat/completions" : apiBase + "/chat/completions";

        int maxTokens = model.getMaxTokens() != null ? model.getMaxTokens() : 4096;

        log.info("创建 ChatModel: providerType={}, model={}, url={}", type, model.getModelId(), fullUrl);

        return switch (type) {
            case ANTHROPIC -> createAnthropicModel(model, fullUrl, maxTokens);
            case MODELSCOPE -> createModelScopeModel(model, apiBase);
            default -> createOpenAiCompatibleModel(model, fullUrl, maxTokens);
        };
    }

    private ChatModel createOpenAiCompatibleModel(AiModel model, String fullUrl, int maxTokens) {
        OpenAiCompatibleChatModel chatModel = new OpenAiCompatibleChatModel(
                fullUrl, model.getModelId(), maxTokens, 0.7, model.getApiKey()
        );
        if (Boolean.TRUE.equals(model.getThinking())) {
            chatModel.setEnableThinking(true);
        }
        return chatModel;
    }

    private ChatModel createAnthropicModel(AiModel model, String fullUrl, int maxTokens) {
        AnthropicChatModel chatModel = new AnthropicChatModel(
                fullUrl, model.getModelId(), maxTokens, 0.7, model.getApiKey()
        );
        if (Boolean.TRUE.equals(model.getThinking())) {
            chatModel.setEnableThinking(true);
        }
        return chatModel;
    }

    private ChatModel createModelScopeModel(AiModel model, String apiBase) {
        WebClient webClient = WebClient.builder()
                .baseUrl(apiBase)
                .defaultHeader("Authorization", "Bearer " + model.getApiKey())
                .build();
        return new ModelScopeWebClientChatModel(webClient, model.getModelId(), true, true);
    }
}

package com.hczk.hczkaiagentserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hczk.hczkaiagentserver.dto.ChatRequest;
import com.hczk.hczkaiagentserver.entity.AiModel;
import com.hczk.hczkaiagentserver.enums.ModelStatus;
import com.hczk.hczkaiagentserver.repository.AiModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final AiModelRepository aiModelRepository;
    private final ObjectMapper objectMapper;

    public SseEmitter streamChat(ChatRequest request) {
        AiModel model = aiModelRepository.findById(request.getModelId())
                .orElseThrow(() -> new RuntimeException("模型不存在"));

        if (model.getStatus() != ModelStatus.ACTIVE) {
            throw new RuntimeException("模型已停用");
        }

        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean completed = new AtomicBoolean(false);

        emitter.onCompletion(() -> completed.set(true));
        emitter.onTimeout(() -> completed.set(true));
        emitter.onError(e -> completed.set(true));

        new Thread(() -> {
            try {
                String apiBase = model.getApiBase();
                if (apiBase == null || apiBase.isBlank()) {
                    throw new RuntimeException("模型未配置 API Base URL");
                }
                String endpoint = apiBase.endsWith("/") ? apiBase + "chat/completions" : apiBase + "/chat/completions";

                String bodyJson = buildRequestBody(model, request);

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Authorization", "Bearer " + model.getApiKey())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<java.io.InputStream> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                    safeCompleteWithError(emitter, completed, new RuntimeException("上游接口错误: " + response.statusCode() + " " + errorBody));
                    return;
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.startsWith("data: ")) {
                            continue;
                        }
                        String data = line.substring(6);
                        if ("[DONE]".equals(data)) {
                            safeSend(emitter, completed, SseEmitter.event().name("done").data("[DONE]"));
                            safeComplete(emitter, completed);
                            return;
                        }
                        if (!safeSend(emitter, completed, SseEmitter.event().data(data))) {
                            return;
                        }
                    }
                    safeComplete(emitter, completed);
                }
            } catch (Exception e) {
                safeCompleteWithError(emitter, completed, e);
            }
        }).start();

        return emitter;
    }

    private boolean safeSend(SseEmitter emitter, AtomicBoolean completed, Object data) {
        if (completed.get()) {
            return false;
        }
        try {
            emitter.send(data);
            return true;
        } catch (Exception e) {
            completed.set(true);
            return false;
        }
    }

    private void safeComplete(SseEmitter emitter, AtomicBoolean completed) {
        if (completed.compareAndSet(false, true)) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        }
    }

    private void safeCompleteWithError(SseEmitter emitter, AtomicBoolean completed, Exception e) {
        if (completed.compareAndSet(false, true)) {
            try {
                emitter.completeWithError(e);
            } catch (Exception ignored) {
            }
        }
    }

    private String buildRequestBody(AiModel model, ChatRequest request) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model.getModelId());
        body.put("messages", List.of(Map.of("role", "user", "content", request.getMessage())));
        body.put("stream", true);
        if (Boolean.TRUE.equals(model.getThinking())) {
            body.put("enable_thinking", true);
        }
        return objectMapper.writeValueAsString(body);
    }
}

package com.hczk.hczkaiagentserver.controller;

import com.hczk.hczkaiagentserver.dto.ChatRequest;
import com.hczk.hczkaiagentserver.entity.Agent;
import com.hczk.hczkaiagentserver.entity.ApiKey;
import com.hczk.hczkaiagentserver.entity.MerchantAgentBinding;
import com.hczk.hczkaiagentserver.service.AgentService;
import com.hczk.hczkaiagentserver.service.ApiKeyService;
import com.hczk.hczkaiagentserver.service.ChatService;
import com.hczk.hczkaiagentserver.service.MerchantAgentBindingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 聊天接口控制器
 * 支持三种调用方式：
 * 1. 内部调用：/chat/completions（JWT 认证，使用 modelId）
 * 2. OpenAI 兼容调用：/v1/chat/completions（API Key 认证）
 * 3. 平台路由调用：/api/chat（通过 ApiKey 路由到绑定的智能体）
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;
    private final ApiKeyService apiKeyService;
    private final AgentService agentService;
    private final MerchantAgentBindingService bindingService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    /**
     * 内部聊天接口（管理后台/前端使用）
     */
    @PostMapping(value = "/chat/completions", produces = {MediaType.TEXT_EVENT_STREAM_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public Object completions(@RequestBody ChatRequest request) {
        if (Boolean.FALSE.equals(request.getStream())) {
            return chatService.chat(request);
        }
        return chatService.streamChat(request);
    }

    /**
     * OpenAI 兼容格式接口（外部 API 调用）
     */
    @PostMapping(value = "/v1/chat/completions", produces = {MediaType.TEXT_EVENT_STREAM_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public Object v1Completions(@RequestBody ChatRequest request) {
        if (Boolean.FALSE.equals(request.getStream())) {
            return chatService.chat(request);
        }
        return chatService.streamChat(request);
    }

    /**
     * 平台路由聊天接口（同步，通过 ApiKey 路由到绑定的智能体）
     */
    @PostMapping("/api/chat")
    public ResponseEntity<?> platformChat(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> request) {
        return doPlatformChat(authHeader, request, false);
    }

    /**
     * 平台路由流式聊天接口（SSE，通过 ApiKey 路由到绑定的智能体）
     * 如果智能体配置了 streamEndpoint，则转发到该端点
     * 否则降级为同步调用
     */
    @PostMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Object platformChatStream(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> request) {

        String apiKey = extractApiKey(authHeader);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Authorization header 不能为空"));
        }

        String message = (String) request.get("message");
        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message 不能为空"));
        }

        try {
            ApiKey key = apiKeyService.getActiveApiKey(apiKey);
            Long userId = key.getUserId();

            var bindingOpt = bindingService.findByMerchantId(key.getName());
            if (bindingOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "商家未绑定智能体"));
            }

            MerchantAgentBinding binding = bindingOpt.get();
            if (!binding.getEnabled()) {
                return ResponseEntity.badRequest().body(Map.of("error", "商家智能体绑定已禁用"));
            }

            if (binding.getAgentId() != null) {
                Agent agent = agentService.getAgentById(binding.getAgentId());
                String streamUrl = agent.getStreamEndpoint();

                if (streamUrl != null && !streamUrl.trim().isEmpty()) {
                    // 智能体支持流式，代理转发 SSE
                    return proxySse(agent, userId, request, binding);
                } else {
                    // 降级：同步调用，包装为 SSE 格式返回
                    return fallbackSse(agent, userId, request, binding);
                }
            }

            return ResponseEntity.badRequest().body(Map.of("error", "商家绑定配置不支持流式"));
        } catch (Exception e) {
            log.error("流式聊天认证失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "ApiKey 无效或已禁用"));
        }
    }

    /**
     * 获取会话历史（代理转发到智能体的 historyEndpoint）
     */
    @GetMapping("/api/chat/history/{sessionId}")
    public ResponseEntity<?> getChatHistory(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String sessionId) {

        String apiKey = extractApiKey(authHeader);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Authorization header 不能为空"));
        }

        try {
            ApiKey key = apiKeyService.getActiveApiKey(apiKey);
            var bindingOpt = bindingService.findByMerchantId(key.getName());
            if (bindingOpt.isEmpty() || bindingOpt.get().getAgentId() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "未绑定智能体"));
            }

            Agent agent = agentService.getAgentById(bindingOpt.get().getAgentId());
            String historyUrl = agent.getHistoryEndpoint();
            if (historyUrl == null || historyUrl.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "智能体不支持会话历史"));
            }

            // 拼接完整 URL：historyEndpoint/sessionId
            String fullUrl = historyUrl.replaceAll("/+$", "") + "/" + sessionId;
            HttpHeaders headers = new HttpHeaders();
            if (agent.getAuthHeader() != null && !agent.getAuthHeader().trim().isEmpty()) {
                headers.set("Authorization", agent.getAuthHeader());
            }

            @SuppressWarnings("unchecked")
            ResponseEntity<Map> response = restTemplate.exchange(fullUrl, HttpMethod.GET,
                    new HttpEntity<>(headers), Map.class);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.error("获取会话历史失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取会话历史失败: " + e.getMessage()));
        }
    }

    /**
     * 清除会话（代理转发到智能体的 historyEndpoint，DELETE 方法）
     */
    @DeleteMapping("/api/chat/sessions/{sessionId}")
    public ResponseEntity<?> clearSession(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String sessionId) {

        String apiKey = extractApiKey(authHeader);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Authorization header 不能为空"));
        }

        try {
            ApiKey key = apiKeyService.getActiveApiKey(apiKey);
            var bindingOpt = bindingService.findByMerchantId(key.getName());
            if (bindingOpt.isEmpty() || bindingOpt.get().getAgentId() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "未绑定智能体"));
            }

            Agent agent = agentService.getAgentById(bindingOpt.get().getAgentId());
            String historyUrl = agent.getHistoryEndpoint();
            if (historyUrl == null || historyUrl.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "智能体不支持会话管理"));
            }

            String fullUrl = historyUrl.replaceAll("/+$", "") + "/" + sessionId;
            HttpHeaders headers = new HttpHeaders();
            if (agent.getAuthHeader() != null && !agent.getAuthHeader().trim().isEmpty()) {
                headers.set("Authorization", agent.getAuthHeader());
            }

            @SuppressWarnings("unchecked")
            ResponseEntity<Map> response = restTemplate.exchange(fullUrl, HttpMethod.DELETE,
                    new HttpEntity<>(headers), Map.class);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.error("清除会话失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "清除会话失败: " + e.getMessage()));
        }
    }

    // ========== 内部方法 ==========

    private ResponseEntity<?> doPlatformChat(String authHeader, Map<String, Object> request, boolean stream) {
        String apiKey = extractApiKey(authHeader);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Authorization header 不能为空"));
        }

        String message = (String) request.get("message");
        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message 不能为空"));
        }

        String sessionId = (String) request.get("session_id");
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        try {
            ApiKey key = apiKeyService.getActiveApiKey(apiKey);
            Long userId = key.getUserId();

            var bindingOpt = bindingService.findByMerchantId(key.getName());
            if (bindingOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "商家未绑定智能体"));
            }

            MerchantAgentBinding binding = bindingOpt.get();
            if (!binding.getEnabled()) {
                return ResponseEntity.badRequest().body(Map.of("error", "商家智能体绑定已禁用"));
            }

            if (binding.getAgentId() != null) {
                return forwardToAgent(binding.getAgentId(), userId, message, sessionId, request, binding);
            } else if (binding.getAgentEndpoint() != null && !binding.getAgentEndpoint().isEmpty()) {
                return forwardToEndpoint(binding.getAgentEndpoint(), binding.getAgentAuthHeader(), userId, message, sessionId, binding);
            } else if (binding.getSkillId() != null && !binding.getSkillId().isEmpty()) {
                return forwardToRuntime(binding.getSkillId(), userId, message, sessionId, (String) request.get("collection_name"));
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "商家绑定配置无效"));
            }
        } catch (Exception e) {
            log.error("ApiKey 验证失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "ApiKey 无效或已禁用"));
        }
    }

    private ResponseEntity<?> forwardToAgent(Long agentId, Long userId, String message, String sessionId, Map<String, Object> originalRequest, MerchantAgentBinding binding) {
        Agent agent = agentService.getAgentById(agentId);
        if (agent == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "智能体不存在"));
        }
        if (agent.getChatEndpoint() == null || agent.getChatEndpoint().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "智能体对话接口未配置"));
        }
        if (agent.getStatus() != null && agent.getStatus().ordinal() != 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "智能体已禁用"));
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // 优先使用智能体自身的认证头
            if (agent.getAuthHeader() != null && !agent.getAuthHeader().trim().isEmpty()) {
                headers.set("Authorization", agent.getAuthHeader());
            }
            // 如果绑定中配置了 API Key，作为 X-Api-Key 传递给智能体
            if (binding != null && binding.getApiKey() != null && !binding.getApiKey().trim().isEmpty()) {
                headers.set("X-Api-Key", binding.getApiKey());
            }

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("merchant_id", String.valueOf(userId));
            body.put("message", message);
            body.put("session_id", sessionId);
            if (originalRequest != null) {
                for (Map.Entry<String, Object> entry : originalRequest.entrySet()) {
                    if (!body.containsKey(entry.getKey())) {
                        body.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            log.info("转发到智能体: agentId={}, chatEndpoint={}, withApiKey={}", agentId, agent.getChatEndpoint(), binding != null && binding.getApiKey() != null);
            @SuppressWarnings("unchecked")
            ResponseEntity<Map> response = restTemplate.postForEntity(
                agent.getChatEndpoint(), request, Map.class);

            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.error("转发到智能体失败: agentId={}, error={}", agentId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "转发到智能体失败: " + e.getMessage()));
        }
    }

    /**
     * 代理转发 SSE 流式对话
     */
    private SseEmitter proxySse(Agent agent, Long userId, Map<String, Object> originalRequest, MerchantAgentBinding binding) {
        SseEmitter emitter = new SseEmitter(60000L);

        sseExecutor.execute(() -> {
            try {
                String streamUrl = agent.getStreamEndpoint();
                HttpURLConnection conn = (HttpURLConnection) URI.create(streamUrl).toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "text/event-stream");
                if (agent.getAuthHeader() != null && !agent.getAuthHeader().trim().isEmpty()) {
                    conn.setRequestProperty("Authorization", agent.getAuthHeader());
                }
                // 传递绑定的 API Key
                if (binding != null && binding.getApiKey() != null && !binding.getApiKey().trim().isEmpty()) {
                    conn.setRequestProperty("X-Api-Key", binding.getApiKey());
                }
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(60000);

                // 构建请求体
                Map<String, Object> body = new java.util.HashMap<>();
                body.put("merchant_id", String.valueOf(userId));
                if (originalRequest != null) {
                    body.putAll(originalRequest);
                }
                if (!body.containsKey("merchant_id")) {
                    body.put("merchant_id", String.valueOf(userId));
                }

                String jsonBody = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body);
                conn.getOutputStream().write(jsonBody.getBytes());
                conn.getOutputStream().flush();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6);
                            emitter.send(SseEmitter.event().data(data));
                        } else if (!line.isEmpty()) {
                            emitter.send(SseEmitter.event().data(line));
                        }
                    }
                }
                emitter.complete();
                conn.disconnect();
            } catch (Exception e) {
                log.error("SSE 代理转发失败: agentId={}, error={}", agent.getId(), e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 降级：同步调用包装为 SSE 格式返回
     */
    private SseEmitter fallbackSse(Agent agent, Long userId, Map<String, Object> originalRequest, MerchantAgentBinding binding) {
        SseEmitter emitter = new SseEmitter(30000L);

        sseExecutor.execute(() -> {
            try {
                ResponseEntity<?> response = forwardToAgent(agent.getId(), userId,
                        (String) originalRequest.get("message"),
                        (String) originalRequest.getOrDefault("session_id", UUID.randomUUID().toString()),
                        originalRequest, binding);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = (Map<String, Object>) response.getBody();
                    String reply = (String) body.getOrDefault("reply", body.getOrDefault("content", ""));
                    if (reply != null && !reply.isEmpty()) {
                        emitter.send(SseEmitter.event().data("{\"type\":\"content\",\"content\":\"" +
                                reply.replace("\"", "\\\"").replace("\n", "\\n") + "\"}"));
                    }
                    emitter.send(SseEmitter.event().data("{\"type\":\"done\"}"));
                }
                emitter.complete();
            } catch (Exception e) {
                log.error("降级 SSE 失败: agentId={}, error={}", agent.getId(), e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private String extractApiKey(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private ResponseEntity<?> forwardToRuntime(String skillId, Long userId, String message, String sessionId, String collectionName) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("merchant_id", String.valueOf(userId));
            body.put("message", message);
            body.put("session_id", sessionId);
            body.put("skill_id", skillId);
            if (collectionName != null && !collectionName.isEmpty()) {
                body.put("collection_name", collectionName);
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            log.info("转发到通用运行时: skillId={}", skillId);
            @SuppressWarnings("unchecked")
            ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:3000/api/chat", request, Map.class);

            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.error("转发到通用运行时失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "转发到通用运行时失败: " + e.getMessage()));
        }
    }

    private ResponseEntity<?> forwardToEndpoint(String endpoint, String authHeader, Long userId, String message, String sessionId, MerchantAgentBinding binding) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (authHeader != null) {
                headers.set("Authorization", authHeader);
            }
            // 如果绑定中配置了 API Key，作为 X-Api-Key 传递
            if (binding != null && binding.getApiKey() != null && !binding.getApiKey().trim().isEmpty()) {
                headers.set("X-Api-Key", binding.getApiKey());
            }

            Map<String, Object> body = Map.of(
                "merchant_id", String.valueOf(userId),
                "message", message,
                "session_id", sessionId
            );
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, request, Map.class);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.error("转发到定制智能体失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "转发到定制智能体失败: " + e.getMessage()));
        }
    }
}

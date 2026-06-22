package com.hczk.hczkaiagentserver.controller;

import com.hczk.hczkaiagentserver.dto.ChatRequest;
import com.hczk.hczkaiagentserver.entity.Agent;
import com.hczk.hczkaiagentserver.entity.ApiKey;
import com.hczk.hczkaiagentserver.entity.MerchantAgentBinding;
import com.hczk.hczkaiagentserver.entity.Skill;
import com.hczk.hczkaiagentserver.service.AgentService;
import com.hczk.hczkaiagentserver.service.ApiKeyService;
import com.hczk.hczkaiagentserver.service.ChatService;
import com.hczk.hczkaiagentserver.service.MerchantAgentBindingService;
import com.hczk.hczkaiagentserver.service.SkillService;
import com.hczk.hczkaiagentserver.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    private final SkillService skillService;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate = new RestTemplate();
    /**
     * SSE 转发线程池（有界）：
     * core=8, max=64, queue=200，队列满时由调用线程同步执行（避免丢失请求）
     */
    private final ExecutorService sseExecutor = new ThreadPoolExecutor(
            8, 64, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(200),
            r -> {
                Thread t = new Thread(r, "sse-forward");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    private final HttpServletRequest httpRequest;

    /**
     * 智能体工具端点中转回平台的基础 URL。
     * 智能体通过此地址回调平台执行工具，因此必须是智能体能访问到的地址。
     *
     * 优先级：
     * 1. 环境变量 APP_PLATFORM_URL 显式设置（生产/跨服务器部署，如 https://api.example.com）
     * 2. 请求 Host 头 + X-Forwarded-Proto（未设环境变量时自动推断，适配开发/试用场景）
     * 3. 本地地址兜底
     */
    private String getPlatformBaseUrl() {
        // 环境变量显式设置时直接使用（跨服务器部署必需）
        String envUrl = System.getenv("APP_PLATFORM_URL");
        if (envUrl != null && !envUrl.isBlank()) {
            return envUrl;
        }
        // 未设置环境变量时，从请求 Host 头推断
        String host = httpRequest.getHeader("Host");
        if (host != null && !host.isBlank()) {
            String scheme = httpRequest.getHeader("X-Forwarded-Proto");
            if (scheme == null || scheme.isBlank()) scheme = "http";
            return scheme + "://" + host;
        }
        return "http://" + httpRequest.getLocalAddr() + ":" + httpRequest.getLocalPort();
    }

    /**
     * 内部聊天接口（管理后台/前端使用）
     */
    @PostMapping(value = "/chat/completions", produces = {MediaType.TEXT_EVENT_STREAM_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public Object completions(@RequestBody ChatRequest request) {
        // 如果请求包含 agentId，走智能体代理逻辑
        Long agentId = request.getResolvedAgentId();
        if (agentId != null) {
            return trialChat(agentId, request);
        }
        // 否则走原有的模型调用逻辑
        if (Boolean.FALSE.equals(request.getStream())) {
            return chatService.chat(request);
        }
        return chatService.streamChat(request);
    }

    /**
     * 智能体试用接口（管理后台专用，JWT 认证）
     * 通过 agentId 代理请求到智能体的 endpoint，支持流式和非流式
     */
    private Object trialChat(Long agentId, ChatRequest request) {
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

        String message = request.getEffectiveMessage();
        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "消息内容不能为空"));
        }

        // 从认证上下文获取当前用户ID，而非硬编码 "trial"
        String currentUserId = resolveCurrentUserId();

        boolean useStream = Boolean.TRUE.equals(request.getStream());
        String streamEndpoint = agent.getStreamEndpoint();

        // 如果请求流式且智能体配置了流式端点，使用 SSE 代理
        if (useStream && streamEndpoint != null && !streamEndpoint.trim().isEmpty()) {
            return proxyTrialSse(agent, message, currentUserId);
        }

        // 否则使用同步调用
        return syncTrialCall(agent, message, currentUserId);
    }

    /**
     * 从 SecurityContext 中解析当前用户ID
     */
    private String resolveCurrentUserId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof Map<?, ?> details) {
            Object userId = details.get("userId");
            if (userId != null) return String.valueOf(userId);
        }
        if (auth != null && auth.getName() != null && auth.getName().startsWith("apikey-user-")) {
            return auth.getName().substring("apikey-user-".length());
        }
        return null;
    }

    /**
     * 智能体试用 - SSE 流式代理
     */
    private SseEmitter proxyTrialSse(Agent agent, String message, String currentUserId) {
        SseEmitter emitter = new SseEmitter(60000L);

        // 在线程池外提前解析（HttpServletRequest 绑定在当前请求线程）
        String platformBaseUrl = getPlatformBaseUrl();
        List<Map<String, Object>> availableSkills = getAvailableSkills(currentUserId);
        String agentToken = jwtUtil.generateAgentToken(currentUserId, null);

        sseExecutor.execute(() -> {
            try {
                String streamUrl = agent.getStreamEndpoint();
                HttpURLConnection conn = (HttpURLConnection) URI.create(streamUrl).toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "text/event-stream");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(60000);

                conn.setRequestProperty("Authorization", "Bearer " + agentToken);

                Map<String, Object> bodyMap = new java.util.HashMap<>();
                bodyMap.put("message", message);
                bodyMap.put("session_id", UUID.randomUUID().toString());
                bodyMap.put("user_id", currentUserId);
                bodyMap.put("available_skills", availableSkills);
                bodyMap.put("tools_discovery_endpoint", platformBaseUrl + "/api/tools/group");
                bodyMap.put("tools_execution_endpoint", platformBaseUrl + "/api/tools/execute");
                bodyMap.put("tools_auth_token", agentToken);
                String body = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(bodyMap);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes());
                    os.flush();
                }

                // 读取 SSE 响应并转发
                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    String error = new BufferedReader(new InputStreamReader(conn.getErrorStream()))
                        .lines().collect(Collectors.joining("\n"));
                    emitter.send(SseEmitter.event().data("{\"error\":\"智能体返回错误: " + responseCode + "\"}"));
                    emitter.complete();
                    return;
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isEmpty()) {
                            // 智能体返回的行可能带 "data:" 前缀，去掉后让 SseEmitter 统一包装
                            String dataContent = line.startsWith("data:") ? line.substring(5).trim() : line;
                            emitter.send(SseEmitter.event().data(dataContent));
                        }
                    }
                }
                emitter.complete();
            } catch (Exception e) {
                log.error("智能体试用流式代理失败: agentId={}, error={}", agent.getId(), e.getMessage());
                try {
                    emitter.send(SseEmitter.event().data("{\"error\":\"" + e.getMessage() + "\"}"));
                    emitter.completeWithError(e);
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        return emitter;
    }

    /**
     * 智能体试用 - 同步调用
     */
    private ResponseEntity<?> syncTrialCall(Agent agent, String message, String currentUserId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // 使用JWT鉴权：生成智能体调用JWT
            String agentToken = jwtUtil.generateAgentToken(currentUserId, null);
            headers.set("Authorization", "Bearer " + agentToken);

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("user_id", currentUserId);
            body.put("message", message);
            body.put("session_id", UUID.randomUUID().toString());
            // 传递平台注册的 MCP 工具列表
            body.put("available_skills", getAvailableSkills(currentUserId));
            body.put("tools_discovery_endpoint", getPlatformBaseUrl() + "/api/tools/group");
            body.put("tools_execution_endpoint", getPlatformBaseUrl() + "/api/tools/execute");
            body.put("tools_auth_token", agentToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            log.info("智能体试用同步调用: agentId={}, chatEndpoint={}", agent.getId(), agent.getChatEndpoint());
            @SuppressWarnings("unchecked")
            ResponseEntity<Map> response = restTemplate.postForEntity(
                agent.getChatEndpoint(), request, Map.class);

            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.error("智能体试用同步调用失败: agentId={}, error={}", agent.getId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "调用智能体失败: " + e.getMessage()));
        }
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
            String userId = key.getUserId();

            var bindingOpt = bindingService.findByUserId(userId);
            if (bindingOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "用户未绑定智能体"));
            }

            MerchantAgentBinding binding = bindingOpt.get();
            if (!binding.getEnabled()) {
                return ResponseEntity.badRequest().body(Map.of("error", "用户智能体绑定已禁用"));
            }

            if (binding.getAgentId() != null) {
                Agent agent = agentService.getAgentById(binding.getAgentId());
                String streamUrl = agent.getStreamEndpoint();

                if (streamUrl != null && !streamUrl.trim().isEmpty()) {
                    // 智能体支持流式，代理转发 SSE
                    return proxySse(agent, userId, request, binding, key.getApiKey());
                } else {
                    // 降级：同步调用，包装为 SSE 格式返回
                    return fallbackSse(agent, userId, request, binding, key.getApiKey());
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
            var bindingOpt = bindingService.findByUserId(key.getUserId());
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
            // 使用JWT鉴权
            String agentToken = jwtUtil.generateAgentToken(
                    key.getUserId(), key.getApiKey());
            headers.set("Authorization", "Bearer " + agentToken);

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
            var bindingOpt = bindingService.findByUserId(key.getUserId());
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
            // 使用JWT鉴权
            String agentToken = jwtUtil.generateAgentToken(
                    key.getUserId(), key.getApiKey());
            headers.set("Authorization", "Bearer " + agentToken);

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
            String userId = key.getUserId();

            var bindingOpt = bindingService.findByUserId(userId);
            if (bindingOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "用户未绑定智能体"));
            }

            MerchantAgentBinding binding = bindingOpt.get();
            if (!binding.getEnabled()) {
                return ResponseEntity.badRequest().body(Map.of("error", "智能体绑定已禁用"));
            }

            if (binding.getAgentId() != null) {
                return forwardToAgent(binding.getAgentId(), userId, message, sessionId, request, binding, key.getApiKey());
            } else if (binding.getAgentEndpoint() != null && !binding.getAgentEndpoint().isEmpty()) {
                return forwardToEndpoint(binding.getAgentEndpoint(), binding.getAgentAuthHeader(), userId, message, sessionId, binding, key.getApiKey());
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "用户绑定配置无效"));
            }
        } catch (Exception e) {
            log.error("ApiKey 验证失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "ApiKey 无效或已禁用"));
        }
    }

    private ResponseEntity<?> forwardToAgent(Long agentId, String userId, String message, String sessionId, Map<String, Object> originalRequest, MerchantAgentBinding binding, String apiKey) {
        return forwardToAgent(agentId, userId, message, sessionId, originalRequest, binding, apiKey,
                getPlatformBaseUrl(), getAvailableSkills(userId));
    }

    private ResponseEntity<?> forwardToAgent(Long agentId, String userId, String message, String sessionId, Map<String, Object> originalRequest, MerchantAgentBinding binding, String apiKey,
                                             String platformBaseUrl, List<Map<String, Object>> availableSkills) {
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
            // 使用JWT鉴权：将雪花userId和apiKey封装到JWT中
            String agentToken = jwtUtil.generateAgentToken(userId, apiKey);
            headers.set("Authorization", "Bearer " + agentToken);

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("user_id", userId);
            body.put("message", message);
            body.put("session_id", sessionId);
            body.put("available_skills", availableSkills);
            body.put("tools_discovery_endpoint", platformBaseUrl + "/api/tools/group");
            body.put("tools_execution_endpoint", platformBaseUrl + "/api/tools/execute");
            body.put("tools_auth_token", agentToken);  // 用短期 agentToken 作为回调认证凭据（避免明文 API Key 泄露）
            if (originalRequest != null) {
                for (Map.Entry<String, Object> entry : originalRequest.entrySet()) {
                    if (!body.containsKey(entry.getKey())) {
                        body.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            log.info("转发到智能体: agentId={}, chatEndpoint={}, userId={}", agentId, agent.getChatEndpoint(), userId);
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
    private SseEmitter proxySse(Agent agent, String userId, Map<String, Object> originalRequest, MerchantAgentBinding binding, String apiKey) {
        SseEmitter emitter = new SseEmitter(60000L);

        // 在线程池外提前解析（HttpServletRequest 绑定在当前请求线程）
        String platformBaseUrl = getPlatformBaseUrl();
        List<Map<String, Object>> availableSkills = getAvailableSkills(userId);
        String agentToken = jwtUtil.generateAgentToken(userId, apiKey);

        sseExecutor.execute(() -> {
            try {
                String streamUrl = agent.getStreamEndpoint();
                HttpURLConnection conn = (HttpURLConnection) URI.create(streamUrl).toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "text/event-stream");
                conn.setRequestProperty("Authorization", "Bearer " + agentToken);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(60000);

                Map<String, Object> body = new java.util.HashMap<>();
                body.put("user_id", userId);
                body.put("available_skills", availableSkills);
                body.put("tools_discovery_endpoint", platformBaseUrl + "/api/tools/group");
                body.put("tools_execution_endpoint", platformBaseUrl + "/api/tools/execute");
                body.put("tools_auth_token", agentToken);
                if (originalRequest != null) {
                    body.putAll(originalRequest);
                }
                if (!body.containsKey("user_id")) {
                    body.put("user_id", userId);
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
    private SseEmitter fallbackSse(Agent agent, String userId, Map<String, Object> originalRequest, MerchantAgentBinding binding, String apiKey) {
        SseEmitter emitter = new SseEmitter(30000L);

        // 在线程池外提前解析（HttpServletRequest 绑定在当前请求线程）
        String platformBaseUrl = getPlatformBaseUrl();
        List<Map<String, Object>> availableSkills = getAvailableSkills(userId);

        sseExecutor.execute(() -> {
            try {
                ResponseEntity<?> response = forwardToAgent(agent.getId(), userId,
                        (String) originalRequest.get("message"),
                        (String) originalRequest.getOrDefault("session_id", UUID.randomUUID().toString()),
                        originalRequest, binding, apiKey, platformBaseUrl, availableSkills);

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

    private ResponseEntity<?> forwardToEndpoint(String endpoint, String authHeader, String userId, String message, String sessionId, MerchantAgentBinding binding, String apiKey) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // 使用JWT鉴权：将雪花userId和apiKey封装到JWT中
            String agentToken = jwtUtil.generateAgentToken(userId, apiKey);
            headers.set("Authorization", "Bearer " + agentToken);

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("user_id", userId);
            body.put("message", message);
            body.put("session_id", sessionId);
            body.put("available_skills", getAvailableSkills(userId));
            body.put("tools_discovery_endpoint", getPlatformBaseUrl() + "/api/tools/group");
            // 传递工具执行端点和认证信息，供远端智能体回调平台工具时使用
            body.put("tools_execution_endpoint", getPlatformBaseUrl() + "/api/tools/execute");
            body.put("tools_auth_token", agentToken);  // 用短期 agentToken 作为回调认证凭据（避免明文 API Key 泄露）
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

    /** 构建工具组目录列表（传递给智能体的初始化目录） */
    private List<Map<String, Object>> getAvailableSkills() {
        return getAvailableSkills(null);
    }

    /**
     * 构建工具组目录列表（按用户身份过滤，传递给智能体的初始化目录）
     * v10：仅传递用户可访问的工具组（public + 用户已绑定的 private）
     *
     * @param userId 用户ID，为 null 时仅返回 public
     */
    private List<Map<String, Object>> getAvailableSkills(String userId) {
        List<Skill> skills = skillService.getAccessibleSkillsWithToolCount(userId);
        return skills.stream()
                .filter(s -> "active".equals(s.getStatus()))
                .map(s -> {
                    Map<String, Object> g = new java.util.LinkedHashMap<>();
                    g.put("name", s.getName());
                    g.put("display_name", s.getDisplayName());
                    g.put("description", s.getDescription());
                    g.put("category", s.getCategory());
                    g.put("visibility", s.getVisibility());
                    g.put("tool_count", s.getToolCount());
                    return g;
                })
                .collect(Collectors.toList());
    }
}

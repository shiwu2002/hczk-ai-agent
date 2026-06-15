package com.hczk.hczkaiagentserver.controller;

import com.hczk.hczkaiagentserver.dto.ChatRequest;
import com.hczk.hczkaiagentserver.entity.Agent;
import com.hczk.hczkaiagentserver.entity.ApiKey;
import com.hczk.hczkaiagentserver.entity.MerchantAgentBinding;
import com.hczk.hczkaiagentserver.service.AgentService;
import com.hczk.hczkaiagentserver.service.ApiKeyService;
import com.hczk.hczkaiagentserver.service.ChatService;
import com.hczk.hczkaiagentserver.service.MerchantAgentBindingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * 聊天接口控制器
 * 支持三种调用方式：
 * 1. 内部调用：/chat/completions（JWT 认证，使用 modelId）
 * 2. OpenAI 兼容调用：/v1/chat/completions（API Key 认证）
 * 3. 平台路由调用：/api/chat（通过 ApiKey 路由到用户绑定的智能体）
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {

    @Value("${agent.runtime.url:http://localhost:3000/api/chat}")
    private String runtimeUrl;

    private final ChatService chatService;
    private final ApiKeyService apiKeyService;
    private final AgentService agentService;
    private final MerchantAgentBindingService bindingService;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 内部聊天接口（管理后台/前端使用）
     * 支持 stream=true（SSE流式）和 stream=false（JSON完整响应）
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
     * 支持 stream=true（SSE流式）和 stream=false（JSON完整响应）
     */
    @PostMapping(value = "/v1/chat/completions", produces = {MediaType.TEXT_EVENT_STREAM_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public Object v1Completions(@RequestBody ChatRequest request) {
        if (Boolean.FALSE.equals(request.getStream())) {
            return chatService.chat(request);
        }
        return chatService.streamChat(request);
    }

    /**
     * 平台路由聊天接口（通过 ApiKey 路由）
     * 流程：
     * 1. 验证 ApiKey → 获取 userId
     * 2. 查询 merchant_agent_binding → 获取绑定信息
     * 3. 根据绑定类型路由：
     *    - agentId 有值 → 查询平台智能体，根据智能体类型路由
     *    - skillId 有值 → 转发到通用运行时
     *    - agentEndpoint 有值 → 转发到定制智能体
     * 
     * 请求头：Authorization: Bearer {api_key}
     * 请求体：{ "message": "xxx", "session_id": "xxx", "collection_name": "xxx" }
     */
    @PostMapping("/api/chat")
    public ResponseEntity<?> platformChat(
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

        String sessionId = (String) request.get("session_id");
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        String collectionName = (String) request.get("collection_name");

        try {
            // 1. 验证 ApiKey → 获取 userId
            ApiKey key = apiKeyService.getActiveApiKey(apiKey);
            Long userId = key.getUserId();
            
            // 2. 查询商家绑定（merchant_agent_binding）
            var bindingOpt = bindingService.findByMerchantId(key.getName());
            if (bindingOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "商家未绑定智能体"));
            }

            MerchantAgentBinding binding = bindingOpt.get();
            if (!binding.getEnabled()) {
                return ResponseEntity.badRequest().body(Map.of("error", "商家智能体绑定已禁用"));
            }

            // 3. 根据绑定类型路由
            if (binding.getAgentEndpoint() != null && !binding.getAgentEndpoint().isEmpty()) {
                // Endpoint 模式：转发到定制智能体
                return forwardToEndpoint(binding, userId, message, sessionId);
            } else if (binding.getSkillId() != null && !binding.getSkillId().isEmpty()) {
                // Skill 模式：转发到通用运行时
                return forwardToRuntime(binding, userId, message, sessionId, collectionName);
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "商家绑定配置无效"));
            }
            
        } catch (Exception e) {
            log.error("ApiKey 验证失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "ApiKey 无效或已禁用"));
        }
    }

    /**
     * 平台智能体路由：根据智能体类型进行二次路由
     */
    private ResponseEntity<?> routeByAgent(Long agentId, Long userId, String message, String sessionId, String collectionName) {
        Agent agent = agentService.getAgentById(agentId);
        if (agent == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "平台智能体不存在"));
        }
        
        String agentType = agent.getAgentType();
        
        // 根据智能体类型路由
        if ("ENDPOINT".equals(agentType)) {
            // 智能体本身是 Endpoint 模式
            MerchantAgentBinding tempBinding = new MerchantAgentBinding();
            tempBinding.setAgentEndpoint(agent.getEndpoint());
            tempBinding.setAgentAuthHeader(agent.getEndpointAuthHeader());
            return forwardToEndpoint(tempBinding, userId, message, sessionId);
        } else if ("SKILL".equals(agentType)) {
            // 智能体本身是 Skill 模式
            MerchantAgentBinding tempBinding = new MerchantAgentBinding();
            tempBinding.setSkillId(agent.getSkillId());
            return forwardToRuntime(tempBinding, userId, message, sessionId, collectionName);
        } else {
            // MODEL 模式或其他类型
            ChatRequest chatRequest = new ChatRequest();
            chatRequest.setModelId(agent.getModelId());
            chatRequest.setMessage(message);
            return ResponseEntity.ok(Map.of(
                "agentId", agentId,
                "agentName", agent.getName(),
                "agentType", agentType,
                "message", "智能体对话已接收"
            ));
        }
    }

    private String extractApiKey(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    /**
     * Skill 模式：转发到通用运行时
     */
    private ResponseEntity<?> forwardToRuntime(MerchantAgentBinding binding, Long userId, String message, String sessionId, String collectionName) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("merchant_id", String.valueOf(userId));
            body.put("message", message);
            body.put("session_id", sessionId);
            body.put("skill_id", binding.getSkillId());
            if (collectionName != null && !collectionName.isEmpty()) {
                body.put("collection_name", collectionName);
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            log.info("转发到通用运行时: {}, skillId: {}", runtimeUrl, binding.getSkillId());
            ResponseEntity<Map> response = restTemplate.postForEntity(
                runtimeUrl,
                request,
                Map.class
            );

            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.error("转发到通用运行时失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "转发到通用运行时失败: " + e.getMessage()));
        }
    }

    /**
     * Endpoint 模式：转发到定制智能体
     */
    private ResponseEntity<?> forwardToEndpoint(MerchantAgentBinding binding, Long userId, String message, String sessionId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (binding.getAgentAuthHeader() != null) {
                headers.set("Authorization", binding.getAgentAuthHeader());
            }

            Map<String, Object> body = Map.of(
                "merchant_id", String.valueOf(userId),
                "message", message,
                "session_id", sessionId
            );
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                binding.getAgentEndpoint(),
                request,
                Map.class
            );

            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.error("转发到定制智能体失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "转发到定制智能体失败: " + e.getMessage()));
        }
    }
}
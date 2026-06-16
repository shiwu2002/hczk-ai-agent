package com.hczk.hczkaiagentserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hczk.hczkaiagentserver.entity.Agent;
import com.hczk.hczkaiagentserver.enums.AgentStatus;
import com.hczk.hczkaiagentserver.mapper.AgentMapper;
import com.hczk.hczkaiagentserver.service.AgentService;
import com.hczk.hczkaiagentserver.service.RedisCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class AgentServiceImpl implements AgentService {

    private final AgentMapper agentMapper;
    private final RedisCacheService redisCache;
    private final ObjectMapper objectMapper;
    private final RestTemplate healthRestTemplate;
    private static final String CACHE_KEY = "agents:all";

    public AgentServiceImpl(AgentMapper agentMapper, RedisCacheService redisCache, ObjectMapper objectMapper) {
        this.agentMapper = agentMapper;
        this.redisCache = redisCache;
        this.objectMapper = objectMapper;
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(3000);
        this.healthRestTemplate = new RestTemplate(requestFactory);
    }

    @Override
    public List<Agent> getAllAgents() {
        String cached = redisCache.getCachedJson(CACHE_KEY);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, objectMapper.getTypeFactory().constructCollectionType(List.class, Agent.class));
            } catch (Exception e) { log.debug("agents缓存反序列化失败，回源"); }
        }
        List<Agent> list = agentMapper.selectList(null);
        try { redisCache.cacheJsonWithRandomTTL(CACHE_KEY, objectMapper.writeValueAsString(list)); } catch (Exception ignored) {}
        return list;
    }

    @Override
    public List<Agent> getAgentsByUserId(Long userId) {
        return agentMapper.selectList(new LambdaQueryWrapper<Agent>().eq(Agent::getUserId, userId));
    }

    @Override
    public Agent getAgentById(Long id) {
        Agent agent = agentMapper.selectById(id);
        if (agent == null) {
            throw new RuntimeException("智能体不存在");
        }
        return agent;
    }

    @Override
    @Transactional
    public Agent createAgent(Agent agent) {
        if (agent.getName() == null || agent.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("智能体名称不能为空");
        }
        if (agent.getHealthEndpoint() == null || agent.getHealthEndpoint().trim().isEmpty()) {
            throw new IllegalArgumentException("健康检测接口地址不能为空");
        }
        if (agent.getChatEndpoint() == null || agent.getChatEndpoint().trim().isEmpty()) {
            throw new IllegalArgumentException("对话接口地址不能为空");
        }

        if (agent.getStatus() == null) {
            agent.setStatus(AgentStatus.ACTIVE);
        }

        agentMapper.insert(agent);
        redisCache.deleteKey(CACHE_KEY);
        return agent;
    }

    @Override
    @Transactional
    public Agent updateAgent(Long id, Agent agent) {
        Agent existing = getAgentById(id);
        existing.setName(agent.getName());
        existing.setDescription(agent.getDescription());
        existing.setAgentType(agent.getAgentType());
        existing.setHealthEndpoint(agent.getHealthEndpoint());
        existing.setChatEndpoint(agent.getChatEndpoint());
        existing.setDocumentEndpoint(agent.getDocumentEndpoint());
        existing.setInfoEndpoint(agent.getInfoEndpoint());
        existing.setStreamEndpoint(agent.getStreamEndpoint());
        existing.setHistoryEndpoint(agent.getHistoryEndpoint());
        existing.setVersion(agent.getVersion());

        agentMapper.updateById(existing);
        redisCache.deleteKey(CACHE_KEY);
        return existing;
    }

    @Override
    @Transactional
    public void deleteAgent(Long id) {
        agentMapper.deleteById(id);
        redisCache.deleteKey(CACHE_KEY);
    }

    @Override
    @Transactional
    public Agent toggleStatus(Long id) {
        Agent agent = getAgentById(id);
        agent.setStatus(agent.getStatus() == AgentStatus.ACTIVE ? AgentStatus.INACTIVE : AgentStatus.ACTIVE);
        agentMapper.updateById(agent);
        redisCache.deleteKey(CACHE_KEY);
        return agent;
    }

    @Override
    public Map<Long, Map<String, Object>> checkAllAgentsHealth() {
        List<Agent> agents = getAllAgents();
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();

        for (Agent agent : agents) {
            try {
                Map<String, Object> health = checkAgentHealthByUrl(agent);
                health.put("lastCheck", LocalDateTime.now().toString());
                result.put(agent.getId(), health);
            } catch (Exception e) {
                log.error("检测智能体健康状态异常: agentId={}, error={}", agent.getId(), e.getMessage());
                Map<String, Object> health = buildHealthResult(false, null, "检测异常: " + e.getMessage());
                health.put("lastCheck", LocalDateTime.now().toString());
                result.put(agent.getId(), health);
            }
        }

        return result;
    }

    @Override
    public Map<String, Object> checkAgentHealth(Long id) {
        Agent agent = getAgentById(id);
        Map<String, Object> health = checkAgentHealthByUrl(agent);
        health.put("lastCheck", LocalDateTime.now().toString());
        return health;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getAgentInfo(Long id) {
        Agent agent = getAgentById(id);
        String infoUrl = agent.getInfoEndpoint();
        if (infoUrl == null || infoUrl.trim().isEmpty()) {
            // 没有 infoEndpoint，返回数据库中的基本信息
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", agent.getName());
            info.put("version", agent.getVersion());
            info.put("description", agent.getDescription());
            info.put("capabilities", Map.of(
                "knowledge_retrieval", false,
                "tool_calling", false,
                "multi_turn", true,
                "streaming", agent.getStreamEndpoint() != null && !agent.getStreamEndpoint().trim().isEmpty(),
                "thinking", false
            ));
            info.put("_source", "database");
            return info;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = healthRestTemplate.exchange(infoUrl, HttpMethod.GET, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> result = new LinkedHashMap<>(response.getBody());
                result.put("_source", "endpoint");
                return result;
            }
            return Map.of("error", "获取元信息失败", "_source", "endpoint");
        } catch (Exception e) {
            log.warn("获取智能体元信息失败: agentId={}, infoEndpoint={}, error={}", agent.getId(), infoUrl, e.getMessage());
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", agent.getName());
            info.put("version", agent.getVersion());
            info.put("description", agent.getDescription());
            info.put("_source", "database_fallback");
            info.put("_error", e.getMessage());
            return info;
        }
    }

    /**
     * 通过智能体的 healthEndpoint 检测健康状态
     * GET 请求 healthEndpoint，解析返回的 JSON：
     * { "status": "ok"|"degraded"|其他, "version": "...", "uptime": ..., "components": {...} }
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> checkAgentHealthByUrl(Agent agent) {
        String healthUrl = agent.getHealthEndpoint();
        if (healthUrl == null || healthUrl.trim().isEmpty()) {
            return buildHealthResult(false, null, "健康检测接口未配置");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> request = new HttpEntity<>(headers);
            long start = System.currentTimeMillis();
            ResponseEntity<Map> response = healthRestTemplate.exchange(healthUrl, HttpMethod.GET, request, Map.class);
            long latency = System.currentTimeMillis() - start;

            if (!response.getStatusCode().is2xxSuccessful()) {
                return buildHealthResult(false, latency, "接口响应异常: HTTP " + response.getStatusCode());
            }

            Map<String, Object> body = response.getBody();
            if (body == null) {
                return buildHealthResult(false, latency, "接口返回空响应");
            }

            // 解析返回的 status 字段
            Object statusObj = body.get("status");
            String status = statusObj != null ? statusObj.toString() : null;
            boolean online = "ok".equals(status) || "degraded".equals(status);
            String message = switch (status) {
                case "ok" -> "运行正常";
                case "degraded" -> "部分降级运行中";
                case null -> "状态未知";
                default -> "状态: " + status;
            };

            Map<String, Object> result = buildHealthResult(online, latency, message);
            result.put("runtimeStatus", status);
            // 透传智能体返回的详细信息
            if (body.get("version") != null) result.put("version", body.get("version"));
            if (body.get("uptime") != null) result.put("uptime", body.get("uptime"));
            if (body.get("components") != null) result.put("components", body.get("components"));
            if (body.get("runtime") != null) result.put("runtime", body.get("runtime"));

            return result;
        } catch (Exception e) {
            log.warn("智能体健康检测失败: agentId={}, healthEndpoint={}, error={}",
                    agent.getId(), healthUrl, e.getMessage());
            return buildHealthResult(false, null, "连接失败: " + e.getMessage());
        }
    }

    private Map<String, Object> buildHealthResult(boolean online, Long latencyMs, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("online", online);
        result.put("latencyMs", latencyMs);
        result.put("message", message);
        return result;
    }
}

package com.hczk.hczkaiagentserver.security;

import com.hczk.hczkaiagentserver.entity.ApiKey;
import com.hczk.hczkaiagentserver.service.RedisCacheService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * API Key认证过滤器
 *
 * 从请求头Authorization中提取API Key（sk-hczk-前缀），
 * 通过Redis缓存查询API Key有效性，认证成功后将userId和apiKeyId写入SecurityContext。
 *
 * 认证流程：
 * 1. 检查SecurityContext是否已有认证（已有则跳过，避免覆盖JWT认证）
 * 2. 从Authorization头提取API Key
 * 3. 通过RedisCacheService查询API Key（优先缓存，降级数据库）
 * 4. 验证API Key状态（status=0可用）
 * 5. 认证成功：构建UsernamePasswordAuthenticationToken，details中携带userId和apiKeyId
 *
 * 支持的Authorization格式：
 * - Bearer sk-hczk-xxxx
 * - sk-hczk-xxxx（直接传值）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    /** API Key前缀标识 */
    private static final String API_KEY_PREFIX = "sk-hczk-";

    /** Redis缓存服务（查询API Key） */
    private final RedisCacheService redisCacheService;

    /**
     * 过滤器核心逻辑
     *
     * @param request     HTTP请求
     * @param response    HTTP响应
     * @param filterChain 过滤器链
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 已有认证信息（如JWT），跳过API Key认证
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        // 从请求头提取API Key
        String apiKeyValue = extractApiKey(request);

        if (apiKeyValue != null) {
            // 查询API Key（优先Redis缓存，降级数据库）
            ApiKey apiKey = redisCacheService.getApiKey(apiKeyValue);
            if (apiKey != null && apiKey.getStatus() == 0) {
                // 认证成功：构建认证Token
                log.debug("API Key 认证成功: userId={}, apiKeyId={}, keyName={}", apiKey.getUserId(), apiKey.getId(), apiKey.getName());

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                "apikey-user-" + apiKey.getUserId(),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        );
                // 将userId和apiKeyId存入details，供ChatService等后续使用
                authentication.setDetails(Map.of("userId", apiKey.getUserId(), "apiKeyId", apiKey.getId()));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else if (apiKeyValue.startsWith(API_KEY_PREFIX)) {
                // 格式正确但无效或已禁用
                log.warn("API Key 认证失败: key 无效或已禁用");
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头提取API Key
     *
     * 支持格式：
     * - Authorization: Bearer sk-hczk-xxxx
     * - Authorization: sk-hczk-xxxx
     *
     * @param request HTTP请求
     * @return API Key字符串，非sk-hczk-前缀返回null
     */
    private String extractApiKey(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (!StringUtils.hasText(authHeader)) {
            return null;
        }

        String token;
        if (authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        } else {
            token = authHeader.trim();
        }

        // 仅识别sk-hczk-前缀的Token作为API Key
        return token.startsWith(API_KEY_PREFIX) ? token : null;
    }
}
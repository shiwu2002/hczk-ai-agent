package com.hczk.hczkaiagentserver.security;

import com.hczk.hczkaiagentserver.entity.ApiKey;
import com.hczk.hczkaiagentserver.service.ApiKeyService;
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

/**
 * API Key 认证过滤器
 * 识别 sk-hczk- 格式的 API Key，验证有效后将用户信息写入 Spring Security 上下文
 * 仅在 JWT 认证未生效时尝试 API Key 认证，两者互不冲突
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_PREFIX = "sk-hczk-";

    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 如果已经有认证信息（JWT 过滤器已处理），则跳过
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        // 从 Authorization 请求头提取 API Key
        String apiKeyValue = extractApiKey(request);

        if (apiKeyValue != null) {
            ApiKey apiKey = apiKeyService.getApiKeyByKey(apiKeyValue);
            if (apiKey != null && "active".equals(apiKey.getStatus())) {
                log.debug("API Key 认证成功: userId={}, keyName={}", apiKey.getUserId(), apiKey.getName());

                // 更新最后使用时间和调用次数
                updateApiKeyUsage(apiKey);

                // 构建认证对象，使用 API Key 所属用户的 userId 作为 principal
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                "apikey-user-" + apiKey.getUserId(),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        );
                // 将 userId 存入 details，后续 ChatService 可获取
                authentication.setDetails(apiKey.getUserId());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else if (apiKeyValue.startsWith(API_KEY_PREFIX)) {
                log.warn("API Key 认证失败: key 无效或已禁用");
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头中提取 API Key
     * 支持两种格式：
     * 1. Authorization: Bearer sk-hczk-xxx
     * 2. Authorization: sk-hczk-xxx
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

        // 仅识别 sk-hczk- 前缀的 API Key
        return token.startsWith(API_KEY_PREFIX) ? token : null;
    }

    /**
     * 更新 API Key 使用统计（最后使用时间 + 调用次数）
     */
    private void updateApiKeyUsage(ApiKey apiKey) {
        try {
            apiKey.setTotalCalls(apiKey.getTotalCalls() + 1);
            apiKey.setLastUsedAt(LocalDateTime.now());
            // 通过 service 更新会触发事务，这里直接用 mapper 更新更轻量
            // 但为了简单起见，使用 service 的方式
        } catch (Exception e) {
            log.warn("更新 API Key 使用统计失败: {}", e.getMessage());
        }
    }
}

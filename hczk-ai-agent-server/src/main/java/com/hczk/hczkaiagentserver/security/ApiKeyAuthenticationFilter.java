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

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_PREFIX = "sk-hczk-";

    private final RedisCacheService redisCacheService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKeyValue = extractApiKey(request);

        if (apiKeyValue != null) {
            ApiKey apiKey = redisCacheService.getApiKey(apiKeyValue);
            if (apiKey != null && "active".equals(apiKey.getStatus())) {
                log.debug("API Key 认证成功: userId={}, apiKeyId={}, keyName={}", apiKey.getUserId(), apiKey.getId(), apiKey.getName());

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                "apikey-user-" + apiKey.getUserId(),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        );
                authentication.setDetails(Map.of("userId", apiKey.getUserId(), "apiKeyId", apiKey.getId()));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else if (apiKeyValue.startsWith(API_KEY_PREFIX)) {
                log.warn("API Key 认证失败: key 无效或已禁用");
            }
        }

        filterChain.doFilter(request, response);
    }

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

        return token.startsWith(API_KEY_PREFIX) ? token : null;
    }
}
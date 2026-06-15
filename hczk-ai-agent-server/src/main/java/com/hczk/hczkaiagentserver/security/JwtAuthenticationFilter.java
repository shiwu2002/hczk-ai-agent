package com.hczk.hczkaiagentserver.security;

import com.hczk.hczkaiagentserver.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器
 * 从请求头中提取 JWT Token，验证有效后将用户信息写入 Spring Security 上下文
 * 对公开路径（/auth/、/webhook/、/error）直接放行，不进行 Token 校验
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 公开路径直接放行，无需 Token 验证
        String requestPath = request.getServletPath();
        if (requestPath.startsWith("/auth/") || requestPath.startsWith("/webhook/") || "/error".equals(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 从 Authorization 请求头提取 Token
        String token = extractToken(request);

        // Token 存在且有效时，解析用户信息并写入安全上下文
        if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
            String username = jwtUtil.getUsernameFromToken(token);
            Integer roleValue = jwtUtil.getRoleFromToken(token);

            // 将 Integer 角色值映射为 Spring Security 角色名：0→ADMIN，1→USER
            String roleName = (roleValue != null && roleValue == 0) ? "ADMIN" : "USER";

            // 构建认证对象，角色添加 ROLE_ 前缀以匹配 Spring Security 的 hasRole() 判断
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            username,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + roleName))
                    );
            authentication.setDetails(request);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // Token 不存在或无效时，不设置认证信息，由后续 SecurityConfig 中的权限规则决定是否拒绝
        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头中提取 Bearer Token
     * 期望格式：Authorization: Bearer <token>
     *
     * @param request HTTP 请求
     * @return Token 字符串，不存在则返回 null
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}

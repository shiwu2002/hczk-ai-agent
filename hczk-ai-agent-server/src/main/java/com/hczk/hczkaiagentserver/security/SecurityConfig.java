package com.hczk.hczkaiagentserver.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 安全配置
 * 基于 JWT 的无状态认证体系，配置路径权限、CORS、异常处理等
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 跨域配置
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // 禁用 CSRF（无状态 JWT 模式不需要）
            .csrf(AbstractHttpConfigurer::disable)
            // 无状态会话管理，不使用 HttpSession
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 路径权限配置
            .authorizeHttpRequests(auth -> auth
                // 放行异步分发请求（SseEmitter 流式响应需要）
                .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                // 放行 OPTIONS 预检请求
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // 放行公开接口：认证、Webhook 回调、健康检查、运行时状态检测、错误页
                .requestMatchers("/auth/**", "/webhook/**", "/health/**", "/platform/runtime/health", "/error").permitAll()
                // 其余请求均需认证
                .anyRequest().authenticated()
            )
            // 在 UsernamePasswordAuthenticationFilter 之前插入 JWT 过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            // 在 JWT 过滤器之后插入 API Key 过滤器（JWT 优先，API Key 兜底）
            .addFilterAfter(apiKeyAuthenticationFilter, JwtAuthenticationFilter.class)
            .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
            // 禁用 HTTP Basic 和表单登录
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            // 自定义认证异常响应（返回 JSON 而非默认重定向）
            .exceptionHandling(ex -> ex
                // 未认证（401）：Token 缺失或无效
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"未认证\"}");
                })
                // 无权限（403）：已认证但角色不足
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":403,\"message\":\"无权访问\"}");
                })
            );
        return http.build();
    }

    /**
     * CORS 跨域配置
     * 允许所有来源、常用 HTTP 方法和请求头
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * 密码编码器
     * 使用 BCrypt 算法加密用户密码
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

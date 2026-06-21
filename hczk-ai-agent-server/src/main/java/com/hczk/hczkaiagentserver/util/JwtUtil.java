package com.hczk.hczkaiagentserver.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    /** 智能体调用JWT的共享签名密钥（与智能体端共享，用于验证平台签发的JWT） */
    @Value("${jwt.agent.shared-secret:${jwt.secret}}")
    private String agentSharedSecret;

    /** 智能体调用JWT的过期时间（默认1小时） */
    @Value("${jwt.agent.expiration:3600000}")
    private Long agentTokenExpiration;

    /** 智能体调用JWT的签发者标识 */
    @Value("${jwt.agent.issuer:hczk-platform}")
    private String agentTokenIssuer;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private SecretKey getAgentSigningKey() {
        return Keys.hmacShaKeyFor(agentSharedSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username, Integer role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 生成包含用户ID的JWT Token
     */
    public String generateToken(String username, Integer role, String userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpiration);

        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 生成智能体调用JWT
     * 用于平台调用智能体时传递鉴权信息，智能体端通过共享密钥验证JWT
     *
     * @param userId    用户ID（雪花ID）
     * @param apiKey    平台API Key（访问知识库和模型）
     * @return JWT字符串
     */
    public String generateAgentToken(String userId, String apiKey) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + agentTokenExpiration);

        var builder = Jwts.builder()
                .subject("user_" + userId)
                .issuer(agentTokenIssuer)
                .issuedAt(now)
                .expiration(expiryDate)
                // claim 命名与 generateToken 保持一致，确保 JwtAuthenticationFilter 能通过 getUserIdFromToken 解析
                .claim("userId", userId)
                .claim("scope", "chat");

        if (apiKey != null && !apiKey.trim().isEmpty()) {
            builder.claim("apiKey", apiKey);
        }

        return builder
                .signWith(getAgentSigningKey())
                .compact();
    }

    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getSubject();
    }

    public Integer getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("role", Integer.class);
    }

    public String getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", String.class);
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 验证智能体调用 JWT（使用 agentSharedSecret 签名）
     * 用于 JwtAuthenticationFilter 识别智能体回调请求
     */
    public boolean validateAgentToken(String token) {
        try {
            parseAgentToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从智能体调用 JWT 中提取 userId
     */
    public String getAgentTokenUserId(String token) {
        try {
            Claims claims = parseAgentToken(token);
            return claims.get("userId", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Claims parseAgentToken(String token) {
        return Jwts.parser()
                .verifyWith(getAgentSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

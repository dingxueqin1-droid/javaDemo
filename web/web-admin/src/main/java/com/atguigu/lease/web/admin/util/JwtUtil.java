package com.atguigu.lease.web.admin.util;

import com.atguigu.lease.common.result.ResultCodeEnum;
import com.atguigu.lease.web.admin.exception.LoginException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtUtil {

    @Value("${lease.jwt.secret:}")
    private String secret;

    @Value("${lease.jwt.expiration-seconds:7200}")
    private long expirationSeconds;

    private Key signingKey;

    @PostConstruct
    public void init() {
        if (expirationSeconds <= 0) {
            throw new IllegalArgumentException("JWT 有效期必须大于 0");
        }
        if (StringUtils.hasText(secret)) {
            // HS256 密钥需为至少 32 字节随机数据的 Base64 编码。
            signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        } else {
            signingKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
            log.warn("未配置 JWT_SECRET，使用临时签名密钥，重启后旧 token 失效；正式部署请配置固定密钥");
        }
    }

    public String createToken(Long userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setIssuer("lease-admin")
                .setSubject(userId.toString())
                .setId(UUID.randomUUID().toString())
                .claim("userId", userId)
                .claim("username", username)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 校验签名、签发方和有效期，返回可信的用户信息。
     */
    public Claims parseToken(String token) {
        try {
            Jws<Claims> jwt = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .requireIssuer("lease-admin")
                    .build()
                    .parseClaimsJws(token);
            Claims claims = jwt.getBody();
            Long userId = claims.get("userId", Long.class);
            if (!SignatureAlgorithm.HS256.getValue().equals(jwt.getHeader().getAlgorithm())
                    || claims.getExpiration() == null
                    || userId == null || userId <= 0
                    || !userId.toString().equals(claims.getSubject())
                    || !StringUtils.hasText(claims.get("username", String.class))) {
                throw new LoginException(ResultCodeEnum.TOKEN_INVALID);
            }
            return claims;
        } catch (ExpiredJwtException exception) {
            throw new LoginException(ResultCodeEnum.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new LoginException(ResultCodeEnum.TOKEN_INVALID);
        }
    }
}

package com.videoinsight.backend.security;

import com.videoinsight.backend.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtProperties props;

    private SecretKey signingKey() {
        // jjwt 要求 HS256 至少 256 bit = 32 字节。Keys.hmacShaKeyFor 会检查长度。
        return Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(Long userId, String email) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + Duration.ofHours(props.getExpirationHours()).toMillis());
        return Jwts.builder()
                .issuer(props.getIssuer())
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(now)
                .expiration(exp)
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
    }

    public long expirationSeconds() {
        return Duration.ofHours(props.getExpirationHours()).toSeconds();
    }

    /**
     * 解析并校验签名 + 过期时间。任何失败都抛 {@link JwtException}(包含子类如 ExpiredJwtException),
     * 由 {@link JwtAuthenticationFilter} 捕获后回 401。
     */
    public Claims parse(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(signingKey())
                .requireIssuer(props.getIssuer())
                .build()
                .parseSignedClaims(token);
        return jws.getPayload();
    }

    public Long extractUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }
}

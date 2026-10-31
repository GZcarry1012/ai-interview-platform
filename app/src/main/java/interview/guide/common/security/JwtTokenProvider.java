package interview.guide.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * JWT 令牌生成与解析
 *
 * 访问令牌与刷新令牌共用同一密钥，通过 type claim 区分；
 * jti 用于登出黑名单（Redis），uid/role 供下游鉴权与限流使用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

  public static final String CLAIM_TYPE = "type";
  public static final String CLAIM_UID = "uid";
  public static final String CLAIM_ROLE = "role";
  public static final String TYPE_ACCESS = "access";
  public static final String TYPE_REFRESH = "refresh";

  private final JwtProperties properties;
  private SecretKey signingKey;

  @PostConstruct
  void init() {
    String secret = properties.getSecret();
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalStateException(
          "APP_AUTH_JWT_SECRET 未配置或长度不足（至少 32 字节）， refusing to start");
    }
    this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    log.info("JWT 签名密钥加载完成，访问令牌 {} 分钟，刷新令牌 {} 天",
        properties.getAccessTokenExpireMinutes(), properties.getRefreshTokenExpireDays());
  }

  public String generateAccessToken(Long userId, String username, String role) {
    return generateToken(userId, username, role, TYPE_ACCESS,
        Duration.ofMinutes(properties.getAccessTokenExpireMinutes()));
  }

  public String generateRefreshToken(Long userId, String username, String role) {
    return generateToken(userId, username, role, TYPE_REFRESH,
        Duration.ofDays(properties.getRefreshTokenExpireDays()));
  }

  private String generateToken(Long userId, String username, String role, String type,
      Duration ttl) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(username)
        .id(java.util.UUID.randomUUID().toString())
        .claim(CLAIM_TYPE, type)
        .claim(CLAIM_UID, userId)
        .claim(CLAIM_ROLE, role)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ttl)))
        .signWith(signingKey)
        .compact();
  }

  /**
   * 解析并校验令牌签名
   *
   * @return 合法返回 Claims；过期返回 null（由调用方区分过期与非法）
   * @throws JwtException 签名非法等不可恢复错误
   */
  public Claims parseToken(String token) {
    try {
      return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    } catch (ExpiredJwtException e) {
      log.debug("JWT 已过期: subject={}", e.getClaims().getSubject());
      return null;
    }
  }

  public boolean isExpiredToken(JwtException e) {
    return e instanceof ExpiredJwtException;
  }

  public long remainingTtlSeconds(Claims claims) {
    Date expiration = claims.getExpiration();
    if (expiration == null) {
      return 0;
    }
    long remaining = expiration.getTime() - System.currentTimeMillis();
    return Math.max(0, remaining / 1000);
  }

  public long accessTokenTtlSeconds() {
    return Duration.ofMinutes(properties.getAccessTokenExpireMinutes()).toSeconds();
  }
}

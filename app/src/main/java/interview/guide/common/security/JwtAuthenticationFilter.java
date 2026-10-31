package interview.guide.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.result.Result;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 认证过滤器
 *
 * 从 Authorization: Bearer 头解析令牌，校验登出黑名单，
 * 加载用户并写入 SecurityContext；同时把 userId 写入 request attribute，
 * 供 {@code RateLimitAspect} 的 USER 维度限流使用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";

  private final JwtTokenProvider tokenProvider;
  private final RedissonClient redissonClient;
  private final AuthUserLoader authUserLoader;
  private final ObjectMapper objectMapper;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {

    String token = extractBearerToken(request);
    if (token == null) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      Claims claims = tokenProvider.parseToken(token);
      if (claims == null) {
        // 过期令牌：直接按未授权返回，提示前端刷新或重新登录
        writeResult(response, Result.error(ErrorCode.AUTH_TOKEN_EXPIRED,
            ErrorCode.AUTH_TOKEN_EXPIRED.getMessage()));
        return;
      }
      if (isBlacklisted(claims.getId())) {
        log.debug("JWT 命中登出黑名单: subject={}", claims.getSubject());
        writeResult(response, Result.error(ErrorCode.AUTH_TOKEN_INVALID,
            ErrorCode.AUTH_TOKEN_INVALID.getMessage()));
        return;
      }
      authenticate(claims, request);
    } catch (JwtException e) {
      // 令牌签名非法：不设置认证上下文，由入口点按未授权返回
      log.debug("JWT 解析失败: {}", e.getMessage());
    }

    filterChain.doFilter(request, response);
  }

  private void authenticate(Claims claims, HttpServletRequest request) {
    String type = claims.get(JwtTokenProvider.CLAIM_TYPE, String.class);
    if (!JwtTokenProvider.TYPE_ACCESS.equals(type)) {
      // 刷新令牌不能用于业务接口
      return;
    }
    UserPrincipal principal = authUserLoader.loadActiveUser(claims);
    if (principal == null) {
      return;
    }
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
    SecurityContextHolder.getContext().setAuthentication(authentication);
    // 供 RateLimitAspect 的 USER 维度与业务审计使用
    request.setAttribute("userId", principal.id());
  }

  private boolean isBlacklisted(String jti) {
    if (jti == null || jti.isBlank()) {
      return false;
    }
    RBucket<String> bucket = redissonClient.getBucket(BLACKLIST_KEY_PREFIX + jti);
    return bucket.isExists();
  }

  private String extractBearerToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      String token = header.substring(BEARER_PREFIX.length()).trim();
      return token.isEmpty() ? null : token;
    }
    return null;
  }

  private void writeResult(HttpServletResponse response, Result<Void> result) throws IOException {
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(objectMapper.writeValueAsString(result));
  }
}

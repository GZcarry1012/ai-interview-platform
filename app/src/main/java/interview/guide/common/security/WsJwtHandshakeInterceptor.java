package interview.guide.common.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * WebSocket 握手 JWT 鉴权
 *
 * 候选人/求职者连接 /ws/** 时通过 ?token= 访问令牌完成认证；
 * 握手失败直接返回 401，WebSocket 会话建立后可在 attributes 中取 userId
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsJwtHandshakeInterceptor implements HandshakeInterceptor {

  private final JwtTokenProvider tokenProvider;
  private final AuthUserLoader authUserLoader;

  @Override
  public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
      WebSocketHandler wsHandler, Map<String, Object> attributes) {
    if (request instanceof ServletServerHttpRequest servletRequest) {
      HttpServletRequest httpRequest = servletRequest.getServletRequest();
      String token = httpRequest.getParameter("token");
      if (token == null || token.isBlank()) {
        reject(response, "缺少访问令牌");
        return false;
      }
      try {
        Claims claims = tokenProvider.parseToken(token);
        String type = claims != null ? claims.get(JwtTokenProvider.CLAIM_TYPE, String.class) : null;
        if (claims == null || !JwtTokenProvider.TYPE_ACCESS.equals(type)) {
          reject(response, "访问令牌无效或已过期");
          return false;
        }
        UserPrincipal principal = authUserLoader.loadActiveUser(claims);
        if (principal == null) {
          reject(response, "用户不存在或已被禁用");
          return false;
        }
        attributes.put("userId", principal.id());
        attributes.put("username", principal.username());
        return true;
      } catch (Exception e) {
        log.warn("WS 握手鉴权失败: {}", e.getMessage());
        reject(response, "握手鉴权失败");
        return false;
      }
    }
    reject(response, "不支持的握手请求");
    return false;
  }

  private void reject(ServerHttpResponse response, String message) {
    response.setStatusCode(HttpStatus.UNAUTHORIZED);
    log.debug("WS 握手拒绝: {}", message);
  }

  @Override
  public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
      WebSocketHandler wsHandler, Exception exception) {
    // 无需处理
  }
}

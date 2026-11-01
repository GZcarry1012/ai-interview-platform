package interview.guide.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.result.Result;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置
 *
 * 无状态 JWT 认证；方法级授权（@PreAuthorize）覆盖企业端业务接口；
 * CORS 由独立的 CorsFilter Bean 处理（安全链放行 OPTIONS 预检后
 * 由其补响应头）；鉴权错误响应沿用全站约定：HTTP 200 + Result.error(code, message)，
 * 便于前端拦截器统一处理
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final ObjectMapper objectMapper;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers("/api/auth/**").permitAll()
            .requestMatchers("/api/invitations/token/**").permitAll() // 候选人凭邀约令牌访问，无需登录
            .requestMatchers("/api/candidate/**").permitAll() // 候选人视频面试（token 在业务层校验）
            .requestMatchers(HttpMethod.GET, "/api/review/interviews/*/score-stream").permitAll() // 评分进度 SSE（EventSource 无 Authorization 头，token 查询参数在业务层校验）
            .requestMatchers("/sse/**", "/mcp/**").permitAll() // MCP Server SSE 端点
            .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
            .requestMatchers("/actuator/health/**", "/actuator/liveness", "/actuator/readiness",
                "/actuator/prometheus").permitAll()
            .requestMatchers("/ws/**").permitAll() // WS 握手由 WsJwtHandshakeInterceptor 鉴权
            .requestMatchers("/", "/error").permitAll()
            .anyRequest().authenticated())
        .exceptionHandling(handling -> handling
            .authenticationEntryPoint((request, response, ex) ->
                writeError(response, ErrorCode.UNAUTHORIZED))
            .accessDeniedHandler((request, response, ex) ->
                writeError(response, ErrorCode.FORBIDDEN)))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    Result<Void> body = Result.error(errorCode, errorCode.getMessage());
    response.getWriter().write(objectMapper.writeValueAsString(body));
  }
}

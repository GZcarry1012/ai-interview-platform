package interview.guide.modules.auth.controller;

import interview.guide.common.annotation.RateLimit;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.result.Result;
import interview.guide.common.security.UserPrincipal;
import interview.guide.modules.auth.dto.LoginRequest;
import interview.guide.modules.auth.dto.LoginResponse;
import interview.guide.modules.auth.dto.RefreshTokenRequest;
import interview.guide.modules.auth.dto.RegisterRequest;
import interview.guide.modules.auth.dto.UserDTO;
import interview.guide.modules.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口
 *
 * 开放注册仅创建求职者角色；企业端角色由管理员分配（P2 后台管理）
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private static final String BEARER_PREFIX = "Bearer ";

  private final AuthService authService;

  @PostMapping("/register")
  @RateLimit(dimension = RateLimit.Dimension.IP, count = 10, interval = 1)
  public Result<UserDTO> register(@Valid @RequestBody RegisterRequest request) {
    return Result.success(authService.register(request));
  }

  @PostMapping("/login")
  @RateLimit(dimension = RateLimit.Dimension.IP, count = 15, interval = 1)
  public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    return Result.success(authService.login(request));
  }

  @PostMapping("/refresh")
  public Result<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
    return Result.success(authService.refresh(request.refreshToken()));
  }

  @PostMapping("/logout")
  public Result<Void> logout(
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
      throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
    }
    authService.logout(authorization.substring(BEARER_PREFIX.length()).trim());
    return Result.success();
  }

  @GetMapping("/me")
  public Result<UserDTO> me(@AuthenticationPrincipal UserPrincipal principal) {
    return Result.success(authService.currentUser(principal.username()));
  }
}

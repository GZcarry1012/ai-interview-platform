package interview.guide.modules.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.security.JwtTokenProvider;
import interview.guide.common.security.AuthUserLoader;
import interview.guide.common.security.UserPrincipal;
import interview.guide.modules.auth.dto.LoginRequest;
import interview.guide.modules.auth.dto.LoginResponse;
import interview.guide.modules.auth.dto.RegisterRequest;
import interview.guide.modules.auth.dto.UserDTO;
import interview.guide.modules.auth.model.OrganizationEntity;
import interview.guide.modules.auth.model.OrganizationType;
import interview.guide.modules.auth.model.UserEntity;
import interview.guide.modules.auth.model.UserRole;
import interview.guide.modules.auth.model.UserStatus;
import interview.guide.modules.auth.repository.OrganizationRepository;
import interview.guide.modules.auth.repository.UserRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务
 *
 * 注册 / 登录 / 刷新 / 登出（Redis 黑名单）/ 当前用户信息；
 * 同时实现 {@link AuthUserLoader}，为 JWT 过滤器提供用户加载
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements AuthUserLoader {

  private static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";
  private static final String DEFAULT_PLATFORM_ORG_NAME = "平台运营方";

  private final UserRepository userRepository;
  private final OrganizationRepository organizationRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider tokenProvider;
  private final RedissonClient redissonClient;

  @Transactional
  public UserDTO register(RegisterRequest request) {
    if (userRepository.existsByUsername(request.username())) {
      throw new BusinessException(ErrorCode.AUTH_USERNAME_EXISTS);
    }
    if (request.email() != null && !request.email().isBlank()
        && userRepository.existsByEmail(request.email())) {
      throw new BusinessException(ErrorCode.AUTH_EMAIL_EXISTS);
    }

    UserEntity user = new UserEntity();
    user.setUsername(request.username());
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setDisplayName(request.displayName());
    user.setEmail(request.email());
    user.setRole(UserRole.USER);
    user.setStatus(UserStatus.ACTIVE);
    UserEntity saved = userRepository.save(user);
    log.info("新用户注册: id={}, username={}", saved.getId(), saved.getUsername());
    return UserDTO.from(saved);
  }

  @Transactional
  public LoginResponse login(LoginRequest request) {
    UserEntity user = userRepository.findByUsernameAndStatus(request.username(), UserStatus.ACTIVE)
        .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    user.setLastLoginAt(LocalDateTime.now());
    userRepository.save(user);

    String accessToken = tokenProvider.generateAccessToken(
        user.getId(), user.getUsername(), user.getRole().name());
    String refreshToken = tokenProvider.generateRefreshToken(
        user.getId(), user.getUsername(), user.getRole().name());
    log.info("用户登录成功: id={}, username={}", user.getId(), user.getUsername());

    return new LoginResponse(accessToken, refreshToken, "Bearer",
        tokenProvider.accessTokenTtlSeconds(), UserDTO.from(user));
  }

  public LoginResponse refresh(String refreshToken) {
    Claims claims = parseRequired(refreshToken);
    String type = claims.get(JwtTokenProvider.CLAIM_TYPE, String.class);
    if (!JwtTokenProvider.TYPE_REFRESH.equals(type)) {
      throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
    }

    UserEntity user = userRepository.findByUsernameAndStatus(
            claims.getSubject(), UserStatus.ACTIVE)
        .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_ACCOUNT_DISABLED));

    String accessToken = tokenProvider.generateAccessToken(
        user.getId(), user.getUsername(), user.getRole().name());
    return new LoginResponse(accessToken, refreshToken, "Bearer",
        tokenProvider.accessTokenTtlSeconds(), UserDTO.from(user));
  }

  /**
   * 登出：把访问令牌的 jti 加入 Redis 黑名单，TTL 与令牌剩余有效期一致
   */
  public void logout(String accessToken) {
    Claims claims = tokenProvider.parseToken(accessToken);
    if (claims == null) {
      // 令牌已过期，无需拉黑
      return;
    }
    long ttlSeconds = tokenProvider.remainingTtlSeconds(claims);
    if (ttlSeconds <= 0 || claims.getId() == null) {
      return;
    }
    RBucket<String> bucket = redissonClient.getBucket(BLACKLIST_KEY_PREFIX + claims.getId());
    bucket.set("1", java.time.Duration.ofSeconds(ttlSeconds));
    log.info("用户登出: subject={}, jti 黑名单 TTL={}s", claims.getSubject(), ttlSeconds);
  }

  @Transactional(readOnly = true)
  public UserDTO currentUser(String username) {
    UserEntity user = userRepository.findByUsername(username)
        .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));
    return UserDTO.from(user);
  }

  /**
   * 首次启动种子：确保存在一个管理员账号（admin / admin123）
   */
  @Transactional
  public void ensureAdminSeeded() {
    if (userRepository.existsByRole(UserRole.ADMIN)) {
      return;
    }
    OrganizationEntity platformOrg = organizationRepository
        .findByNameAndOrgType(DEFAULT_PLATFORM_ORG_NAME, OrganizationType.PLATFORM)
        .orElseGet(() -> {
          OrganizationEntity org = new OrganizationEntity();
          org.setName(DEFAULT_PLATFORM_ORG_NAME);
          org.setOrgType(OrganizationType.PLATFORM);
          return organizationRepository.save(org);
        });

    UserEntity admin = new UserEntity();
    admin.setUsername("admin");
    admin.setPasswordHash(passwordEncoder.encode("admin123"));
    admin.setDisplayName("系统管理员");
    admin.setRole(UserRole.ADMIN);
    admin.setOrgType(OrganizationType.PLATFORM);
    admin.setOrganization(platformOrg);
    admin.setStatus(UserStatus.ACTIVE);
    userRepository.save(admin);
    log.warn("已创建默认管理员账号 admin/admin123，请登录后尽快修改密码");
  }

  @Override
  public UserPrincipal loadActiveUser(Claims claims) {
    return userRepository.findByUsernameAndStatus(claims.getSubject(), UserStatus.ACTIVE)
        .map(user -> new UserPrincipal(user.getId(), user.getUsername(),
            user.getRole().name(),
            user.getOrganization() != null ? user.getOrganization().getId() : null,
            user.getStatus() == UserStatus.ACTIVE))
        .orElse(null);
  }

  private Claims parseRequired(String token) {
    try {
      Claims claims = tokenProvider.parseToken(token);
      if (claims == null) {
        throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
      }
      return claims;
    } catch (JwtException e) {
      throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
    }
  }
}

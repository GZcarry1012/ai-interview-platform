package interview.guide.modules.auth.listener;

import interview.guide.modules.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 管理员账号种子
 *
 * 首次启动时确保存在管理员账号，便于演示与运维进入后台
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserInitializer implements ApplicationRunner {

  private final AuthService authService;

  @Override
  public void run(ApplicationArguments args) {
    authService.ensureAdminSeeded();
  }
}

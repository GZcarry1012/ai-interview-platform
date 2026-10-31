package interview.guide.modules.invitation.controller;

import interview.guide.common.result.Result;
import interview.guide.common.security.UserPrincipal;
import interview.guide.modules.invitation.dto.InvitationDTO;
import interview.guide.modules.invitation.dto.InvitationSendRequest;
import interview.guide.modules.invitation.dto.InvitationTokenDTO;
import interview.guide.modules.invitation.service.InvitationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面试邀约接口
 *
 * /token/** 为候选人侧公开接口（凭邀约令牌访问，无需登录）
 */
@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
public class InvitationController {

  private final InvitationService invitationService;

  @PostMapping("/engineer/{engineerId}")
  @PreAuthorize("hasAnyRole('PARTNER_ADMIN','REVIEWER','ADMIN')")
  public Result<InvitationDTO> send(
      @PathVariable Long engineerId,
      @Valid @RequestBody InvitationSendRequest request,
      @AuthenticationPrincipal UserPrincipal operator) {
    return Result.success(invitationService.send(engineerId, request, operator));
  }

  @GetMapping("/engineer/{engineerId}")
  @PreAuthorize("hasAnyRole('PARTNER_ADMIN','REVIEWER','ADMIN')")
  public Result<List<InvitationDTO>> listByEngineer(
      @PathVariable Long engineerId,
      @AuthenticationPrincipal UserPrincipal operator) {
    return Result.success(invitationService.listByEngineer(engineerId, operator));
  }

  /**
   * 候选人查看邀约信息
   */
  @GetMapping("/token/{token}")
  public Result<InvitationTokenDTO> getByToken(@PathVariable String token) {
    return Result.success(invitationService.getByToken(token));
  }

  /**
   * 候选人确认参加
   */
  @PostMapping("/token/{token}/confirm")
  public Result<InvitationTokenDTO> confirm(@PathVariable String token) {
    return Result.success(invitationService.confirmByToken(token));
  }
}

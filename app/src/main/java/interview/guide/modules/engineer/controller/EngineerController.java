package interview.guide.modules.engineer.controller;

import interview.guide.common.result.Result;
import interview.guide.common.security.UserPrincipal;
import interview.guide.modules.engineer.dto.EngineerDetailDTO;
import interview.guide.modules.engineer.dto.EngineerListItemDTO;
import interview.guide.modules.engineer.dto.EngineerReviewRequest;
import interview.guide.modules.engineer.dto.EngineerSaveRequest;
import interview.guide.modules.engineer.model.EngineerStatus;
import interview.guide.modules.engineer.service.EngineerService;
import interview.guide.modules.invitation.dto.InvitationDTO;
import interview.guide.modules.invitation.dto.InvitationSendRequest;
import interview.guide.modules.invitation.service.InvitationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 工程师准入接口
 *
 * PARTNER_ADMIN：录入/重新提交（限本组织）
 * REVIEWER / ADMIN：查看全部 + 审核通过/驳回
 * 访问控制走方法级 @PreAuthorize，数据隔离在 Service 内按组织过滤
 */
@RestController
@RequestMapping("/api/engineers")
@RequiredArgsConstructor
public class EngineerController {

  private final EngineerService engineerService;
  private final InvitationService invitationService;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('PARTNER_ADMIN')")
  public Result<EngineerListItemDTO> create(
      @Valid @RequestPart("info") EngineerSaveRequest request,
      @RequestPart("resume") MultipartFile resume,
      @AuthenticationPrincipal UserPrincipal operator) {
    return Result.success(engineerService.create(request, resume, operator));
  }

  @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('PARTNER_ADMIN')")
  public Result<EngineerListItemDTO> resubmit(
      @PathVariable Long id,
      @Valid @RequestPart("info") EngineerSaveRequest request,
      @RequestPart(value = "resume", required = false) MultipartFile resume,
      @AuthenticationPrincipal UserPrincipal operator) {
    return Result.success(engineerService.resubmit(id, request, resume, operator));
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('PARTNER_ADMIN','REVIEWER','ADMIN')")
  public Result<List<EngineerListItemDTO>> list(
      @RequestParam(required = false) EngineerStatus status,
      @AuthenticationPrincipal UserPrincipal operator) {
    List<EngineerListItemDTO> items = engineerService.list(status, operator);
    if (!items.isEmpty()) {
      Set<Long> sentIds = invitationService.engineerIdsWithInvitations(
          items.stream().map(EngineerListItemDTO::id).collect(Collectors.toSet()));
      items = items.stream()
          .map(item -> item.withInvitationSent(sentIds.contains(item.id())))
          .collect(Collectors.toList());
    }
    return Result.success(items);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('PARTNER_ADMIN','REVIEWER','ADMIN')")
  public Result<EngineerDetailDTO> detail(
      @PathVariable Long id,
      @AuthenticationPrincipal UserPrincipal operator) {
    return Result.success(engineerService.detail(id, operator));
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
  public Result<EngineerListItemDTO> approve(
      @PathVariable Long id,
      @Valid @RequestBody EngineerReviewRequest request,
      @AuthenticationPrincipal UserPrincipal operator) {
    return Result.success(engineerService.approve(id, request.comment(), operator));
  }

  @PostMapping("/{id}/reject")
  @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
  public Result<EngineerListItemDTO> reject(
      @PathVariable Long id,
      @Valid @RequestBody EngineerReviewRequest request,
      @AuthenticationPrincipal UserPrincipal operator) {
    return Result.success(engineerService.reject(id, request.comment(), operator));
  }

  /**
   * 发送面试邀约（仅审核通过的工程师）
   */
  @PostMapping("/{id}/invitations")
  @PreAuthorize("hasAnyRole('PARTNER_ADMIN','REVIEWER','ADMIN')")
  public Result<InvitationDTO> sendInvitation(
      @PathVariable Long id,
      @Valid @RequestBody InvitationSendRequest request,
      @AuthenticationPrincipal UserPrincipal operator) {
    return Result.success(invitationService.send(id, request, operator));
  }

  /**
   * 查询某工程师的邀约记录
   */
  @GetMapping("/{id}/invitations")
  @PreAuthorize("hasAnyRole('PARTNER_ADMIN','REVIEWER','ADMIN')")
  public Result<List<InvitationDTO>> invitationsOf(
      @PathVariable Long id,
      @AuthenticationPrincipal UserPrincipal operator) {
    return Result.success(invitationService.listByEngineer(id, operator));
  }
}

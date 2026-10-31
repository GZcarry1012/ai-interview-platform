package interview.guide.modules.auth.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户角色
 *
 * USER 面向求职者（C 端），其余角色对应企业端准入面试流程：
 * 合作伙伴业务负责人录入工程师与简历，业务审核人执行审核与复核，
 * 面试官参与面试，管理员负责用户与组织治理
 */
@Getter
@AllArgsConstructor
public enum UserRole {

  USER("求职者"),
  PARTNER_ADMIN("合作伙伴业务负责人"),
  REVIEWER("业务审核人"),
  INTERVIEWER("面试官"),
  ADMIN("管理员");

  private final String displayName;

  public boolean isBackofficeRole() {
    return this != USER;
  }
}

package interview.guide.modules.auth.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 组织类型
 *
 * PLATFORM 为平台运营方，PARTNER 为接入的合作伙伴企业（如车企供应商）
 */
@Getter
@AllArgsConstructor
public enum OrganizationType {

  PLATFORM("平台方"),
  PARTNER("合作伙伴");

  private final String displayName;
}

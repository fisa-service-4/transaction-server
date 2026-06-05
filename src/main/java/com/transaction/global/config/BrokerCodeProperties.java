package com.transaction.global.config;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "broker")
@Getter
@Setter
public class BrokerCodeProperties {

  private Set<String> codes;
  private Map<String, Long> settlementAccounts;
  private Long settlementXUserId;

  @PostConstruct
  public void validate() {
    if (codes == null || codes.isEmpty()) {
      throw new IllegalStateException("broker.codes 설정이 누락되었습니다.");
    }
    if (settlementAccounts == null || settlementAccounts.isEmpty()) {
      throw new IllegalStateException("broker.settlement-accounts 설정이 누락되었습니다.");
    }
    if (settlementXUserId == null) {
      throw new IllegalStateException("broker.settlement-x-user-id 설정이 누락되었습니다.");
    }
  }

  public Long getSettlementAccountId(String brokerCode) {
    return settlementAccounts.get(brokerCode);
  }
}

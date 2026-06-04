package com.transaction.global.config;

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

  public Long getSettlementAccountId(String brokerCode) {
    if (settlementAccounts == null) return null;
    return settlementAccounts.get(brokerCode);
  }
}

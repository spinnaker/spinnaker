package com.netflix.spinnaker.orca.clouddriver.tasks.instance;

import java.util.List;
import lombok.Data;

@Data
public class InstanceHealthCheckInputs {
  private String region;
  private String account;
  private String credentials;
  private List<String> instanceIds;
  private List<String> interestingHealthProviderNames;

  public String accountToUse() {
    return account != null ? account : credentials;
  }

  public boolean hasInstanceIds() {
    return instanceIds != null && !instanceIds.isEmpty();
  }

  public boolean hasEmptyInterestingHealthProviders() {
    return interestingHealthProviderNames != null && interestingHealthProviderNames.isEmpty();
  }
}

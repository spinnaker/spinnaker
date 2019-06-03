package com.netflix.spinnaker.halyard.config.model.v1.providers.ecs;

import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class EcsProvider extends Provider<EcsAccount> {
  private String awsAccount;

  @Override
  public ProviderType providerType() {
    return ProviderType.ECS;
  }
}

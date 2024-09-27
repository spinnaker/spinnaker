package com.netflix.spinnaker.halyard.config.model.v1.providers.cloudrun;

import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CloudrunProvider extends Provider<CloudrunAccount> {
  private String gcloudPath;

  @Override
  public Provider.ProviderType providerType() {
    return ProviderType.CLOUDRUN;
  }
}

package com.netflix.spinnaker.halyard.config.model.v1.providers.dcos;

import com.netflix.spinnaker.halyard.config.model.v1.node.HasClustersProvider;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class DCOSProvider extends HasClustersProvider<DCOSAccount, DCOSCluster> {

  @Override
  public ProviderType providerType() {
    return ProviderType.DCOS;
  }
}

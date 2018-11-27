package com.netflix.spinnaker.halyard.config.model.v1.providers.openstack;

import com.netflix.spinnaker.halyard.config.model.v1.node.HasImageProvider;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OpenstackProvider extends HasImageProvider<OpenstackAccount, OpenstackBakeryDefaults> implements Cloneable {
  @Override
  public ProviderType providerType() {
    return ProviderType.OPENSTACK;
  }

  @Override
  public OpenstackBakeryDefaults emptyBakeryDefaults() {
    OpenstackBakeryDefaults result = new OpenstackBakeryDefaults();
    return result;
  }
}

package com.netflix.spinnaker.halyard.config.model.v1.providers.openstack;

import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;

public class OpenstackProvider extends Provider<OpenstackAccount> {
  //TODO(emjburns): add support for rosco options

  @Override
  public ProviderType providerType() {
    return ProviderType.OPENSTACK;
  }

  @Override
  public void accept(ConfigProblemSetBuilder psBuilder, Validator v) {
    v.validate(psBuilder, this);
  }
}

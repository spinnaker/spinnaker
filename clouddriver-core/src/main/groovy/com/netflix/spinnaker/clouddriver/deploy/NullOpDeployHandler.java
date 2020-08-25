package com.netflix.spinnaker.clouddriver.deploy;

import java.util.List;

public class NullOpDeployHandler implements DeployHandler<String> {
  @Override
  public DeploymentResult handle(String description, List priorOutputs) {
    return null;
  }

  @Override
  public boolean handles(DeployDescription description) {
    return false;
  }
}

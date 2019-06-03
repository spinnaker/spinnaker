package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;

public abstract class SharedDistributedServiceSettings {

  protected DeploymentConfiguration deploymentConfiguration;

  public SharedDistributedServiceSettings(DeploymentConfiguration deploymentConfiguration) {
    this.deploymentConfiguration = deploymentConfiguration;
  }

  public abstract String getDeployLocation();
}

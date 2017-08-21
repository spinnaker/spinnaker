package com.netflix.spinnaker.clouddriver.ecs.deploy.handlers;

import com.netflix.spinnaker.clouddriver.deploy.DeployDescription;
import com.netflix.spinnaker.clouddriver.deploy.DeployHandler;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.BasicEcsDeployDescription;

import java.util.List;

public class BasicEcsDeployHandler implements DeployHandler<BasicEcsDeployDescription> {

  @Override
  public boolean handles(DeployDescription description) {
    return description instanceof BasicEcsDeployDescription;
  }

  @Override
  public DeploymentResult handle(BasicEcsDeployDescription description, List priorOutputs) {

    //TODO - Implement this stub

    return new DeploymentResult();
  }
}

package com.netflix.spinnaker.clouddriver.ecs.deploy.ops;

import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.DeleteScalingPolicyDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;

import java.util.List;

public class DeleteScalingPolicyAtomicOperation implements AtomicOperation<Void> {

  DeleteScalingPolicyDescription description;

  public DeleteScalingPolicyAtomicOperation(DeleteScalingPolicyDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {

    // TODO - implement this stub

    return null;
  }

}

package com.netflix.spinnaker.clouddriver.ecs.deploy.ops;

import com.netflix.spinnaker.clouddriver.ecs.deploy.description.DestroyServiceDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.UpsertScalingPolicyDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;

import java.util.List;

public class UpsertScalingPolicyAtomicOperation implements AtomicOperation<Void> {

  UpsertScalingPolicyDescription description;

  public UpsertScalingPolicyAtomicOperation(UpsertScalingPolicyDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {

    // TODO - implement this stub

    return null;
  }

}

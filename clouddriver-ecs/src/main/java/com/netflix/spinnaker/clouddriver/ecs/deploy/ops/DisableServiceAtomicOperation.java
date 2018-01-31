package com.netflix.spinnaker.clouddriver.ecs.deploy.ops;

import com.netflix.spinnaker.clouddriver.ecs.deploy.description.DisableServiceDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;

import java.util.List;

public class DisableServiceAtomicOperation implements AtomicOperation<Void> {

  DisableServiceDescription description;

  public DisableServiceAtomicOperation(DisableServiceDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {

    // TODO - implement this stub

    return null;
  }

}

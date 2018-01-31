package com.netflix.spinnaker.clouddriver.ecs.deploy.ops;

import com.netflix.spinnaker.clouddriver.ecs.deploy.description.StopServiceDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;

import java.util.List;

public class StopServiceAtomicOperation implements AtomicOperation<Void> {

  StopServiceDescription description;

  public StopServiceAtomicOperation(StopServiceDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {

    // TODO - implement this stub

    return null;
  }

}

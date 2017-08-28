package com.netflix.spinnaker.clouddriver.ecs.deploy.ops;

import com.netflix.spinnaker.clouddriver.ecs.deploy.description.UpdateServiceAndTaskConfigDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;

import java.util.List;

public class UpdateServiceAndTaskConfigAtomicOperation implements AtomicOperation<Void> {

  UpdateServiceAndTaskConfigDescription description;

  public UpdateServiceAndTaskConfigAtomicOperation(UpdateServiceAndTaskConfigDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {

    // TODO - implement this stub

    return null;
  }

}

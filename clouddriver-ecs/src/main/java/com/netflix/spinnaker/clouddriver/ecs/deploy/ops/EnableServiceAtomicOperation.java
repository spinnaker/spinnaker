package com.netflix.spinnaker.clouddriver.ecs.deploy.ops;

import com.netflix.spinnaker.clouddriver.ecs.deploy.description.DestroyServiceDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.EnableServiceDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;

import java.util.List;

public class EnableServiceAtomicOperation implements AtomicOperation<Void> {

  EnableServiceDescription description;

  public EnableServiceAtomicOperation(EnableServiceDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {

    // TODO - implement this stub

    return null;
  }

}

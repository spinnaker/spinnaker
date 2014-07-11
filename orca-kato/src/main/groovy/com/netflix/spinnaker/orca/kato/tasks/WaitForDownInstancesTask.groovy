package com.netflix.spinnaker.orca.kato.tasks

abstract class WaitForDownInstancesTask extends AbstractInstancesCheckTask {

  @Override
  protected boolean hasSucceeded(List instances) {
    !instances.find { it.isHealthy }
  }
}

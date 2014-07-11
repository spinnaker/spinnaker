package com.netflix.spinnaker.orca.kato.tasks

import com.netflix.spinnaker.orca.TaskContext

/**
 * Created by aglover on 7/10/14.
 */
abstract class WaitForDownInstancesTask extends AbstractInstancesCheckTask {

  @Override
  protected boolean hasSucceeded(List instances) {
    !instances.find { it.isHealthy }
  }
}

package com.netflix.spinnaker.orca.kato.tasks

import com.netflix.spinnaker.orca.TaskContext

/**
 * Created by aglover on 7/10/14.
 */
class AsgActionWaitForDownInstancesTask extends WaitForDownInstancesTask{
  @Override
  protected Map<String, List<String>> getServerGroups(TaskContext context) {
    return null
  }
}

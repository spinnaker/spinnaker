package com.netflix.spinnaker.orca.kato.tasks

import com.netflix.spinnaker.orca.TaskContext

/**
 * Created by aglover on 7/10/14.
 */
class AsgActionWaitForDownInstancesTask extends WaitForDownInstancesTask {
  @Override
  protected Map<String, List<String>> getServerGroups(TaskContext context) {
    def inputMap = context.getInputs()
    def key = "disableAsg"

    if (inputMap.containsKey("targetop.asg.enableAsg.name")) {
      key = "enableAsg"
    }

    String asgName = inputMap."targetop.asg.enableAsg.name"
    List<String> regions = inputMap."targetop.asg.enableAsg.regions"

    Map<String, List<String>> serverGroups = [:]

    regions.each { region ->
      if (!serverGroups.containsKey(region)) {
        serverGroups[region] = [asgName]
      }
      serverGroups[region] << asgName
    }
    serverGroups
  }
}

package com.netflix.spinnaker.orca.kato.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.oort.OortService
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractInstancesCheckTask implements RetryableTask {
  long backoffPeriod = 1000
  long timeout = 30000

  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  abstract protected Map<String, List<String>> getServerGroups(TaskContext context)

  abstract protected boolean hasSucceeded(List instances)

  @Override
  TaskResult execute(TaskContext context) {
    String account = context.getInputs()."deploy.account.name"

    Map<String, List<String>> serverGroups = getServerGroups(context)
    println "serverGroups is ${serverGroups}"

    if (!serverGroups || !serverGroups?.values()?.flatten()) {
      return new DefaultTaskResult(TaskResult.Status.FAILED)
    }
    Names names = Names.parseName(serverGroups.values().flatten()[0])
    def response = oortService.getCluster(names.app, account, names.cluster)
    if (response.status != 200) {
      return new DefaultTaskResult(TaskResult.Status.RUNNING)
    }
    def clusters = objectMapper.readValue(response.body.in().text, List)
    if (!clusters) {
      return new DefaultTaskResult(TaskResult.Status.RUNNING)
    }
    Map cluster = (Map) clusters[0]
    for (Map serverGroup in cluster.serverGroups) {
      String region = serverGroup.region
      String name = serverGroup.name

      List instances = serverGroup.instances
      Map asg = serverGroup.asg
      int minSize = asg.minSize

      if (!serverGroups[region].contains(name) || minSize >= instances.size()) {
        continue
      }

      def isComplete = hasSucceeded(instances)
      if (!isComplete) {
        return new DefaultTaskResult(TaskResult.Status.RUNNING)
      }
    }

    return new DefaultTaskResult(TaskResult.Status.SUCCEEDED)
  }

}

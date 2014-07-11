package com.netflix.spinnaker.orca.kato.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.oort.OortService
import org.springframework.beans.factory.annotation.Autowired

/**
 * Created by aglover on 7/10/14.
 */
class WaitForDownInstancesTask implements RetryableTask{
  long backoffPeriod = 1000
  long timeout = 30000

  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  @Override
  TaskResult execute(TaskContext context) {
    String account = context.inputs."deploy.account.name"
    Map<String, List<String>> serverGroups = getServerGroups(context)
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
    Map cluster = (Map)clusters[0]
    for (Map serverGroup in cluster.serverGroups) {
      String region = serverGroup.region
      String name = serverGroup.name

      List instances = serverGroup.instances
      Map asg = serverGroup.asg
      int minSize = asg.minSize

      if (!serverGroups[region].contains(name) || minSize < instances.size()) {
        continue
      }

      def allHealthy = !instances.find { !it.isHealthy }
      if (!allHealthy) {
        return new DefaultTaskResult(TaskResult.Status.RUNNING)
      }
    }

    return new DefaultTaskResult(TaskResult.Status.SUCCEEDED)
  }

  private Map<String, List<String>> getServerGroups(TaskContext context) {
    Map<String, List<String>> serverGroups = (Map<String, List<String>>) context.inputs."deploy.server.groups"
    serverGroups
  }
}

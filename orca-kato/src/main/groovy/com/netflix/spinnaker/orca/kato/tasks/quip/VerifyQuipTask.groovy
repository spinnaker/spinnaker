package com.netflix.spinnaker.orca.kato.tasks.quip

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RestAdapter
import retrofit.RetrofitError

@Component
class VerifyQuipTask extends AbstractQuipTask implements Task {

  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  @Override
  TaskResult execute(Stage stage) {
    String cluster = stage.context?.clusterName
    String region = stage.context?.region
    String account = stage.context?.account
    String app = stage.context?.application
    ArrayList healthProviders = stage.context?.healthProviders
    ArrayList instances
    Map stageOutputs = [:]
    ExecutionStatus executionStatus = ExecutionStatus.SUCCEEDED
    if (cluster && region && account && healthProviders && app) {
      def response = oortService.getCluster(app, account, cluster, stage.context.providerType ?: "aws")
      def oortCluster = objectMapper.readValue(response.body.in().text, Map)

      if (!oortCluster || !oortCluster.serverGroups) {
        throw new RuntimeException("unable to find any server groups for ${cluster}, ${region}, ${account}")
      }

      def asgsForCluster = oortCluster.serverGroups.findAll {
        it.region == region
      }

      //verify that there is only one ASG, maybe support it in the future
      if (asgsForCluster.size() != 1) {
        throw new RuntimeException("quick patch only runs if there is a single server group in the cluster for ${cluster}, ${region}, ${account}")
      }

      instances = asgsForCluster.collect {
        it.instances.publicDnsName
      }.get(0)

      def instanceIds = asgsForCluster.collect {
        it.instances.instanceId
      }.get(0)

      if(!instances || instances.size() <=0) {
        throw new RuntimeException("could not find any instances for ${cluster}, ${region}, ${account}")
      }

      // inject instances into the context
      stageOutputs.put("instances", instances)
      stageOutputs.put("instanceIds",instanceIds) // for waitForUpInstanceHealthTask
      stageOutputs.put("relevant.health.providers",healthProviders) // for waitForUpInstanceHealthTask
      stageOutputs.put("deploy.server.groups", [region : asgsForCluster.get(0).name]) // for ServerGroupCacheForceRefreshTask

      if(!checkInstancesForQuip(instances)) {
        throw new RuntimeException("quip is not running on all instances : ${instances}")
      }
    } else {
      throw new RuntimeException("one or more of these parameters is missing : cluster || region || account || healthProviders || app")
    }
    return new DefaultTaskResult(executionStatus, stageOutputs, [:])
  }

  private boolean checkInstancesForQuip(Collection instances) {
    // verify that /tasks endpoint responds
    boolean allInstancesHaveQuip = true
    instances.each {
      def instanceService = createInstanceService("http://${it}:5050")
      try {
        instanceService.listTasks()
      } catch(RetrofitError e) {
        allInstancesHaveQuip = false
      }
    }
    return allInstancesHaveQuip
  }
}

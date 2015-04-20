package com.netflix.spinnaker.orca.kato.tasks.quip

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.oort.InstanceService
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RestAdapter

@Component
class VerifyQuipTask implements Task {

  @Autowired
  OortService oortService

  InstanceService instanceService

  // FIXME
  boolean testing = false

  @Autowired
  ObjectMapper objectMapper

  @Override
  TaskResult execute(Stage stage) {
    String cluster = stage.context?.clusterName
    String region = stage.context?.region
    String account = stage.context?.account
    String app = stage.context?.application
    ArrayList instances
    Map stageOutputs = [:]
    TaskResult result
    ExecutionStatus executionStatus = ExecutionStatus.SUCCEEDED
    if (cluster && region && account) {
      def response = oortService.getCluster(app, account, cluster, stage.context.providerType ?: "aws")

      if (response.status != 200) {
        return new DefaultTaskResult(ExecutionStatus.FAILED)
      }
      def oortCluster = objectMapper.readValue(response.body.in().text, Map)

      if (!oortCluster || !oortCluster.serverGroups) {
        return new DefaultTaskResult(ExecutionStatus.FAILED)
      }

      def asgsForCluster = oortCluster.serverGroups.findAll {
        it.region == region
      }

      //verify that there is only one ASG, maybe support it in the future
      if (asgsForCluster.size() != 1) {
        return new DefaultTaskResult(ExecutionStatus.FAILED)
      }

      instances = asgsForCluster.collect {
        it.instances.publicDnsName
      }.get(0)

      def instanceIds = asgsForCluster.collect {
        it.instances.instanceId
      }.get(0)

      if(!instances || instances.size() <=0) {
        return new DefaultTaskResult(ExecutionStatus.FAILED)
      }

      // inject instances into the context
      stageOutputs.put("instances", instances)
      stageOutputs.put("instanceIds",instanceIds) // for waitForUpInstanceHealthTask
      stageOutputs.put("deploy.server.groups", [region : asgsForCluster.get(0).name]) // for ServerGroupCacheForceRefreshTask

      if(!checkInstancesForQuip(instances)) {
        executionStatus = ExecutionStatus.FAILED
      }

    } else {
      executionStatus = ExecutionStatus.FAILED
    }
    return new DefaultTaskResult(executionStatus, stageOutputs, [:])
  }

  private boolean checkInstancesForQuip(Collection instances) {
    // verify that /tasks endpoint responds
    boolean allInstancesHaveQuip = true
    instances.each {
      RestAdapter restAdapter = new RestAdapter.Builder()
        .setEndpoint("http://${it}:5050")
        .build()

      if(!testing) {
        instanceService = restAdapter.create(InstanceService.class)
      }
      def instanceResponse = instanceService.listTasks()
      if (instanceResponse.status != 200) {
        allInstancesHaveQuip = false
      }
    }
    return allInstancesHaveQuip
  }
}

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
    Map instances = stage.context?.instances
    ArrayList healthProviders = stage.context?.healthProviders
    Map stageOutputs = [:]
    ExecutionStatus executionStatus = ExecutionStatus.SUCCEEDED
    if (cluster && region && account && healthProviders && app && instances) {
      stageOutputs.put("relevant.health.providers",healthProviders) // for waitForUpInstanceHealthTask

      if(!checkInstancesForQuip(instances)) {
        throw new RuntimeException("quip is not running on all instances : ${instances}")
      }
    } else {
      throw new RuntimeException("one or more of these parameters is missing : cluster || region || account || healthProviders || app")
    }
    return new DefaultTaskResult(executionStatus, stageOutputs, [:])
  }

  private boolean checkInstancesForQuip(Map instances) {
    // verify that /tasks endpoint responds
    boolean allInstancesHaveQuip = true
    instances.each { key, value ->
      def instanceService = createInstanceService("http://${value}:5050")
      try {
        instanceService.listTasks()
      } catch(RetrofitError e) {
        allInstancesHaveQuip = false
      }
    }
    return allInstancesHaveQuip
  }
}

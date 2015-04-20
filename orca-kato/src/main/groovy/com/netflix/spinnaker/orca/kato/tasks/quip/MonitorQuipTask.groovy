package com.netflix.spinnaker.orca.kato.tasks.quip

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.oort.InstanceService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RestAdapter

@Component
class MonitorQuipTask implements RetryableTask {
  @Autowired ObjectMapper objectMapper

  long backoffPeriod = 1000
  long timeout = 3600000 // 1hr

  // FIXME
  boolean testing = false
  InstanceService instanceService

  /**
   * TODO: make this more efficient by only polling the instances which are not done.
   * I'm not dealing with it now since I think this will be used on a small scale, 1-2 instances at a time
   */
  @Override
  TaskResult execute(Stage stage) {
    boolean anyFailed = false
    def result = new DefaultTaskResult(ExecutionStatus.SUCCEEDED)

    if(!stage.context.taskIds || !stage.context.instances) {
      return new DefaultTaskResult(ExecutionStatus.FAILED)
    }

    stage.context?.instances.each {
      RestAdapter restAdapter = new RestAdapter.Builder()
        .setEndpoint("http://${it}:5050")
        .build()

      if(!testing) {
        instanceService = restAdapter.create(InstanceService.class)
      }
      def instanceResponse = instanceService.listTask(stage.context.taskIds.get(it))
      // return status
      int retries = 5
      while (retries-- > 0 && instanceResponse.status != 200) {
        instanceResponse = instanceService.listTask(stage.context.taskIds.get(it))
      }

      if(instanceResponse.status != 200) {
        result = new DefaultTaskResult(ExecutionStatus.FAILED)
      }

      def status = objectMapper.readValue(instanceResponse.body.in().text, Map).status
      if(status == "Successful") {
        // noop unless they all succeeded
      } else if(status == "Failed") {
        anyFailed = true
        result = new DefaultTaskResult(ExecutionStatus.FAILED)
      } else if(status == "Running" && !anyFailed) {
        result =  new DefaultTaskResult(ExecutionStatus.RUNNING)
      } else {
        result = new DefaultTaskResult(ExecutionStatus.FAILED)
      }
    }
    return result
  }
}

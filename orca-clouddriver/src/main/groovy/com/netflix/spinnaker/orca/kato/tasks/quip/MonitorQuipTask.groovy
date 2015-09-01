package com.netflix.spinnaker.orca.kato.tasks.quip

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Component
class MonitorQuipTask extends AbstractQuipTask implements RetryableTask {
  @Autowired ObjectMapper objectMapper

  long backoffPeriod = 10000
  long timeout = 600000 // 10mins

  /**
   * TODO: make this more efficient by only polling the instances which are not done.
   * I'm not dealing with it now since I think this will be used on a small scale, 1-2 instances at a time
   */
  @Override
  TaskResult execute(Stage stage) {
    def result = new DefaultTaskResult(ExecutionStatus.SUCCEEDED)

    if(!stage.context.taskIds || !stage.context.instances) {
      throw new RuntimeException("missing taskIds and/or instances")
    }

    stage.context?.instances.each {String key, Map valueMap ->
      String hostName = valueMap.hostName
      def taskId = stage.context.taskIds.get(hostName)
      def instanceService = createInstanceService("http://${hostName}:5050")
      try {
        def instanceResponse = instanceService.listTask(taskId)
        def status = objectMapper.readValue(instanceResponse.body.in().text, Map).status
        if(status == "Successful") {
          // noop unless they all succeeded
        } else if(status == "Failed") {
          throw new RuntimeException("quip task failed for ${hostName} with a result of ${status}, see http://${hostName}:5050/tasks/${taskId}")
        } else if(status == "Running") {
          result = new DefaultTaskResult(ExecutionStatus.RUNNING)
        } else {
          throw new RuntimeException("quip task failed for ${hostName} with a result of ${status}, see http://${hostName}:5050/tasks/${taskId}")
        }
      } catch(RetrofitError e) {
        result = new DefaultTaskResult(ExecutionStatus.RUNNING)
      }
    }
    return result
  }


}

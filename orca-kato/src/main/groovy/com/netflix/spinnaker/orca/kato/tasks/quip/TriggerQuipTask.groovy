package com.netflix.spinnaker.orca.kato.tasks.quip

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.oort.InstanceService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.OperatingSystem
import com.netflix.spinnaker.orca.pipeline.util.PackageInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RestAdapter

@Component
class TriggerQuipTask implements Task  {
  @Autowired ObjectMapper objectMapper

  InstanceService instanceService

  // FIXME
  boolean testing = false

  @Override
  TaskResult execute(Stage stage) {
    Map taskIdMap = [:]
    OperatingSystem operatingSystem = OperatingSystem.valueOf(stage.context.baseOs)
    PackageInfo packageInfo = new PackageInfo(stage, operatingSystem.packageType.packageType,
      operatingSystem.packageType.versionDelimiter, true, true, objectMapper)
    String packageName = stage.context?.packageName
    String version = stage.context?.patchVersion ?:  packageInfo.findTargetPackage()?.packageVersion
    def instances = stage.context?.instances
    //TaskResult taskResult //= new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
    ExecutionStatus executionStatus = ExecutionStatus.SUCCEEDED
    // verify instance list, package, and version are in the context
    if(version && packageName && instances) {
      // trigger patch on target server
      instances.each {
        RestAdapter restAdapter = new RestAdapter.Builder()
          .setEndpoint("http://${it}:5050")
          .build()

        if(!testing) {
          instanceService = restAdapter.create(InstanceService.class)
        }

        def instanceResponse = instanceService.patchInstance(packageName, version)
        int retries = 5

        while (retries-- > 0 && instanceResponse.status != 200) {
          instanceResponse = instanceService.patchInstance(packageName, version)
        }

        if(instanceResponse.status == 200) {
          def ref = objectMapper.readValue(instanceResponse.body.in().text, Map).ref
          taskIdMap.put(it, ref.substring(1+ref.lastIndexOf('/')))
        } else {
          executionStatus = ExecutionStatus.FAILED
        }
      }
    } else {
      executionStatus = ExecutionStatus.FAILED
    }
    return new DefaultTaskResult(executionStatus, ["taskIds" : taskIdMap])
  }
}

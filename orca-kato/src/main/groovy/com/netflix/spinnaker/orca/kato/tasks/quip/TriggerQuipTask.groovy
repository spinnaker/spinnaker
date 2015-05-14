package com.netflix.spinnaker.orca.kato.tasks.quip

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.OperatingSystem
import com.netflix.spinnaker.orca.pipeline.util.PackageInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Component
class TriggerQuipTask extends AbstractQuipTask implements RetryableTask  {
  @Autowired ObjectMapper objectMapper

  long backoffPeriod = 10000
  long timeout = 60000 // 1min

  @Override
  TaskResult execute(Stage stage) {
    Map taskIdMap = [:]
    OperatingSystem operatingSystem = OperatingSystem.valueOf(stage.context.baseOs)
    PackageInfo packageInfo = new PackageInfo(stage, operatingSystem.packageType.packageType,
      operatingSystem.packageType.versionDelimiter, true, true, objectMapper)
    String packageName = stage.context?.package
    String version = stage.context?.patchVersion ?:  packageInfo.findTargetPackage()?.packageVersion
    Map stageOutputs = [:]
    stageOutputs.put("version", version) // so the ui can display the discovered package version
    def instances = stage.context?.instances
    ExecutionStatus executionStatus = ExecutionStatus.SUCCEEDED
    // verify instance list, package, and version are in the context
    if(version && packageName && instances) {
      // trigger patch on target server
      instances.each { String key, Map valueMap ->
        String hostName = valueMap.hostName
        def instanceService = createInstanceService("http://${hostName}:5050")

        try {
          def instanceResponse = instanceService.patchInstance(packageName, version)
          def ref = objectMapper.readValue(instanceResponse.body.in().text, Map).ref
          taskIdMap.put(hostName, ref.substring(1+ref.lastIndexOf('/')))
        } catch(RetrofitError e) {
          executionStatus = ExecutionStatus.RUNNING
        }
      }
    } else {
      throw new RuntimeException("one or more required parameters are missing : version (${version}) || package (${packageName})|| instances (${instances})")
    }
    stageOutputs.put("taskIds", taskIdMap)
    return new DefaultTaskResult(executionStatus, stageOutputs)
  }
}

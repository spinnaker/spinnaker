/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.kato.tasks.quip

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.InstanceService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import retrofit.client.Client

@Component
@Slf4j
class TriggerQuipTask extends AbstractQuipTask implements RetryableTask {
  @Autowired
  ObjectMapper objectMapper

  @Autowired
  Client retrofitClient

  private static final long DEFAULT_INSTANCE_VERSION_SLEEP = 10000

  long instanceVersionSleep = DEFAULT_INSTANCE_VERSION_SLEEP

  long backoffPeriod = 10000
  long timeout = 600000 // 10min

  @Override
  TaskResult execute(Stage stage) {
    Map taskIdMap = [:]

    Map<String, Map> instances = stage.context.instances
    Map<String, Map> remainingInstances = stage.context.remainingInstances == null ? new HashMap<>(instances) : stage.context.remainingInstances
    String packageName = stage.context?.package
    String version = stage.ancestors().find { ancestorStage ->
      ancestorStage.id == stage.parentStageId
    }?.context?.version ?: stage.context.version
   Map<String, Map> skippedInstances = stage.context.skippedInstances ?: [:]
    Set<String> patchedInstanceIds = []
    // verify instance list, package, and version are in the context
    if (version && packageName && remainingInstances) {
      // trigger patch on target server
      remainingInstances.each { String instanceId, Map instance ->
        String instanceHostName = instance.privateIpAddress ?: instance.hostName
        def instanceService = createInstanceService("http://${instanceHostName}:5050")
        if (stage.context.skipUpToDate &&
          // optionally check the installed package version and skip if == target version
          (getAppVersion(instanceService, packageName) == version)) {
            skippedInstances.put(instanceId, instance)
            //remove from instances so we don't wait for it to quip
            instances.remove(instanceId)
        } else {
          try {
            def instanceResponse = instanceService.patchInstance(packageName, version, "")
            def ref = objectMapper.readValue(instanceResponse.body.in().text, Map).ref
            taskIdMap.put(instanceHostName, ref.substring(1 + ref.lastIndexOf('/')))
            patchedInstanceIds << instanceId
          } catch (RetrofitError e) {
            log.warn("Error in Quip request: {}", e.message)
          }
        }
      }
      remainingInstances.keySet().removeAll(patchedInstanceIds)
      remainingInstances.keySet().removeAll(skippedInstances.keySet())
    } else {
      throw new RuntimeException("one or more required parameters are missing : version (${version}) || package (${packageName})|| instances (${instances})")
    }
    Map stageOutputs = [
      taskIds: taskIdMap,
      instances: instances,
      instanceIds: instances.keySet() + skippedInstances.keySet(), // for WaitForDown/UpInstancesTask
      skippedInstances: skippedInstances,
      remainingInstances: remainingInstances,
      version: version
    ]
    return new TaskResult(remainingInstances ? ExecutionStatus.RUNNING : ExecutionStatus.SUCCEEDED, stageOutputs)
  }

  String getAppVersion(InstanceService instanceService, String packageName) {
    int retries = 5;
    def instanceResponse
    String version

    while (retries) {
      try {
        instanceResponse = instanceService.getCurrentVersion(packageName)
        version = objectMapper.readValue(instanceResponse.body.in().text, Map)?.version
        if (version && !version.isEmpty()) {
          return version
        }
      } catch (RetrofitError e) {
        //retry
      }
      sleep(instanceVersionSleep)
      --retries
    }

    // instead of failing the stage if we can't detect the version, try to install new version anyway
    return null
  }
}

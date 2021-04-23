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

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.model.Instance.InstanceInfo
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Component
@Deprecated
class InstanceHealthCheckTask extends AbstractQuipTask implements RetryableTask {

  long backoffPeriod = 10000
  long timeout = 3600000 // 60min

  @Autowired
  OortHelper oortHelper

  @Override
  TaskResult execute(StageExecution stage) {
    Map<String, InstanceInfo> instances = stage.mapTo(Inputs).instances

    if (instances == null || instances.isEmpty()) {
      return TaskResult.SUCCEEDED
    }

    Map<String, Object> stageOutputs = new HashMap<>()
    ExecutionStatus executionStatus = ExecutionStatus.SUCCEEDED
    for (instanceEntry in instances) {
      // TODO: Run these health checks in parallel?
      InstanceInfo instance = instanceEntry.value
      if (!instance.healthCheckUrl || instance.healthCheckUrl.isEmpty()) {
        // get a refreshed version of the instance info
        instances = oortHelper.getInstancesForCluster(stage.context, null, true)
        stageOutputs.put("instances", instances)
        return TaskResult.builder(ExecutionStatus.RUNNING).context(stageOutputs).build()
      }

      URL healthCheckUrl = new URL(instance.healthCheckUrl)
      def host = instance.privateIpAddress ?: healthCheckUrl.host
      def instanceService = createInstanceService("http://${host}:${healthCheckUrl.port}")
      try { // keep trying until we get a 200 or time out
        instanceService.healthCheck(healthCheckUrl.path.substring(1))
      } catch (RetrofitError e) {
        executionStatus = ExecutionStatus.RUNNING
      }
    }
    return TaskResult.builder(executionStatus).context(stageOutputs).build()
  }

  protected static class Inputs {
    public Map<String, InstanceInfo> instances
  }
}

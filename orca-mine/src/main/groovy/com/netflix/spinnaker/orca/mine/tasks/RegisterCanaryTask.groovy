/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.mine.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.mine.pipeline.DeployCanaryStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import retrofit.client.Response
import static java.util.concurrent.TimeUnit.HOURS
import static java.util.concurrent.TimeUnit.MINUTES

@Component
@Slf4j
class RegisterCanaryTask implements Task {

  @Autowired
  MineService mineService

  @Override
  TaskResult execute(Stage stage) {
    String app = stage.context.application ?: stage.execution.application
    Stage deployStage = stage.execution.stages.find {
      it.parentStageId == stage.parentStageId && it.type == DeployCanaryStage.PIPELINE_CONFIG_TYPE
    }

    Map c = buildCanary(app, deployStage)

    log.info("Registering Canary (executionId: ${stage.execution.id}, stageId: ${stage.id}, canary: ${c})")

    String canaryId = null

    try {
      Response response = mineService.registerCanary(c)
      if (response.status == 200 && response.body.mimeType().startsWith('text/plain')) {
        canaryId = response.body.in().text
      }
    } catch (RetrofitError re) {
      def response = [:]
      try {
        response = re.getBodyAs(Map) as Map
      } catch (Exception e) {
        response.error = e.message
      }

      response.status = re.response?.status
      response.errorKind = re.kind

      throw new IllegalStateException(
        "Unable to register canary (executionId: ${stage.execution.id}, stageId: ${stage.id} canary: ${c}), response: ${response}"
      )
    }

    log.info("Registered Canary (executionId: ${stage.execution.id}, stageId: ${stage.id}, canaryId: ${canaryId})")

    def canary = mineService.getCanary(canaryId)
    def outputs = [
      canary              : canary,
      stageTimeoutMs      : getMonitorTimeout(canary),
      deployedClusterPairs: deployStage.context.deployedClusterPairs,
      application         : c.application
    ]

    if (deployStage.context.deployedClusterPairs?.getAt(0)?.canaryCluster?.accountName) {
      outputs.account = deployStage.context.deployedClusterPairs[0].canaryCluster.accountName
    }

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build()
  }

  Map buildCanary(String app, Stage stage) {
    Map c = stage.context.canary
//    c.canaryDeployments.each {
//      it["@class"] = ".ClusterCanaryDeployment"
//    }
    c.application = c.application ?: app
    c.canaryConfig.canaryHealthCheckHandler = c.canaryConfig.canaryHealthCheckHandler ?: [:]
    c.canaryConfig.canaryHealthCheckHandler['@class'] = c.canaryConfig.canaryHealthCheckHandler['@class'] ?: 'com.netflix.spinnaker.mine.CanaryResultHealthCheckHandler'
    c.canaryConfig.name = c.canaryConfig.name ?: stage.execution.id
    c.canaryConfig.application = c.canaryConfig.application ?: c.application ?: app
    return c
  }

  private static Long getMonitorTimeout(Map canary) {
    def timeoutPaddingMin = 120
    def lifetimeHours = canary.canaryConfig.lifetimeHours?.toString() ?: "46"
    def warmupMinutes = canary.canaryConfig.canaryAnalysisConfig?.beginCanaryAnalysisAfterMins?.toString() ?: "0"
    int timeoutMinutes = HOURS.toMinutes(lifetimeHours.isInteger() ? lifetimeHours.toInteger() : 46) + (warmupMinutes.isInteger() ? warmupMinutes.toInteger() + timeoutPaddingMin : timeoutPaddingMin)
    return MINUTES.toMillis(timeoutMinutes)
  }
}

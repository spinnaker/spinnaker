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

import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerConversionException
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.mine.pipeline.DeployCanaryStage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit2.Response
import javax.annotation.Nonnull

import static java.util.concurrent.TimeUnit.HOURS
import static java.util.concurrent.TimeUnit.MINUTES

@Component
@Slf4j
class RegisterCanaryTask implements Task {

  @Autowired
  MineService mineService

  @Nonnull
  @Override
  TaskResult execute(@Nonnull StageExecution stage) {
    String app = stage.context.application ?: stage.execution.application
    StageExecution deployStage = stage.execution.stages.find {
      it.parentStageId == stage.parentStageId && it.type == DeployCanaryStage.PIPELINE_CONFIG_TYPE
    }

    Map c = buildCanary(app, deployStage)

    log.info("Registering Canary (executionId: ${stage.execution.id}, stageId: ${stage.id}, canary: ${c})")

    String canaryId = null

    try {
      Response response = Retrofit2SyncCall.executeCall(mineService.registerCanary(c))
      if (response.code() == 200 && response.body().contentType().toString().startsWith('text/plain')) {
        canaryId = response.body().byteStream().text
      }
    } catch (SpinnakerHttpException re) {
      def response = [:]
      try {
        def responseBody = re.responseBody
        response = responseBody!=null ? responseBody : response
      } catch (Exception e) {
        response.error = e.message
      }

      response.status = re.responseCode
      response.errorKind = "HTTP"

      throw new IllegalStateException(
        "Unable to register canary (executionId: ${stage.execution.id}, stageId: ${stage.id} canary: ${c}), response: ${response}"
      )
    } catch(SpinnakerServerException e){
      def response = [:]
      response.status = null
      if (e instanceof SpinnakerNetworkException){
        response.errorKind = "NETWORK"
      } else if(e instanceof SpinnakerConversionException) {
        response.errorKind = "CONVERSION"
      } else {
        response.errorKind = "UNEXPECTED"
      }
      throw new IllegalStateException(
          "Unable to register canary (executionId: ${stage.execution.id}, stageId: ${stage.id} canary: ${c}), response: ${response}"
      )
    }

    log.info("Registered Canary (executionId: ${stage.execution.id}, stageId: ${stage.id}, canaryId: ${canaryId})")

    def canary = Retrofit2SyncCall.execute(mineService.getCanary(canaryId))
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

  Map buildCanary(String app, StageExecution stage) {
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

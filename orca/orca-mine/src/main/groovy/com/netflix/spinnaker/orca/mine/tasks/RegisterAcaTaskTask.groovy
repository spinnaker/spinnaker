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
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.mine.MineService
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit2.Response
import javax.annotation.Nonnull

import static java.util.concurrent.TimeUnit.HOURS
import static java.util.concurrent.TimeUnit.MINUTES

@Component
@Slf4j
class RegisterAcaTaskTask implements Task {

  @Autowired
  MineService mineService

  @Nonnull
  @Override
  TaskResult execute(@Nonnull StageExecution stage) {
    String app = stage.context.application ?: stage.execution.application

    Map c = buildCanary(app, stage)

    log.info("Registering Canary (executionId: ${stage.execution.id}, canary: ${c})")

    Response response = Retrofit2SyncCall.executeCall(mineService.registerCanary(c))
    String canaryId

    if (response.code() == 200 && response.body().contentType().toString().startsWith('text/plain')) {
      canaryId = response.body().byteStream().text
    } else {
      throw new IllegalStateException("Unable to handle $response")
    }

    def canary = Retrofit2SyncCall.execute(mineService.getCanary(canaryId))
    def outputs = [
      canary: canary,
      continueOnUnhealthy: stage.context.continueOnUnhealthy ?: false,
      stageTimeoutMs: getMonitorTimeout(canary),
    ]

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build()
  }

  Map buildCanary(String app, StageExecution stage) {
    Map c = stage.context.canary
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

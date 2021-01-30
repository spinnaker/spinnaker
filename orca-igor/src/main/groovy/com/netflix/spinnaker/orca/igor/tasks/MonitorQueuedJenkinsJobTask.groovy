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

package com.netflix.spinnaker.orca.igor.tasks

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.igor.IgorService
import org.springframework.beans.factory.annotation.Value

import java.util.concurrent.TimeUnit
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.igor.BuildService
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Slf4j
@Component
class MonitorQueuedJenkinsJobTask implements OverridableTimeoutRetryableTask {
  private final BuildService buildService
  private final String wwwBaseUrl

  @Autowired
  MonitorQueuedJenkinsJobTask(BuildService buildService, @Value("\${spinnaker.base-url.www}") String wwwBaseUrl) {
    this.buildService = buildService
    this.wwwBaseUrl = wwwBaseUrl
  }

  @Override
  TaskResult execute(StageExecution stage) {
    String jenkinsController = stage.context.master
    String jobName = stage.context.job
    String queuedBuild = stage.context.queuedBuild

    try {
      Map<String, Object> build = buildService.queuedBuild(jenkinsController, queuedBuild)
      if (build?.number == null) {
        return TaskResult.ofStatus(ExecutionStatus.RUNNING)
      } else {
        createBacklink(stage, jenkinsController, jobName, build.number as Integer)
        return TaskResult.builder(ExecutionStatus.SUCCEEDED).context([buildNumber: build.number]).build()
      }
    } catch (RetrofitError e) {
      if ([503, 500, 404].contains(e.response?.status)) {
        log.warn("Http ${e.response.status} received from `igor`, retrying...")
        return TaskResult.ofStatus(ExecutionStatus.RUNNING)
      }
      throw e
    }
  }

  @Override
  long getBackoffPeriod() {
    return 10000
  }

  @Override
  long getTimeout() {
    return TimeUnit.HOURS.toMillis(2)
  }

  /**
   * Update the description of a running Jenkins build and provide a deep link back to the particular execution in
   * Spinnaker.
   *
   * Requires that `wwwHost` be specified _and_ that the build is running (vs. queued).
   */
  private void createBacklink(StageExecution stageExecution,
                              String jenkinsController,
                              String jobName,
                              Integer buildNumber) {
    if (wwwBaseUrl == null) {
      log.info("Not creating backlink from Jenkins to Spinnaker, see https://spinnaker.io/setup/ci/jenkins/ for more info")
      return
    }

    try {
      buildService.updateBuild(
          jenkinsController,
          jobName,
          buildNumber,
          new IgorService.UpdatedBuild(
              String.format(
                  "This build was triggered by '<a href=\"%s/#/applications/%s/executions/details/%s\">%s</a>' in Spinnaker.",
                  wwwBaseUrl,
                  stageExecution.execution.application,
                  stageExecution.execution.id,
                  Optional.ofNullable(stageExecution.execution.name).orElse("Unknown Pipeline")
              )
          )
      )
    } catch (Exception e) {
      log.warn("Unable to update build description on {}:{}:{}", jenkinsController, jobName, buildNumber, e)
    }
  }
}

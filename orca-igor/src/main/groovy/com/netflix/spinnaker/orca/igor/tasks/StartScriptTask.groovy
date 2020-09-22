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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.exceptions.IntegrationException
import com.netflix.spinnaker.kork.exceptions.SystemException
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.igor.BuildService
import com.netflix.spinnaker.orca.retrofit.exceptions.RetrofitExceptionHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import retrofit.client.Response

import javax.annotation.Nonnull
import java.time.Duration

@Component
class StartScriptTask implements RetryableTask {
  private final Logger log = LoggerFactory.getLogger(getClass())

  @Autowired
  BuildService buildService

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  RetrofitExceptionHandler retrofitExceptionHandler

  @Value('${script.master:master}')
  String master

  @Value('${script.job:job}')
  String defaultJob

  @Nonnull
  @Override
  TaskResult execute(@Nonnull StageExecution stage) {
    String scriptPath = stage.context.scriptPath
    String command = stage.context.command
    String image = stage.context.image
    String region = stage.context.region
    String account = stage.context.account
    String cluster = stage.context.cluster
    String cmc = stage.context.cmc
    String repoUrl = stage.context.repoUrl
    String repoBranch = stage.context.repoBranch
    String job = stage.context.job ?: defaultJob

    if (stage.execution.trigger.strategy) {
      def trigger = stage.execution.trigger
      image = image ?: trigger.parameters.amiName ?: trigger.parameters.imageId ?: ''
      cluster = cluster ?: trigger.parameters.cluster ?: ''
      account = account ?: trigger.parameters.credentials ?: ''
      region = region ?: trigger.parameters.region ?: trigger.parameters.zone ?: ''
    }

    def parameters = [
        SCRIPT_PATH  : scriptPath,
        COMMAND      : command,
        IMAGE_ID     : image,
        REGION_PARAM : region,
        ENV_PARAM    : account,
        CLUSTER_PARAM: cluster,
        CMC          : cmc
    ]

    if (repoUrl) {
      parameters.REPO_URL = repoUrl
    }

    if (repoBranch) {
      parameters.REPO_BRANCH = repoBranch
    }

    try {
      Response igorResponse = buildService.build(master, job, parameters)

      if (igorResponse.getStatus() == HttpStatus.ACCEPTED.value()) {
        log.info("script for job=$job on master=$master is pending, waiting for script to start")
        return TaskResult.RUNNING
      }

      if (igorResponse.getStatus() == HttpStatus.OK.value()) {
        String queuedBuild = igorResponse.body.in().text
        return TaskResult
            .builder(ExecutionStatus.SUCCEEDED)
            .context([master: master, job: job, queuedBuild: queuedBuild, REPO_URL: repoUrl ?: 'default'])
            .build()
      }
    }
    catch (RetrofitError e) {
      // This igor call is idempotent so we can retry despite it being PUT/POST
      ExceptionHandler.Response exceptionResponse = retrofitExceptionHandler.handle("StartJenkinsJob", e)

      if (exceptionResponse.shouldRetry) {
        log.warn("Failure communicating with igor to start a jenkins job, will retry", e)
        return TaskResult.RUNNING
      }
      throw e
    }

    throw new SystemException("Failure starting script")
  }

  @Override
  long getBackoffPeriod() {
    return Duration.ofSeconds(30).toMillis()
  }

  @Override
  long getTimeout() {
    return Duration.ofMinutes(15).toMillis()
  }
}

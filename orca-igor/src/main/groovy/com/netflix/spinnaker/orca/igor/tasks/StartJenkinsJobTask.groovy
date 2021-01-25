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
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import retrofit.client.Response

import javax.annotation.Nonnull
import java.time.Duration

@Component
class StartJenkinsJobTask implements RetryableTask {
  private final Logger log = LoggerFactory.getLogger(getClass())

  @Autowired
  BuildService buildService

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  RetrofitExceptionHandler retrofitExceptionHandler

  @Nonnull
  @Override
  TaskResult execute(@Nonnull StageExecution stage) {
    String master = stage.context.master
    String job = stage.context.job

    try {
      Response igorResponse = buildService.build(master, job, stage.context.parameters, stage.startTime.toString())

      if (igorResponse.getStatus() == HttpStatus.ACCEPTED.value()) {
        log.info("build for job=$job on master=$master is pending, waiting for build to start")
        return TaskResult.RUNNING
      }

      if (igorResponse.getStatus() == HttpStatus.OK.value()) {
        String queuedBuild = igorResponse.body.in().text
        return TaskResult
            .builder(ExecutionStatus.SUCCEEDED)
            .context([queuedBuild: queuedBuild])
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

    throw new SystemException("Failure starting jenkins job")
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

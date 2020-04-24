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

package com.netflix.spinnaker.orca.clouddriver.tasks.job

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoRestService
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.pipeline.model.OverridableStageTimeout
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull
import javax.annotation.Nullable
import java.time.Duration
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit

@Component
public class WaitOnJobCompletion extends AbstractCloudProviderAwareTask implements OverridableTimeoutRetryableTask {
  private final Logger log = LoggerFactory.getLogger(getClass())

  final long backoffPeriod = TimeUnit.SECONDS.toMillis(10)
  final long timeout = TimeUnit.MINUTES.toMillis(120)

  @Autowired
  KatoRestService katoRestService

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  RetrySupport retrySupport

  @Autowired
  JobUtils jobUtils

  static final String REFRESH_TYPE = "Job"
  /**
   * Extra minute to pad the timing supplied by the job provider.
   * E.g. if TitusJobRunner says this task is limitted to 50minutes we will wait 50m + 5m(padding),
   * we should wait a bit longer to allow for any inaccuracies of the clock across the systems
   */
  static final Duration PROVIDER_PADDING = Duration.ofMinutes(5)

  @Override
  long getDynamicTimeout(@Nonnull StageExecution stage) {
    String jobTimeoutFromProvider = (stage.context.get("jobRuntimeLimit") as String)

    if (jobTimeoutFromProvider != null) {
      try {
        return Duration.parse(jobTimeoutFromProvider).plus(PROVIDER_PADDING).toMillis()
      } catch (DateTimeParseException e) {
        log.warn("Failed to parse job timeout specified by provider: '${jobTimeoutFromProvider}', using default", e)
      }
    }

    OverridableStageTimeout timeout = stage.mapTo(OverridableStageTimeout.class)
    if (timeout.timeoutMinutes.isPresent()) {
      return TimeUnit.MINUTES.toMillis(timeout.timeoutMinutes.getAsLong())
    }

    return getTimeout()
  }

  @Override @Nullable
  TaskResult onTimeout(@Nonnull StageExecution stage) {
    jobUtils.cancelWait(stage)

    return null
  }

  @Override
  void onCancel(@Nonnull StageExecution stage) {
    jobUtils.cancelWait(stage)
  }

  @Override
  TaskResult execute(StageExecution stage) {
    String account = getCredentials(stage)
    Map<String, List<String>> jobs = stage.context."deploy.jobs"

    def status = ExecutionStatus.RUNNING

    if (!jobs) {
      throw new IllegalStateException("No jobs in stage context.")
    }

    Map<String, Object> outputs = [:]
    if (jobs.size() > 1) {
      throw new IllegalStateException("At most one job location can be specified at a time.")
    }

    jobs.each { location, names ->
      if (!names) {
        return
      }

      if (names.size() > 1) {
        throw new IllegalStateException("At most one job can be run and monitored at a time.")
      }

      def name = names[0]
      def parsedName = Names.parseName(name)
      String appName = stage.context.moniker?.app ?: stage.context.application ?: parsedName.app

      InputStream jobStream
      retrySupport.retry({
        jobStream = katoRestService.collectJob(appName, account, location, name).body.in()
      }, 6, 5000, false) // retry for 30 seconds
      Map job = objectMapper.readValue(jobStream, new TypeReference<Map>() {})
      outputs.jobStatus = job

      switch ((String) job.jobState) {
        case "Succeeded":
          status = ExecutionStatus.SUCCEEDED
          outputs.completionDetails = job.completionDetails

          if (stage.context.propertyFile) {
            Map<String, Object> properties = [:]
            retrySupport.retry({
              properties = katoRestService.getFileContents(appName, account, location, name, stage.context.propertyFile)
              if (properties.size() == 0) {
                throw new IllegalStateException("Expected properties file ${stage.context.propertyFile} but it was either missing, empty or contained invalid syntax")
              }
            }, 6, 5000, false) // retry for 30 seconds
            outputs << properties
            outputs.propertyFileContents = properties
          }

          return

        case "Failed":
          status = ExecutionStatus.TERMINAL
          outputs.completionDetails = job.completionDetails
          return
      }
    }

    TaskResult.builder(status).context(outputs).outputs(outputs).build()
  }
}

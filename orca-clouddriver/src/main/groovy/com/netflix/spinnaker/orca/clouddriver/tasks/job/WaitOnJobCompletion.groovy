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
import com.netflix.spinnaker.kork.exceptions.ConfigurationException
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoRestService
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.clouddriver.exception.JobFailedException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.springframework.stereotype.Component
import retrofit.RetrofitError

import javax.annotation.Nonnull
import javax.annotation.Nullable
import java.time.Duration
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit

@Component
public class WaitOnJobCompletion implements CloudProviderAware, OverridableTimeoutRetryableTask {
  private final Logger log = LoggerFactory.getLogger(getClass())

  final long backoffPeriod = TimeUnit.SECONDS.toMillis(10)
  final long timeout = TimeUnit.MINUTES.toMillis(120)

  private final KatoRestService katoRestService
  private final ObjectMapper objectMapper
  private final RetrySupport retrySupport
  private final JobUtils jobUtils
  private final ExecutionRepository repository
  Front50Service front50Service

  static final String REFRESH_TYPE = "Job"
  /**
   * Extra time to pad the timing supplied by the job provider.
   * E.g. if TitusJobRunner says this task is limited to 50minutes we will wait 50m + 5m(padding),
   * we should wait a bit longer to allow for any inaccuracies of the clock across the systems
   */
  static final Duration PROVIDER_PADDING = Duration.ofMinutes(5)

  WaitOnJobCompletion(KatoRestService katoRestService,
                      ObjectMapper objectMapper,
                      RetrySupport retrySupport,
                      JobUtils jobUtils,
                      @Nullable Front50Service front50Service,
                      ExecutionRepository repository) {
    this.katoRestService = katoRestService
    this.objectMapper = objectMapper
    this.retrySupport = retrySupport
    this.jobUtils = jobUtils
    this.front50Service = front50Service
    this.repository = repository
  }

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
      String appName = stage.context.moniker?.app ?: stage.context.application ?: stage.execution.application

      if (appName == null && applicationExists(parsedName.app)) {
        appName = parsedName.app
      }

      InputStream jobStream
      retrySupport.retry({
        jobStream = katoRestService.collectJob(appName, account, location, name).body.in()
      }, 6, 5000, false) // retry for 30 seconds
      Map job = objectMapper.readValue(jobStream, new TypeReference<Map>() {})
      outputs.jobStatus = job

      outputs.completionDetails = job.completionDetails

      switch ((String) job.jobState) {
        case "Succeeded":
          status = ExecutionStatus.SUCCEEDED
          break

        case "Failed":
          status = ExecutionStatus.TERMINAL
          break
      }

      if ((status == ExecutionStatus.SUCCEEDED) || (status == ExecutionStatus.TERMINAL)) {
        if (stage.context.propertyFile) {
          Map<String, Object> properties = [:]
          try {
            retrySupport.retry({
              properties = katoRestService.getFileContents(appName, account, location, name, stage.context.propertyFile)
            }, 6, 5000, false) // retry for 30 seconds
          } catch (Exception e) {
            if (status == ExecutionStatus.SUCCEEDED) {
              throw new ConfigurationException("Property File: ${stage.context.propertyFile} contents could not be retrieved. Error: " + e)
            }
            log.warn("failed to get file contents for ${appName}, account: ${account}, namespace: ${location}, " +
                "manifest: ${name} from propertyFile: ${stage.context.propertyFile}. Error: ", e)
          }

          if (properties.size() == 0) {
            if (status == ExecutionStatus.SUCCEEDED) {
              throw new ConfigurationException("Expected properties file ${stage.context.propertyFile} but it was either missing, empty or contained invalid syntax")
            }
          } else if (properties.size() > 0) {
            outputs << properties
            outputs.propertyFileContents = properties
          }
        }
      }

      if (status == ExecutionStatus.TERMINAL) {
          // bubble up the error, but first save the job result otherwise UI could show
          // "Collecting Additional Details..." message if the jobStatus isn't present in the stage context
          updateStageContext(stage, outputs)
          processFailure(job)
      }
    }

    TaskResult.builder(status).context(outputs).outputs(outputs).build()
  }

  private Boolean applicationExists(String appName) {
    if (appName == null || front50Service == null) {
      return false
    }
    try {
      return front50Service.get(appName) != null
    } catch (RetrofitError e) {
      throw e
    }
  }

  /**
   * adds the outputs, provided as an input, to the stage parameter and updates the backend repository with the same
   *
   * @param stage - stage execution context that needs to be updated
   * @param outputs - the values that need to be added to the stage execution context
   */
  void updateStageContext(StageExecution stage, Map<String, Object> outputs) {
    if (stage.context) {
      stage.context.putAll(outputs)
      repository.storeStage(stage)
    }
  }

  /**
   * this function is called into action when the job has failed. It throws a {@link JobFailedException} with all the
   * error details that it computes from the input parameter
   *
   * @param job - contains the job status
   */
  static void processFailure(Map job) {
    String jobName = job.getOrDefault("name", "")
    String message = job.getOrDefault("message", "")
    String reason = job.getOrDefault("reason", "")
    String failureDetails = job.getOrDefault("failureDetails", "")

    String errorMessage = "Job: '${jobName}' failed."
    if (reason) {
      errorMessage += " Reason: ${reason}."
    }
    if (message) {
      errorMessage += " Details: ${message}."
    }
    if (failureDetails) {
      errorMessage += " Additional Details: ${failureDetails}"
    }
    throw new JobFailedException(errorMessage)
  }
}

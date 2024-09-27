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
import com.netflix.spinnaker.orca.clouddriver.config.TaskConfigurationProperties
import com.netflix.spinnaker.orca.clouddriver.config.TaskConfigurationProperties.WaitOnJobCompletionTaskConfig
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.clouddriver.exception.JobFailedException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.springframework.stereotype.Component

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
  private final WaitOnJobCompletionTaskConfig configProperties
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
                      TaskConfigurationProperties configProperties,
                      ExecutionRepository repository) {
    this.katoRestService = katoRestService
    this.objectMapper = objectMapper
    this.retrySupport = retrySupport
    this.jobUtils = jobUtils
    this.front50Service = front50Service
    this.configProperties = configProperties.getWaitOnJobCompletionTask()
    this.repository = repository

    log.info("output keys to filter: {}", this.configProperties.getExcludeKeysFromOutputs())
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
      },
          configProperties.getJobStatusRetry().maxAttempts,
          Duration.ofMillis(configProperties.getJobStatusRetry().getBackOffInMs()),
          configProperties.getJobStatusRetry().exponentialBackoffEnabled
      )
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
          Map<String, Object> properties = getPropertyFileContents(
              job,
              appName,
              status,
              account,
              location,
              name,
              stage.context.propertyFile as String)

          if (properties.size() > 0) {
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

    // exclude certain configured keys from being stored in the stage outputs
    Map<String, Object> filteredOutputs =  filterContextOutputs(outputs, configProperties.getExcludeKeysFromOutputs())
    log.info("context outputs will only contain: ${filteredOutputs.keySet()} keys")

    TaskResult.builder(status)
        .context(outputs)
        .outputs(filteredOutputs)
        .build()
  }

  private Boolean applicationExists(String appName) {
    if (appName == null || front50Service == null) {
      return false
    }
    return front50Service.get(appName) != null
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

  /**
   * <p>this method attempts to get property file from clouddriver and then parses its contents. Depending
   * on the job itself, it could be handled by any job provider in clouddriver. This method should only be
   * called for jobs with ExecutionStatus as either SUCCEEDED or TERMINAL.
   *
   * <p>If property file contents could not be retrieved from clouddriver, then the error handling depends
   * on the job's ExecutionStatus. If it is SUCCEEDED, then an exception is thrown. Otherwise, no exception
   * is thrown since we don't want to mask the real reason behind the job failure.
   *
   * <p>If ExecutionStatus == SUCCEEDED, and especially for kubernetes run jobs, it can so happen that a user
   * has configured the job spec to run 1 pod, have completions and parallelism == 1, and
   * restartPolicy == Never. Despite that, kubernetes may end up running another pod as stated here:
   * https://kubernetes.io/docs/concepts/workloads/controllers/job/#handling-pod-and-container-failures
   * In such a scenario, it may so happen that two pods are created for that job. The first pod may still be
   * around, such as in a PodInitializing state and the second pod could complete before the first one is
   * terminated. This leads to the getFileContents() call failing, since under the covers, kubernetes job
   * provider runs kubectl logs job/<jobName> command, which picks one out of the two pods to obtain the
   * logs as seen here:
   *
   * <p>kubectl -n test logs job/test-run-job-5j2vl -c parser
   * Found 2 pods, using pod/test-run-job-5j2vl-fj8hd
   * Error from server (BadRequest): container "parser" in pod "test-run-job-5j2vl-fj8hd" is PodInitializing
   *
   * <p>That means, even if kubernetes and clouddriver marked the job as successful, since number of
   * succeeded pods >= number of completions, the kubectl command shown above could still end using
   * the failed pod for obtaining the logs.
   *
   * <p>To handle this case, if we get an error while making the getFileContents() call or if we don't receive
   *  any properties, then for kubernetes jobs, we figure out if the job status has any pod with phase
   *  SUCCEEDED. If we find such a pod, then we directly get the logs from this succeeded pod. Otherwise,
   *  we throw an exception as before.
   *
   *  <p> we aren't handling the above case for ExecutionStatus == TERMINAL, because at that point, we wouldn't
   *  know which pod to query for properties file contents. It could so happen that all the pods in such a job
   *  have failed, then we would have to loop over each pod and see what it generated. Then if say, two pods
   *  generated different property values for the same key, which one do we choose? Bearing this complexity
   *  in mind, and knowing that for succeeded jobs, this solution actually helps prevent a pipeline failure,
   *  we are limiting this logic to succeeded jobs only for now.
   *
   * @param job - job status returned by clouddriver
   * @param appName - application name where the job is run
   * @param status - Execution status of the job. Should either be SUCCEEDED or TERMINAL
   * @param account - account under which this job is run
   * @param location - where this job is run
   * @param name - name of the job
   * @param propertyFile - file name to query from the job
   * @return map of property file contents
   */
  private Map<String, Object> getPropertyFileContents(
      Map job,
      String appName,
      ExecutionStatus status,
      String account,
      String location,
      String name,
      String propertyFile
  ) {
    Map<String, Object> properties = [:]
    try {
      retrySupport.retry({
        properties = katoRestService.getFileContents(appName, account, location, name, propertyFile)
      },
          configProperties.getFileContentRetry().maxAttempts,
          Duration.ofMillis(configProperties.getFileContentRetry().getBackOffInMs()),
          configProperties.getFileContentRetry().exponentialBackoffEnabled
      )
    } catch (Exception e) {
      log.warn("Error occurred while retrieving property file contents from job: ${name}" +
          " in application: ${appName}, in account: ${account}, location: ${location}," +
          " using propertyFile: ${propertyFile}. Error: ", e
      )

      // For succeeded kubernetes jobs, let's try one more time to get property file contents.
      if (status == ExecutionStatus.SUCCEEDED) {
        properties = getPropertyFileContentsForSucceededKubernetesJob(
            job,
            appName,
            account,
            location,
            propertyFile
        )
        if (properties.size() == 0) {
          // since we didn't get any properties, we fail with this exception
          throw new ConfigurationException("Expected properties file: ${propertyFile} in " +
              "job: ${name}, application: ${appName}, location: ${location}, account: ${account} " +
              "but it was either missing, empty or contained invalid syntax. Error: ${e}")
        }
      }
    }

    if (properties.size() == 0) {
      log.warn("Could not parse propertyFile: ${propertyFile} in job: ${name}" +
          " in application: ${appName}, in account: ${account}, location: ${location}." +
          " It is either missing, empty or contains invalid syntax"
      )

      // For succeeded kubernetes jobs, let's try one more time to get property file contents.
      if (status == ExecutionStatus.SUCCEEDED) {
        // let's try one more time to get properties from a kubernetes pod
        properties = getPropertyFileContentsForSucceededKubernetesJob(
            job,
            appName,
            account,
            location,
            propertyFile
        )
        if (properties.size() == 0) {
          // since we didn't get any properties, we fail with this exception
          throw new ConfigurationException("Expected properties file: ${propertyFile} in " +
              "job: ${name}, application: ${appName}, location: ${location}, account: ${account} " +
              "but it was either missing, empty or contained invalid syntax")
        }
      }
    }
    return properties
  }

  /**
   * This method is supposed to be called from getPropertyFileContents(). This is only applicable for
   * Kubernetes jobs. It finds a successful pod in the job and directly queries it for property file
   * contents.
   *
   * <p>It is meant to handle the following case:
   *
   * <p> if ExecutionStatus == SUCCEEDED, and especially for kubernetes run jobs, it can so happen that a
   * user has configured the job spec to run 1 pod, have completions and parallelism == 1, and
   * restartPolicy == Never. Despite that, kubernetes may end up running another pod as stated here:
   * https://kubernetes.io/docs/concepts/workloads/controllers/job/#handling-pod-and-container-failures
   * In such a scenario, it may so happen that two pods are created for that job. The first pod may still be
   * around, such as in a PodInitializing state and the second pod could complete before the first one is
   * terminated. This leads to the getFileContents() call failing, since under the covers, kubernetes job
   * provider runs kubectl logs job/<jobName> command, which picks one out of the two pods to obtain the
   * logs as seen here:
   *
   * <p>kubectl -n test logs job/test-run-job-5j2vl -c parser
   * Found 2 pods, using pod/test-run-job-5j2vl-fj8hd
   * Error from server (BadRequest): container "parser" in pod "test-run-job-5j2vl-fj8hd" is PodInitializing
   *
   * <p>That means, even if kubernetes and clouddriver marked the job as successful, since number of
   * succeeded pods >= number of completions, the kubectl command shown above could still end using
   * the failed pod for obtaining the logs.
   *
   * <p>To handle this case, if we get an error while making the getFileContents() call or if we don't receive
   * any properties, then for kubernetes jobs, we figure out if the job status has any pod with phase
   * SUCCEEDED. If we find such a pod, then we directly get the logs from this succeeded pod. Otherwise,
   * we throw an exception as before.
   *
   * <p>To keep it simple, and not worry about how to deal with property file
   * contents obtained from various successful pods in a job, if that may happen, we simply query the first
   * successful pod in that job.
   *
   * @param job - job status returned by clouddriver
   * @param appName - application in which this job is run
   * @param account - account under which this job is run
   * @param namespace - where this job is run
   * @param propertyFile - file name to query from the job
   * @return map of property file contents
   */
  private Map<String, Object> getPropertyFileContentsForSucceededKubernetesJob(
      Map job,
      String appName,
      String account,
      String namespace,
      String propertyFile
  ) {
    Map<String, Object> properties = [:]
    if (job.get("provider", "unknown") == "kubernetes") {
      Optional<Map> succeededPod = job.get("pods", [])
          .stream()
          .filter({ Map pod -> pod.get("status", [:]).get("phase", "Running") == "Succeeded"
          })
          .findFirst()

      if (succeededPod.isPresent()) {
        String podName = (succeededPod.get() as Map).get("name")
        retrySupport.retry({
          properties = katoRestService.getFileContentsFromKubernetesPod(
              appName,
              account,
              namespace,
              podName,
              propertyFile
          )
        },
            configProperties.getFileContentRetry().maxAttempts,
            Duration.ofMillis(configProperties.getFileContentRetry().getBackOffInMs()),
            configProperties.getFileContentRetry().exponentialBackoffEnabled
        )
      }
    }
    return properties
  }
}

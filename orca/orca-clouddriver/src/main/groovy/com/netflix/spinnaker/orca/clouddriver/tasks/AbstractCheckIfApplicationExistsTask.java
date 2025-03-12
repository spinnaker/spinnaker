/*
 * Copyright 2021 Salesforce.com, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.clouddriver.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.config.TaskConfigurationProperties;
import com.netflix.spinnaker.orca.clouddriver.config.tasks.CheckIfApplicationExistsTaskConfig;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.model.Application;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

/**
 * Abstract class that is meant to test the presence of a spinnaker application. It will first check
 * if the application exists in front50. Since front50 can be disabled, it falls back to checking
 * for the application in clouddriver.
 *
 * <p>If the application doesn't exist, the task fails.
 *
 * <p>The motivation for adding such a task is to prevent creation of any ad-hoc applications in
 * amazon and kubernetes deployment pipeline stages.
 *
 * <p>Depending on what is the application value set in the moniker and/or the cluster keys in such
 * stages, any application that isn't known to front50 can be created by clouddriver on demand. This
 * can have an adverse effect on the security of such applications since these applications aren't
 * created via a controlled process.
 */
@Slf4j
@Component
public abstract class AbstractCheckIfApplicationExistsTask implements Task {
  @Getter private static final String taskName = "checkIfApplicationExists";
  @Nullable private final Front50Service front50Service;
  private final OortService oortService;
  private final ObjectMapper objectMapper;
  private final RetrySupport retrySupport;
  private final CheckIfApplicationExistsTaskConfig config;

  public AbstractCheckIfApplicationExistsTask(
      @Nullable Front50Service front50Service,
      OortService oortService,
      ObjectMapper objectMapper,
      RetrySupport retrySupport,
      TaskConfigurationProperties configurationProperties) {
    this.front50Service = front50Service;
    this.oortService = oortService;
    this.objectMapper = objectMapper;
    this.retrySupport = retrySupport;
    this.config = configurationProperties.getCheckIfApplicationExistsTask();
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    Map<String, Object> outputs = new HashMap<>();
    // get the application name
    String applicationName = getApplicationName(stage);

    // first check front50 to see if this application exists in it
    log.info("Querying front50 to get information about the application: {}", applicationName);
    Application fetchedApplication = getApplicationFromFront50(applicationName);
    String errorMessage = "did not find application: " + applicationName + " in front50";
    if (fetchedApplication == null) {
      if (this.config.isCheckClouddriver()) {
        log.info("querying clouddriver for application: {}", applicationName);
        fetchedApplication = getApplicationFromClouddriver(applicationName);
        if (fetchedApplication == null) {
          errorMessage += " and in clouddriver";
        }
      }
    }
    if (fetchedApplication == null) {
      if (this.config.isAuditModeEnabled()) {
        String pipelineName = "unknown";
        if (stage.getParent() != null) {
          pipelineName = stage.getParent().getName();
        }
        log.warn(
            "Warning: stage: {}, pipeline: {}, message: {}. "
                + "This will be a terminal failure in the near future.",
            errorMessage,
            stage.getName(),
            pipelineName);
        outputs.put("checkIfApplicationExistsWarning", errorMessage);
      } else {
        log.error(errorMessage);
        throw new NotFoundException(errorMessage);
      }
    }
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).outputs(outputs).build();
  }

  /**
   * attempts to query front50 for the application name that is provided to it as an input.
   *
   * <p>If front50 is disabled, then it returns null. It also returns null if the application
   * doesn't exist or if any exception arises on querying this data from front50. The expectation is
   * that the caller method should handle the return value in a suitable manner
   *
   * @param applicationName the application to search for in front50
   * @return the application, if it exists in front50, or null otherwise
   */
  protected Application getApplicationFromFront50(String applicationName) {
    // this can happen if front50 is disabled
    if (front50Service == null) {
      log.info("Front50 is disabled, cannot query application: {}", applicationName);
      return null;
    }
    return retrySupport.retry(
        () -> {
          try {
            Application fetchedApplication = front50Service.get(applicationName);
            if (fetchedApplication == null) {
              log.warn("Application: " + applicationName + " does not exist in front50");
            } else {
              log.info("Application: " + applicationName + " found in front50");
            }
            return fetchedApplication;
          } catch (Exception e) {
            log.error(
                "Application: " + applicationName + " could not be retrieved from front50. Error: ",
                e);
            return null;
          }
        },
        this.config.getFront50Retries().getMaxAttempts(),
        Duration.ofMillis(this.config.getFront50Retries().getBackOffInMs()),
        this.config.getFront50Retries().isExponentialBackoffEnabled());
  }

  /**
   * attempts to query clouddriver for the application name that is provided to it as an input.
   *
   * <p>It returns null if the application doesn't exist in clouddriver or if any exception arises
   * on querying this data from clouddriver. The expectation is that the caller method should handle
   * the return value in a suitable manner
   *
   * @param applicationName the application to search for in clouddriver
   * @return the application, if it exists in clouddriver, or null otherwise
   */
  protected Application getApplicationFromClouddriver(String applicationName) {
    return retrySupport.retry(
        () -> {
          try {
            Response response = oortService.getApplication(applicationName);
            Application fetchedApplication =
                objectMapper.readValue(response.getBody().in(), Application.class);
            if (fetchedApplication == null) {
              log.warn("Application: " + applicationName + " does not exist in clouddriver");
            } else {
              log.info("Application: " + applicationName + " found in clouddriver");
            }
            return fetchedApplication;
          } catch (Exception e) {
            log.error(
                "Application: "
                    + applicationName
                    + " could not be retrieved from clouddriver. Error: ",
                e);
            return null;
          }
        },
        this.config.getClouddriverRetries().getMaxAttempts(),
        Duration.ofMillis(this.config.getClouddriverRetries().getBackOffInMs()),
        this.config.getClouddriverRetries().isExponentialBackoffEnabled());
  }

  public abstract String getApplicationName(@Nonnull StageExecution stageExecution);
}

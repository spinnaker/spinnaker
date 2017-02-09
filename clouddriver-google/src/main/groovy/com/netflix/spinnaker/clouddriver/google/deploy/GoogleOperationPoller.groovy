/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy

import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.Operation
import com.google.common.annotations.VisibleForTesting
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationException
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationTimedOutException
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.TimeUnit;


class GoogleOperationPoller {
  static final String METRIC_NAME = "google.operationWaits"  // Timer
  static final String STARTED_METRIC_NAME = "google.operationWaitRequests"  // Counter

  // This only exists to facilitate testing.
  static class ThreadSleeper {
    void sleep(long seconds) {
      Thread.currentThread().sleep(seconds * 1000)
    }
  }

  @Autowired
  Registry registry

  @Autowired
  GoogleConfigurationProperties googleConfigurationProperties

  @Autowired
  SafeRetry safeRetry

  @VisibleForTesting
  ThreadSleeper threadSleeper = new ThreadSleeper()

  // The methods below are used to wait on the operation specified in |operationName|. This is used in practice to
  // turn the asynchronous GCE client operations into synchronous calls. Will poll the state of the operation until
  // either state is DONE or |timeoutSeconds| is reached.
  Operation waitForZonalOperation(Compute compute, String projectName, String zone, String operationName,
                                  Long timeoutSeconds, Task task, String resourceString, String basePhase) {
    def tags = [basePhase: basePhase, scope: "zonal", zone:zone]
    return handleFinishedAsyncOperation(
        waitForOperation({compute.zoneOperations().get(projectName, zone, operationName).execute()},
                         tags, basePhase, getTimeout(timeoutSeconds)),
        task, resourceString, basePhase)
  }

  Operation waitForRegionalOperation(Compute compute, String projectName, String region, String operationName,
                                     Long timeoutSeconds, Task task, String resourceString, String basePhase) {
    def tags = [basePhase: basePhase, scope: "regional", region: region]
    return handleFinishedAsyncOperation(
        waitForOperation({compute.regionOperations().get(projectName, region, operationName).execute()},
                         tags, basePhase, getTimeout(timeoutSeconds)),
        task, resourceString, basePhase)
  }

  Operation waitForGlobalOperation(Compute compute, String projectName, String operationName,
                                   Long timeoutSeconds, Task task, String resourceString, String basePhase) {
    def tags = [basePhase: basePhase, scope: "global"]
    return handleFinishedAsyncOperation(
        waitForOperation({compute.globalOperations().get(projectName, operationName).execute()},
                         tags, basePhase, getTimeout(timeoutSeconds)),
        task, resourceString, basePhase)
  }

  private long getTimeout(Long timeoutSeconds) {
    // Note that we cannot use an Elvis operator here because we might have a timeoutSeconds value of
    // zero. In that case, we still want to pass that value. So we use null comparison here instead.
    Math.max(timeoutSeconds != null ? timeoutSeconds : googleConfigurationProperties.asyncOperationTimeoutSecondsDefault, 0)
  }

  private static handleFinishedAsyncOperation(Operation operation, Task task, String resourceString, String basePhase) {
    if (!operation) {
      String errorMsg = "Operation on $resourceString timed out."
      task.updateStatus basePhase, errorMsg
      throw new GoogleOperationTimedOutException(errorMsg)
    }

    if (operation.getError()) {
      def error = operation?.getError()?.getErrors()?.get(0)
      String errorMsg = "Failed to complete operation on $resourceString with error: $error"
      task.updateStatus basePhase, errorMsg
      throw new GoogleOperationException(errorMsg)
    }

    task.updateStatus basePhase, "Done operating on $resourceString."
  }

  /*
    This method does not correct for potential drift at each interval (we trade some precision for readability).
    The timeoutSeconds parameter is really treated as a lower-bound. We will poll until the operation reaches a DONE
    state or until <em>at least</em> that many seconds have passed.
   */
  private Operation waitForOperation(Closure<Operation> getOperation, Map timerTags, String basePhase, long timeoutSeconds) {
    Clock clock = registry.clock()
    long startNs = clock.monotonicTime();
    int totalTimePollingSeconds = 0
    boolean timeoutExceeded = false

    // Fibonacci backoff in seconds, up to googleConfigurationProperties.asyncOperationMaxPollingIntervalSeconds interval.
    int pollInterval = 1
    int pollIncrement = 0

    registry.counter(registry.createId(STARTED_METRIC_NAME, timerTags)).increment()
    Id metricId = registry.createId(METRIC_NAME, timerTags)
    while (!timeoutExceeded) {
      threadSleeper.sleep(pollInterval)

      totalTimePollingSeconds += pollInterval

      Operation operation = safeRetry.doRetry(getOperation, "operation", null, [], [], [action: "wait", phase: basePhase], registry) as Operation

      if (operation.getStatus() == "DONE") {
        registry.timer(metricId.withTag("status", "DONE")).record(clock.monotonicTime() - startNs, TimeUnit.NANOSECONDS);
        return operation
      }

      if (totalTimePollingSeconds > timeoutSeconds) {
        registry.timer(metricId.withTag("status", "TIMEOUT")).record(clock.monotonicTime() - startNs, TimeUnit.NANOSECONDS);
        timeoutExceeded = true
      } else {
        // Update polling interval.
        int oldIncrement = pollIncrement
        pollIncrement = pollInterval
        pollInterval += oldIncrement
        pollInterval = Math.min(pollInterval, googleConfigurationProperties.asyncOperationMaxPollingIntervalSeconds)
      }
    }

    return null
  }
}

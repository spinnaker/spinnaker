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

package com.netflix.spinnaker.kato.gce.deploy

import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.Operation
import com.google.api.services.replicapool.Replicapool
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.gce.deploy.config.GoogleConfig
import com.netflix.spinnaker.kato.gce.deploy.exception.GoogleOperationException
import com.netflix.spinnaker.kato.gce.deploy.exception.GoogleOperationTimedOutException
import com.netflix.spinnaker.kato.gce.deploy.exception.GoogleResourceNotFoundException
import org.springframework.beans.factory.annotation.Autowired

class GoogleOperationPoller {

  // This only exists to facilitate testing.
  static class ThreadSleeper {
    void sleep(long seconds) {
      Thread.currentThread().sleep(seconds * 1000)
    }
  }

  @Autowired
  GoogleConfig.GoogleConfigurationProperties googleConfigurationProperties

  private ThreadSleeper threadSleeper = new ThreadSleeper()

  // The methods below are used to wait on the operation specified in |operationName|. This is used in practice to
  // turn the asynchronous GCE client operations into synchronous calls. Will poll the state of the operation until
  // either state is DONE or |timeoutSeconds| is reached.
  Operation waitForRegionalOperation(Compute compute, String projectName, String region, String operationName,
                                     Long timeoutSeconds, Task task, String resourceString, String basePhase) {
    return handleFinishedAsyncOperation(
        waitForOperation({compute.regionOperations().get(projectName, region, operationName).execute()},
                         getTimeout(timeoutSeconds)), task, resourceString, basePhase)
  }

  Operation waitForGlobalOperation(Compute compute, String projectName, String operationName,
                                   Long timeoutSeconds, Task task, String resourceString, String basePhase) {
    return handleFinishedAsyncOperation(
        waitForOperation({compute.globalOperations().get(projectName, operationName).execute()},
                         getTimeout(timeoutSeconds)), task, resourceString, basePhase)
  }

  // This method is like the two above except that it operates using a Replicapool object (rather than Compute), which
  // is the base class for operations relating to managed instance groups.
  void waitForZoneOperation(Replicapool replicapool, String projectName, String zone, String operationName,
                            Long timeoutSeconds, Task task, String resourceString, String basePhase) {
    handleFinishedAsyncOperation(
        waitForOperation({replicapool.zoneOperations().get(projectName, zone, operationName).execute()},
                         getTimeout(timeoutSeconds)), task, resourceString, basePhase)
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
  private Operation waitForOperation(Closure getOperation, long timeoutSeconds) {
    int totalTimePollingSeconds = 0
    boolean timeoutExceeded = false

    // Fibonacci backoff in seconds, up to googleConfigurationProperties.asyncOperationMaxPollingIntervalSeconds interval.
    int pollInterval = 1
    int pollIncrement = 0

    while (!timeoutExceeded) {
      threadSleeper.sleep(pollInterval)

      totalTimePollingSeconds += pollInterval

      Operation operation = getOperation()

      if (operation.getStatus() == "DONE") {
        return operation
      }

      if (totalTimePollingSeconds > timeoutSeconds) {
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

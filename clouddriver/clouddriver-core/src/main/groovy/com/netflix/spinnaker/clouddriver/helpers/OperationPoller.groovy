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

package com.netflix.spinnaker.clouddriver.helpers

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.exceptions.OperationTimedOutException
import groovy.util.logging.Slf4j

import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

/**
 * A poller with an upper time limit combined with a Fibonacci-based backoff.
 * Let's you wrap the operation in a Groovy closure and the "if complete" operation in another.
 * The "if complete" closure is fed the results of the first.
 */
@Slf4j
class OperationPoller {

  final int asyncOperationTimeoutSecondsDefault
  final int asyncOperationMaxPollingIntervalSeconds

  ThreadSleeper threadSleeper = new ThreadSleeper()

  OperationPoller(int asyncOperationTimeoutSecondsDefault, int asyncOperationMaxPollingIntervalSeconds) {
    this.asyncOperationTimeoutSecondsDefault = asyncOperationTimeoutSecondsDefault
    this.asyncOperationMaxPollingIntervalSeconds = asyncOperationMaxPollingIntervalSeconds
  }

  OperationPoller(int asyncOperationTimeoutSecondsDefault, int asyncOperationMaxPollingIntervalSeconds, ThreadSleeper threadSleeper) {
    this(asyncOperationTimeoutSecondsDefault, asyncOperationMaxPollingIntervalSeconds)
    this.threadSleeper = threadSleeper
  }

  public <T> T waitForOperation(Supplier<T> operation, Function<T, Boolean> ifDone,
                                Long timeoutSeconds, Task task, String resourceString, String basePhase) {
    (T) waitForOperation({ operation.get() }, { T t -> ifDone.apply(t) },
      timeoutSeconds, task, resourceString, basePhase)
  }

  /**
   * Wrap an operational closure with a back off algorithm to check until completed.
   *
   * @param operation - a closure to perform an operation and return a testable value
   * @param ifDone - a closure that receives the operation's results, and returns true/false if completed
   * @param timeoutSeconds
   * @param task
   * @param resourceString
   * @param basePhase
   * @return results of operation
   */
  Object waitForOperation(Closure operation, Closure ifDone,
                          Long timeoutSeconds, Task task, String resourceString, String basePhase) {
    return handleFinishedAsyncOperation(
        pollOperation(operation, ifDone, getTimeout(timeoutSeconds)), task, resourceString, basePhase)
  }

  static Object retryWithBackoff(Function operation, long backOff, int maxRetries) {
    int retries = 0
    Object result
    boolean succeeded = false
    while (!succeeded) {
      try {
        result = operation.apply(null);
        succeeded = true
      } catch (Exception e) {
        if (retries >= maxRetries) {
          throw e
        }
        retries++
        long timeout = Math.pow(2, retries) * backOff
        Thread.sleep(timeout)
      }
    }
    return result
  }

  private long getTimeout(Long timeoutSeconds) {
    // Note that we cannot use an Elvis operator here because we might have a timeoutSeconds value of
    // zero. In that case, we still want to pass that value. So we use null comparison here instead.
    Math.max(timeoutSeconds != null ? timeoutSeconds : asyncOperationTimeoutSecondsDefault, 0)
  }

  private static handleFinishedAsyncOperation(Object operation, Task task, String resourceString, String basePhase) {
    if (!operation) {
      String errorMsg = "Operation on $resourceString timed out."
      if (task != null) {
        task.updateStatus basePhase, errorMsg
      } else {
        log.info errorMsg
      }
      throw new OperationTimedOutException(errorMsg)
    }

    if (task != null) {
      task.updateStatus basePhase, "Done operating on $resourceString."
    } else {
      log.info "Done operating on $resourceString"
    }

    operation
  }

  private Object pollOperation(Closure operation, Closure ifDone, long timeoutSeconds) {
    int totalTimePollingSeconds = 0
    boolean timeoutExceeded = false

    // Fibonacci backoff in seconds, up to asyncOperationMaxPollingIntervalSeconds interval.
    int pollInterval = 1
    int pollIncrement = 0

    while (!timeoutExceeded) {
      threadSleeper.sleep(pollInterval)

      totalTimePollingSeconds += pollInterval

      Object results = operation()

      if (ifDone(results)) {
        return results
      }

      if (totalTimePollingSeconds > timeoutSeconds) {
        timeoutExceeded = true
      } else {
        // Update polling interval.
        int oldIncrement = pollIncrement
        pollIncrement = pollInterval
        pollInterval += oldIncrement
        pollInterval = Math.min(pollInterval, asyncOperationMaxPollingIntervalSeconds)
      }
    }

    return null
  }

  // This only exists to facilitate testing.
  static class ThreadSleeper {
    void sleep(long seconds) {
      Thread.currentThread().sleep(seconds * 1000)
    }
  }

}

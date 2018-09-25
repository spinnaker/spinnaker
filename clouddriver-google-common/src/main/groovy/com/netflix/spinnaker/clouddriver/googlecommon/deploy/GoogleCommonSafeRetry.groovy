/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.googlecommon.deploy

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.netflix.spectator.api.Registry
import groovy.util.logging.Slf4j

import java.util.concurrent.TimeUnit

@Slf4j
abstract class GoogleCommonSafeRetry {
  public static class SafeRetryState {
    Object finalResult
    Exception lastSeenException
    int tries = 0
    boolean success = false
  }

  /**
   * Retry an operation if it fails. Treat any error codes in successfulErrorCodes as success.
   *
   * @param operation - The operation.
   * @param action - String describing the operation.
   * @param resource - Resource we are operating on.
   * @param phase
   * @param retryCodes - GoogleJsonResponseException codes we retry on.
   * @param successfulErrorCodes - GoogleJsonException codes we treat as success.
   *
   * @return Object returned from the operation.
   */
  public Object doRetry(Closure operation,
                        String resource,
                        List<Integer> retryCodes,
                        List<Integer> successfulErrorCodes,
                        Long maxWaitInterval,
                        Long retryIntervalBase,
                        Long jitterMultiplier,
                        Long maxRetries,
                        Map tags,
                        Registry registry) {
    long startTime = registry.clock().monotonicTime()
    SafeRetryState state = performOperation(
        operation, tags.action, resource, tags.phase,
        retryCodes, successfulErrorCodes,
        maxWaitInterval, retryIntervalBase, jitterMultiplier, maxRetries)

    def all_tags = [:]
    all_tags.putAll(tags)
    all_tags.success = state.success ? "true" : "false"
    registry.timer(registry.createId("google.safeRetry", all_tags))
        .record(registry.clock().monotonicTime() - startTime, TimeUnit.NANOSECONDS)

    return determineFinalResult(state, tags.action, resource)
  }


  protected SafeRetryState performOperation(Closure operation,
                                            String action,
                                            String resource,
                                            String phase,
                                            List<Integer> retryCodes,
                                            List<Integer> successfulErrorCodes,
                                            Long maxWaitInterval,
                                            Long retryIntervalBase,
                                            Long jitterMultiplier,
                                            Long maxRetries) {
    def maxAttempts = Math.max(1, maxRetries)
    SafeRetryState state = new SafeRetryState()

    while (state.tries < maxAttempts) {
      def tries = ++state.tries
      try {
        if (tries != 1) {
          // Sleep with exponential backoff based on the number of retries. Add retry jitter with Math.random() to
          // prevent clients syncing up and bursting at regular intervals. Don't wait longer than a minute.
          Long thisIntervalWait = TimeUnit.SECONDS.toMillis(Math.pow(retryIntervalBase, tries - 1) as Integer)
          def sleepMillis = Math.min(thisIntervalWait, maxWaitInterval) + Math.round(Math.random() * jitterMultiplier)
          log.warn "Waiting $sleepMillis ms to retry $action."
          sleep(sleepMillis)
          log.warn "Retrying $action $resource attempt #$tries..."
        }
        state.finalResult = operation()
        state.success = true
        return state
      } catch (GoogleJsonResponseException jsonException) {
        if (jsonException.statusCode in successfulErrorCodes) {
           state.success = true
           return state
        }

        if (jsonException.statusCode in retryCodes) {
          log.warn "$action of $resource attempt #$tries encountered retryable statusCode=${jsonException.statusCode} with error message: ${jsonException.message}."
        } else {
          throw jsonException
        }
        state.lastSeenException = jsonException
      } catch (SocketTimeoutException toEx) {
        log.warn "Retryable $action attempt #$tries timed out."
        state.lastSeenException = toEx
      }
    }
    log.warn "$action of $resource giving up after $maxAttempts tries with ${state.lastSeenException.message}"

    return state
  }

  /**
   * @return true if the status code is contained in the retryCodes list, or is a 5xx. The happens
   * across the platform randomly, and our only real option is to retry.
   */
  static boolean isRetryable(Exception e, List<Integer> retryCodes) {
    if (e instanceof GoogleJsonResponseException) {
      GoogleJsonResponseException g = (GoogleJsonResponseException) e
      return g.statusCode in retryCodes || ((int)(g.statusCode / 100)) == 5
    }
    return true
  }

  protected Object determineFinalResult(SafeRetryState state,
                                        String action,
                                        String resource) {
    if (state.success) {
      return state.finalResult
    }
    Exception lastSeenException = state.lastSeenException
    int tries = state.tries
    if (lastSeenException instanceof GoogleJsonResponseException) {
        def lastSeenError = lastSeenException?.getDetails()?.getErrors()[0] ?: null
        if (lastSeenError) {
          if (lastSeenError.getReason() == 'resourceInUseByAnotherResource') {
            // Don't fail the operation if the resource is in use. The main use case for this is resiliency in delete operations -
            // we don't want to fail the operation if something is in use by another resource.
            log.warn("Failed to $action $resource after #$tries."
              + " Last seen exception has status code ${lastSeenException.getStatusCode()} with error message ${lastSeenError.getMessage()}"
              + " and reason ${lastSeenError.getReason()}.")
            return null
          } else {
            throw providerOperationException("Failed to $action $resource after #$tries."
              + " Last seen exception has status code ${lastSeenException.getStatusCode()} with error message ${lastSeenError.getMessage()}"
              + " and reason ${lastSeenError.getReason()}.")
          }
        } else {
          throw providerOperationException("Failed to $action $resource after #$tries."
            + " Last seen exception has status code ${lastSeenException.getStatusCode()} with message ${lastSeenException.getMessage()}.")
        }
      } else if (lastSeenException instanceof SocketTimeoutException) {
        throw providerOperationException("Failed to $action $resource after #$tries."
          + " Last operation timed out.")
    } else {
      throw new IllegalStateException("Caught exception is neither a JsonResponseException nor a OperationTimedOutException."
        + " Caught exception: ${lastSeenException}")
    }
  }

  abstract Exception providerOperationException(String message)
}

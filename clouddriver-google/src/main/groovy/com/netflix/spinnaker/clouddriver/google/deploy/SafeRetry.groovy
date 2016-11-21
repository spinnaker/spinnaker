/*
 * Copyright 2016 Google, Inc.
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

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationException
import groovy.util.logging.Slf4j

import java.util.concurrent.TimeUnit

@Slf4j
class SafeRetry<T> {
  private static Long MAX_WAIT_INTERVAL = TimeUnit.SECONDS.toMillis(60)

  @VisibleForTesting
  static Long RETRY_INTERVAL_SEC = 2

  /**
   * Retry a GCP operation if it fails. Treat any error codes in successfulErrorCodes as success.
   *
   * @param operation - The GCP operation.
   * @param action - String describing the GCP operation.
   * @param resource - Resource we are operating on.
   * @param task - Spinnaker task. Can be null.
   * @param phase
   * @param retryCodes - GoogleJsonResponseException codes we retry on.
   * @param successfulErrorCodes - GoogleJsonException codes we treat as success.
   *
   * @return Object of type T returned from the operation.
   */
  public T doRetry(Closure<T> operation,
                   String action,
                   String resource,
                   Task task,
                   String phase,
                   List<Integer> retryCodes,
                   List<Integer> successfulErrorCodes) {
    try {
      task?.updateStatus phase, "Attempting $action of $resource..."
      return operation()
    } catch (GoogleJsonResponseException | SocketTimeoutException | SocketException _) {
      log.warn "Initial $action of $resource failed, retrying..."

      int tries = 1
      Exception lastSeenException = null
      while (tries < 10) {
        try {
          tries++
          // Sleep with exponential backoff based on the number of retries. Add retry jitter with Math.random() to
          // prevent clients syncing up and bursting at regular intervals. Don't wait longer than a minute.
          Long thisIntervalWait = TimeUnit.SECONDS.toMillis(Math.pow(RETRY_INTERVAL_SEC, tries) as Integer)
          sleep(Math.min(thisIntervalWait, MAX_WAIT_INTERVAL) + Math.round(Math.random() * 1000))
          log.warn "$action $resource attempt #$tries..."
          return operation()
        } catch (GoogleJsonResponseException jsonException) {
          if (jsonException.statusCode in successfulErrorCodes) {
            log.warn "Retry $action of $resource encountered ${jsonException.statusCode}, treating as success..."
            return null
          } else if (jsonException.statusCode in retryCodes) {
            log.warn "Retry $action of $resource encountered ${jsonException.statusCode} with error message: ${jsonException.message}. Trying again..."
          } else {
            throw jsonException
          }
          lastSeenException = jsonException
        } catch (SocketTimeoutException toEx) {
          log.warn "Retry $action timed out again, trying again..."
          lastSeenException = toEx
        }
      }

      if (lastSeenException && lastSeenException instanceof GoogleJsonResponseException) {
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
            throw new GoogleOperationException("Failed to $action $resource after #$tries."
              + " Last seen exception has status code ${lastSeenException.getStatusCode()} with error message ${lastSeenError.getMessage()}"
              + " and reason ${lastSeenError.getReason()}.")
          }
        } else {
          throw new GoogleOperationException("Failed to $action $resource after #$tries."
            + " Last seen exception has status code ${lastSeenException.getStatusCode()} with message ${lastSeenException.getMessage()}.")
        }
      } else if (lastSeenException && lastSeenException instanceof SocketTimeoutException) {
        throw new GoogleOperationException("Failed to $action $resource after #$tries."
          + " Last operation timed out.")
      } else {
        throw new IllegalStateException("Caught exception is neither a JsonResponseException nor a OperationTimedOutException."
          + " Caught exception: ${lastSeenException}")
      }
    }
  }
}

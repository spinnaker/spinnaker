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

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationException
import com.netflix.spinnaker.clouddriver.googlecommon.deploy.GoogleCommonSafeRetry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

// TODO(jacobkiefer): This used to have a generic return type associated with 'doRetry'. Find a way to reincorporate while still making this a Bean.
@Component
class SafeRetry extends GoogleCommonSafeRetry {

  @Value('${google.safe-retry-max-wait-interval-ms:60000}')
  Long maxWaitInterval

  @Value('${google.safe-retry-retry-interval-base-sec:2}')
  Long retryIntervalBase

  @Value('${google.safe-retry-jitter-multiplier:1000}')
  Long jitterMultiplier

  @Value('${google.safe-retry-max-retries:10}')
  Long maxRetries

  public Object doRetry(Closure operation,
                        String resource,
                        Task task,
                        List<Integer> retryCodes,
                        List<Integer> successfulErrorCodes,
                        Map tags,
                        Registry registry) {
    task?.updateStatus tags.phase, "Attempting $tags.action of $resource..."
    return super.doRetry(operation,
                         resource,
                         retryCodes,
                         successfulErrorCodes,
                         maxWaitInterval,
                         retryIntervalBase,
                         jitterMultiplier,
                         maxRetries,
                         tags,
                         registry)
  }

  @Override
  GoogleOperationException providerOperationException(String message) {
    new GoogleOperationException(message)
  }
}

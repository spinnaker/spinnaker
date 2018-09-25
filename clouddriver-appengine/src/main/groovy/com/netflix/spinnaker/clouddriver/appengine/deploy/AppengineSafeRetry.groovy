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

package com.netflix.spinnaker.clouddriver.appengine.deploy

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.appengine.deploy.exception.AppengineOperationException
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.googlecommon.deploy.GoogleCommonSafeRetry
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class AppengineSafeRetry extends GoogleCommonSafeRetry {
  @Value('${appengine.safeRetryMaxWaitIntervalMs:60000}')
  Long maxWaitInterval

  @Value('${appengine.safeRetryRetryIntervalBaseSec:2}')
  Long retryIntervalBase

  @Value('${appengine.safeRetryJitterMultiplier:1000}')
  Long jitterMultiplier

  @Value('${appengine.safeRetryMaxRetries:10}')
  Long maxRetries

  public Object doRetry(Closure operation,
                        String resource,
                        Task task,
                        List<Integer> retryCodes,
                        List<Integer> successfulErrorCodes,
                        Map tags,
                        Registry registry) {
    task?.updateStatus tags.phase, "Attempting $tags.action of $resource..."
    return super.doRetry(
      operation,
      resource,
      retryCodes,
      successfulErrorCodes,
      maxWaitInterval,
      retryIntervalBase,
      jitterMultiplier,
      maxRetries,
      tags,
      registry
    )
  }

  @Override
  AppengineOperationException providerOperationException(String message) {
    new AppengineOperationException(message)
  }
}

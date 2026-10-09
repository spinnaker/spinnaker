/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.clouddriver.orchestration;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * This instance is already running as many operations as it is configured to. Reported as 503 with
 * {@code Retry-After} rather than the default 500, so callers treat it as transient: Orca retries a
 * 503 on any request method, but fails the stage on a 500.
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class OperationsSaturatedException extends RuntimeException {

  private final int retryAfterSeconds;

  public OperationsSaturatedException(int maxThreads, int retryAfterSeconds) {
    super("Clouddriver is already running its limit of " + maxThreads + " operations");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public int getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}

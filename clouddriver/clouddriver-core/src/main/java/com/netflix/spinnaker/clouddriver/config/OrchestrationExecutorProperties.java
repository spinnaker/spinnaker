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

package com.netflix.spinnaker.clouddriver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Limits on how many atomic operations a clouddriver instance runs at once. */
@ConfigurationProperties("clouddriver.operations.executor")
@Data
public class OrchestrationExecutorProperties {

  /**
   * The most operations one instance runs concurrently. Further requests are rejected with a 503
   * until one finishes, instead of starting an unbounded number of threads.
   *
   * <p>Operations are I/O bound and often last minutes, so this is sized well above the number a
   * typical instance runs at once (tens to low hundreds), not by CPU count.
   */
  private int maxThreads = 500;

  /**
   * Operations that may wait for a free thread before requests are rejected. Zero (the default)
   * rejects immediately: a queued operation looks {@code STARTED} to Orca without making progress.
   */
  private int queueCapacity = 0;

  /** Sent as {@code Retry-After} when a request is rejected. */
  private int retryAfterSeconds = 5;
}

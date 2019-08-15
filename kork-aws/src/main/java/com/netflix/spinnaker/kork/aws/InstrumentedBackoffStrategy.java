/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.kork.aws;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.retry.PredefinedRetryPolicies;
import com.amazonaws.retry.RetryPolicy;
import com.netflix.spectator.api.Registry;
import java.util.Objects;

public class InstrumentedBackoffStrategy implements RetryPolicy.BackoffStrategy {
  private final Registry registry;
  private final RetryPolicy.BackoffStrategy delegate;

  public InstrumentedBackoffStrategy(Registry registry) {
    this(registry, PredefinedRetryPolicies.DEFAULT_BACKOFF_STRATEGY);
  }

  public InstrumentedBackoffStrategy(Registry registry, RetryPolicy.BackoffStrategy delegate) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  public long delayBeforeNextRetry(
      AmazonWebServiceRequest originalRequest,
      AmazonClientException exception,
      int retriesAttempted) {
    long delay = delegate.delayBeforeNextRetry(originalRequest, exception, retriesAttempted);
    registry
        .distributionSummary(
            "AWS_delay", AwsMetricsSupport.buildExceptionTags(originalRequest, exception))
        .record(delay);
    return delay;
  }
}

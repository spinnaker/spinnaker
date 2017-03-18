/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.batch.retry

import groovy.transform.CompileStatic
import org.springframework.retry.RetryPolicy
import org.springframework.retry.policy.AlwaysRetryPolicy
import org.springframework.retry.policy.ExceptionClassifierRetryPolicy

/**
 * A retry policy that will <em>always</em> retry when a step throws
 * {@link PollRequiresRetry} and <em>never</em> retry for other exception types.
 */
@CompileStatic
class PollingRetryPolicy implements RetryPolicy {

  private static final Map<Class, RetryPolicy> policyMap = [
    (PollRequiresRetry): new AlwaysRetryPolicy()
  ] as Map<Class, RetryPolicy>

  @Delegate private final RetryPolicy delegate

  PollingRetryPolicy() {
    delegate = new ExceptionClassifierRetryPolicy(
      policyMap: policyMap
    )
  }
}

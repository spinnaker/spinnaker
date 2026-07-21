/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.security.sdkclient;

import java.time.Duration;
import lombok.Builder;
import lombok.Value;

/**
 * Per-service tuning applied to an AWS SDK v2 client built by {@link AwsSdkV2ClientSupplier}. This
 * is the v2 equivalent of the v1 {@code com.amazonaws.ClientConfiguration} that callers used to
 * pass to {@code AmazonClientProvider}. All fields are optional; {@code null} means "leave the
 * supplier default in place".
 *
 * <p>This type participates in the supplier's cache identity (via value-based {@code equals}/{@code
 * hashCode}), so clients requested with different tuning for the same account, region, and service
 * resolve to distinct cached instances. This lets a single caller obtain, for example, a
 * short-timeout client for most calls and a long-timeout client for synchronous invocations.
 */
@Value
@Builder
public class AwsSdkV2ClientConfiguration {

  /**
   * Maximum number of retries. When set, overrides the number of retries on the supplier's shared
   * retry policy while keeping its backoff strategy.
   */
  Integer maxErrorRetry;

  /** Socket timeout applied to the underlying Apache HTTP client. */
  Duration socketTimeout;

  /** Whether to enable TCP keep-alive on the underlying Apache HTTP client. */
  @Builder.Default boolean tcpKeepAlive = false;
}
